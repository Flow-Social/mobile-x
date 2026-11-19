package me.floow.shared.chats.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import me.floow.uikit.chat.components.RecordingLockPill
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.bookmark_icon
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.withSavedMessagesSemantics
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.uikit.chat.common.ChatScreenBody
import me.floow.uikit.chat.common.MessageContextMenuOverlay
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.chat.components.FlowPinnedMessagesBar
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.rememberChatInputController
import me.floow.uikit.chat.input.rememberChatInputLayoutState
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatDraftImage
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState
import me.floow.uikit.chat.model.resolveReplyTargetId
import me.floow.uikit.chat.states.ChatMessagesSkeleton
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.chat.states.LocalContextMenuElevation
import me.floow.uikit.util.overlayHorizontalSwipeZone
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SharedDirectChatScreen(
	state: DirectChatScreenState,
	initialHeader: ChatThreadHeaderModel,
	selectionState: ChatSelectionState,
	activeContextMenuMessageId: Long? = null,
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
	onScrollSettled: () -> Unit = {},
	onAnchorRestoreSettled: (Long, Int) -> Unit,
	onAnchorRestoreTimedOut: (Long) -> Unit,
	onMessageClick: (Long) -> Unit,
	onMessageLongClick: (Long) -> Unit,
	onReplyPreviewClick: (Long) -> Unit,
	onContextMenuOpen: (Long, Rect?, Rect?, me.floow.uikit.chat.common.ElevatedMessageData?) -> Unit,
	contextMenuAnchorRect: Rect?,
	contextMenuHighlightRect: Rect?,
	contextMenuElevatedData: me.floow.uikit.chat.common.ElevatedMessageData? = null,
	isContextMenuOutgoing: Boolean? = null,
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
	playbackBar: @Composable () -> Unit = {},
	onReplyCreatedFeedback: () -> Unit = {},
	emojiPanel: @Composable (ChatInputController) -> Unit = {},
	videoRecordingOverlay: @Composable (Int, Rect?, Modifier) -> Unit = { _, _, _ -> },
	flightState: me.floow.uikit.chat.model.FlightState? = null,
	videoCircleFlightOverlay: @Composable (Modifier) -> Unit = {},
	recordingState: me.floow.uikit.chat.model.VideoRecordingState = me.floow.uikit.chat.model.VideoRecordingState(),
	onRecordButtonPress: () -> Unit = {},
	onRecordButtonRelease: () -> Unit = {},
	onRecordSwipeUp: () -> Unit = {},
	onRecordSwipeLeft: () -> Unit = {},
	onRecordDrag: (Float, Float) -> Unit = { _, _ -> },
	onRecordStopClick: () -> Unit = {},
	onRecordSendClick: () -> Unit = onRecordStopClick,
	videoCircleInteractionActive: Boolean = false,
	videoCircleContent: @Composable (me.floow.uikit.chat.model.VideoCircleOutMessage, Boolean, Dp, () -> Unit) -> Unit = { _, _, _, _ -> },
	voiceContent: @Composable (me.floow.uikit.chat.model.VoiceMessage, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
	imageContent: @Composable (
		message: me.floow.uikit.chat.model.ImageMessage,
		isSelectionMode: Boolean,
		selectedImageIds: Set<Long>,
		onToggleImageSelection: (Long) -> Unit,
		onClick: () -> Unit,
		onLongClick: () -> Unit,
		onRetry: (String) -> Unit,
		onCancel: (String) -> Unit,
	) -> Unit = { _, _, _, _, _, _, _, _ -> },
	onImageRetry: ((String) -> Unit)? = null,
	onImageCancel: ((String) -> Unit)? = null,
	onAttachClick: (() -> Unit)? = null,
	recordButtonMode: me.floow.uikit.chat.model.ChatRecordButtonMode = me.floow.uikit.chat.model.ChatRecordButtonMode.Voice,
	onRecordButtonModeToggle: () -> Unit = {},
	voiceDraft: me.floow.uikit.chat.model.VoiceDraftUiState? = null,
	onVoiceDraftPlayPause: () -> Unit = {},
	onVoiceDraftSeek: (Float) -> Unit = {},
	onVoiceDraftDelete: () -> Unit = {},
	onVoiceDraftSend: () -> Unit = {},
	voiceRecordingState: () -> me.floow.uikit.chat.model.VoiceRecordingInputState = { me.floow.uikit.chat.model.VoiceRecordingInputState() },
	imageDrafts: List<ChatDraftImage> = emptyList(),
	onImageDraftRemove: (String) -> Unit = {},
	onImageDraftReorder: (List<String>) -> Unit = {},
	rowBoundsByMessageKey: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.geometry.Rect>,
	bubbleBoundsByMessageKey: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.geometry.Rect> = androidx.compose.runtime.mutableStateMapOf(),
	onHighlightDismissed: () -> Unit = {},
	modifier: Modifier = Modifier,
) {
	val strings = rememberSharedChatStrings()
	val scope = rememberCoroutineScope()
	val loadedHeader = state.headerOrNull()
	val header = remember(loadedHeader, initialHeader, strings.savedMessagesTitle) {
		resolveDirectChatHeader(
			loadedHeader = loadedHeader,
			initialHeader = initialHeader,
			savedMessagesTitle = strings.savedMessagesTitle,
		)
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
	val isVoiceRecording by remember(voiceRecordingState) {
		derivedStateOf(structuralEqualityPolicy()) { voiceRecordingState().isRecording }
	}
	val placeMenuAboveKeyboard = remember(activeContextMenuMessageId) {
		activeContextMenuMessageId != null && actualImeHeightPx > 0
	}
	val recordingOverlayBottomInsetPx by remember(inputController, actualImeHeightPx, recordingState.mode, isVoiceRecording) {
		derivedStateOf {
			if (recordingState.mode == VideoRecordingMode.Idle && !isVoiceRecording) {
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

	fun beginReply(messageId: Long) {
		onReplyCreatedFeedback()
		onReplyClick(messageId)
		inputController.openKeyboard()
	}

	LaunchedEffect(recordingState.mode) {
		val previousMode = lastRecordingMode
		val currentMode = recordingState.mode
		val wasActiveFlow = previousMode != VideoRecordingMode.Idle

		when (currentMode) {
			VideoRecordingMode.Recording,
			VideoRecordingMode.Preview -> {
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

	var lastVoiceRecording by remember { mutableStateOf(false) }
	LaunchedEffect(isVoiceRecording) {
		val wasRecording = lastVoiceRecording
		val isRecording = isVoiceRecording
		if (isRecording && !wasRecording) {
			inputController.enterRecording()
		} else if (!isRecording && wasRecording) {
			inputController.exitRecordingWithRestore()
		}
		lastVoiceRecording = isRecording
	}

	LaunchedEffect(replyField?.replyId, hasDataState?.messageToEditId) {
		if ((replyField != null || hasDataState?.messageToEditId != null) && !selectionState.isSelectionMode) {
			inputController.openKeyboard()
		}
	}
	LaunchedEffect(selectionState.isSelectionMode) {
		if (selectionState.isSelectionMode &&
			(inputController.inputMode == ChatInputMode.Keyboard || inputController.inputMode == ChatInputMode.Emoji)
		) {
			inputController.closeInput()
		}
	}

	val sendButtonActive by remember(state, inputController, imageDrafts) {
		derivedStateOf {
			(imageDrafts.isNotEmpty() || inputController.textFieldValue.text.isNotBlank()) &&
				(state as? DirectChatScreenState.HasData)?.sending != true
		}
	}
	val savedMessagesAvatarPainter = resolveSavedMessagesAvatarPainter(header)
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
	val recordingContentTransformModifier = Modifier
		.graphicsLayer {
			scaleX = recordingContentScale
			scaleY = recordingContentScale
			transformOrigin = TransformOrigin(0.5f, 0.52f)
		}

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

	CompositionLocalProvider(
		LocalContextMenuElevation provides contextMenuElevatedData,
	) {
		ChatScreenBody(
			title = header.title,
			subtitle = header.subtitle,
		avatarSource = header.avatarSource,
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
		onRecordSendClick = onRecordSendClick,
		onRecordButtonBoundsChanged = { bounds ->
			recordingButtonBounds = bounds
		},
		recordingState = recordingState,
		recordCancelThresholdPx = recordingState.cancelThresholdPx,
		recordLockThresholdPx = recordingState.lockThresholdPx,
		recordButtonMode = recordButtonMode,
		onRecordButtonModeToggle = onRecordButtonModeToggle,
		voiceDraft = voiceDraft,
		onVoiceDraftPlayPause = onVoiceDraftPlayPause,
		onVoiceDraftSeek = onVoiceDraftSeek,
		onVoiceDraftDelete = onVoiceDraftDelete,
		onVoiceDraftSend = onVoiceDraftSend,
		voiceRecordingState = voiceRecordingState,
		imageDrafts = imageDrafts,
		onImageDraftRemove = onImageDraftRemove,
		onImageDraftReorder = onImageDraftReorder,
		onAttachClick = if (hasDataState?.messageToEditId == null) onAttachClick else null,
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
		playbackBar = playbackBar,
		pinnedMessages = if (hasDataState != null && hasDataState.pinnedMessages.isNotEmpty()) {
			{
				FlowPinnedMessagesBar(
					pinnedMessages = hasDataState.pinnedMessages,
					onMessageClick = { messageId ->
						messageId.toLongOrNull()?.let { onPinnedMessageClick(it) }
					},
					onUnpinClick = { messageId ->
						messageId.toLongOrNull()?.let { onTogglePinClick(it, false) }
					},
				)
			}
		} else {
			null
		},
		emojiPanel = {
			emojiPanel(inputController)
		},
		content = { contentModifier, composerBottomInset ->
			when (uiState) {
				is ChatScreenUiState.Loading -> ChatMessagesSkeleton(
					showIncomingAvatarSpace = config.interactionPolicy.showAuthorHeaderForInMessages,
					modifier = contentModifier,
				)

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
						onReply = { beginReply(it.id) },
						onReplyClick = { onReplyPreviewClick(resolveReplyTargetId(it)) },
						onRetrySendClick = null,
						onImageRetry = onImageRetry,
						onImageCancel = onImageCancel,
						onPostImageClick = { _, _, _, _ -> },
						hiddenPostImageIndex = null,
						hiddenPostImageRevealProgress = 0f,
						onRequestScrollToBottom = onRequestScrollToBottom,
						onUserStartedScroll = onUserStartedScroll,
						onViewportSnapshotChanged = onViewportSnapshotChanged,
						onScrollSettled = onScrollSettled,
						onAnchorRestoreSettled = onAnchorRestoreSettled,
						onAnchorRestoreTimedOut = onAnchorRestoreTimedOut,
						resolveContextMenuActions = { message ->
							resolveContextMenuActions(message.id)
						},
						onContextMenuOpenRequest = { message, anchorRect, highlightRect, elevatedData ->
							onContextMenuOpen(message.id, anchorRect, highlightRect, elevatedData)
						},
						onContextMenuDismissRequest = onDismissContextMenu,
						rowBoundsByMessageKey = rowBoundsByMessageKey,
						bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
						onLoadMore = onLoadMore,
						config = config,
						composerBottomInset = composerBottomInset,
						messageListScrollEnabled = !videoCircleInteractionActive,
						videoCircleContent = videoCircleContent,
						voiceContent = voiceContent,
						imageContent = imageContent,
						onHighlightDismissed = onHighlightDismissed,
						modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceContainer),
					)
				}
			}
		},
		bodyContentModifier = recordingContentTransformModifier,
		screenOverlay = when {
			recordingState.isActive -> {
				{ overlayModifier ->
					videoRecordingOverlay(recordingOverlayBottomInsetPx, recordingButtonBounds, overlayModifier)
				}
			}
			flightState != null && flightState.phase == me.floow.uikit.chat.model.FlightPhase.InFlight -> {
				{ overlayModifier ->
					videoCircleFlightOverlay(overlayModifier)
				}
			}
			isVoiceRecording -> {
				{ overlayModifier ->
					val voiceState = voiceRecordingState()
					val lockThreshold = if (recordingState.lockThresholdPx > 0f) recordingState.lockThresholdPx else 180f
					val lockProgress = ((-voiceState.dragOffsetY) / lockThreshold).coerceIn(0f, 1f)
					val density = LocalDensity.current
					val bottomInsetDp = with(density) { recordingOverlayBottomInsetPx.toDp() }
					Box(
						modifier = overlayModifier.fillMaxSize().navigationBarsPadding(),
						contentAlignment = Alignment.BottomEnd,
					) {
						Box(
							modifier = Modifier
								.padding(end = 23.dp, bottom = 108.dp + bottomInsetDp)
								.width(36.dp),
							contentAlignment = Alignment.Center,
						) {
							RecordingLockPill(
								isLocked = voiceState.isLocked,
								lockProgress = lockProgress,
								onPauseClick = onRecordStopClick,
							)
						}
					}
				}
			}
			else -> null
		},
		contextMenuOverlay = { viewport ->
			MessageContextMenuOverlay(
				actions = contextMenuActions,
				anchorRect = contextMenuAnchorRect,
				highlightRect = contextMenuHighlightRect,
				elevatedData = contextMenuElevatedData,
				isOutgoing = isContextMenuOutgoing,
				onActionClick = onContextMenuActionClick,
				onDismissRequest = onDismissContextMenu,
				aboveKeyboard = placeMenuAboveKeyboard,
				messageViewport = viewport,
			)
		},
		modifier = modifier
			.fillMaxSize()
			.then(recordingSwipeBlockModifier),
	)
	}
}

@Composable
private fun resolveSavedMessagesAvatarPainter(
	header: ChatThreadHeaderModel,
) = if (header.isSavedMessages) {
	painterResource(Res.drawable.bookmark_icon)
} else {
	null
}

private fun resolveDirectChatHeader(
	loadedHeader: ChatThreadHeaderModel?,
	initialHeader: ChatThreadHeaderModel,
	savedMessagesTitle: String,
): ChatThreadHeaderModel {
	val baseHeader = loadedHeader ?: initialHeader
	return if (initialHeader.isSavedMessages || loadedHeader?.isSavedMessages == true) {
		baseHeader.copy(isSavedMessages = true)
			.withSavedMessagesSemantics(titleOverride = savedMessagesTitle)
	} else {
		baseHeader
	}
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
