package me.floow.shared.profile.ui.addpost

import androidx.compose.runtime.Composable

data class CreatePostGalleryImage(
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isCurrentPostImage: Boolean = false,
)

data class CreatePostGalleryState(
    val images: List<CreatePostGalleryImage>,
    val isLoading: Boolean,
    val hasPermission: Boolean,
    val requestPermission: () -> Unit,
)

@Composable
expect fun rememberCreatePostGalleryState(): CreatePostGalleryState
