package me.floow.uikit.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.floow.uikit.chat.components.ChatBubbleOption
import me.floow.uikit.chat.components.ChatScreenTopBar
import me.floow.uikit.chat.components.ChatScreenTitleTopBar
import me.floow.uikit.chat.components.CurrentReply
import me.floow.uikit.chat.components.MessageInputField
import me.floow.uikit.chat.components.PinnedMessagesBar
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.ChatTopBarMode
import me.floow.uikit.chat.states.ErrorState
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.chat.states.LoadingState
import me.floow.uikit.chat.states.NoMessagesState
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.R

@Composable
fun ChatScreen(
	onBackClick: () -> Unit,
	onProfileClick: () -> Unit,
	onTopBarDropdownClick: () -> Unit,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onMessageActionClick: ((ChatMessage) -> Unit)? = null,
	onAvatarClick: (ChatMessage) -> Unit = {},
	onJumpToMessage: (Long) -> Unit,
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onCurrentReplyClose: () -> Unit,
	onCurrentReplyClick: () -> Unit,
	onCancelEdit: () -> Unit,
	onMessageInputFieldValueChange: (String) -> Unit,
	onEmojiPickerClick: () -> Unit,
	onSendClick: () -> Unit,
	onRequestScrollToBottom: () -> Unit,
	onUserStartedScroll: () -> Unit = {},
	onVisibleMessageIdsChanged: (Set<Long>) -> Unit = {},
	onFirstVisibleMessageIdChanged: (Long?) -> Unit = {},
	onLoadMore: () -> Unit = {},
	onPostImageClick: (PostPreviewMessage, Int) -> Unit = { _, _ -> },
	suspendInitialPlacement: Boolean = false,
	onPinMessage: (Long) -> Unit,
	onUnpinMessage: (Long) -> Unit,
	onDeleteMessage: (Long) -> Unit,
	onEditMessage: (Long, String) -> Unit,
	onUndoDelete: () -> Unit,
	config: ChatScreenConfig = ChatScreenConfig(),
	state: ChatScreenUiState,
	modifier: Modifier = Modifier
) {
	val interactionPolicy = config.interactionPolicy
	val scrollPolicy = config.scrollPolicy
	val topBarPolicy = config.topBarPolicy
	val snackbarHostState = remember { SnackbarHostState() }
	val coroutineScope = rememberCoroutineScope()
	var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
	val deleteMessageTitle = stringResource(R.string.chat_delete_message_title)
	val deleteMessageText = stringResource(R.string.chat_delete_message_text)
	val deleteMessageConfirm = stringResource(R.string.chat_delete_message_confirm)
	val deleteMessageCancel = stringResource(R.string.chat_delete_message_cancel)
	val messageDeletedText = stringResource(R.string.chat_message_deleted)
	val undoActionText = stringResource(R.string.chat_undo_action)
	val editingTitleText = stringResource(R.string.chat_editing_title)

	if (messageToDelete != null) {
		AlertDialog(
			onDismissRequest = { messageToDelete = null },
			title = { Text(deleteMessageTitle) },
			text = { Text(deleteMessageText) },
			confirmButton = {
				Button(
						onClick = {
							messageToDelete?.let { msg ->
								onDeleteMessage(msg.id)
								coroutineScope.launch {
								val result = snackbarHostState.showSnackbar(
									message = messageDeletedText,
									actionLabel = undoActionText,
									duration = androidx.compose.material3.SnackbarDuration.Short
								)
								if (result == SnackbarResult.ActionPerformed) {
									onUndoDelete()
								}
							}
							}
							messageToDelete = null
						}
					) {
						Text(deleteMessageConfirm)
					}
				},
				dismissButton = {
					TextButton(onClick = { messageToDelete = null }) {
						Text(deleteMessageCancel)
					}
				}
			)
		}

	Scaffold(
		topBar = {
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
					modifier = Modifier.statusBarsPadding()
				)
			} else {
				ChatScreenTopBar(
					profileName = state.chatInterlocutorName,
					isOnline = false,
					typingUsers = typingUsers,
					showSubtitle = topBarPolicy.showSubtitle,
					profileAvatar = { innerModifier ->
						if (topBarPolicy.avatarResId != null) {
							Image(
								painter = painterResource(topBarPolicy.avatarResId),
								contentDescription = null,
								contentScale = ContentScale.Crop,
								modifier = innerModifier.clip(CircleShape)
							)
						} else {
							NetworkAvatar(
								name = state.chatInterlocutorName,
								avatarModel = state.chatInterlocutorAvatarUrl,
								contentDescription = null,
								size = 42.dp,
								shape = CircleShape,
								modifier = innerModifier
							)
						}
					},
					onBackClick = onBackClick,
					onDropdownClick = onTopBarDropdownClick,
					onProfileClick = onProfileClick,
					dividerColor = topBarPolicy.dividerColor,
					modifier = Modifier.statusBarsPadding()
				)
			}
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
		contentWindowInsets = WindowInsets(0.dp),
		bottomBar = {
			if (interactionPolicy.showInputBar) {
				Column(
					modifier = Modifier
						.fillMaxWidth()
						.background(MaterialTheme.colorScheme.surface)
						.navigationBarsPadding()
						.imePadding()
				) {
						val isEditMode = state is ChatScreenUiState.HasData && state.messageToEditId != null
						if (isEditMode) {
							CurrentReply(
								userNameToReply = "",
								replyMessageText = state.messageFieldValue,
								titleText = editingTitleText,
								onClose = onCancelEdit,
								modifier = Modifier.fillMaxWidth()
							)
						} else {
							state.messageFieldReply?.let { reply ->
								CurrentReply(
								userNameToReply = reply.replyAuthorName,
								replyMessageText = reply.replyMessageText,
								onClose = onCurrentReplyClose,
								onClick = onCurrentReplyClick,
								modifier = Modifier.fillMaxWidth()
							)
						}
					}

					HorizontalDivider(color = config.dividerColor ?: MaterialTheme.colorScheme.outlineVariant)

					MessageInputField(
						value = state.messageFieldValue,
						onValueChange = onMessageInputFieldValueChange,
						onEmojiPickerClick = onEmojiPickerClick,
						onSendClick = onSendClick,
						sendButtonActive = state.messageFieldValue.isNotEmpty(),
						isEditMode = if (state is ChatScreenUiState.HasData) state.messageToEditId != null else false,
						showEmojiButton = interactionPolicy.showEmojiButton,
						maxLength = interactionPolicy.maxInputLength,
						focusRequestKey = if (state is ChatScreenUiState.HasData && state.messageToEditId == null) {
							state.messageFieldReply?.replyId
						} else {
							null
						},
						onFocusChanged = { focused ->
							if (focused && scrollPolicy.scrollToBottomOnInputFocus) {
								onRequestScrollToBottom()
							}
						},
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		},
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background)
	) { innerPadding ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(innerPadding)
		) {
			if (interactionPolicy.showPinActions && state is ChatScreenUiState.HasData) {
				PinnedMessagesBar(
					pinnedMessages = state.pinnedMessages,
					onMessageClick = { onJumpToMessage(it.id) },
					onUnpinClick = { onUnpinMessage(it.id) }
				)
			}

			val commonModifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.background(MaterialTheme.colorScheme.surfaceContainer)

			when (state) {
				is ChatScreenUiState.Loading -> {
					LoadingState(commonModifier)
				}

				is ChatScreenUiState.Error -> {
					ErrorState(commonModifier)
				}

				is ChatScreenUiState.NoMessages -> {
					NoMessagesState(commonModifier)
				}

				is ChatScreenUiState.HasData -> {
						HasDataState(
							state = state,
							onChatBubbleClick = onChatBubbleClick,
							onMessageActionClick = onMessageActionClick,
							onAvatarClick = onAvatarClick,
							onReply = onReply,
							onReplyClick = onReplyClick,
							onPostImageClick = onPostImageClick,
							onRequestScrollToBottom = onRequestScrollToBottom,
							onUserStartedScroll = onUserStartedScroll,
							onVisibleMessageIdsChanged = onVisibleMessageIdsChanged,
							onFirstVisibleMessageIdChanged = onFirstVisibleMessageIdChanged,
							suspendInitialPlacement = suspendInitialPlacement,
							onOptionClick = if (interactionPolicy.showMessageOptions) {
								{ option, message ->
									when (option) {
										ChatBubbleOption.Pin -> if (interactionPolicy.showPinActions) {
											if (message.isPinned) onUnpinMessage(message.id) else onPinMessage(message.id)
										}
										ChatBubbleOption.Delete -> messageToDelete = message
										ChatBubbleOption.Edit -> onEditMessage(message.id, message.messageText)
									}
								}
							} else null,
							onLoadMore = onLoadMore,
							config = config,
							modifier = commonModifier
						)
				}
			}

		}
	}
}
