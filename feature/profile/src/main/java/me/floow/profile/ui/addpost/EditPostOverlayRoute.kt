package me.floow.profile.ui.addpost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.floow.profile.uilogic.addpost.EditPostSaveResult
import me.floow.profile.uilogic.addpost.EditPostViewModel
import me.floow.profile.uilogic.addpost.toCreatePostUiState
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.SetStatusBarStyle

@Composable
fun EditPostOverlayRoute(
	postId: String,
	initialDescription: String?,
	initialImageUrls: List<String>,
	mediaTransferToken: String? = null,
	mediaTransferStore: PostMediaTransferStore? = null,
	onBackClick: () -> Unit,
	onOptimistic: (EditPostSaveResult) -> Unit = {},
	onSaved: (EditPostSaveResult) -> Unit,
	onSaveFailed: (EditPostSaveResult) -> Unit = {},
	modifier: Modifier = Modifier,
	viewModel: EditPostViewModel,
) {
	val state by viewModel.state.collectAsState()
	var replaceTargetImageId by remember { mutableStateOf<String?>(null) }
	val handoffSnapshot = remember(mediaTransferStore, mediaTransferToken, postId) {
		val transferStore = mediaTransferStore ?: return@remember null
		mediaTransferToken
			?.let { token -> transferStore.consume(token) }
			?.takeIf { snapshot -> snapshot.postId == postId }
			?: transferStore.peek(postId)?.takeIf { snapshot -> snapshot.postId == postId }
	}
	val initialPaintersByImageId = remember(handoffSnapshot) {
		buildMap {
			handoffSnapshot
				?.paintersByIndex
				?.forEach { (index, ref) ->
					put("initial_$index", ref.painter)
				}
		}
	}
	val replaceImageLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickVisualMedia()
	) { uri ->
		val targetImageId = replaceTargetImageId
		replaceTargetImageId = null
		if (targetImageId != null && uri != null) {
			viewModel.replaceImage(targetImageId, uri.toString())
		}
	}

	LaunchedEffect(postId, initialDescription, initialImageUrls) {
		viewModel.initialize(
			postId = postId,
			description = initialDescription,
			imageUrls = initialImageUrls,
		)
	}

	val uiState = remember(state) { state.toCreatePostUiState() }

	SetStatusBarStyle(
		color = Color.Transparent,
		darkIcons = false
	)

	CreatePostOverlayScreen(
		uiState = uiState,
		initialPaintersByImageId = initialPaintersByImageId,
		onCloseClick = onBackClick,
		onPickImagesClick = {},
		onRemoveImageClick = viewModel::removeImage,
		onCommitImageOrder = viewModel::reorderImages,
		onDescriptionChange = viewModel::updateDescription,
		onCategoryChange = {},
		onRetryCategoriesClick = {},
		onPublishClick = {
			viewModel.save(
				onOptimistic = onOptimistic,
				onSuccess = onSaved,
				onFailure = onSaveFailed,
			)
		},
		onImageCardClick = { imageId ->
			replaceTargetImageId = imageId
			replaceImageLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
			)
		},
		showCategorySelector = false,
		allowAddImageCard = false,
		modifier = modifier,
	)
}
