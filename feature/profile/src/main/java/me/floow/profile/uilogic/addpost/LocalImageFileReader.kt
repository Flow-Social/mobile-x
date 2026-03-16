package me.floow.profile.uilogic.addpost

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

data class LocalImageFile(
	val fileName: String,
	val contentType: String,
	val sizeBytes: Long,
	val bytes: ByteArray
)

enum class LocalImageReadError {
	UNSUPPORTED_TYPE,
	FILE_TOO_LARGE,
	CORRUPTED_IMAGE,
}

sealed interface LocalImageReadResult {
	data class Success(val file: LocalImageFile) : LocalImageReadResult
	data class Failure(val error: LocalImageReadError) : LocalImageReadResult
}

interface LocalImageFileReader {
	suspend fun read(uriString: String): LocalImageFile?

	suspend fun readAvatar(uriString: String): LocalImageReadResult

	suspend fun readBackground(uriString: String): LocalImageReadResult
}

class AndroidLocalImageFileReader(
	private val context: Context
) : LocalImageFileReader {
	override suspend fun read(uriString: String): LocalImageFile? = withContext(Dispatchers.IO) {
		when (val result = readInternal(uriString, UploadTarget.POST)) {
			is LocalImageReadResult.Success -> result.file
			is LocalImageReadResult.Failure -> null
		}
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

	private fun cropCenterToAspect(source: Bitmap, targetAspectRatio: Float): Bitmap {
		if (targetAspectRatio <= 0f) return source
		val sourceAspect = source.width.toFloat() / source.height.toFloat()
		if (kotlin.math.abs(sourceAspect - targetAspectRatio) < 0.01f) {
			return source
		}
		return if (sourceAspect > targetAspectRatio) {
			val targetWidth = (source.height * targetAspectRatio).toInt().coerceAtLeast(1)
			val offsetX = ((source.width - targetWidth) / 2).coerceAtLeast(0)
			Bitmap.createBitmap(source, offsetX, 0, targetWidth.coerceAtMost(source.width), source.height)
		} else {
			val targetHeight = (source.width / targetAspectRatio).toInt().coerceAtLeast(1)
			val offsetY = ((source.height - targetHeight) / 2).coerceAtLeast(0)
			Bitmap.createBitmap(source, 0, offsetY, source.width, targetHeight.coerceAtMost(source.height))
		}
	}

	private fun resizeBitmapIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
		val currentMax = maxOf(source.width, source.height)
		if (currentMax <= maxDimension) {
			return source
		}
		val ratio = maxDimension.toFloat() / currentMax.toFloat()
		val targetWidth = (source.width * ratio).toInt().coerceAtLeast(1)
		val targetHeight = (source.height * ratio).toInt().coerceAtLeast(1)
		return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
	}

	private fun compressToJpeg(bitmap: Bitmap, quality: Int): ByteArray? {
		val output = ByteArrayOutputStream()
		val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
		if (!compressed) return null
		return output.toByteArray()
	}

	private fun readExifOrientation(sourceBytes: ByteArray): Int {
		return runCatching {
			ExifInterface(ByteArrayInputStream(sourceBytes)).getAttributeInt(
				ExifInterface.TAG_ORIENTATION,
				ExifInterface.ORIENTATION_UNDEFINED
			)
		}.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
	}

	private fun normalizeBitmapOrientation(source: Bitmap, orientation: Int): Bitmap {
		if (orientation == ExifInterface.ORIENTATION_UNDEFINED || orientation == ExifInterface.ORIENTATION_NORMAL) {
			return source
		}

		val matrix = Matrix()
		when (orientation) {
			ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
				matrix.setScale(-1f, 1f)
				matrix.postTranslate(source.width.toFloat(), 0f)
			}
			ExifInterface.ORIENTATION_ROTATE_180 -> {
				matrix.setRotate(180f)
				matrix.postTranslate(source.width.toFloat(), source.height.toFloat())
			}
			ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
				matrix.setScale(1f, -1f)
				matrix.postTranslate(0f, source.height.toFloat())
			}
			ExifInterface.ORIENTATION_TRANSPOSE -> {
				matrix.setRotate(90f)
				matrix.postScale(-1f, 1f)
				matrix.postTranslate(source.height.toFloat(), 0f)
			}
			ExifInterface.ORIENTATION_ROTATE_90 -> {
				matrix.setRotate(90f)
				matrix.postTranslate(source.height.toFloat(), 0f)
			}
			ExifInterface.ORIENTATION_TRANSVERSE -> {
				matrix.setRotate(270f)
				matrix.postScale(-1f, 1f)
				matrix.postTranslate(0f, source.width.toFloat())
			}
			ExifInterface.ORIENTATION_ROTATE_270 -> {
				matrix.setRotate(270f)
				matrix.postTranslate(0f, source.width.toFloat())
			}
			else -> return source
		}

		return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
	}

	@Suppress("DEPRECATION")
	private fun compressToWebp(bitmap: Bitmap, quality: Int): ByteArray? {
		val output = ByteArrayOutputStream()
		val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			Bitmap.CompressFormat.WEBP_LOSSY
		} else {
			Bitmap.CompressFormat.WEBP
		}
		val compressed = bitmap.compress(format, quality, output)
		if (!compressed) return null
		return output.toByteArray()
	}

	private fun normalizeFileName(rawName: String?, contentType: String): String {
		val ext = when (contentType) {
			"image/jpeg" -> "jpg"
			"image/png" -> "png"
			"image/webp" -> "webp"
			"image/gif" -> "gif"
			else -> "jpg"
		}

		val base = rawName
			?.trim()
			?.substringBeforeLast('.')
			?.replace(Regex("\\s+"), "_")
			?.takeIf { it.isNotBlank() }
			?: "image_${System.currentTimeMillis()}"

		return "$base.$ext"
	}

	private data class PreparedUpload(
		val contentType: String,
		val bytes: ByteArray,
	)

	private fun decodeImageSize(sourceBytes: ByteArray): Pair<Int, Int>? {
		val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
		BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
		val width = bounds.outWidth
		val height = bounds.outHeight
		if (width <= 0 || height <= 0) return null
		return width to height
	}

	private enum class UploadTarget {
		POST,
		AVATAR,
		BACKGROUND,
	}

	private companion object {
		val allowedPostMimeTypes = setOf("image/jpeg", "image/png", "image/webp")
		val supportedAvatarSourceMimeTypes = setOf(
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/gif",
			"image/heic",
			"image/heif"
		)
		val supportedBackgroundSourceMimeTypes = setOf(
			"image/jpeg",
			"image/png",
			"image/webp",
			"image/heic",
			"image/heif"
		)
		const val maxPostUploadBytes = 10 * 1024 * 1024
		const val preferredPostUploadBytes = 2 * 1024 * 1024
		const val preferredPostMaxDimensionPx = 1920
		const val maxAvatarUploadBytes = 1 * 1024 * 1024
		const val maxBackgroundUploadBytes = 2 * 1024 * 1024

		val postMaxDimensionCandidates = intArrayOf(1920, 1600, 1440, 1280, 1080, 960, 800)
		val postJpegQualityCandidates = intArrayOf(90, 84, 78, 72, 66, 60, 54)

		val avatarMaxDimensionCandidates = intArrayOf(1024, 896, 768, 640, 512, 384, 320)
		val avatarWebpQualityCandidates = intArrayOf(88, 80, 72, 64, 56, 48, 40)
		val backgroundMaxDimensionCandidates = intArrayOf(1536, 1280, 1152, 1024, 896, 768, 640)
		val backgroundWebpQualityCandidates = intArrayOf(90, 82, 74, 66, 58, 50, 42)
		const val backgroundHeroAspectRatio = 2f / 3f
	}
}
