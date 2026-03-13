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
		if (chatsRealtimeApi.sendPushAck(enriched)) return
		withContext(Dispatchers.IO) {
			when (pushApi.ackPush(enriched)) {
				is PushAckResponse.Success -> Unit
				is PushAckResponse.Error -> Unit
			}
		}
	}
}
