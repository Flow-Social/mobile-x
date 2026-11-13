package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "replies_inbox_notifications",
	indices = [
		Index(value = ["created_at"]),
		Index(value = ["is_read", "seq"])
	]
)
data class RepliesInboxNotificationEntity(
	@PrimaryKey
	@ColumnInfo(name = "seq")
	val seq: Long,
	@ColumnInfo(name = "id")
	val id: String,
	@ColumnInfo(name = "type")
	val type: String,
	@ColumnInfo(name = "channel")
	val channel: String,
	@ColumnInfo(name = "actor_id")
	val actorId: String,
	@ColumnInfo(name = "actor_username")
	val actorUsername: String?,
	@ColumnInfo(name = "actor_name")
	val actorName: String?,
	@ColumnInfo(name = "actor_avatar_url")
	val actorAvatarUrl: String?,
	@ColumnInfo(name = "post_id")
	val postId: String,
	@ColumnInfo(name = "comment_id")
	val commentId: String,
	@ColumnInfo(name = "thread_id")
	val threadId: String,
	@ColumnInfo(name = "reply_to_comment_id")
	val replyToCommentId: String?,
	@ColumnInfo(name = "comment_text")
	val commentText: String?,
	@ColumnInfo(name = "reply_to_comment_text")
	val replyToCommentText: String?,
	@ColumnInfo(name = "title")
	val title: String,
	@ColumnInfo(name = "body")
	val body: String,
	@ColumnInfo(name = "is_read")
	val isRead: Boolean,
	@ColumnInfo(name = "read_at")
	val readAt: Long?,
	@ColumnInfo(name = "created_at")
	val createdAt: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long
)
