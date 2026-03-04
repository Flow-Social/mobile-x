package me.floow.mock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.RepliesRealtimeState

class MockNotificationsRealtimeRepository : NotificationsRealtimeRepository {
	private val state = MutableStateFlow(
		RepliesRealtimeState(
			isBootstrapping = false,
			isConnected = false,
			hasError = false
		)
	)

	override val repliesState: StateFlow<RepliesRealtimeState> = state

	override fun start() = Unit

	override fun stop(resetState: Boolean) {
		if (resetState) {
			state.value = RepliesRealtimeState(
				isBootstrapping = false,
				isConnected = false,
				hasError = false
			)
		}
	}

	override suspend fun refresh() = Unit

	override suspend fun applyLocalReadState(lastReadSeq: Long) = Unit
}
