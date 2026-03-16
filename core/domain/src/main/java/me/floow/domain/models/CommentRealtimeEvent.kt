package me.floow.domain.models

sealed interface CommentRealtimeEvent {
	val postId: String
	val eventId: String?
	val seq: Long
	val lastReadSeq: Long
	val unreadCount: Int
	val firstUnreadSeq: Long?
	val maxSeq: Long

	data class Hello(
		override val postId: String,
		override val eventId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentRealtimeEvent

	data class CommentCreated(
		override val postId: String,
		override val eventId: String?,
		val comment: Comment,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long,
		val isReplay: Boolean
	) : CommentRealtimeEvent

	data class CommentUpdated(
		override val postId: String,
		override val eventId: String?,
		val comment: Comment,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentRealtimeEvent

	data class CommentDeleted(
		override val postId: String,
		override val eventId: String?,
		val deletedCommentId: String,
		val actorUserId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentRealtimeEvent

	data class ReadUpToUpdated(
		override val postId: String,
		override val eventId: String?,
		val readUpToSeq: Long,
		val actorUserId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentRealtimeEvent

	data class ResyncRequired(
		override val postId: String,
		override val eventId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentRealtimeEvent
}
