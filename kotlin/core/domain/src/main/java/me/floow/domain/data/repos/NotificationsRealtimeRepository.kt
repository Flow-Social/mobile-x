package me.floow.domain.data.repos

import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.models.UserNotificationsPage

data class RepliesRealtimeState(
	val isBootstrapping: Boolean = true,
	val isConnected: Boolean = false,
	val hasError: Boolean = false,
	val pageVersion: Long = 0L,
	val page: UserNotificationsPage = UserNotificationsPage(
		items = emptyList(),
		nextCursor = null
	)
)

interface NotificationsRealtimeRepository {
	val repliesState: StateFlow<RepliesRealtimeState>

	fun start()

	fun stop(resetState: Boolean = false)

	suspend fun refresh()

	suspend fun applyLocalReadState(lastReadSeq: Long)
}
