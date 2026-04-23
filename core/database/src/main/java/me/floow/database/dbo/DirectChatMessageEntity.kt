package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "direct_chat_messages",
	indices = [
		Index(value = ["conversation_id", "id"]),
		Index(value = ["conversation_id", "created_at"]),
		Index(value = ["conversation_id", "client_message_id"])
	]
)
data class DirectChatMessageEntity(
	@PrimaryKey
	@ColumnInfo(name = "id")
	val id: Long,
	@ColumnInfo(name = "conversation_id")
	val conversationId: Long,
	@ColumnInfo(name = "sender_id")
	val senderId: String,
	@ColumnInfo(name = "sender_username")
	val senderUsername: String?,
	@ColumnInfo(name = "sender_name")
	val senderName: String?,
	@ColumnInfo(name = "sender_avatar_url")
	val senderAvatarUrl: String?,
	@ColumnInfo(name = "text")
	val text: String,
	@ColumnInfo(name = "content_type")
	val contentType: String?,
	@ColumnInfo(name = "media_url")
	val mediaUrl: String?,
	@ColumnInfo(name = "media_object_key")
	val mediaObjectKey: String?,
	@ColumnInfo(name = "media_mime_type")
	val mediaMimeType: String?,
	@ColumnInfo(name = "media_size_bytes")
	val mediaSizeBytes: Long?,
	@ColumnInfo(name = "media_duration_ms")
	val mediaDurationMs: Long?,
	@ColumnInfo(name = "media_width")
	val mediaWidth: Int?,
	@ColumnInfo(name = "media_height")
	val mediaHeight: Int?,
	@ColumnInfo(name = "client_message_id")
	val clientMessageId: String?,
	@ColumnInfo(name = "reply_to_message_id")
	val replyToMessageId: Long?,
	@ColumnInfo(name = "reply_to_message_text")
	val replyToMessageText: String?,
	@ColumnInfo(name = "is_pinned")
	val isPinned: Boolean,
	@ColumnInfo(name = "pinned_at")
	val pinnedAt: Long?,
	@ColumnInfo(name = "pinned_by_user_id")
	val pinnedByUserId: String?,
	@ColumnInfo(name = "delivery_status")
	val deliveryStatus: String,
	@ColumnInfo(name = "created_at")
	val createdAt: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long
)
