package me.floow.profile.ui.addpost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.floow.profile.uilogic.addpost.AddPostVmState
import me.floow.profile.uilogic.addpost.AddPostViewModel

@Composable
fun CreatePostOverlayRoute(
	onBackClick: () -> Unit,
	onPublished: () -> Unit,
	isMockBuild: Boolean,
	modifier: Modifier = Modifier,
	viewModel: AddPostViewModel,
) {
	val state by viewModel.state.collectAsState()
	val uiState = remember(state, isMockBuild) {
		state.toCreatePostUiState(allowManualUrls = isMockBuild)
	}
	val pickImagesLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = AddPostVmState.MAX_IMAGES)
	) { uris ->
		viewModel.addLocalImageUris(uris.map { it.toString() })
	}

	CreatePostOverlayScreen(
		uiState = uiState,
		onCloseClick = onBackClick,
		onPickImagesClick = {
			pickImagesLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
			)
		},
		onRemoveImageClick = viewModel::removeLocalImageByUri,
		onCommitImageOrder = viewModel::setLocalImageOrder,
		onDescriptionChange = viewModel::updateDescription,
		onCategoryChange = viewModel::updateCategory,
		onRetryCategoriesClick = viewModel::retryLoadCategories,
		onPublishClick = {
			viewModel.publish(
				allowManualUrls = isMockBuild,
				onSuccess = onPublished
			)
		},
		modifier = modifier,
	)
}
