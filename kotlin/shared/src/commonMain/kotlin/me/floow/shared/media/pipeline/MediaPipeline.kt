package me.floow.shared.media.pipeline

enum class MediaOutputFormat {
    JPEG,
    WEBP,
    PNG,
}

data class MediaUploadPolicy(
    val kind: String,
    val maxDimensionPx: Int,
    val maxFileSizeBytes: Long,
    val jpegQuality: Int = 82,
    val outputFormat: MediaOutputFormat = MediaOutputFormat.JPEG,
    val allowedInputMimeTypes: Set<String> = defaultAllowedMimeTypes,
) {
    companion object {
        val defaultAllowedMimeTypes = setOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/heic",
            "image/heif",
            "image/gif",
        )

        val AVATAR = MediaUploadPolicy(
            kind = "avatar",
            maxDimensionPx = 1024,
            maxFileSizeBytes = 1_000_000L,
            jpegQuality = 80,
        )

        val BACKGROUND = MediaUploadPolicy(
            kind = "background",
            maxDimensionPx = 2048,
            maxFileSizeBytes = 2_000_000L,
            jpegQuality = 80,
        )

        val POST = MediaUploadPolicy(
            kind = "post",
            maxDimensionPx = 2048,
            maxFileSizeBytes = 2_000_000L,
            jpegQuality = 82,
        )

        val CHAT_IMAGE = MediaUploadPolicy(
            kind = "chat_image",
            maxDimensionPx = 2048,
            maxFileSizeBytes = 2_000_000L,
            jpegQuality = 82,
        )
    }
}

data class PreparedMediaFile(
    val filePath: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val microThumbnail: String? = null,
)

enum class MediaPreparationError {
    FILE_NOT_FOUND,
    UNSUPPORTED_TYPE,
    FILE_TOO_LARGE,
    CORRUPTED_IMAGE,
}

sealed interface MediaPreparationResult {
    data class Success(val media: PreparedMediaFile) : MediaPreparationResult
    data class Failure(val error: MediaPreparationError) : MediaPreparationResult
}

interface LocalMediaPreparer {
    suspend fun prepareMedia(
        sourceUriOrPath: String,
        policy: MediaUploadPolicy,
    ): MediaPreparationResult
}

expect fun createLocalMediaPreparer(): LocalMediaPreparer

expect fun deletePreparedTempFile(filePath: String): Boolean
