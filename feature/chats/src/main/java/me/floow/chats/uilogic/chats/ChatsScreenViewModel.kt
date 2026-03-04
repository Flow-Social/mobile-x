package me.floow.chats.uilogic.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.RepliesRealtimeState
import me.floow.domain.models.UserNotification
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private data class ChatsScreenVmState(
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val chats: List<Chat>? = null
) {
	fun toUiState(): ChatsScreenUiState {
		if (isLoading) return ChatsScreenUiState.Loading

		return if (chats != null && !isError) {
			if (chats.isEmpty()) {
				ChatsScreenUiState.NoChats
			} else {
				ChatsScreenUiState.HasData(chats)
			}
		} else {
			ChatsScreenUiState.Error
		}
	}
}

class ChatsScreenViewModel(
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository
) : ViewModel() {
	private val _state = MutableStateFlow(ChatsScreenVmState())
	private var useMockData: Boolean = false
	private var regularChats: List<Chat> = emptyList()

	val state: StateFlow<ChatsScreenUiState> = _state
		.map(ChatsScreenVmState::toUiState)
		.stateIn(viewModelScope, SharingStarted.Eagerly, ChatsScreenUiState.Loading)

	init {
		viewModelScope.launch {
			notificationsRealtimeRepository.repliesState.collectLatest { repliesState ->
				updateStateFromReplies(repliesState)
			}
		}
	}

	fun setUseMockData(flag: Boolean) {
		useMockData = flag
	}

	fun load() {
		regularChats = if (useMockData) generateRandomChats(50) else emptyList()
		notificationsRealtimeRepository.start()
		updateStateFromReplies(notificationsRealtimeRepository.repliesState.value)
	}

	private fun updateStateFromReplies(repliesState: RepliesRealtimeState) {
		val repliesInboxChat = buildRepliesInboxChatFromRealtime(repliesState)
		_state.update {
			it.copy(
				isLoading = repliesState.isBootstrapping && repliesState.page.items.isEmpty(),
				isError = repliesState.hasError && repliesState.page.items.isEmpty(),
				chats = buildList {
					add(repliesInboxChat)
					addAll(regularChats)
				}
			)
		}
	}

	private fun buildRepliesInboxChatFromRealtime(repliesState: RepliesRealtimeState): Chat {
		val replyNotifications = repliesState.page.items
			.filter(::isReplyNotification)
		val latestReplyNotification = replyNotifications.maxByOrNull(UserNotification::createdAt)
		val unreadRepliesCount = repliesState.page.unreadCount
		val latestReplyCommentText = latestReplyNotification
			?.commentText
			?.trim()
			?.takeIf(String::isNotEmpty)

		val previewText = latestReplyCommentText
			?: latestReplyNotification
				?.messagePreviewTextOrNull()
			?: "Ответы и комментарии к вашим постам"
		val previewTime = latestReplyNotification
			?.createdAt
			?.toLocalDateTime()
			?: LocalDateTime.now()

		return buildRepliesInboxChat(
			lastMessageText = previewText,
			lastMessageDateTime = previewTime,
			unreadCount = unreadRepliesCount
		)
	}

	private fun isReplyNotification(notification: UserNotification): Boolean {
		return notification.type == "comment_on_post" ||
			notification.type == "reply_to_comment" ||
			notification.type == "comment_reply"
	}

	private fun UserNotification.messagePreviewTextOrNull(): String? {
		return body.trim().takeIf(String::isNotEmpty)
			?: title.trim().takeIf(String::isNotEmpty)
	}

	private fun Long.toLocalDateTime(): LocalDateTime {
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
	}
}
