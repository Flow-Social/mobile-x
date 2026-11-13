package me.floow.domain.api

import me.floow.domain.api.models.PresenceGetResponse
import me.floow.domain.api.models.PresenceUpdateResponse

interface PresenceApi {
	suspend fun getPresence(userIds: List<String>): PresenceGetResponse
	suspend fun heartbeat(sessionId: String?): PresenceUpdateResponse
	suspend fun offline(sessionId: String?): PresenceUpdateResponse
}
