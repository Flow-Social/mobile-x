package me.floow.profile.ui.addpost

import androidx.compose.runtime.Immutable
import me.floow.profile.uilogic.addpost.AddPostVmState
import me.floow.uikit.theme.toDisplayTag

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
	val maxImages: Int = AddPostVmState.MAX_IMAGES,
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

fun AddPostVmState.toCreatePostUiState(allowManualUrls: Boolean): CreatePostUiState {
	return CreatePostUiState(
		description = description,
		selectedImages = localImageUris.map { uri ->
			CreatePostImageItem(
				id = uri,
				uri = uri,
				isLocalReplacement = false,
			)
		},
		categories = categories.map { category ->
			CreatePostCategoryItem(
				code = category.code,
				title = category.code.toDisplayTag()
			)
		},
		selectedCategoryCode = selectedCategoryCode,
		isCategoriesLoading = isCategoriesLoading,
		isCategoriesError = isCategoriesError,
		isPublishing = isPublishing,
		publishingStage = publishingStage,
		uploadProgress = uploadProgress,
		uploadTotal = uploadTotal,
		errorMessage = errorMessage,
		canPublish = canPublish(allowManualUrls = allowManualUrls),
		hasManualUrlsInDraft = allowManualUrls && imageUrls.any { it.isNotBlank() },
	)
}
