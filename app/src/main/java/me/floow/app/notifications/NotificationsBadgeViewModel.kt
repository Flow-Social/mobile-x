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
		chatsRepository.observeConversations()
	) { realtimeState, conversations ->
		val repliesUnread = realtimeState.page.unreadCount.coerceAtLeast(0)
		val directUnread = conversations.sumOf { conversation -> conversation.unreadCount.coerceAtLeast(0) }
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
			notificationsReadCursorStore.enqueueReadUpTo(REPLIES_CHANNEL, maxSeq)
			notificationsRealtimeRepository.applyLocalReadState(maxSeq)
		}
	}

	override fun onCleared() {
		stopPolling(resetUnread = false)
		super.onCleared()
	}
}
