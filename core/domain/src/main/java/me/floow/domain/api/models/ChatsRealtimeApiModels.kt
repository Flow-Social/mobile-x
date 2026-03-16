package me.floow.domain.api.models

sealed interface ChatsRealtimeEvent {
	val conversationId: Long
	val eventId: String?
	val seq: Long
	val lastReadMessageId: Long
	val unreadCount: Int
	val firstUnreadId: Long?
	val maxMessageId: Long
	val readStateVersion: Long

	data class Hello(
		override val conversationId: Long,
		override val eventId: String?,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent

	data class MessageCreated(
		override val conversationId: Long,
		override val eventId: String?,
		val message: ChatMessageItem,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L,
		val isReplay: Boolean
	) : ChatsRealtimeEvent

	data class ReadUpToUpdated(
		override val conversationId: Long,
		override val eventId: String?,
		val actorUserId: String,
		val actorLastReadMessageId: Long?,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent

	data class MessageDeleted(
		override val conversationId: Long,
		override val eventId: String?,
		val deletedMessageId: Long,
		val actorUserId: String,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent

	data class MessageUpdated(
		override val conversationId: Long,
		override val eventId: String?,
		val message: ChatMessageItem,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent

	data class MessagePinnedUpdated(
		override val conversationId: Long,
		override val eventId: String?,
		val message: ChatMessageItem,
		val pinnedMessageId: Long,
		val isPinned: Boolean,
		val pinnedAt: Long?,
		val pinnedByUserId: String?,
		val actorUserId: String,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent

	data class Typing(
		override val conversationId: Long,
		override val eventId: String?,
		val actorUserId: String,
		val isTyping: Boolean,
		val typingTtlMs: Long,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent

	data class ResyncRequired(
		override val conversationId: Long,
		override val eventId: String?,
		override val seq: Long,
		override val lastReadMessageId: Long,
		override val unreadCount: Int,
		override val firstUnreadId: Long?,
		override val maxMessageId: Long,
		override val readStateVersion: Long = 0L
	) : ChatsRealtimeEvent
}
