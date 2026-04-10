package me.floow.profile.ui.addpost

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import me.floow.shared.profile.ui.addpost.CreatePostOverlayScreen as SharedCreatePostOverlayScreen
import me.floow.shared.profile.ui.addpost.CreatePostUiState

@Composable
internal fun CreatePostOverlayScreen(
	uiState: CreatePostUiState,
	initialPaintersByImageId: Map<String, Painter> = emptyMap(),
	onCloseClick: () -> Unit,
	onPickImagesClick: () -> Unit,
	onRemoveImageClick: (String) -> Unit,
	onCommitImageOrder: (List<String>) -> Unit,
	onDescriptionChange: (String) -> Unit,
	onCategoryChange: (String) -> Unit,
	onRetryCategoriesClick: () -> Unit,
	onPublishClick: () -> Unit,
	onImageCardClick: ((String) -> Unit)? = null,
	showCategorySelector: Boolean = true,
	allowAddImageCard: Boolean = true,
	resumeSignal: Int = 0,
	blockAutoFocus: Boolean = false,
	modifier: Modifier = Modifier,
) {
	SharedCreatePostOverlayScreen(
		uiState = uiState,
		initialPaintersByImageId = initialPaintersByImageId,
		onCloseClick = onCloseClick,
		onPickImagesClick = onPickImagesClick,
		onRemoveImageClick = onRemoveImageClick,
		onCommitImageOrder = onCommitImageOrder,
		onDescriptionChange = onDescriptionChange,
		onCategoryChange = onCategoryChange,
		onRetryCategoriesClick = onRetryCategoriesClick,
		onPublishClick = onPublishClick,
		onImageCardClick = onImageCardClick,
		showCategorySelector = showCategorySelector,
		allowAddImageCard = allowAddImageCard,
		resumeSignal = resumeSignal,
		blockAutoFocus = blockAutoFocus,
		modifier = modifier,
	)
}
