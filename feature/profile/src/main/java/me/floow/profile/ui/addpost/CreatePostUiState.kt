package me.floow.profile.ui.addpost

import me.floow.profile.uilogic.addpost.AddPostVmState
import me.floow.shared.profile.ui.addpost.CreatePostCategoryItem
import me.floow.shared.profile.ui.addpost.CreatePostImageItem
import me.floow.shared.profile.ui.addpost.CreatePostUiState
import me.floow.uikit.theme.toDisplayTag

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
		maxImages = AddPostVmState.MAX_IMAGES,
		hasManualUrlsInDraft = allowManualUrls && imageUrls.any { it.isNotBlank() },
	)
}
