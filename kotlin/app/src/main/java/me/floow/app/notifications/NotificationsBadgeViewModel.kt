package me.floow.app.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatReadState

private const val REPLIES_CHANNEL = "replies"

data class NotificationsBadgeUiState(
	val unreadCount: Int = 0
)

class NotificationsBadgeViewModel(
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository,
	private val notificationsReadCursorStore: NotificationsReadCursorStore,
	private val chatsRepository: ChatsRepository
) : ViewModel() {
	private var refreshJob: Job? = null

	val state: StateFlow<NotificationsBadgeUiState> = combine(
		notificationsRealtimeRepository.repliesState,
		chatsRepository.observeConversations(),
		chatsRepository.observeLocalReadStates(),
	) { realtimeState, conversations, localReadStates ->
		val repliesUnread = realtimeState.page.unreadCount.coerceAtLeast(0)
		val directUnread = conversations.sumOf { conversation ->
			conversation.resolveUnreadCount(localReadStates).coerceAtLeast(0)
		}
		NotificationsBadgeUiState(
			unreadCount = (repliesUnread + directUnread).coerceAtLeast(0)
		)
	}
		.stateIn(
			viewModelScope,
			SharingStarted.Eagerly,
			NotificationsBadgeUiState()
		)

	fun startPolling() {
		notificationsRealtimeRepository.start()
		viewModelScope.launch {
			chatsRepository.getConversations(limit = 50, cursor = null)
		}
	}

	fun stopPolling(resetUnread: Boolean) {
		notificationsRealtimeRepository.stop(resetState = resetUnread)
	}

	fun refreshUnreadCount() {
		if (refreshJob?.isActive == true) return
		refreshJob = viewModelScope.launch {
			try {
				notificationsRealtimeRepository.refresh()
			} finally {
				refreshJob = null
			}
		}
	}

	fun markAllAsRead() {
		viewModelScope.launch {
			val maxSeq = notificationsRealtimeRepository.repliesState.value.page.maxSeq
			if (maxSeq <= 0L) return@launch
			markRepliesReadUpToInternal(maxSeq)
		}
	}

	fun markRepliesReadUpTo(seq: Long) {
		if (seq <= 0L) return
		viewModelScope.launch {
			markRepliesReadUpToInternal(seq)
		}
	}

	private suspend fun markRepliesReadUpToInternal(seq: Long) {
		notificationsReadCursorStore.setLocalLastReadSeq(REPLIES_CHANNEL, seq)
		notificationsReadCursorStore.enqueueReadUpTo(REPLIES_CHANNEL, seq)
		notificationsRealtimeRepository.applyLocalReadState(seq)
	}

	override fun onCleared() {
		stopPolling(resetUnread = false)
		super.onCleared()
	}
}

private fun DirectChatConversation.resolveUnreadCount(
	localReadStates: Map<Long, DirectChatReadState>
): Int {
	return localReadStates[id]?.unreadCount?.coerceAtLeast(0) ?: unreadCount.coerceAtLeast(0)
}
