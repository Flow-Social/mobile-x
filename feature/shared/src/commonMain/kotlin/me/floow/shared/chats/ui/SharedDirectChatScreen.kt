package me.floow.shared.chats.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.bookmark_icon
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.uikit.chat.common.ChatScreenBody
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.rememberChatInputController
import me.floow.uikit.chat.input.rememberChatInputLayoutState
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState
import me.floow.uikit.chat.model.resolveReplyTargetId
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.util.overlayHorizontalSwipeZone
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SharedDirectChatScreen(
	state: DirectChatScreenState,
	initialHeader: ChatThreadHeaderModel,
	selectionState: ChatSelectionState,
	contextMenuActions: List<ChatContextMenuAction>,
	resolveContextMenuActions: (Long) -> List<ChatContextMenuAction>,
	jumpRequest: SharedJumpRequest?,
	onBackClick: () -> Unit,
	onHeaderClick: (() -> Unit)?,
	onInputChange: (String) -> Unit,
	onSendClick: () -> Unit,
	onReplyClick: (Long) -> Unit,
	onClearReplyClick: () -> Unit,
	onEditClick: (Long) -> Unit,
	onCancelEditClick: () -> Unit,
	onDeleteClick: (Long) -> Unit,
	onTogglePinClick: (Long, Boolean) -> Unit,
	onLoadMore: () -> Unit,
	onRequestScrollToBottom: () -> Unit,
	onUserStartedScroll: () -> Unit,
	onViewportSnapshotChanged: (ChatViewportSnapshot) -> Unit,
	onMessageClick: (Long) -> Unit,
	onMessageLongClick: (Long) -> Unit,
	onReplyPreviewClick: (Long) -> Unit,
	onContextMenuOpen: (Long, Rect?, Rect?) -> Unit,
	contextMenuAnchorRect: Rect?,
	contextMenuHighlightRect: Rect?,
	onDismissContextMenu: () -> Unit,
	onContextMenuActionClick: (ChatContextMenuAction) -> Unit,
	pendingDeleteMessageId: Long?,
	isDeleteSelectionConfirmationVisible: Boolean,
	onDismissDeleteMessageConfirmation: () -> Unit,
	onDismissDeleteSelectionConfirmation: () -> Unit,
	onConfirmDeleteMessage: (Long) -> Boolean,
	onUndoDeleteClick: () -> Unit,
	onConfirmDeleteSelection: () -> Unit,
	onSelectionCloseClick: () -> Unit,
	onCopySelectionClick: () -> Unit,
	onDeleteSelectionClick: () -> Unit,
	onPinnedMessageClick: (Long) -> Unit,
	snackbarHostState: SnackbarHostState,
	emojiPanel: @Composable (ChatInputController) -> Unit = {},
	videoRecordingOverlay: @Composable (Int, Rect?, Modifier) -> Unit = { _, _, _ -> },
	recordingState: me.floow.uikit.chat.model.VideoRecordingState = me.floow.uikit.chat.model.VideoRecordingState(),
	onRecordButtonPress: () -> Unit = {},
	onRecordButtonRelease: () -> Unit = {},
	onRecordSwipeUp: () -> Unit = {},
	onRecordSwipeLeft: () -> Unit = {},
	onRecordDrag: (Float, Float) -> Unit = { _, _ -> },
	onRecordStopClick: () -> Unit = {},
	videoCircleInteractionActive: Boolean = false,
	videoCircleContent: @Composable (me.floow.uikit.chat.model.VideoCircleOutMessage) -> Unit = {},
	bubbleBoundsByMessageKey: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.geometry.Rect> = androidx.compose.runtime.mutableStateMapOf(),
	modifier: Modifier = Modifier,
) {
	val strings = rememberSharedChatStrings()
	val scope = rememberCoroutineScope()
	val loadedHeader = state.headerOrNull()
	val header = remember(loadedHeader, initialHeader, strings.savedMessagesTitle) {
		if (loadedHeader == null) {
			if (initialHeader.isSavedMessages) {
				initialHeader.copy(title = strings.savedMessagesTitle)
			} else {
				initialHeader
			}
		} else if (loadedHeader.isSavedMessages) {
			loadedHeader.copy(title = strings.savedMessagesTitle)
		} else {
			loadedHeader
		}
	}
	val config = remember(header) { sharedDirectChatConfig(header) }
	val uiState = remember(state, selectionState, jumpRequest) {
		state.toSharedChatUiState(
			selectionState = selectionState,
			jumpRequest = jumpRequest,
		)
	}
	val density = LocalDensity.current
	val fallbackPanelHeightPx = remember(density) { with(density) { 340.dp.roundToPx() } }
	val inputController = rememberChatInputController(
		text = state.inputValue(),
		maxLength = null,
		fallbackPanelHeightPx = fallbackPanelHeightPx,
		onTextChanged = onInputChange,
	)
	rememberSharedChatInputLifecycleBridge(controller = inputController)
	rememberSharedChatInputPersistenceBridge(
		controller = inputController,
		persistenceKey = (uiState as? ChatScreenUiState.HasData)?.timelineSessionToken,
	)
	val actualImeHeightPx = rememberChatInputLayoutState(controller = inputController)
	val recordingOverlayBottomInsetPx by remember(inputController, actualImeHeightPx, recordingState.mode) {
		derivedStateOf {
			if (recordingState.mode == VideoRecordingMode.Idle) {
				0
			} else {
				maxOf(actualImeHeightPx, inputController.keyboardLayoutHeightPx)
			}
		}
	}
	val hasDataState = uiState as? ChatScreenUiState.HasData
	val replyField = hasDataState?.messageFieldReply
	var lastRecordingMode by remember { mutableStateOf(recordingState.mode) }
	var recordingButtonBounds by remember { mutableStateOf<Rect?>(null) }

	LaunchedEffect(recordingState.mode) {
		val previousMode = lastRecordingMode
		val currentMode = recordingState.mode
		val wasActiveFlow = previousMode != VideoRecordingMode.Idle

		when (currentMode) {
			VideoRecordingMode.Recording -> {
				inputController.enterRecording()
			}
			VideoRecordingMode.Sending -> {
				inputController.exitRecording()
			}
			VideoRecordingMode.Failed -> {
				if (wasActiveFlow) {
					inputController.exitRecording()
				}
			}
			VideoRecordingMode.Idle -> {
				if (wasActiveFlow) {
					if (previousMode == VideoRecordingMode.Sending) {
						inputController.exitRecording()
					} else {
						inputController.exitRecordingWithRestore()
					}
				}
			}
		}

		lastRecordingMode = currentMode
	}

	val sendButtonActive by remember(state, inputController) {
		derivedStateOf {
			inputController.textFieldValue.text.isNotBlank() &&
				(state as? DirectChatScreenState.HasData)?.sending != true
		}
	}
	val rowBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }
	val savedMessagesAvatarPainter = if (header.isSavedMessages) {
		painterResource(Res.drawable.bookmark_icon)
	} else {
		null
	}
	val recordingSwipeBlockZoneKey = remember { "direct_chat_recording_swipe_block_zone" }
	val recordingSwipeBlockModifier = if (recordingState.mode == VideoRecordingMode.Recording) {
		Modifier.overlayHorizontalSwipeZone(
			zoneKey = recordingSwipeBlockZoneKey,
			atStart = true,
			blockOverlay = true,
			priority = 100,
		)
	} else {
		Modifier
	}
	val recordingContentScale by animateFloatAsState(
		targetValue = if (recordingState.isActive) 1.035f else 1f,
		animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
		label = "recording_content_parallax_scale",
	)
	val recordingContentBlur by animateDpAsState(
		targetValue = if (recordingState.isActive) 6.dp else 0.dp,
		animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
		label = "recording_content_blur",
	)
	val recordingContentTransformModifier = Modifier
		.graphicsLayer {
			scaleX = recordingContentScale
			scaleY = recordingContentScale
			transformOrigin = TransformOrigin(0.5f, 0.52f)
		}
		.blur(recordingContentBlur)

	LaunchedEffect(pendingDeleteMessageId) {
		if (pendingDeleteMessageId != null && pendingDeleteMessageId !in currentMessageIds(uiState)) {
			onDismissDeleteMessageConfirmation()
		}
	}

	if (pendingDeleteMessageId != null) {
		SharedDeleteConfirmationDialog(
			title = strings.deleteMessageTitle,
			text = strings.deleteMessageText,
			confirmText = strings.deleteMessageConfirm,
			cancelText = strings.deleteMessageCancel,
			onConfirm = {
				val undoAvailable = onConfirmDeleteMessage(pendingDeleteMessageId)
				onDismissDeleteMessageConfirmation()
				if (undoAvailable) {
					scope.launch {
						val result = snackbarHostState.showSnackbar(
							message = strings.messageDeleted,
							actionLabel = strings.undoAction,
							duration = SnackbarDuration.Short,
						)
						if (result == SnackbarResult.ActionPerformed) {
							onUndoDeleteClick()
						}
					}
				}
			},
			onDismiss = onDismissDeleteMessageConfirmation,
		)
	}

	if (isDeleteSelectionConfirmationVisible) {
		SharedDeleteConfirmationDialog(
			title = strings.deleteSelectedMessagesTitle,
			text = strings.deleteSelectedMessagesText,
			confirmText = strings.deleteMessageConfirm,
			cancelText = strings.deleteMessageCancel,
			onConfirm = {
				onConfirmDeleteSelection()
				onDismissDeleteSelectionConfirmation()
			},
			onDismiss = onDismissDeleteSelectionConfirmation,
		)
	}

	ChatScreenBody(
		title = header.title,
		subtitle = header.subtitle,
		avatarUrl = header.avatarUrl,
		isSavedMessages = header.isSavedMessages,
		onBackClick = onBackClick,
		onHeaderClick = onHeaderClick,
		isSelectionMode = selectionState.isSelectionMode,
		selectedCount = selectionState.selectedCount,
		canCopySelection = selectionState.canCopy,
		canDeleteSelection = selectionState.canDelete,
		onSelectionCloseClick = onSelectionCloseClick,
		onCopySelectionClick = onCopySelectionClick,
		onDeleteSelectionClick = onDeleteSelectionClick,
		selectedCountLabel = sharedSelectionCountLabel(selectionState.selectedCount),
		closeContentDescription = strings.selectionClose,
		copyContentDescription = strings.selectionCopy,
		deleteContentDescription = strings.selectionDelete,
		showInputBar = true,
		inputController = inputController,
		actualImeHeightPx = actualImeHeightPx,
		onSendClick = onSendClick,
		sendButtonActive = sendButtonActive,
		isEditMode = hasDataState?.messageToEditId != null,
		showEmojiButton = config.showEmojiButton,
		showRecordButton = true,
		onRecordButtonPress = onRecordButtonPress,
		onRecordButtonRelease = onRecordButtonRelease,
		onRecordSwipeUp = onRecordSwipeUp,
		onRecordSwipeLeft = onRecordSwipeLeft,
		onRecordDrag = onRecordDrag,
		onRecordStopClick = onRecordStopClick,
		onRecordButtonBoundsChanged = { bounds ->
			recordingButtonBounds = bounds
		},
		recordingState = recordingState,
		recordCancelThresholdPx = recordingState.cancelThresholdPx,
		recordLockThresholdPx = recordingState.lockThresholdPx,
		composerReplyTitle = when {
			hasDataState?.messageToEditId != null -> strings.composerEditing
			replyField != null -> sharedComposerReplyingToLabel(replyField.replyAuthorName)
			else -> null
		},
		composerReplySubtitle = when {
			hasDataState?.messageToEditId != null -> state.inputValue()
			else -> replyField?.replyMessageText
		},
		onComposerReplyClose = if (hasDataState?.messageToEditId != null) onCancelEditClick else onClearReplyClick,
		onTrailingClick = null,
		headerAvatarPainter = savedMessagesAvatarPainter,
		dividerColor = config.dividerColor,
		snackbarHost = { SnackbarHost(snackbarHostState) },
		pinnedMessages = if (hasDataState != null && hasDataState.pinnedMessages.isNotEmpty()) {
			{
				SharedPinnedMessagesBar(
					pinnedMessages = hasDataState.pinnedMessages,
					pinnedLabel = strings.pinnedLabel,
					unpinContentDescription = strings.unpinContentDescription,
					onMessageClick = { onPinnedMessageClick(it.id) },
					onUnpinClick = { onTogglePinClick(it.id, false) },
				)
			}
		} else {
			null
		},
		emojiPanel = {
			emojiPanel(inputController)
		},
		content = { contentModifier ->
			when (uiState) {
				is ChatScreenUiState.Loading -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center,
				) {
					FlowLoadingIndicator()
				}

				is ChatScreenUiState.Error -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = (state as? DirectChatScreenState.Error)?.message ?: strings.chatLoadFailed,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}

				is ChatScreenUiState.NoMessages -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = strings.chatNoMessages,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}

				is ChatScreenUiState.HasData -> {
					HasDataState(
						state = uiState,
						onChatBubbleClick = { onMessageClick(it.id) },
						onChatBubbleLongClick = { onMessageLongClick(it.id) },
						onToggleSelection = onMessageClick,
						onMessageActionClick = null,
						onAvatarClick = {},
						onReply = { onReplyClick(it.id) },
						onReplyClick = { onReplyPreviewClick(resolveReplyTargetId(it)) },
						onRetrySendClick = null,
						onPostImageClick = { _, _, _, _ -> },
						hiddenPostImageIndex = null,
						hiddenPostImageRevealProgress = 0f,
						onRequestScrollToBottom = onRequestScrollToBottom,
						onUserStartedScroll = onUserStartedScroll,
						onViewportSnapshotChanged = onViewportSnapshotChanged,
						onAnchorRestoreSettled = { _, _ -> },
						onAnchorRestoreTimedOut = {},
						resolveContextMenuActions = { message ->
							resolveContextMenuActions(message.id)
						},
						onContextMenuOpenRequest = { message, anchorRect, highlightRect ->
							onContextMenuOpen(message.id, anchorRect, highlightRect)
						},
						onContextMenuDismissRequest = onDismissContextMenu,
						rowBoundsByMessageKey = rowBoundsByMessageKey,
						bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
						onLoadMore = onLoadMore,
						config = config,
						messageListScrollEnabled = !videoCircleInteractionActive,
						videoCircleContent = videoCircleContent,
						modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceContainer),
					)
				}
			}
		},
		bodyContentModifier = recordingContentTransformModifier,
		screenOverlay = if (recordingState.isActive) {
				{ overlayModifier ->
					videoRecordingOverlay(recordingOverlayBottomInsetPx, recordingButtonBounds, overlayModifier)
				}
			} else {
				null
			},
			contextMenuOverlay = {
			if (contextMenuActions.isNotEmpty()) {
				me.floow.uikit.chat.common.MessageContextMenuOverlay(
					actions = contextMenuActions,
					anchorRect = contextMenuAnchorRect,
					highlightRect = contextMenuHighlightRect,
					onActionClick = onContextMenuActionClick,
					onDismissRequest = onDismissContextMenu,
				)
			}
		},
		modifier = modifier
			.fillMaxSize()
			.then(recordingSwipeBlockModifier),
	)
	}

private fun currentMessageIds(
	uiState: ChatScreenUiState,
): Set<Long> {
	val hasDataState = uiState as? ChatScreenUiState.HasData ?: return emptySet()
	return hasDataState.messages
		.flatMap { datedMessages -> datedMessages.messages }
		.map { message -> message.id }
		.toSet()
}
