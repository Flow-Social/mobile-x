package me.floow.domain.api.models

data class PushAckRequest(
	val notificationId: String,
	val conversationId: Long,
	val messageId: Long,
	val receivedAtMs: Long,
	val deviceId: String? = null
)

sealed interface PushAckResponse {
	data object Success : PushAckResponse
	data object Error : PushAckResponse
}
