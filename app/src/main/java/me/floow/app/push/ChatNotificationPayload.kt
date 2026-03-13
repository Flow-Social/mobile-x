package me.floow.app.push

data class ChatNotificationPayload(
	val type: String,
	val notificationId: String,
	val conversationId: Long,
	val messageId: Long,
	val senderId: String?,
	val senderName: String?,
	val senderAvatarUrl: String?,
	val messageText: String,
	val messageTimestampMs: Long,
	val isGroup: Boolean,
	val conversationTitle: String?,
	val isFallback: Boolean,
	val source: ChatNotificationSource
)

enum class ChatNotificationSource {
	FCM,
	WS,
	FALLBACK
}
