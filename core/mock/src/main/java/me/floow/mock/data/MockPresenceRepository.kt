package me.floow.mock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.UserPresence

class MockPresenceRepository : PresenceRepository {
	private val _presences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
	override val presences: StateFlow<Map<String, UserPresence>> = _presences

	override fun setTargets(owner: String, userIds: List<String>) {
		// no-op
	}

	override fun clearTargets(owner: String) {
		// no-op
	}

	override fun onAppForeground() {
		// no-op
	}

	override fun onAppBackground() {
		// no-op
	}
}
