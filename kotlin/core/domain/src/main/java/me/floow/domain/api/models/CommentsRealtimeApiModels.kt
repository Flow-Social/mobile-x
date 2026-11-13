package me.floow.domain.api.models

sealed interface CommentsRealtimeEvent {
	val postId: Long
	val eventId: String?
	val seq: Long
	val lastReadSeq: Long
	val unreadCount: Int
	val firstUnreadSeq: Long?
	val maxSeq: Long

	data class Hello(
		override val postId: Long,
		override val eventId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentsRealtimeEvent

	data class CommentCreated(
		override val postId: Long,
		override val eventId: String?,
		val comment: CommentItem,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long,
		val isReplay: Boolean
	) : CommentsRealtimeEvent

	data class CommentUpdated(
		override val postId: Long,
		override val eventId: String?,
		val comment: CommentItem,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentsRealtimeEvent

	data class CommentDeleted(
		override val postId: Long,
		override val eventId: String?,
		val deletedCommentId: Long,
		val actorUserId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentsRealtimeEvent

	data class ReadUpToUpdated(
		override val postId: Long,
		override val eventId: String?,
		val readUpToSeq: Long,
		val actorUserId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentsRealtimeEvent

	data class ResyncRequired(
		override val postId: Long,
		override val eventId: String?,
		override val seq: Long,
		override val lastReadSeq: Long,
		override val unreadCount: Int,
		override val firstUnreadSeq: Long?,
		override val maxSeq: Long
	) : CommentsRealtimeEvent
}
