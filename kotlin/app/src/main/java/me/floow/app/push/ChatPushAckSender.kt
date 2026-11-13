package me.floow.app.push

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.domain.api.ChatsRealtimeApi
import me.floow.domain.api.PushApi
import me.floow.domain.api.models.PushAckRequest
import me.floow.domain.api.models.PushAckResponse

class ChatPushAckSender(
	private val context: android.content.Context,
	private val pushApi: PushApi,
	private val chatsRealtimeApi: ChatsRealtimeApi
) {
	suspend fun sendAck(payload: ChatNotificationPayload) {
		val request = PushAckRequest(
			notificationId = payload.notificationId,
			conversationId = payload.conversationId,
			messageId = payload.messageId,
			receivedAtMs = System.currentTimeMillis()
		)
		val deviceToken = PushTokenStorage.get(context)
		val enriched = if (!deviceToken.isNullOrBlank()) {
			request.copy(deviceId = deviceToken)
		} else {
			request
		}
		// 1. First attempt to send fast ACK via active WebSocket connection
		val sentViaWs = runCatching { chatsRealtimeApi.sendPushAck(enriched) }.getOrDefault(false)
		if (sentViaWs) return

		// 2. If WS is closed, try sending directly via HTTP REST API
		val httpSuccess = withContext(Dispatchers.IO) {
			val response = runCatching { pushApi.ackPush(enriched) }.getOrNull()
			response is PushAckResponse.Success
		}
		if (httpSuccess) return

		// 3. If direct HTTP request fails, enqueue background retry via WorkManager
		PushAckScheduler.enqueue(
			context = context,
			notificationId = enriched.notificationId,
			conversationId = enriched.conversationId,
			messageId = enriched.messageId,
			deviceToken = deviceToken
		)
	}
}
