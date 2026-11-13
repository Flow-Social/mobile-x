package me.floow.domain.data.repos

import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.models.UserPresence

interface PresenceRepository {
	val presences: StateFlow<Map<String, UserPresence>>

	fun setTargets(owner: String, userIds: List<String>)
	fun clearTargets(owner: String)

	fun onAppForeground()
	fun onAppBackground()
}
