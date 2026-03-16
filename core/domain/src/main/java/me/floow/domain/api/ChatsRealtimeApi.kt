package me.floow.domain.api

import kotlinx.coroutines.flow.Flow
import me.floow.domain.api.models.ChatsRealtimeEvent
import me.floow.domain.api.models.PushAckRequest

interface ChatsRealtimeApi {
	fun subscribeConversation(
		conversationId: Long,
		afterSeq: Long,
		replayLimit: Int = 200
	): Flow<ChatsRealtimeEvent>

	fun subscribeAllConversations(
		afterSeq: Long,
		replayLimit: Int = 200
	): Flow<ChatsRealtimeEvent>

	fun sendPushAck(request: PushAckRequest): Boolean
}
