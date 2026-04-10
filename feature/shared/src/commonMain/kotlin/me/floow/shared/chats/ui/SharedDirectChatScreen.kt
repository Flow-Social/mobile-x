package me.floow.shared.chats.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.bookmark_icon
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.uikit.chat.common.ChatScreenBody
import me.floow.uikit.chat.input.rememberChatInputController
import me.floow.uikit.chat.input.rememberChatInputLayoutState
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.resolveReplyTargetId
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.components.loading.FlowLoadingIndicator
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
	val hasDataState = uiState as? ChatScreenUiState.HasData
	val replyField = hasDataState?.messageFieldReply
	val sendButtonActive by remember(state, inputController) {
		derivedStateOf {
			inputController.textFieldValue.text.isNotBlank() &&
				(state as? DirectChatScreenState.HasData)?.sending != true
		}
	}
	val rowBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }
	val bubbleBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }
	val savedMessagesAvatarPainter = if (header.isSavedMessages) {
		painterResource(Res.drawable.bookmark_icon)
	} else {
		null
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
						modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceContainer),
					)
				}
			}
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
		modifier = modifier.fillMaxSize(),
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
