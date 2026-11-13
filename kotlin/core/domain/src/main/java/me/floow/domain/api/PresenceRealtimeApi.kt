package me.floow.domain.api

import kotlinx.coroutines.flow.Flow
import me.floow.domain.api.models.PresenceRealtimeEvent

interface PresenceRealtimeApi {
	suspend fun openSession(): PresenceRealtimeSession?
}

interface PresenceRealtimeSession {
	val events: Flow<PresenceRealtimeEvent>

	suspend fun subscribe(userIds: List<String>)

	suspend fun unsubscribe(userIds: List<String>)

	suspend fun close()
}
