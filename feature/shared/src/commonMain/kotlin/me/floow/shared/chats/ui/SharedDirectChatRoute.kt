package me.floow.shared.chats.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.uilogic.direct.DeleteMessageResult
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.VideoCircleOutMessage

@Composable
fun SharedDirectChatRoute(
	stateHolder: DirectChatStateHolder,
	initialRequest: DirectChatInitialRequest,
	onBackClick: () -> Unit,
	onShowMessage: (String) -> Unit,
	onCopyText: (String) -> Unit = {},
	onHeaderClick: (() -> Unit)? = null,
	emojiPanel: @Composable (ChatInputController) -> Unit = {},
	videoRecordingOverlay: @Composable (Int, Rect?, Modifier) -> Unit = { _, _, _ -> },
	recordingState: me.floow.uikit.chat.model.VideoRecordingState = me.floow.uikit.chat.model.VideoRecordingState(),
	onRecordButtonPress: () -> Unit = {},
	onRecordButtonRelease: () -> Unit = {},
	onRecordSwipeUp: () -> Unit = {},
	onRecordSwipeLeft: () -> Unit = {},
	onRecordDrag: (Float, Float) -> Unit = { _, _ -> },
	onRecordStopClick: () -> Unit = {},
	onViewportSnapshotChanged: (ChatViewportSnapshot) -> Unit = {},
	videoCircleInteractionActive: Boolean = false,
	videoCircleContent: @Composable (me.floow.uikit.chat.model.VideoCircleOutMessage) -> Unit = {},
	bubbleBoundsByMessageKey: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.geometry.Rect> = androidx.compose.runtime.mutableStateMapOf(),
	modifier: Modifier = Modifier,
) {
	val state by stateHolder.state.collectAsState()
	val strings = rememberSharedChatStrings()
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }
	var jumpRequest by remember(stateHolder) { mutableStateOf<SharedJumpRequest?>(null) }
	var contextMenuMessageId by remember(stateHolder) { mutableStateOf<Long?>(null) }
	var contextMenuAnchorRect by remember(stateHolder) { mutableStateOf<Rect?>(null) }
	var contextMenuHighlightRect by remember(stateHolder) { mutableStateOf<Rect?>(null) }
	var selectedMessageIds by remember(stateHolder) { mutableStateOf<Set<Long>>(emptySet()) }
	var pendingDeleteMessageId by remember(stateHolder) { mutableStateOf<Long?>(null) }
	var isDeleteSelectionConfirmationVisible by remember(stateHolder) { mutableStateOf(false) }

	val currentMessages = when (val currentState = state) {
		is DirectChatScreenState.HasData -> currentState.messages
		else -> emptyList()
	}
	val messagesById = remember(currentMessages) {
		currentMessages.associateBy(ChatMessageItemModel::id)
	}
	val contextMenuMessage = contextMenuMessageId?.let(messagesById::get)
	val selectionMode = selectedMessageIds.isNotEmpty()
	val selectedMessages = remember(selectedMessageIds, messagesById) {
		selectedMessageIds.mapNotNull(messagesById::get)
	}
	val selectedCopyText = remember(selectedMessages) {
		selectedMessages
			.filterNot(ChatMessageItemModel::isDeleted)
			.map(ChatMessageItemModel::text)
			.filter(String::isNotBlank)
			.joinToString(separator = "\n")
	}
	val selectionState = remember(selectedMessageIds, selectedCopyText) {
		ChatSelectionState(
			selectedMessageIds = selectedMessageIds,
			selectedCount = selectedMessageIds.size,
			canCopy = selectedCopyText.isNotBlank(),
			canDelete = selectedMessageIds.isNotEmpty(),
			copyText = selectedCopyText,
		)
	}
	val contextMenuActions = remember(contextMenuMessage) {
		contextMenuMessage?.let(::resolveContextMenuActions).orEmpty()
	}

	LaunchedEffect(stateHolder, initialRequest) {
		stateHolder.load(initialRequest)
	}

	DisposableEffect(stateHolder) {
		onDispose {
			stateHolder.dispose()
		}
	}

	LaunchedEffect(messagesById) {
		selectedMessageIds = selectedMessageIds.filter(messagesById::containsKey).toSet()
		if (contextMenuMessageId != null && contextMenuMessageId !in messagesById) {
			contextMenuMessageId = null
			contextMenuAnchorRect = null
			contextMenuHighlightRect = null
		}
		if (pendingDeleteMessageId != null && pendingDeleteMessageId !in messagesById) {
			pendingDeleteMessageId = null
		}
		if (selectedMessageIds.isEmpty()) {
			isDeleteSelectionConfirmationVisible = false
		}
	}

	LaunchedEffect(stateHolder) {
		stateHolder.events.collectLatest { event ->
			when (event) {
				is DirectChatStateHolder.Event.ShowMessage -> snackbarHostState.showSnackbar(event.message)
				is DirectChatStateHolder.Event.JumpToMessage -> {
					val nextToken = (jumpRequest?.requestToken ?: 0L) + 1L
					jumpRequest = SharedJumpRequest(
						messageId = event.messageId,
						requestToken = nextToken,
					)
				}
			}
		}
	}

	SharedDirectChatScreen(
		state = state,
		initialHeader = ChatThreadHeaderModel(
			peerUserId = if (initialRequest.isSavedMessages) "" else initialRequest.peerUserId,
			title = initialRequest.peerDisplayName,
			avatarUrl = initialRequest.peerAvatarUrl,
			subtitle = null,
			isSavedMessages = initialRequest.isSavedMessages,
		),
		selectionState = selectionState,
		contextMenuActions = contextMenuActions,
		resolveContextMenuActions = { messageId ->
			messagesById[messageId]?.let(::resolveContextMenuActions).orEmpty()
		},
		jumpRequest = jumpRequest,
		onBackClick = onBackClick,
		onHeaderClick = onHeaderClick,
		onInputChange = stateHolder::updateInput,
		onSendClick = stateHolder::sendOrEdit,
		onReplyClick = stateHolder::addReply,
		onClearReplyClick = stateHolder::clearReply,
		onEditClick = stateHolder::startEditing,
		onCancelEditClick = stateHolder::cancelEditing,
		onDeleteClick = { stateHolder.deleteMessage(it) },
		onTogglePinClick = stateHolder::togglePin,
		onLoadMore = stateHolder::loadMore,
		onRequestScrollToBottom = stateHolder::requestScrollToBottom,
		onUserStartedScroll = stateHolder::onUserStartedScroll,
		onViewportSnapshotChanged = { snapshot ->
			stateHolder.onViewportSnapshotChanged(snapshot)
			onViewportSnapshotChanged(snapshot)
		},
		onMessageClick = { messageId ->
			if (selectionMode) {
				selectedMessageIds = selectedMessageIds.toggle(messageId)
			}
		},
		onMessageLongClick = { messageId ->
			contextMenuMessageId = null
			selectedMessageIds = selectedMessageIds.toggle(messageId)
		},
		onReplyPreviewClick = stateHolder::jumpToMessage,
		onContextMenuOpen = { messageId, anchorRect, highlightRect ->
			contextMenuMessageId = messageId
			contextMenuAnchorRect = anchorRect
			contextMenuHighlightRect = highlightRect
		},
		contextMenuAnchorRect = contextMenuAnchorRect,
		contextMenuHighlightRect = contextMenuHighlightRect,
		onDismissContextMenu = {
			contextMenuMessageId = null
			contextMenuAnchorRect = null
			contextMenuHighlightRect = null
		},
		onContextMenuActionClick = { action ->
			val message = contextMenuMessage ?: return@SharedDirectChatScreen
			contextMenuMessageId = null
			contextMenuAnchorRect = null
			contextMenuHighlightRect = null
			when (action) {
				ChatContextMenuAction.Reply -> stateHolder.addReply(message.id)
				ChatContextMenuAction.Pin -> stateHolder.togglePin(message.id, true)
				ChatContextMenuAction.Unpin -> stateHolder.togglePin(message.id, false)
				ChatContextMenuAction.CopyText -> {
					onCopyText(message.text)
					scope.launch {
						snackbarHostState.showSnackbar(strings.copied)
					}
				}
				ChatContextMenuAction.Edit -> stateHolder.startEditing(message.id)
				ChatContextMenuAction.Delete -> pendingDeleteMessageId = message.id
				ChatContextMenuAction.Retry -> {
					if (message.deliveryState == ChatDeliveryState.FAILED) {
						stateHolder.retryMessage(message.id)
					}
				}
			}
		},
		pendingDeleteMessageId = pendingDeleteMessageId,
		isDeleteSelectionConfirmationVisible = isDeleteSelectionConfirmationVisible,
		onDismissDeleteMessageConfirmation = {
			pendingDeleteMessageId = null
		},
		onDismissDeleteSelectionConfirmation = {
			isDeleteSelectionConfirmationVisible = false
		},
		onConfirmDeleteMessage = { messageId ->
			pendingDeleteMessageId = null
			stateHolder.deleteMessage(messageId) == DeleteMessageResult.DELETED_WITH_UNDO
		},
		onUndoDeleteClick = {
			stateHolder.undoDeleteMessage()
		},
		onConfirmDeleteSelection = {
			stateHolder.deleteMessages(selectedMessages.map(ChatMessageItemModel::id))
			selectedMessageIds = emptySet()
			isDeleteSelectionConfirmationVisible = false
		},
		onSelectionCloseClick = {
			selectedMessageIds = emptySet()
			isDeleteSelectionConfirmationVisible = false
		},
		onCopySelectionClick = {
			if (selectedCopyText.isNotBlank()) {
				onCopyText(selectedCopyText)
				scope.launch {
					snackbarHostState.showSnackbar(strings.copied)
				}
			}
		},
		onDeleteSelectionClick = {
			isDeleteSelectionConfirmationVisible = true
		},
		onPinnedMessageClick = stateHolder::jumpToMessage,
		snackbarHostState = snackbarHostState,
		emojiPanel = emojiPanel,
		videoRecordingOverlay = videoRecordingOverlay,
		recordingState = recordingState,
		onRecordButtonPress = onRecordButtonPress,
		onRecordButtonRelease = onRecordButtonRelease,
		onRecordSwipeUp = onRecordSwipeUp,
		onRecordSwipeLeft = onRecordSwipeLeft,
		onRecordDrag = onRecordDrag,
		onRecordStopClick = onRecordStopClick,
		videoCircleInteractionActive = videoCircleInteractionActive,
		videoCircleContent = videoCircleContent,
		bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
		modifier = modifier,
	)
}

private fun Set<Long>.toggle(messageId: Long): Set<Long> {
	return if (contains(messageId)) this - messageId else this + messageId
}

private fun resolveContextMenuActions(message: ChatMessageItemModel): List<ChatContextMenuAction> {
	return buildList {
		if (!message.isDeleted) {
			add(ChatContextMenuAction.Reply)
		}
		if (message.isPinned) {
			add(ChatContextMenuAction.Unpin)
		} else {
			add(ChatContextMenuAction.Pin)
		}
		if (message.text.isNotBlank()) {
			add(ChatContextMenuAction.CopyText)
		}
		if (message.isOutgoing && !message.isDeleted) {
			add(ChatContextMenuAction.Edit)
			add(ChatContextMenuAction.Delete)
		} else if (!message.isDeleted) {
			add(ChatContextMenuAction.Delete)
		}
		if (message.deliveryState == ChatDeliveryState.FAILED) {
			add(ChatContextMenuAction.Retry)
		}
	}
}
