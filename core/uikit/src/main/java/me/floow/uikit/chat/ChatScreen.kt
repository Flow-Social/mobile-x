package me.floow.uikit.chat

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.floow.uikit.R
import me.floow.uikit.chat.components.ChatBottomHost
import me.floow.uikit.chat.components.ChatDeleteConfirmationDialog
import me.floow.uikit.chat.components.ChatScreenTitleTopBar
import me.floow.uikit.chat.components.ChatScreenTopBar
import me.floow.uikit.chat.components.ChatSelectionTopBar
import me.floow.uikit.chat.components.CurrentReply
import me.floow.uikit.chat.components.MessageContextMenuOverlay
import me.floow.uikit.chat.components.MessageInputField
import me.floow.uikit.chat.components.PinnedMessagesBar
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.chat.input.rememberChatInputController
import me.floow.uikit.chat.input.rememberChatInputLayoutState
import me.floow.uikit.chat.input.rememberChatInputLifecycleBridge
import me.floow.uikit.chat.input.rememberChatInputPersistenceBridge
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatTopBarMode
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.states.ErrorState
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.chat.states.LoadingState
import me.floow.uikit.chat.states.NoMessagesState
import me.floow.uikit.components.avatar.NetworkAvatar
import androidx.compose.ui.graphics.painter.Painter
import me.floow.uikit.components.pickers.FlowEmojiPanel

@Composable
fun ChatScreen(
	onBackClick: () -> Unit,
	onProfileClick: () -> Unit,
	onTopBarDropdownClick: () -> Unit,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onChatBubbleLongClick: (ChatMessage) -> Unit = {},
	onToggleSelection: (Long) -> Unit = {},
	onMessageActionClick: ((ChatMessage) -> Unit)? = null,
	onAvatarClick: (ChatMessage) -> Unit = {},
	onJumpToMessage: (Long) -> Unit,
	onPinnedMessageClick: (Long) -> Unit = onJumpToMessage,
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onCurrentReplyClose: () -> Unit,
	onCurrentReplyClick: () -> Unit,
	onCancelEdit: () -> Unit,
	onMessageInputFieldValueChange: (String) -> Unit,
	onSendClick: () -> Unit,
	onRequestScrollToBottom: () -> Unit,
	onUserStartedScroll: () -> Unit = {},
	onRetryClick: () -> Unit = {},
	onViewportSnapshotChanged: (ChatViewportSnapshot) -> Unit = {},
	onAnchorRestoreSettled: (Long, Int) -> Unit = { _, _ -> },
	onAnchorRestoreTimedOut: (Long) -> Unit = {},
	onLoadMore: () -> Unit = {},
	onPostImageClick: (PostPreviewMessage, Int, Rect?, Painter?) -> Unit = { _, _, _, _ -> },
	hiddenPostImageIndex: Int? = null,
	hiddenPostImageRevealProgress: Float = 0f,
	suspendInitialPlacement: Boolean = false,
	onPinMessage: (Long) -> Unit,
	onUnpinMessage: (Long) -> Unit,
	onDeleteMessage: (ChatMessage) -> Unit,
	onEditMessage: (Long, String) -> Unit,
	onRetryMessage: (ChatMessage) -> Unit,
	onUndoDelete: () -> Unit,
	onClearSelection: () -> Unit = {},
	onDeleteSelectedMessages: () -> Unit = {},
	resolveContextMenuActions: (ChatMessage) -> List<ChatContextMenuAction> = { emptyList() },
	config: ChatScreenConfig = ChatScreenConfig(),
	state: ChatScreenUiState,
	modifier: Modifier = Modifier,
) {
	val interactionPolicy = config.interactionPolicy
	val topBarPolicy = config.topBarPolicy
	val snackbarHostState = remember { SnackbarHostState() }
	val coroutineScope = rememberCoroutineScope()
	val density = LocalDensity.current
	val clipboard = LocalClipboard.current
	var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
	var shouldConfirmDeleteSelected by remember { mutableStateOf(false) }
	var contextMenuMessage by remember { mutableStateOf<ChatMessage?>(null) }
	var menuOpenedOverKeyboard by remember { mutableStateOf(false) }
	val boundsKey = remember(state) {
		when (state) {
			is ChatScreenUiState.HasData -> "${state.chatInterlocutorId}:${state.timelineSessionToken}"
			else -> "no-session"
		}
	}
	val rowBoundsByMessageKey = remember(boundsKey) { mutableStateMapOf<String, Rect>() }
	val bubbleBoundsByMessageKey = remember(boundsKey) { mutableStateMapOf<String, Rect>() }
	var contextMenuTargetKey by remember { mutableStateOf<String?>(null) }
	val deleteMessageTitle = stringResource(R.string.chat_delete_message_title)
	val deleteMessageText = stringResource(R.string.chat_delete_message_text)
	val deleteMessageConfirm = stringResource(R.string.chat_delete_message_confirm)
	val deleteMessageCancel = stringResource(R.string.chat_delete_message_cancel)
	val deleteSelectedMessagesTitle = stringResource(R.string.chat_delete_selected_messages_title)
	val deleteSelectedMessagesText = stringResource(R.string.chat_delete_selected_messages_text)
	val messageDeletedText = stringResource(R.string.chat_message_deleted)
	val undoActionText = stringResource(R.string.chat_undo_action)
	val editingTitleText = stringResource(R.string.chat_editing_title)
	val fallbackPanelHeightPx = with(density) { 340.dp.roundToPx() }
	val selectionState = (state as? ChatScreenUiState.HasData)?.selectionState
	val isSelectionMode = selectionState?.isSelectionMode == true
	val messageFieldReply = state.messageFieldReply
	val inputController = rememberChatInputController(
		text = state.messageFieldValue,
		maxLength = interactionPolicy.maxInputLength,
		fallbackPanelHeightPx = fallbackPanelHeightPx,
		onTextChanged = onMessageInputFieldValueChange,
	)
	rememberChatInputLifecycleBridge(controller = inputController)
	rememberChatInputPersistenceBridge(controller = inputController)
	val actualImeHeightPx = rememberChatInputLayoutState(controller = inputController)

	val composerActivationToken = when {
		state is ChatScreenUiState.HasData && state.messageToEditId != null -> "edit:${state.messageToEditId}"
		messageFieldReply != null -> "reply:${messageFieldReply.replyId}"
		else -> null
	}

	LaunchedEffect(interactionPolicy.showEmojiButton, interactionPolicy.showInputBar, isSelectionMode) {
		if (!interactionPolicy.showEmojiButton || !interactionPolicy.showInputBar || isSelectionMode) {
			inputController.closeInput()
		}
		if (isSelectionMode) {
			contextMenuMessage = null
			contextMenuTargetKey = null
		}
	}

	LaunchedEffect(composerActivationToken, interactionPolicy.showInputBar, isSelectionMode, contextMenuMessage) {
		if (
			composerActivationToken != null &&
			interactionPolicy.showInputBar &&
			!isSelectionMode &&
			contextMenuMessage == null &&
			inputController.inputMode == ChatInputMode.None
		) {
			inputController.openKeyboard()
		}
	}

	LaunchedEffect(contextMenuMessage, actualImeHeightPx, inputController.inputMode) {
		if (contextMenuMessage == null) return@LaunchedEffect
		if (!menuOpenedOverKeyboard) return@LaunchedEffect
		if (actualImeHeightPx > 0) return@LaunchedEffect

		contextMenuMessage = null
		contextMenuTargetKey = null
		menuOpenedOverKeyboard = false

		if (inputController.inputMode == ChatInputMode.Keyboard) {
			inputController.openKeyboard()
		}
	}

	val isBackHandlerActive = contextMenuMessage != null ||
		isSelectionMode ||
		inputController.inputMode != ChatInputMode.None

	BackHandler(enabled = isBackHandlerActive) {
		when {
			contextMenuMessage != null -> {
				contextMenuMessage = null
				contextMenuTargetKey = null
				menuOpenedOverKeyboard = false
				if (actualImeHeightPx > 0 || inputController.inputMode == ChatInputMode.Keyboard) {
					inputController.openKeyboard()
				}
			}
			isSelectionMode -> {
				shouldConfirmDeleteSelected = false
				onClearSelection()
			}
			else -> {
				if (!inputController.handleBack()) {
					onBackClick()
				}
			}
		}
	}

	if (messageToDelete != null) {
		ChatDeleteConfirmationDialog(
			title = deleteMessageTitle,
			text = deleteMessageText,
			confirmText = deleteMessageConfirm,
			cancelText = deleteMessageCancel,
			onConfirm = {
				messageToDelete?.let { msg ->
					onDeleteMessage(msg)
					coroutineScope.launch {
						val result = snackbarHostState.showSnackbar(
							message = messageDeletedText,
							actionLabel = undoActionText,
							duration = androidx.compose.material3.SnackbarDuration.Short,
						)
						if (result == SnackbarResult.ActionPerformed) {
							onUndoDelete()
						}
					}
				}
				messageToDelete = null
			},
			onDismiss = { messageToDelete = null },
		)
	}

	if (shouldConfirmDeleteSelected) {
		ChatDeleteConfirmationDialog(
			title = deleteSelectedMessagesTitle,
			text = deleteSelectedMessagesText,
			confirmText = deleteMessageConfirm,
			cancelText = deleteMessageCancel,
			onConfirm = {
				shouldConfirmDeleteSelected = false
				onDeleteSelectedMessages()
			},
			onDismiss = { shouldConfirmDeleteSelected = false },
		)
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) {
		Scaffold(
			topBar = {
				if (isSelectionMode) {
					ChatSelectionTopBar(
						selectedCount = selectionState.selectedCount,
						canCopy = selectionState.canCopy,
						canDelete = selectionState.canDelete,
						onCloseClick = onClearSelection,
						onCopyClick = {
							if (selectionState.copyText.isNotBlank()) {
								coroutineScope.launch {
									clipboard.setClipEntry(
										ClipEntry(ClipData.newPlainText("chat-selection", selectionState.copyText)),
									)
								}
							}
						},
						onDeleteClick = { shouldConfirmDeleteSelected = true },
						dividerColor = topBarPolicy.dividerColor,
						modifier = Modifier.statusBarsPadding(),
					)
				} else {
					val typingUsers = if (state is ChatScreenUiState.HasData && interactionPolicy.showTypingIndicator) {
						state.typingUserNames
					} else {
						emptyList()
					}
					if (topBarPolicy.mode == ChatTopBarMode.TitleOnly) {
						ChatScreenTitleTopBar(
							title = topBarPolicy.title ?: state.chatInterlocutorName,
							onBackClick = onBackClick,
							onDropdownClick = onTopBarDropdownClick,
							showDropdown = topBarPolicy.showDropdown,
							dividerColor = topBarPolicy.dividerColor,
							modifier = Modifier.statusBarsPadding(),
						)
					} else {
						ChatScreenTopBar(
							profileName = topBarPolicy.title ?: state.chatInterlocutorName,
							isOnline = state.peerIsOnline,
							lastSeenAtMillis = state.peerLastSeenAtMillis,
							typingUsers = typingUsers,
							showSubtitle = topBarPolicy.showSubtitle,
							profileAvatar = { innerModifier ->
								if (topBarPolicy.avatarResId != null) {
									if (topBarPolicy.avatarResId == R.drawable.bookmark_icon) {
										Box(
											modifier = innerModifier
												.clip(CircleShape)
												.background(MaterialTheme.colorScheme.primary),
											contentAlignment = Alignment.Center,
										) {
											Icon(
												painter = painterResource(topBarPolicy.avatarResId),
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onPrimary,
												modifier = Modifier.size(21.dp),
											)
										}
									} else {
										Image(
											painter = painterResource(topBarPolicy.avatarResId),
											contentDescription = null,
											contentScale = ContentScale.Crop,
											modifier = innerModifier.clip(CircleShape),
										)
									}
								} else {
									NetworkAvatar(
										name = state.chatInterlocutorName,
										avatarModel = state.chatInterlocutorAvatarUrl,
										contentDescription = null,
										size = 42.dp,
										shape = CircleShape,
										modifier = innerModifier,
									)
								}
							},
							onBackClick = onBackClick,
							onDropdownClick = onTopBarDropdownClick,
							onProfileClick = onProfileClick,
							dividerColor = topBarPolicy.dividerColor,
							modifier = Modifier.statusBarsPadding(),
						)
					}
				}
			},
			bottomBar = {
				if (interactionPolicy.showInputBar) {
					ChatBottomHost(
						controller = inputController,
						actualImeHeightPx = actualImeHeightPx,
						modifier = Modifier.fillMaxWidth(),
						composer = {
							val isEditMode = state is ChatScreenUiState.HasData && state.messageToEditId != null
							if (isEditMode) {
								CurrentReply(
									userNameToReply = "",
									replyMessageText = state.messageFieldValue,
									titleText = editingTitleText,
									onClose = onCancelEdit,
									modifier = Modifier.fillMaxWidth(),
								)
							} else {
								state.messageFieldReply?.let { reply ->
									CurrentReply(
										userNameToReply = reply.replyAuthorName,
										replyMessageText = reply.replyMessageText,
										onClose = onCurrentReplyClose,
										onClick = onCurrentReplyClick,
										modifier = Modifier.fillMaxWidth(),
									)
								}
							}
							HorizontalDivider(color = config.dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
							MessageInputField(
								controller = inputController,
								onSendClick = onSendClick,
								sendButtonActive = inputController.textFieldValue.text.isNotEmpty(),
								isEditMode = state is ChatScreenUiState.HasData && state.messageToEditId != null,
								showEmojiButton = interactionPolicy.showEmojiButton && !isSelectionMode,
								modifier = Modifier.fillMaxWidth(),
							)
						},
						emojiPanel = {
							Column(
								modifier = Modifier
									.fillMaxSize()
									.background(MaterialTheme.colorScheme.surface),
							) {
								HorizontalDivider(color = config.dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
								FlowEmojiPanel(
									onEmojiPicked = inputController::insertEmoji,
									modifier = Modifier
										.fillMaxSize()
										.background(MaterialTheme.colorScheme.surface),
								)
							}
						},
					)
				}
			},
			snackbarHost = { SnackbarHost(snackbarHostState) },
			contentWindowInsets = WindowInsets(0.dp),
			modifier = Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.background),
		) { innerPadding ->
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding),
			) {
				if (interactionPolicy.showPinActions && state is ChatScreenUiState.HasData) {
					PinnedMessagesBar(
						pinnedMessages = state.pinnedMessages,
						onMessageClick = { onPinnedMessageClick(it.id) },
						onUnpinClick = { onUnpinMessage(it.id) },
					)
				}

				val commonModifier = Modifier
					.fillMaxWidth()
					.weight(1f)
					.background(MaterialTheme.colorScheme.surfaceContainer)

				when (state) {
					is ChatScreenUiState.Loading -> LoadingState(commonModifier)
					is ChatScreenUiState.Error -> ErrorState(
						onRetryClick = onRetryClick,
						modifier = commonModifier,
					)
					is ChatScreenUiState.NoMessages -> NoMessagesState(commonModifier)
					is ChatScreenUiState.HasData -> {
						HasDataState(
							state = state,
							onChatBubbleClick = onChatBubbleClick,
							onMessageActionClick = onMessageActionClick,
							onAvatarClick = onAvatarClick,
							onReply = onReply,
							onReplyClick = onReplyClick,
							onRetrySendClick = { message -> onRetryMessage(message) },
							onPostImageClick = onPostImageClick,
							hiddenPostImageIndex = hiddenPostImageIndex,
							hiddenPostImageRevealProgress = hiddenPostImageRevealProgress,
							onRequestScrollToBottom = onRequestScrollToBottom,
							onUserStartedScroll = onUserStartedScroll,
							onViewportSnapshotChanged = onViewportSnapshotChanged,
							onAnchorRestoreSettled = onAnchorRestoreSettled,
							onAnchorRestoreTimedOut = onAnchorRestoreTimedOut,
							suspendInitialPlacement = suspendInitialPlacement,
							onChatBubbleLongClick = onChatBubbleLongClick,
							onToggleSelection = onToggleSelection,
							resolveContextMenuActions = resolveContextMenuActions,
							onContextMenuOpenRequest = { message, _, _ ->
								contextMenuMessage = message
								contextMenuTargetKey = message.uiKey
								menuOpenedOverKeyboard =
									actualImeHeightPx > 0 || inputController.inputMode == ChatInputMode.Keyboard
							},
							onContextMenuDismissRequest = {
								contextMenuMessage = null
								contextMenuTargetKey = null
								menuOpenedOverKeyboard = false
							},
							rowBoundsByMessageKey = rowBoundsByMessageKey,
							bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
							onLoadMore = onLoadMore,
							config = config,
							modifier = commonModifier,
						)
					}
				}
			}
		}

		val contextMenuAnchorRect = contextMenuTargetKey?.let(rowBoundsByMessageKey::get)
		val contextMenuHighlightRect = contextMenuTargetKey?.let(bubbleBoundsByMessageKey::get)
		contextMenuMessage?.let { menuTargetMessage ->
			MessageContextMenuOverlay(
				actions = resolveContextMenuActions(menuTargetMessage),
				anchorRect = contextMenuAnchorRect,
				highlightRect = contextMenuHighlightRect,
				onActionClick = { action ->
					when (action) {
						ChatContextMenuAction.Reply -> onReply(menuTargetMessage)
						ChatContextMenuAction.Pin -> onPinMessage(menuTargetMessage.id)
						ChatContextMenuAction.Unpin -> onUnpinMessage(menuTargetMessage.id)
						ChatContextMenuAction.CopyText -> {
							if (menuTargetMessage.messageText.isNotBlank()) {
								coroutineScope.launch {
									clipboard.setClipEntry(
										ClipEntry(ClipData.newPlainText("chat-message", menuTargetMessage.messageText)),
									)
								}
							}
						}
						ChatContextMenuAction.Edit -> onEditMessage(menuTargetMessage.id, menuTargetMessage.messageText)
						ChatContextMenuAction.Delete -> messageToDelete = menuTargetMessage
						ChatContextMenuAction.Retry -> onRetryMessage(menuTargetMessage)
					}
					contextMenuMessage = null
					contextMenuTargetKey = null
					menuOpenedOverKeyboard = false
				},
				onDismissRequest = {
					contextMenuMessage = null
					contextMenuTargetKey = null
					menuOpenedOverKeyboard = false
				},
				modifier = Modifier.fillMaxSize(),
			)
		}
	}
}
