package me.floow.domain.models

data class UserNotificationActor(
	val id: String,
	val username: String?,
	val name: String?,
	val avatarUrl: String?
)

data class UserNotification(
	val id: String,
	val seq: Long,
	val type: String,
	val channel: String,
	val actor: UserNotificationActor,
	val postId: String,
	val commentId: String,
	val threadId: String,
	val replyToCommentId: String?,
	val commentText: String?,
	val replyToCommentText: String?,
	val title: String,
	val body: String,
	val isRead: Boolean,
	val readAt: Long?,
	val createdAt: Long,
	val updatedAt: Long
)

data class UserNotificationsPage(
	val items: List<UserNotification>,
	val nextCursor: String?,
	val unreadCount: Int = 0,
	val lastReadSeq: Long = 0L,
	val firstUnreadSeq: Long? = null,
	val maxSeq: Long = 0L
)
