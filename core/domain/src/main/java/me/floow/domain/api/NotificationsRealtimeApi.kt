package me.floow.domain.api

import kotlinx.coroutines.flow.Flow
import me.floow.domain.api.models.NotificationsRealtimeEvent

interface NotificationsRealtimeApi {
	fun subscribeReplies(afterSeq: Long): Flow<NotificationsRealtimeEvent>
}
