package me.floow.shared.media.pipeline

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AndroidLocalMediaPreparer(
    private val context: Context,
) : LocalMediaPreparer {

    override suspend fun prepareMedia(
        sourceUriOrPath: String,
        policy: MediaUploadPolicy,
    ): MediaPreparationResult = withContext(Dispatchers.IO) {
        if (sourceUriOrPath.isBlank()) {
            return@withContext MediaPreparationResult.Failure(MediaPreparationError.FILE_NOT_FOUND)
        }

        val uri = if (sourceUriOrPath.startsWith("content://") || sourceUriOrPath.startsWith("file://")) {
            runCatching { Uri.parse(sourceUriOrPath) }.getOrNull()
        } else {
            Uri.fromFile(File(sourceUriOrPath))
        } ?: return@withContext MediaPreparationResult.Failure(MediaPreparationError.FILE_NOT_FOUND)

        val resolver = context.contentResolver
        val mimeType = runCatching { resolver.getType(uri)?.lowercase()?.trim() }.getOrNull()
            ?: guessMimeType(sourceUriOrPath)

        if (mimeType != null && mimeType !in policy.allowedInputMimeTypes && !mimeType.startsWith("image/")) {
            return@withContext MediaPreparationResult.Failure(MediaPreparationError.UNSUPPORTED_TYPE)
        }

        // 1. Read image bounds without allocating memory for pixels
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val streamOpened = runCatching {
            resolver.openInputStream(uri)?.buffered()?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
                true
            }
        }.getOrNull() ?: false

        if (!streamOpened || boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            val file = if (uri.scheme == "file") File(uri.path.orEmpty()) else File(sourceUriOrPath)
            if (file.exists() && file.canRead()) {
                BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            }
        }

        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            return@withContext MediaPreparationResult.Failure(MediaPreparationError.CORRUPTED_IMAGE)
        }

        // 2. Read EXIF orientation
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        // 3. Compute sample size to avoid allocating full resolution bitmap
        val maxDimension = policy.maxDimensionPx
        var sampleSize = 1
        val rawMax = maxOf(rawWidth, rawHeight)
        while (rawMax / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decodedBitmap = runCatching {
            resolver.openInputStream(uri)?.buffered()?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        }.getOrNull() ?: return@withContext MediaPreparationResult.Failure(MediaPreparationError.CORRUPTED_IMAGE)

        val orientedBitmap = normalizeOrientation(decodedBitmap, orientation)

        // 4. Resize if still larger than max dimension
        val currentMax = maxOf(orientedBitmap.width, orientedBitmap.height)
        val finalBitmap = if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax.toFloat()
            val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
            val resized = Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
            if (orientedBitmap !== decodedBitmap && orientedBitmap !== resized) {
                orientedBitmap.recycle()
            }
            resized
        } else {
            orientedBitmap
        }

        // 5. Compress directly into a temporary file in cacheDir
        val outputDir = File(context.cacheDir, "media_pipeline_cache").apply { mkdirs() }
        val tempFile = File(outputDir, "media_${UUID.randomUUID()}.jpg")

        val qualities = intArrayOf(policy.jpegQuality, 75, 65, 55, 45, 35)
        var compressedSuccess = false

        try {
            for (quality in qualities) {
                FileOutputStream(tempFile).use { out ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    out.flush()
                }
                if (tempFile.length() <= policy.maxFileSizeBytes) {
                    compressedSuccess = true
                    break
                }
            }

            if (!compressedSuccess && tempFile.length() > policy.maxFileSizeBytes) {
                tempFile.delete()
                return@withContext MediaPreparationResult.Failure(MediaPreparationError.FILE_TOO_LARGE)
            }

            val originalDisplayName = queryDisplayName(uri) ?: "image_${System.currentTimeMillis()}.jpg"
            val safeFileName = if (originalDisplayName.contains('.')) {
                originalDisplayName.substringBeforeLast('.') + ".jpg"
            } else {
                "$originalDisplayName.jpg"
            }

            // Generate Telegram-style stripped micro-thumbnail (16x16, ~40 bytes base64)
            val microThumbDataUri = runCatching {
                val maxDim = 16
                val aspect = finalBitmap.width.toFloat() / finalBitmap.height.toFloat()
                val (microW, microH) = if (aspect >= 1f) {
                    maxDim to (maxDim / aspect).toInt().coerceAtLeast(1)
                } else {
                    (maxDim * aspect).toInt().coerceAtLeast(1) to maxDim
                }
                val microBitmap = Bitmap.createScaledBitmap(finalBitmap, microW, microH, true)
                val microOut = java.io.ByteArrayOutputStream()
                microBitmap.compress(Bitmap.CompressFormat.JPEG, 30, microOut)
                val microBytes = microOut.toByteArray()
                if (microBitmap !== finalBitmap) microBitmap.recycle()
                "data:image/jpeg;base64," + android.util.Base64.encodeToString(microBytes, android.util.Base64.NO_WRAP)
            }.getOrNull()

            MediaPreparationResult.Success(
                PreparedMediaFile(
                    filePath = tempFile.absolutePath,
                    fileName = safeFileName,
                    contentType = "image/jpeg",
                    sizeBytes = tempFile.length(),
                    width = finalBitmap.width,
                    height = finalBitmap.height,
                    microThumbnail = microThumbDataUri,
                )
            )
        } finally {
            if (finalBitmap !== decodedBitmap) {
                finalBitmap.recycle()
            }
            decodedBitmap.recycle()
        }
    }

    private fun normalizeOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        return transformed
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        }.getOrNull()
    }

    private fun guessMimeType(path: String): String? {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".heic") || lower.endsWith(".heif") -> "image/heic"
            else -> null
        }
    }
}

actual fun createLocalMediaPreparer(): LocalMediaPreparer {
    val context = runCatching { me.floow.database.AndroidDatabaseContext.get() }.getOrNull()
        ?: me.floow.shared.auth.AndroidTokenContext.get()
        ?: org.koin.mp.KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<Context>()
        ?: throw IllegalStateException("Context not initialized in AndroidTokenContext or Koin")
    return AndroidLocalMediaPreparer(context)
}

actual fun deletePreparedTempFile(filePath: String): Boolean {
    val file = File(filePath)
    return if (file.exists() && file.isFile) file.delete() else false
}
