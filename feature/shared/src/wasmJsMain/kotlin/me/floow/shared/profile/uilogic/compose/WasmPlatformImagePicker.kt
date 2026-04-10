package me.floow.shared.profile.uilogic.compose

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.shared.profile.auth.flowWasmPickImages
import me.floow.shared.profile.auth.flowWasmPickSingleImage
import kotlin.coroutines.resume

class WasmPlatformImagePicker : PlatformImagePicker {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> {
        if (maxItems <= 0) return emptyList()
        return suspendCancellableCoroutine { continuation ->
            flowWasmPickImages(
                maxItems = maxItems,
                onSuccess = { raw ->
                    val items = runCatching {
                        json.decodeFromString<List<WasmPickedImageDto>>(raw)
                    }.getOrDefault(emptyList())
                    continuation.resume(items.map(WasmPickedImageDto::toModel))
                },
                onError = {
                    continuation.resume(emptyList())
                }
            )
        }
    }

    override suspend fun pickSingleImage(): PlatformPickedImage? {
        return suspendCancellableCoroutine { continuation ->
            flowWasmPickSingleImage(
                onSuccess = { raw ->
                    val item = runCatching {
                        json.decodeFromString<WasmPickedImageDto?>(raw)
                    }.getOrNull()
                    continuation.resume(item?.toModel())
                },
                onError = {
                    continuation.resume(null)
                }
            )
        }
    }
}

@Serializable
private data class WasmPickedImageDto(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val previewUri: String,
)

private fun WasmPickedImageDto.toModel(): PlatformPickedImage {
    return PlatformPickedImage(
        id = id,
        name = name,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        previewUri = previewUri,
    )
}
