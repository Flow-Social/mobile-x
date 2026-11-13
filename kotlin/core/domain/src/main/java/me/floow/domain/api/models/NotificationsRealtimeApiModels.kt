package me.floow.domain.api.models

sealed interface NotificationsRealtimeEvent {
	val channel: String
	val lastReadSeq: Long
	val unreadCount: Int
	val firstUnreadSeq: Long?
	val maxSeq: Long

	data class Hello(
		override val channel: String,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : NotificationsRealtimeEvent

	data class NotificationCreated(
		override val channel: String,
		val notification: NotificationItem,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long,
		val isReplay: Boolean
	) : NotificationsRealtimeEvent

	data class ReadStateUpdated(
		override val channel: String,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : NotificationsRealtimeEvent
}
