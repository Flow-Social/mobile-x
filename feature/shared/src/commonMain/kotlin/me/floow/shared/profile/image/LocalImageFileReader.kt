package me.floow.shared.profile.image

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

    suspend fun readPost(uriString: String): LocalImageReadResult {
        val file = read(uriString) ?: return LocalImageReadResult.Failure(LocalImageReadError.CORRUPTED_IMAGE)
        return LocalImageReadResult.Success(file)
    }

    suspend fun readAvatar(uriString: String): LocalImageReadResult

    suspend fun readBackground(uriString: String): LocalImageReadResult
}
