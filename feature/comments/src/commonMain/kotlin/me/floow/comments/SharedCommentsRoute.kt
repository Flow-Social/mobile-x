package me.floow.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import me.floow.comments.uilogic.CommentsRouteComponent
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.chat.common.ChatScreenBody
import me.floow.uikit.chat.common.MessageContextMenuOverlay
import me.floow.uikit.chat.input.rememberChatInputController
import me.floow.uikit.chat.input.rememberChatInputLayoutState
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatTopBarMode
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.DEFAULT_CHAT_MESSAGE_MAX_LENGTH
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.resolveReplyTargetId
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.SourceImageScaleMode
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState

@Composable
fun SharedCommentsRoute(
	initialData: CommentsRouteInitialData,
	onBackClick: () -> Unit,
	onAuthorClick: (String) -> Unit = {},
	onCopyText: (String) -> Unit = {},
	component: CommentsRouteComponent,
	mediaTransferStore: PostMediaTransferStore,
	commentsTitle: String,
	commentsPhotoSubtitle: String,
	onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val state by component.state.collectAsState()
	val isInitialTargetResolved by component.isInitialTargetResolved.collectAsState()
	val editingTitle = "Edit"
	val inReplyTo = "In reply to"
	val config = remember {
		ChatScreenConfig(
			layoutMode = ChatLayoutMode.OldestAtTop,
			showTypingIndicator = false,
			showPinActions = false,
			showEmojiButton = false,
			showMessageStatusIndicators = false,
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
	val inputController = rememberChatInputController(
		text = state.messageFieldValue,
		maxLength = DEFAULT_CHAT_MESSAGE_MAX_LENGTH,
		fallbackPanelHeightPx = 0,
		onTextChanged = component::updateMessageInputField
	)
	val actualImeHeightPx = rememberChatInputLayoutState(controller = inputController)
	val hasDataState = state as? ChatScreenUiState.HasData
	val replyField = state.messageFieldReply
	val sendButtonActive by remember(state, inputController) {
		derivedStateOf { inputController.textFieldValue.text.isNotBlank() }
	}
	val rowBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }
	val bubbleBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }
	val messagesById = remember(hasDataState?.messages) {
		hasDataState?.messages
			?.flatMap { it.messages }
			?.associateBy(ChatMessage::id)
			.orEmpty()
	}
	var contextMenuMessageId by remember(hasDataState?.timelineSessionToken) { mutableStateOf<Long?>(null) }
	var contextMenuAnchorRect by remember(hasDataState?.timelineSessionToken) { mutableStateOf<Rect?>(null) }
	var contextMenuHighlightRect by remember(hasDataState?.timelineSessionToken) { mutableStateOf<Rect?>(null) }

	val viewerState = rememberFullscreenImageViewerState()
	val liveOrigins = remember(initialData.postId) { mutableStateMapOf<Int, SharedImageOrigin>() }
	var hiddenPostImageIndex by remember(initialData.postId) { mutableStateOf<Int?>(null) }
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
	val viewerModelProvider = {
		if (images.isEmpty()) {
			null
		} else {
			FullscreenImageViewerModel(
				images = images,
				title = initialData.postAuthorName,
				subtitleProvider = { commentsPhotoSubtitle },
				openingPainter = if (viewerState.phase == ViewerPhase.Opening) openingPainter else null,
				originForPage = { page ->
					val safePage = page.coerceIn(0, images.lastIndex)
					liveOrigins[safePage]
						?: handoffSnapshot
							?.boundsByIndex
							?.get(safePage)
							?.toCommentsSharedOrigin(
								postId = initialData.postId,
								index = safePage,
								aspectRatio = resolveImageAspect(
									sourcePainter = null,
									fallbackVariant = initialData.postImageVariants.getOrNull(safePage)
								)
							)
				}
			)
		}
	}
	val hostedViewer = remember(viewerState, onPresentFullscreenViewer) {
		HostedFullscreenImageViewer(
			modelProvider = viewerModelProvider,
			state = viewerState,
			onAction = { action ->
				viewerState.reduce(action, images.size)
				if (action is FullscreenImageViewerAction.CloseAnimationFinished) {
					openingPainter = null
					hiddenPostImageIndex = null
					onPresentFullscreenViewer?.invoke(null)
				}
			}
		)
	}

	DisposableEffect(onPresentFullscreenViewer) {
		onDispose { onPresentFullscreenViewer?.invoke(null) }
	}

	LaunchedEffect(
		initialData.postId,
		initialData.initialTargetCommentId,
		initialData.fallbackTargetCommentId,
		commentsTitle
	) {
		component.setInitialData(
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
		component.startInitialLoad(
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
		component.onInitialTargetSearchStateChanged()
	}

	val hasInitialTarget = remember(
		initialData.initialTargetCommentId,
		initialData.fallbackTargetCommentId
	) {
		initialData.initialTargetCommentId != null || initialData.fallbackTargetCommentId != null
	}

	ChatScreenBody(
		title = commentsTitle,
		subtitle = null,
		avatarUrl = null,
		isSavedMessages = true,
		onBackClick = onBackClick,
		isSelectionMode = false,
		selectedCount = 0,
		canCopySelection = false,
		canDeleteSelection = false,
		onSelectionCloseClick = {},
		onCopySelectionClick = {},
		onDeleteSelectionClick = {},
		selectedCountLabel = "",
		closeContentDescription = "",
		copyContentDescription = "",
		deleteContentDescription = "",
		showInputBar = true,
		inputController = inputController,
		actualImeHeightPx = actualImeHeightPx,
		onSendClick = {
			val messageToEditId = (state as? ChatScreenUiState.HasData)?.messageToEditId
			if (messageToEditId != null) {
				component.editComment(messageToEditId, state.messageFieldValue)
			} else {
				component.sendComment()
			}
		},
		sendButtonActive = sendButtonActive,
		isEditMode = hasDataState?.messageToEditId != null,
		showEmojiButton = false,
		composerReplyTitle = when {
			hasDataState?.messageToEditId != null -> editingTitle
			replyField != null -> "$inReplyTo ${replyField.replyAuthorName}"
			else -> null
		},
		composerReplySubtitle = when {
			hasDataState?.messageToEditId != null -> state.messageFieldValue
			else -> replyField?.replyMessageText
		},
		onComposerReplyClose = if (hasDataState?.messageToEditId != null) component::cancelEditing else component::closeCurrentReply,
		onComposerReplyClick = if (hasDataState?.messageToEditId != null) null else {
			{ replyField?.replyId?.let(component::jumpToComment) }
		},
		dividerColor = config.dividerColor,
		content = { contentModifier ->
			when (state) {
				is ChatScreenUiState.Loading -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center
				) {
					FlowLoadingIndicator()
				}

				is ChatScreenUiState.Error -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center
				) {
					Text(
						text = "Failed to load comments",
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}

				is ChatScreenUiState.NoMessages -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center
				) {
					Text(
						text = "No comments yet",
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}

				is ChatScreenUiState.HasData -> {
					val currentHasDataState = hasDataState ?: return@ChatScreenBody
 					HasDataState(
 						state = currentHasDataState,
 						onChatBubbleClick = {},
						onChatBubbleLongClick = {},
						onToggleSelection = {},
						onMessageActionClick = null,
						onAvatarClick = { message ->
							when (message) {
								is PostPreviewMessage -> {
									if (!initialData.postIsSelf && initialData.postAuthorId.isNotBlank()) {
										onAuthorClick(initialData.postAuthorId)
									}
								}
								else -> {
									component.getForeignAuthorIdByMessageId(message.id)?.let(onAuthorClick)
								}
							}
						},
						onReply = component::addCurrentReply,
						onReplyClick = { component.jumpToComment(resolveReplyTargetId(it)) },
						onRetrySendClick = null,
						onPostImageClick = { _, index, sourceBounds, sourcePainter ->
							if (images.isEmpty()) return@HasDataState
							if (viewerState.visible) return@HasDataState
							val safeIndex = index.coerceIn(0, images.lastIndex)
							val originBounds = sourceBounds ?: handoffSnapshot?.boundsByIndex?.get(safeIndex)
							if (originBounds != null) {
								liveOrigins[safeIndex] = originBounds.toCommentsSharedOrigin(
									postId = initialData.postId,
									index = safeIndex,
									aspectRatio = resolveImageAspect(
										sourcePainter = sourcePainter,
										fallbackVariant = initialData.postImageVariants.getOrNull(safeIndex)
									)
								)
							}
							hiddenPostImageIndex = safeIndex
							openingPainter = sourcePainter ?: handoffSnapshot
								?.paintersByIndex
								?.get(safeIndex)
								?.painter
							onPresentFullscreenViewer?.invoke(hostedViewer)
							hostedViewer.onAction(
								FullscreenImageViewerAction.Open(
									page = safeIndex,
									origin = liveOrigins[safeIndex]
								)
							)
						},
						hiddenPostImageIndex = hiddenPostImageIndex,
						hiddenPostImageRevealProgress = if (viewerState.visible) 1f else 0f,
						onRequestScrollToBottom = component::requestScrollToBottom,
						onUserStartedScroll = {},
						onViewportSnapshotChanged = { snapshot: ChatViewportSnapshot ->
							component.onVisibleMessageIdsChanged(snapshot.visibleMessageIds)
						},
						onAnchorRestoreSettled = { _, _ -> },
						onAnchorRestoreTimedOut = {},
						suspendInitialPlacement = hasInitialTarget && !isInitialTargetResolved,
						resolveContextMenuActions = component::resolveContextMenuActions,
						onContextMenuOpenRequest = { message, anchorRect, highlightRect ->
							contextMenuMessageId = message.id
							contextMenuAnchorRect = anchorRect
							contextMenuHighlightRect = highlightRect
						},
						onContextMenuDismissRequest = {
							contextMenuMessageId = null
							contextMenuAnchorRect = null
							contextMenuHighlightRect = null
						},
						rowBoundsByMessageKey = rowBoundsByMessageKey,
						bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
						onLoadMore = component::loadMore,
						config = config,
						modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceContainer)
					)
				}
			}
		},
		contextMenuOverlay = {
			val menuTargetMessage = contextMenuMessageId?.let(messagesById::get)
			if (menuTargetMessage != null) {
				MessageContextMenuOverlay(
					actions = component.resolveContextMenuActions(menuTargetMessage),
					anchorRect = contextMenuAnchorRect,
					highlightRect = contextMenuHighlightRect,
					onActionClick = { action ->
						when (action) {
							ChatContextMenuAction.Reply -> component.addCurrentReply(menuTargetMessage)
							ChatContextMenuAction.CopyText -> {
								if (menuTargetMessage.messageText.isNotBlank()) {
									onCopyText(menuTargetMessage.messageText)
								}
							}
							ChatContextMenuAction.Edit -> component.startEditingComment(menuTargetMessage.id, menuTargetMessage.messageText)
							ChatContextMenuAction.Delete -> component.deleteComment(menuTargetMessage.id)
							ChatContextMenuAction.Pin,
							ChatContextMenuAction.Unpin,
							ChatContextMenuAction.Retry -> Unit
						}
						contextMenuMessageId = null
						contextMenuAnchorRect = null
						contextMenuHighlightRect = null
					},
					onDismissRequest = {
						contextMenuMessageId = null
						contextMenuAnchorRect = null
						contextMenuHighlightRect = null
					},
					modifier = Modifier.fillMaxSize()
				)
			}
		},
		modifier = modifier.fillMaxSize()
	)

	if (onPresentFullscreenViewer == null) {
		viewerModelProvider()?.let { viewerModel ->
			SharedFullscreenImageViewer(
				model = viewerModel,
				state = viewerState,
				onAction = hostedViewer.onAction
			)
		}
	}
}

private fun Rect.toCommentsSharedOrigin(
	postId: String,
	index: Int,
	aspectRatio: Float
): SharedImageOrigin {
	return SharedImageOrigin(
		sourceKey = "comments:$postId:image:$index",
		rectInWindow = this,
		aspectRatio = aspectRatio.coerceAtLeast(0.01f),
		contentRectInWindow = this,
		sourceScaleMode = SourceImageScaleMode.Crop
	)
}

private fun resolveImageAspect(
	sourcePainter: Painter?,
	fallbackVariant: PostImageVariant?
): Float {
	val painterAspect = sourcePainter?.intrinsicSize?.let { size ->
		val width = size.width
		val height = size.height
		if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
			(width / height).coerceAtLeast(0.01f)
		} else {
			null
		}
	}
	if (painterAspect != null) return painterAspect
	val variantAspect = fallbackVariant?.let { variant ->
		val width = variant.width?.toFloat() ?: return@let null
		val height = variant.height?.toFloat() ?: return@let null
		if (width > 0f && height > 0f) (width / height).coerceAtLeast(0.01f) else null
	}
	return variantAspect ?: 1f
}
