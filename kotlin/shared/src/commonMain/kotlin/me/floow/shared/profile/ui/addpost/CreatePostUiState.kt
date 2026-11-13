package me.floow.shared.profile.ui.addpost

import androidx.compose.runtime.Immutable

@Immutable
data class CreatePostImageItem(
    val id: String,
    val uri: String,
    val isLocalReplacement: Boolean = false,
)

@Immutable
data class CreatePostCategoryItem(
    val code: String,
    val title: String,
)

@Immutable
data class CreatePostUiState(
    val description: String,
    val selectedImages: List<CreatePostImageItem>,
    val categories: List<CreatePostCategoryItem>,
    val selectedCategoryCode: String?,
    val isCategoriesLoading: Boolean,
    val isCategoriesError: Boolean,
    val isPublishing: Boolean,
    val publishingStage: String?,
    val uploadProgress: Int,
    val uploadTotal: Int,
    val errorMessage: String?,
    val canPublish: Boolean,
    val maxImages: Int = 4,
    val hasManualUrlsInDraft: Boolean = false,
) {
    val hasImages: Boolean
        get() = selectedImages.isNotEmpty()

    val canAddMoreImages: Boolean
        get() = selectedImages.size < maxImages

    val hasDraft: Boolean
        get() = description.isNotBlank() || hasImages || hasManualUrlsInDraft

    val selectedCategoryTitle: String?
        get() = categories.firstOrNull { it.code == selectedCategoryCode }?.title
}
