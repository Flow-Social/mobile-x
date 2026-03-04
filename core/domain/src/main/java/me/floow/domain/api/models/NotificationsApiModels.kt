package me.floow.domain.api.models

data class NotificationActorItem(
	val id: String,
	val username: String?,
	val name: String?,
	val avatar: String?
)

data class NotificationItem(
	val id: String,
	val seq: Long,
	val type: String,
	val channel: String,
	val actor: NotificationActorItem,
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

sealed interface GetNotificationsResponse {
	data class Success(
		val items: List<NotificationItem>,
		val nextCursor: String?,
		val unreadCount: Int,
		val lastReadSeq: Long,
		val firstUnreadSeq: Long?,
		val maxSeq: Long
	) : GetNotificationsResponse

	data object Error : GetNotificationsResponse
}

sealed interface GetUnreadNotificationsCountResponse {
	data class Success(val unreadCount: Int) : GetUnreadNotificationsCountResponse

	data object Error : GetUnreadNotificationsCountResponse
}

sealed interface MarkNotificationReadResponse {
	data object Success : MarkNotificationReadResponse

	data object NotFound : MarkNotificationReadResponse

	data object Error : MarkNotificationReadResponse
}

sealed interface MarkAllNotificationsReadResponse {
	data class Success(val updatedCount: Int) : MarkAllNotificationsReadResponse

	data object Error : MarkAllNotificationsReadResponse
}

sealed interface MarkNotificationsReadUpToResponse {
	data class Success(
		val updatedCount: Int,
		val lastReadSeq: Long,
		val channel: String
	) : MarkNotificationsReadUpToResponse

	data object Error : MarkNotificationsReadUpToResponse
}
