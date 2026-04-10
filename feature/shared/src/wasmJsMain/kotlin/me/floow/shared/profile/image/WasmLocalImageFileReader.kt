package me.floow.shared.profile.image

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.shared.profile.auth.atob
import me.floow.shared.profile.auth.flowWasmReadPickedFile
import kotlin.coroutines.resume

class WasmLocalImageFileReader : LocalImageFileReader {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(uriString: String): LocalImageFile? {
        return when (val result = readPost(uriString)) {
            is LocalImageReadResult.Success -> result.file
            is LocalImageReadResult.Failure -> null
        }
    }

    override suspend fun readPost(uriString: String): LocalImageReadResult {
        return readValidated(
            handleId = uriString,
            allowedMimeTypes = allowedPostMimeTypes,
            maxBytes = maxPostUploadBytes,
        )
    }

    override suspend fun readAvatar(uriString: String): LocalImageReadResult {
        return readValidated(
            handleId = uriString,
            allowedMimeTypes = supportedAvatarMimeTypes,
            maxBytes = maxAvatarUploadBytes,
        )
    }

    override suspend fun readBackground(uriString: String): LocalImageReadResult {
        return readValidated(
            handleId = uriString,
            allowedMimeTypes = supportedBackgroundMimeTypes,
            maxBytes = maxBackgroundUploadBytes,
        )
    }

    private suspend fun readPickedFile(handleId: String): LocalImageFile {
        val raw = suspendCancellableCoroutine<String> { continuation ->
            flowWasmReadPickedFile(
                fileId = handleId,
                onSuccess = { continuation.resume(it) },
                onError = { message ->
                    continuation.resume("""{"error":"$message"}""")
                }
            )
        }
        val payload = json.decodeFromString<PickedFilePayload>(raw)
        if (!payload.error.isNullOrBlank()) error(payload.error)
        return LocalImageFile(
            fileName = payload.name ?: "image.jpg",
            contentType = payload.mimeType ?: "application/octet-stream",
            sizeBytes = payload.sizeBytes ?: 0L,
            bytes = decodeBase64(payload.base64.orEmpty()),
        )
    }

    private suspend fun readValidated(
        handleId: String,
        allowedMimeTypes: Set<String>,
        maxBytes: Long,
    ): LocalImageReadResult {
        val file = runCatching { readPickedFile(handleId) }
            .getOrElse { return LocalImageReadResult.Failure(LocalImageReadError.CORRUPTED_IMAGE) }
        val normalizedMimeType = file.contentType.lowercase().trim()
        if (normalizedMimeType !in allowedMimeTypes) {
            return LocalImageReadResult.Failure(LocalImageReadError.UNSUPPORTED_TYPE)
        }
        if (file.sizeBytes > maxBytes) {
            return LocalImageReadResult.Failure(LocalImageReadError.FILE_TOO_LARGE)
        }
        return LocalImageReadResult.Success(file)
    }

    private fun decodeBase64(value: String): ByteArray {
        val binary = atob(value)
        return ByteArray(binary.length) { index ->
            binary[index].code.toByte()
        }
    }
}

@Serializable
private data class PickedFilePayload(
    val error: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val base64: String? = null,
)

private const val maxPostUploadBytes = 2_000_000L
private const val maxAvatarUploadBytes = 1 * 1024 * 1024L
private const val maxBackgroundUploadBytes = 2 * 1024 * 1024L
private val allowedPostMimeTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
)
private val supportedAvatarMimeTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/heif",
    "image/gif",
)
private val supportedBackgroundMimeTypes = setOf(
    "image/jpeg",
    "image/png",
    "image/webp",
    "image/heic",
    "image/heif",
)
