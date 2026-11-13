package me.floow.shared.profile.uilogic.compose

data class PlatformPickedImage(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val previewUri: String,
)

interface PlatformImagePicker {
    suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage>

    suspend fun pickSingleImage(): PlatformPickedImage?
}

object NoOpPlatformImagePicker : PlatformImagePicker {
    override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> = emptyList()
    override suspend fun pickSingleImage(): PlatformPickedImage? = null
}
