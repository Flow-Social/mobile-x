package me.floow.chats.uilogic.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.MessageFieldReply
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.PostPreviewMessage
data class ChatScreenVmState(
	val messageFieldValue: String = "",
	val chatInterlocutorId: String = "",
	val chatInterlocutorName: String = "",
	val chatInterlocutorAvatarUrl: Uri? = null,
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val messages: List<DatedChatMessages>? = null,
	val messageFieldReply: MessageFieldReply? = null,
	val highlightedMessageId: Long? = null,
	val typingUserNames: List<String> = emptyList(),
	val lastDeletedMessage: ChatMessage? = null,
	val messageToEditId: Long? = null,
	val scrollToBottomRequestToken: Long = 0L
) {
	fun toUiState(): ChatScreenUiState {
		return when {
			isLoading -> {
				ChatScreenUiState.Loading(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messageFieldReply = messageFieldReply,
				)
			}

			isError || messages == null -> {
				ChatScreenUiState.Error(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messageFieldReply = messageFieldReply,
				)
			}

			messages.isEmpty() -> {
				ChatScreenUiState.NoMessages(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messageFieldReply = messageFieldReply,
				)
			}

			else -> {
				ChatScreenUiState.HasData(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messages = messages,
					messageFieldReply = messageFieldReply,
					highlightedMessageId = highlightedMessageId,
					typingUserNames = typingUserNames,
					pinnedMessages = messages.flatMap { it.messages }.filter { it.isPinned },
					messageToEditId = messageToEditId,
					scrollToBottomRequestToken = scrollToBottomRequestToken
				)
			}
		}
	}
}

class ChatScreenViewModel() : ViewModel() {
	private var useMockData: Boolean = false
	private val _state: MutableStateFlow<ChatScreenVmState> = MutableStateFlow(
		ChatScreenVmState()
	)

	val state: StateFlow<ChatScreenUiState> = _state
		.map(ChatScreenVmState::toUiState)
		.stateIn(
			viewModelScope,
			SharingStarted.Eagerly,
			ChatScreenUiState.Loading(
				chatInterlocutorId = "",
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = "null",
				messageFieldReply = null
			)
		)

	fun setUseMockData(flag: Boolean) {
		useMockData = flag
	}

	fun setInitialData(
		chatInterlocutorId: String,
		chatInterlocutorName: String,
		chatInterlocutorAvatarUrl: Uri?
	) {
		_state.update {
			ChatScreenVmState(
				chatInterlocutorId = chatInterlocutorId,
				chatInterlocutorName = chatInterlocutorName,
				chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl
			)
		}
	}

	fun loadData() {
		viewModelScope.launch {
			_state.update {
				it.copy(
					isLoading = true
				)
			}

			delay(300L)

			_state.update {
				it.copy(
					isLoading = false,
					messages = if (useMockData) {
						generateChatMessages()
							.sortedBy { it.dateTime }
							.groupBy { it.dateTime.toLocalDate() }
							.map { (datetime, messages) ->
								DatedChatMessages(
									datetime = datetime,
									messages = messages
								)
							}
					} else {
						emptyList()
					}
				)
			}
		}
	}

	fun closeCurrentReply() {
		_state.update {
			it.copy(
				messageFieldReply = null
			)
		}
	}

	fun updateMessageInputField(newValue: String) {
		_state.update {
			it.copy(
				messageFieldValue = newValue
			)
		}
	}

	fun sendMessage() {
		val currentState = _state.value
		val text = currentState.messageFieldValue
		if (text.isBlank()) return

		val reply = currentState.messageFieldReply
		val newMessage: ChatMessage = if (reply != null) {
			ReplyOutMessage(
				id = System.currentTimeMillis(),
				messageText = text,
				dateTime = java.time.LocalDateTime.now(),
				replyMessageId = reply.replyId,
				replyMessageText = reply.replyMessageText
			)
		} else {
			PrimaryOutMessage(
				id = System.currentTimeMillis(),
				messageText = text,
				dateTime = java.time.LocalDateTime.now()
			)
		}

		_state.update { state ->
			val currentGroups = state.messages ?: emptyList()
			val today = java.time.LocalDate.now()
 
			val updatedGroups = if (currentGroups.any { it.datetime == today }) {
				currentGroups.map { group ->
					if (group.datetime == today) {
						group.copy(messages = group.messages + newMessage)
					} else {
						group
					}
				}
			} else {
				currentGroups + DatedChatMessages(today, listOf(newMessage))
			}

			state.copy(
				messageFieldValue = "",
				messageFieldReply = null,
				messages = updatedGroups,
				scrollToBottomRequestToken = System.currentTimeMillis()
			)
		}

		if (useMockData) {
			viewModelScope.launch {
				delay(1000)
				val replyMessage = PrimaryInMessage(
					id = System.currentTimeMillis(),
					messageText = "Auto-reply to: $text",
					dateTime = java.time.LocalDateTime.now()
				)
				_state.update { state ->
					val currentGroups = state.messages ?: emptyList()
					val today = java.time.LocalDate.now()
					val updatedGroups = if (currentGroups.any { it.datetime == today }) {
						currentGroups.map { group ->
							if (group.datetime == today) {
								group.copy(messages = group.messages + replyMessage)
							} else {
								group
							}
						}
					} else {
						currentGroups + DatedChatMessages(today, listOf(replyMessage))
					}
					state.copy(messages = updatedGroups)
				}
			}
		}
	}

	fun addCurrentReply(chatMessage: ChatMessage) {
		val replyAuthorName = when (chatMessage) {
			is PrimaryInMessage, is ReplyInMessage -> {
				_state.value.chatInterlocutorName
			}
			else -> {
				"You"
			}
		}

		_state.update {
			it.copy(
				messageFieldReply = MessageFieldReply(
					replyId = chatMessage.id,
					replyAuthorName = replyAuthorName,
					replyMessageText = chatMessage.messageText
				)
			)
		}
	}

	fun jumpToMessage(messageId: Long) {
		_state.update {
			it.copy(highlightedMessageId = messageId)
		}
		viewModelScope.launch {
			delay(2500)
			_state.update {
				if (it.highlightedMessageId == messageId) {
					it.copy(highlightedMessageId = null)
				} else {
					it
				}
			}
		}
	}

	fun togglePinMessage(messageId: Long) {
		_state.update { state ->
			val currentGroups = state.messages ?: emptyList()
			val updatedGroups = currentGroups.map { group ->
				group.copy(
					messages = group.messages.map { message ->
						if (message.id == messageId) {
							when (message) {
								is PrimaryOutMessage -> message.copy(isPinned = !message.isPinned)
								is ReplyOutMessage -> message.copy(isPinned = !message.isPinned)
								is PrimaryInMessage -> message.copy(isPinned = !message.isPinned)
								is ReplyInMessage -> message.copy(isPinned = !message.isPinned)
								is PostPreviewMessage -> message
							}
						} else {
							message
						}
					}
				)
			}
			state.copy(messages = updatedGroups)
		}
	}

	fun deleteMessage(messageId: Long) {
		val currentState = _state.value
		val currentGroups = currentState.messages ?: return
		
		val messageToDelete = currentGroups.flatMap { it.messages }.find { it.id == messageId } ?: return

		val updatedGroups = currentGroups.map { group ->
			group.copy(messages = group.messages.filter { it.id != messageId })
		}.filter { it.messages.isNotEmpty() }

		_state.update {
			it.copy(
				messages = updatedGroups,
				lastDeletedMessage = messageToDelete
			)
		}
	}

	fun undoDeleteMessage() {
		val currentState = _state.value
		val messageRestored = currentState.lastDeletedMessage ?: return
		val currentGroups = currentState.messages ?: emptyList()
		val messageDate = messageRestored.dateTime.toLocalDate()

		val updatedGroups = if (currentGroups.any { it.datetime == messageDate }) {
			currentGroups.map { group ->
				if (group.datetime == messageDate) {
					val newMessages = (group.messages + messageRestored).sortedBy { it.dateTime }
					group.copy(messages = newMessages)
				} else {
					group
				}
			}
		} else {
			(currentGroups + DatedChatMessages(messageDate, listOf(messageRestored))).sortedBy { it.datetime }
		}

		_state.update {
			it.copy(
				messages = updatedGroups,
				lastDeletedMessage = null
			)
		}
	}

	fun editMessage(messageId: Long, newText: String) {
		_state.update { state ->
			val currentGroups = state.messages ?: emptyList()
			val updatedGroups = currentGroups.map { group ->
				group.copy(
					messages = group.messages.map { message ->
						if (message.id == messageId) {
							when (message) {
								is PrimaryOutMessage -> message.copy(messageText = newText)
								is ReplyOutMessage -> message.copy(messageText = newText)
								is PrimaryInMessage -> message.copy(messageText = newText)
								is ReplyInMessage -> message.copy(messageText = newText)
								is PostPreviewMessage -> message
							}
						} else {
							message
						}
					}
				)
			}
			state.copy(
				messages = updatedGroups,
				messageToEditId = null,
				messageFieldValue = "",
				scrollToBottomRequestToken = System.currentTimeMillis()
			)
		}
	}

	fun startEditingMessage(messageId: Long, currentText: String) {
		_state.update {
			it.copy(
				messageToEditId = messageId,
				messageFieldValue = currentText,
				messageFieldReply = null
			)
		}
	}

	fun cancelEditing() {
		_state.update {
			it.copy(
				messageToEditId = null,
				messageFieldValue = "",
				messageFieldReply = null
			)
		}
	}

	fun simulateTyping() {
		viewModelScope.launch {
			_state.update { it.copy(typingUserNames = listOf(it.chatInterlocutorName)) }
			delay(4000)
			_state.update { it.copy(typingUserNames = emptyList()) }
		}
	}

	fun requestScrollToBottom() {
		_state.update {
			it.copy(scrollToBottomRequestToken = System.currentTimeMillis())
		}
	}
}
