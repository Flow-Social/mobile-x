package me.floow.domain.models

import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername


data class CommentAuthor(
	val id: String,
	val name: ProfileName?,
	val username: ProfileUsername?,
	val avatarUrl: String?
)

data class CommentReply(
	val id: String,
	val text: String,
	val authorId: String,
	val authorName: String?
)

data class Comment(
	val id: String,
	val seq: Long = 0L,
	val postId: String,
	val author: CommentAuthor,
	val text: String,
	val isRead: Boolean = true,
	val createdAt: Long,
	val updatedAt: Long,
	val replyTo: CommentReply? = null
)

data class CommentsPage(
	val items: List<Comment>,
	val nextCursor: String? = null,
	val unreadCount: Int = 0,
	val lastReadSeq: Long = 0L,
	val firstUnreadSeq: Long? = null,
	val maxSeq: Long = 0L
)
