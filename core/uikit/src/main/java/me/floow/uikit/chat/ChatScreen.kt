package me.floow.uikit.chat

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
import me.floow.uikit.theme.ElevanagonShape

@Composable
fun ChatScreen(
	onBackClick: () -> Unit,
	onProfileClick: () -> Unit,
	onTopBarDropdownClick: () -> Unit,
	onChatBubbleClick: (ChatMessage) -> Unit,
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
	onLoadMore: () -> Unit = {},
	onPostImageClick: (PostPreviewMessage, Int) -> Unit = { _, _ -> },
	onPinMessage: (Long) -> Unit,
	onUnpinMessage: (Long) -> Unit,
	onDeleteMessage: (Long) -> Unit,
	onEditMessage: (Long, String) -> Unit,
	onUndoDelete: () -> Unit,
	config: ChatScreenConfig = ChatScreenConfig(),
	state: ChatScreenUiState,
	modifier: Modifier = Modifier
) {
	val snackbarHostState = remember { SnackbarHostState() }
	val coroutineScope = rememberCoroutineScope()
	var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }

	if (messageToDelete != null) {
		AlertDialog(
			onDismissRequest = { messageToDelete = null },
			title = { Text("Delete Message?") },
			text = { Text("Are you sure you want to delete this message?") },
			confirmButton = {
				Button(
					onClick = {
						messageToDelete?.let { msg ->
							onDeleteMessage(msg.id)
							coroutineScope.launch {
								val result = snackbarHostState.showSnackbar(
									message = "Message deleted",
									actionLabel = "Undo",
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
					Text("Delete")
				}
			},
			dismissButton = {
				TextButton(onClick = { messageToDelete = null }) {
					Text("Cancel")
				}
			}
		)
	}

	Scaffold(
		topBar = {
			val typingUsers = if (state is ChatScreenUiState.HasData && config.showTypingIndicator) {
				state.typingUserNames
			} else {
				emptyList()
			}
			if (config.topBarMode == ChatTopBarMode.TitleOnly) {
				ChatScreenTitleTopBar(
					title = config.topBarTitle ?: state.chatInterlocutorName,
					onBackClick = onBackClick,
					onDropdownClick = onTopBarDropdownClick,
					showDropdown = config.showTopBarDropdown,
					dividerColor = config.dividerColor,
					modifier = Modifier.statusBarsPadding()
				)
			} else {
				ChatScreenTopBar(
					profileName = state.chatInterlocutorName,
					isOnline = false,
					typingUsers = typingUsers,
					profileAvatar = { innerModifier ->
						NetworkAvatar(
							name = state.chatInterlocutorName,
							avatarModel = state.chatInterlocutorAvatarUrl,
							contentDescription = null,
							size = 50.dp,
							shape = ElevanagonShape,
							modifier = innerModifier
						)
					},
					onBackClick = onBackClick,
					onDropdownClick = onTopBarDropdownClick,
					onProfileClick = onProfileClick,
					dividerColor = config.dividerColor,
					modifier = Modifier.statusBarsPadding()
				)
			}
		},
		snackbarHost = { SnackbarHost(snackbarHostState) },
		contentWindowInsets = WindowInsets(0.dp),
		bottomBar = {
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
						titleText = "Редактировать",
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
					showEmojiButton = config.showEmojiButton,
					maxLength = config.maxInputLength,
					focusRequestKey = if (state is ChatScreenUiState.HasData && state.messageToEditId == null) {
						state.messageFieldReply?.replyId
					} else {
						null
					},
					onFocusChanged = { focused ->
						if (focused && config.scrollToBottomOnInputFocus) {
							onRequestScrollToBottom()
						}
					},
					modifier = Modifier.fillMaxWidth()
				)
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
			if (config.showPinActions && state is ChatScreenUiState.HasData) {
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
						onAvatarClick = onAvatarClick,
						onReply = onReply,
						onReplyClick = onReplyClick,
						onPostImageClick = onPostImageClick,
						onOptionClick = { option, message ->
							when (option) {
								ChatBubbleOption.Pin -> if (config.showPinActions) {
									if (message.isPinned) onUnpinMessage(message.id) else onPinMessage(message.id)
								}
								ChatBubbleOption.Delete -> messageToDelete = message
								ChatBubbleOption.Edit -> onEditMessage(message.id, message.messageText)
							}
						},
						onLoadMore = onLoadMore,
						config = config,
						modifier = commonModifier
					)
				}
			}

		}
	}
}
