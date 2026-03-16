package me.floow.comments

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.floow.comments.uilogic.CommentsViewModel
import me.floow.domain.models.CommentId
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.chat.ChatScreen
import me.floow.uikit.chat.model.ChatInteractionAdapter
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatTopBarMode
import me.floow.uikit.chat.model.DEFAULT_CHAT_MESSAGE_MAX_LENGTH
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerV2
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

data class CommentsRouteInitialData(
	val postId: String,
	val postAuthorId: String,
	val postAuthorName: String,
	val postAuthorAvatarUrl: Uri?,
	val postAuthorUsername: String?,
	val postImageUrls: List<String>,
	val postImageVariants: List<PostImageVariant>,
	val postDescription: String?,
	val postCreatedAt: Long,
	val postLikesCount: Int,
	val postIsSelf: Boolean,
	val mediaTransferToken: String? = null,
	val initialTargetCommentId: CommentId? = null,
	val fallbackTargetCommentId: CommentId? = null
)

@Composable
fun CommentsRoute(
	initialData: CommentsRouteInitialData,
	onBackClick: () -> Unit,
	onAuthorClick: (String) -> Unit = {},
	vm: CommentsViewModel = koinViewModel(),
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsStateWithLifecycle()
	val isInitialTargetResolved by vm.isInitialTargetResolved.collectAsStateWithLifecycle()
	val commentsTitle = stringResource(R.string.comments_title)
	val commentsPhotoSubtitle = stringResource(R.string.comments_photo_subtitle)
	val viewerState = rememberFullscreenImageViewerState()
	val mediaTransferStore: PostMediaTransferStore = koinInject()
	val handoffSnapshot: PostMediaSourceSnapshot? = remember(initialData.mediaTransferToken, initialData.postId) {
		initialData.mediaTransferToken
			?.let(mediaTransferStore::consume)
			?.takeIf { it.postId == initialData.postId }
			?: mediaTransferStore.peek(initialData.postId)?.takeIf { it.postId == initialData.postId }
	}
	val images = remember(initialData.postImageUrls, initialData.postImageVariants, handoffSnapshot) {
		val handoffUrls = handoffSnapshot
			?.urls
			?.map(String::trim)
			?.filter(String::isNotEmpty)
			.orEmpty()
		if (handoffUrls.isNotEmpty()) {
			handoffUrls
		} else {
			initialData.postImageUrls
				.filter(String::isNotBlank)
				.ifEmpty {
					initialData.postImageVariants.mapNotNull { variant ->
						variant.fullUrl?.takeIf(String::isNotBlank)
							?: variant.previewUrl?.takeIf(String::isNotBlank)
							?: variant.lqUrl?.takeIf(String::isNotBlank)
					}
				}
		}
	}
	var openingPainter by remember(initialData.postId) { mutableStateOf<Painter?>(null) }
	var openingOrigin by remember(initialData.postId) { mutableStateOf<SharedImageOrigin?>(null) }
	LaunchedEffect(
		initialData.postId,
		initialData.initialTargetCommentId,
		initialData.fallbackTargetCommentId,
		commentsTitle
	) {
		vm.setInitialData(
			postId = initialData.postId,
			postAuthorId = initialData.postAuthorId,
			postAuthorName = initialData.postAuthorName,
			postAuthorAvatarUrl = initialData.postAuthorAvatarUrl,
			postAuthorUsername = initialData.postAuthorUsername,
			postImageUrls = initialData.postImageUrls,
			postImageVariants = initialData.postImageVariants,
			postDescription = initialData.postDescription,
			postCreatedAt = initialData.postCreatedAt,
			postLikesCount = initialData.postLikesCount,
			defaultTitle = commentsTitle
		)
		vm.startInitialLoad(
			primaryTargetCommentId = initialData.initialTargetCommentId,
			fallbackTargetCommentId = initialData.fallbackTargetCommentId
		)
	}

	LaunchedEffect(
		isInitialTargetResolved,
		(state as? ChatScreenUiState.HasData)?.messages,
		(state as? ChatScreenUiState.HasData)?.canLoadMore,
		(state as? ChatScreenUiState.HasData)?.isLoadingMore,
		state is ChatScreenUiState.Error
	) {
		vm.onInitialTargetSearchStateChanged()
	}

	val config = remember {
		ChatScreenConfig(
			layoutMode = ChatLayoutMode.OldestAtTop,
			showTypingIndicator = false,
			showPinActions = false,
			showEmojiButton = false,
				scrollToBottomOnInputFocus = false,
				liftMessageListWithIme = true,
				showAuthorHeaderForInMessages = true,
				maxInputLength = DEFAULT_CHAT_MESSAGE_MAX_LENGTH,
				animateJumpToHighlightedMessage = false,
			topBarMode = ChatTopBarMode.TitleOnly,
			topBarTitle = commentsTitle,
			showTopBarDropdown = false
		)
	}
	val hasInitialTarget = remember(
		initialData.initialTargetCommentId,
		initialData.fallbackTargetCommentId
	) {
		initialData.initialTargetCommentId != null || initialData.fallbackTargetCommentId != null
	}

	ChatScreen(
		onBackClick = onBackClick,
		onProfileClick = {
			if (initialData.postAuthorId.isNotBlank()) {
				onAuthorClick(initialData.postAuthorId)
			}
		},
		onTopBarDropdownClick = {},
		onChatBubbleClick = {},
		onAvatarClick = { message ->
			when (message) {
				is PostPreviewMessage -> {
					if (!initialData.postIsSelf && initialData.postAuthorId.isNotBlank()) {
						onAuthorClick(initialData.postAuthorId)
					}
				}
				else -> {
					vm.getForeignAuthorIdByMessageId(message.id)
						?.let(onAuthorClick)
				}
			}
		},
		onJumpToMessage = vm::jumpToComment,
		onReply = vm::addCurrentReply,
		onReplyClick = {
			ChatInteractionAdapter.onReplyClick(it, vm::jumpToComment)
		},
		onCurrentReplyClose = vm::closeCurrentReply,
		onCurrentReplyClick = {
			ChatInteractionAdapter.onCurrentReplyClick(state, vm::jumpToComment)
		},
		onCancelEdit = vm::cancelEditing,
		onMessageInputFieldValueChange = vm::updateMessageInputField,
		onSendClick = {
			ChatInteractionAdapter.onSendClick(
				state = state,
				onEditMessage = vm::editComment,
				onSendMessage = vm::sendComment
			)
			},
			onRequestScrollToBottom = vm::requestScrollToBottom,
			onRetryClick = vm::loadInitial,
			onViewportSnapshotChanged = { snapshot ->
				vm.onVisibleMessageIdsChanged(snapshot.visibleMessageIds)
			},
			onLoadMore = vm::loadMore,
			onPostImageClick = { _, index ->
			if (images.isEmpty()) return@ChatScreen
			if (viewerState.visible) return@ChatScreen
			val safeIndex = index.coerceIn(0, images.lastIndex)
			val sourceBounds = handoffSnapshot
				?.boundsByIndex
				?.get(safeIndex)
			openingPainter = handoffSnapshot
				?.paintersByIndex
				?.get(safeIndex)
				?.painter
				?: handoffSnapshot
					?.paintersByIndex
					?.get(handoffSnapshot.selectedIndex)
					?.painter
			openingOrigin = sourceBounds?.toCommentsSharedOrigin(
				postId = initialData.postId,
				index = safeIndex
			)
			viewerState.reduce(
				FullscreenImageViewerAction.Open(
					page = safeIndex,
					origin = openingOrigin
				),
				images.size
			)
		},
		onPinMessage = {},
		suspendInitialPlacement = hasInitialTarget && !isInitialTargetResolved,
		onUnpinMessage = {},
		onDeleteMessage = { message -> vm.deleteComment(message.id) },
		onEditMessage = { id, text -> vm.startEditingComment(id, text) },
		onRetryMessage = {},
		onUndoDelete = vm::undoDelete,
		config = config,
		state = state,
		modifier = modifier
	)

	FullscreenImageViewerV2(
		model = FullscreenImageViewerModel(
			images = images,
			title = initialData.postAuthorName,
			subtitleProvider = { commentsPhotoSubtitle },
			openingPainter = if (viewerState.phase == ViewerPhase.Opening) openingPainter else null,
			originForPage = { page ->
				val safePage = page.coerceIn(0, images.lastIndex)
				handoffSnapshot
					?.boundsByIndex
					?.get(safePage)
					?.toCommentsSharedOrigin(
						postId = initialData.postId,
						index = safePage
					)
			}
		),
		state = viewerState,
		onAction = { action ->
			when (action) {
				is FullscreenImageViewerAction.RequestClose -> {
					viewerState.reduce(action, images.size)
				}
				FullscreenImageViewerAction.CloseAnimationFinished -> {
					viewerState.reduce(action, images.size)
					openingPainter = null
					openingOrigin = null
				}
				else -> viewerState.reduce(action, images.size)
			}
		},
		modifier = modifier
	)
}

private fun Rect.toCommentsSharedOrigin(postId: String, index: Int): SharedImageOrigin {
	val aspect = if (width > 0f && height > 0f) width / height else 1f
	return SharedImageOrigin(
		sourceKey = "comments:$postId:image:$index",
		rectInWindow = this,
		aspectRatio = aspect.coerceAtLeast(0.01f)
	)
}
