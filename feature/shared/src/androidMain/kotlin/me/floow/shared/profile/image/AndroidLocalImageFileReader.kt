package me.floow.shared.profile.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class AndroidLocalImageFileReader(
    private val context: Context
) : LocalImageFileReader {
    override suspend fun read(uriString: String): LocalImageFile? = withContext(Dispatchers.IO) {
        when (val result = readInternal(uriString, UploadTarget.POST)) {
            is LocalImageReadResult.Success -> result.file
            is LocalImageReadResult.Failure -> null
        }
    }

    override suspend fun readPost(uriString: String): LocalImageReadResult = withContext(Dispatchers.IO) {
        readInternal(uriString, UploadTarget.POST)
    }

    override suspend fun readAvatar(uriString: String): LocalImageReadResult = withContext(Dispatchers.IO) {
        readInternal(uriString, UploadTarget.AVATAR)
    }

    override suspend fun readBackground(uriString: String): LocalImageReadResult = withContext(Dispatchers.IO) {
        readInternal(uriString, UploadTarget.BACKGROUND)
    }

    private fun readInternal(uriString: String, target: UploadTarget): LocalImageReadResult {
        val uri = runCatching { android.net.Uri.parse(uriString) }
            .getOrNull()
            ?: return LocalImageReadResult.Failure(LocalImageReadError.CORRUPTED_IMAGE)
        val resolver = context.contentResolver

        val mimeType = resolver.getType(uri)?.lowercase()?.trim()

        var displayName: String? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx)
                }
            }
        }

        val sourceBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return LocalImageReadResult.Failure(LocalImageReadError.CORRUPTED_IMAGE)
        if (sourceBytes.isEmpty()) {
            return LocalImageReadResult.Failure(LocalImageReadError.CORRUPTED_IMAGE)
        }

        val prepared = when (target) {
            UploadTarget.POST -> preparePostUpload(sourceBytes = sourceBytes, mimeType = mimeType)
            UploadTarget.AVATAR -> prepareAvatarUpload(sourceBytes = sourceBytes, mimeType = mimeType)
            UploadTarget.BACKGROUND -> prepareBackgroundUpload(sourceBytes = sourceBytes, mimeType = mimeType)
        } ?: return LocalImageReadResult.Failure(
            when (target) {
                UploadTarget.POST -> LocalImageReadError.UNSUPPORTED_TYPE
                UploadTarget.AVATAR -> classifyAvatarFailure(sourceBytes = sourceBytes, mimeType = mimeType)
                UploadTarget.BACKGROUND -> classifyBackgroundFailure(sourceBytes = sourceBytes, mimeType = mimeType)
            }
        )

        val fileName = normalizeFileName(displayName, prepared.contentType)
        return LocalImageReadResult.Success(
            LocalImageFile(
                fileName = fileName,
                contentType = prepared.contentType,
                sizeBytes = prepared.bytes.size.toLong(),
                bytes = prepared.bytes
            )
        )
    }

    private fun preparePostUpload(sourceBytes: ByteArray, mimeType: String?): PreparedUpload? {
        val sourceSize = decodeImageSize(sourceBytes)
        val shouldKeepOriginal =
            mimeType in allowedPostMimeTypes &&
                sourceBytes.size <= preferredPostUploadBytes &&
                sourceSize != null &&
                maxOf(sourceSize.first, sourceSize.second) <= preferredPostMaxDimensionPx
        if (shouldKeepOriginal) {
            return PreparedUpload(contentType = mimeType.orEmpty(), bytes = sourceBytes)
        }

        if (mimeType?.startsWith("image/") == true || mimeType == null) {
            val jpegBytes = transcodeToJpegWithinLimit(
                sourceBytes = sourceBytes,
                maxBytes = maxPostUploadBytes,
                maxDimensionCandidates = postMaxDimensionCandidates,
                qualityCandidates = postJpegQualityCandidates
            ) ?: return null
            return PreparedUpload(contentType = "image/jpeg", bytes = jpegBytes)
        }

        return null
    }

    private fun prepareAvatarUpload(sourceBytes: ByteArray, mimeType: String?): PreparedUpload? {
        val normalizedMime = mimeType?.lowercase()?.trim()
        if (normalizedMime != null && normalizedMime !in supportedAvatarSourceMimeTypes) {
            return null
        }
        if (normalizedMime == "image/gif") {
            if (sourceBytes.size > maxAvatarUploadBytes) {
                return null
            }
            return PreparedUpload(contentType = "image/gif", bytes = sourceBytes)
        }

        val webpBytes = transcodeToWebpWithinLimit(
            sourceBytes = sourceBytes,
            maxBytes = maxAvatarUploadBytes,
            maxDimensionCandidates = avatarMaxDimensionCandidates,
            qualityCandidates = avatarWebpQualityCandidates,
            targetAspectRatio = null
        ) ?: return null
        return PreparedUpload(contentType = "image/webp", bytes = webpBytes)
    }

    private fun prepareBackgroundUpload(sourceBytes: ByteArray, mimeType: String?): PreparedUpload? {
        val normalizedMime = mimeType?.lowercase()?.trim()
        if (normalizedMime != null && normalizedMime !in supportedBackgroundSourceMimeTypes) {
            return null
        }

        val webpBytes = transcodeToWebpWithinLimit(
            sourceBytes = sourceBytes,
            maxBytes = maxBackgroundUploadBytes,
            maxDimensionCandidates = backgroundMaxDimensionCandidates,
            qualityCandidates = backgroundWebpQualityCandidates,
            targetAspectRatio = backgroundHeroAspectRatio
        ) ?: return null
        return PreparedUpload(contentType = "image/webp", bytes = webpBytes)
    }

    private fun classifyAvatarFailure(sourceBytes: ByteArray, mimeType: String?): LocalImageReadError {
        val normalizedMime = mimeType?.lowercase()?.trim()
        if (normalizedMime != null && normalizedMime !in supportedAvatarSourceMimeTypes) {
            return LocalImageReadError.UNSUPPORTED_TYPE
        }
        if (normalizedMime == "image/gif" && sourceBytes.size > maxAvatarUploadBytes) {
            return LocalImageReadError.FILE_TOO_LARGE
        }
        if (sourceBytes.size > maxAvatarUploadBytes) {
            return LocalImageReadError.FILE_TOO_LARGE
        }
        return LocalImageReadError.CORRUPTED_IMAGE
    }

    private fun classifyBackgroundFailure(sourceBytes: ByteArray, mimeType: String?): LocalImageReadError {
        val normalizedMime = mimeType?.lowercase()?.trim()
        if (normalizedMime != null && normalizedMime !in supportedBackgroundSourceMimeTypes) {
            return LocalImageReadError.UNSUPPORTED_TYPE
        }
        if (sourceBytes.size > maxBackgroundUploadBytes) {
            return LocalImageReadError.FILE_TOO_LARGE
        }
        return LocalImageReadError.CORRUPTED_IMAGE
    }

    private fun transcodeToJpegWithinLimit(
        sourceBytes: ByteArray,
        maxBytes: Int,
        maxDimensionCandidates: IntArray,
        qualityCandidates: IntArray
    ): ByteArray? {
        val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
        val orientation = readExifOrientation(sourceBytes)
        val orientedBitmap = normalizeBitmapOrientation(sourceBitmap, orientation)
        return try {
            for (maxDimension in maxDimensionCandidates) {
                val resized = resizeBitmapIfNeeded(orientedBitmap, maxDimension)
                try {
                    for (quality in qualityCandidates) {
                        val encoded = compressToJpeg(resized, quality) ?: continue
                        if (encoded.size <= maxBytes) {
                            return encoded
                        }
                    }
                } finally {
                    if (resized !== orientedBitmap) {
                        resized.recycle()
                    }
                }
            }
            null
        } finally {
            if (orientedBitmap !== sourceBitmap) {
                orientedBitmap.recycle()
            }
            sourceBitmap.recycle()
        }
    }

    private fun transcodeToWebpWithinLimit(
        sourceBytes: ByteArray,
        maxBytes: Int,
        maxDimensionCandidates: IntArray,
        qualityCandidates: IntArray,
        targetAspectRatio: Float?
    ): ByteArray? {
        val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size) ?: return null
        return try {
            val croppedSource = if (targetAspectRatio != null) {
                cropCenterToAspect(sourceBitmap, targetAspectRatio)
            } else {
                sourceBitmap
            }
            try {
                for (maxDimension in maxDimensionCandidates) {
                    val resized = resizeBitmapIfNeeded(croppedSource, maxDimension)
                    try {
                        for (quality in qualityCandidates) {
                            val encoded = compressToWebp(resized, quality) ?: continue
                            if (encoded.size <= maxBytes) {
                                return encoded
                            }
                        }
                    } finally {
                        if (resized !== croppedSource) {
                            resized.recycle()
                        }
                    }
                }
                null
            } finally {
                if (croppedSource !== sourceBitmap) {
                    croppedSource.recycle()
                }
            }
        } finally {
            sourceBitmap.recycle()
        }
    }

    private fun readExifOrientation(sourceBytes: ByteArray): Int {
        return runCatching {
            ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private fun normalizeBitmapOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun cropCenterToAspect(bitmap: Bitmap, targetAspectRatio: Float): Bitmap {
        val currentAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (kotlin.math.abs(currentAspect - targetAspectRatio) < 0.01f) return bitmap

        return if (currentAspect > targetAspectRatio) {
            val targetWidth = (bitmap.height * targetAspectRatio).toInt().coerceAtLeast(1)
            val xOffset = ((bitmap.width - targetWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, xOffset, 0, targetWidth, bitmap.height)
        } else {
            val targetHeight = (bitmap.width / targetAspectRatio).toInt().coerceAtLeast(1)
            val yOffset = ((bitmap.height - targetHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(bitmap, 0, yOffset, bitmap.width, targetHeight)
        }
    }

    private fun compressToJpeg(bitmap: Bitmap, quality: Int): ByteArray? {
        return ByteArrayOutputStream().use { output ->
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                output.toByteArray()
            } else {
                null
            }
        }
    }

    private fun compressToWebp(bitmap: Bitmap, quality: Int): ByteArray? {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        return ByteArrayOutputStream().use { output ->
            if (bitmap.compress(format, quality, output)) {
                output.toByteArray()
            } else {
                null
            }
        }
    }

    private fun decodeImageSize(bytes: ByteArray): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val width = options.outWidth
        val height = options.outHeight
        return if (width > 0 && height > 0) width to height else null
    }

    private fun normalizeFileName(displayName: String?, contentType: String): String {
        val fallbackExtension = when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "bin"
        }
        val sanitized = displayName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (sanitized != null && sanitized.contains('.')) return sanitized
        return "image_upload.$fallbackExtension"
    }

    private enum class UploadTarget {
        POST,
        AVATAR,
        BACKGROUND,
    }

    private data class PreparedUpload(
        val contentType: String,
        val bytes: ByteArray
    )

    private companion object {
        const val preferredPostUploadBytes = 1_500_000
        const val maxPostUploadBytes = 2_000_000
        const val preferredPostMaxDimensionPx = 1600
        const val maxAvatarUploadBytes = 1_000_000
        const val maxBackgroundUploadBytes = 2_000_000
        const val backgroundHeroAspectRatio = 2f / 3f

        val allowedPostMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
        val supportedAvatarSourceMimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif")
        val supportedBackgroundSourceMimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

        val postMaxDimensionCandidates = intArrayOf(1600, 1440, 1280, 1152, 1024, 896, 768, 640)
        val postJpegQualityCandidates = intArrayOf(90, 82, 74, 66, 58, 50, 42)
        val avatarMaxDimensionCandidates = intArrayOf(1024, 896, 768, 640, 512, 384, 320)
        val avatarWebpQualityCandidates = intArrayOf(88, 80, 72, 64, 56, 48, 40)
        val backgroundMaxDimensionCandidates = intArrayOf(1536, 1280, 1152, 1024, 896, 768, 640)
        val backgroundWebpQualityCandidates = intArrayOf(90, 82, 74, 66, 58, 50, 42)
    }
}
