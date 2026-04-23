package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "direct_chat_conversations")
data class DirectChatConversationEntity(
	@PrimaryKey
	@ColumnInfo(name = "id")
	val id: Long,
	@ColumnInfo(name = "kind")
	val kind: String,
	@ColumnInfo(name = "peer_id")
	val peerId: String,
	@ColumnInfo(name = "peer_username")
	val peerUsername: String?,
	@ColumnInfo(name = "peer_name")
	val peerName: String?,
	@ColumnInfo(name = "peer_avatar_url")
	val peerAvatarUrl: String?,
	@ColumnInfo(name = "last_message_id")
	val lastMessageId: Long?,
	@ColumnInfo(name = "last_message_sender_id")
	val lastMessageSenderId: String?,
	@ColumnInfo(name = "last_message_sender_username")
	val lastMessageSenderUsername: String?,
	@ColumnInfo(name = "last_message_sender_name")
	val lastMessageSenderName: String?,
	@ColumnInfo(name = "last_message_sender_avatar_url")
	val lastMessageSenderAvatarUrl: String?,
	@ColumnInfo(name = "last_message_text")
	val lastMessageText: String?,
	@ColumnInfo(name = "last_message_content_type")
	val lastMessageContentType: String?,
	@ColumnInfo(name = "last_message_media_url")
	val lastMessageMediaUrl: String?,
	@ColumnInfo(name = "last_message_media_object_key")
	val lastMessageMediaObjectKey: String?,
	@ColumnInfo(name = "last_message_media_mime_type")
	val lastMessageMediaMimeType: String?,
	@ColumnInfo(name = "last_message_media_size_bytes")
	val lastMessageMediaSizeBytes: Long?,
	@ColumnInfo(name = "last_message_media_duration_ms")
	val lastMessageMediaDurationMs: Long?,
	@ColumnInfo(name = "last_message_media_width")
	val lastMessageMediaWidth: Int?,
	@ColumnInfo(name = "last_message_media_height")
	val lastMessageMediaHeight: Int?,
	@ColumnInfo(name = "last_message_created_at")
	val lastMessageCreatedAt: Long?,
	@ColumnInfo(name = "last_message_updated_at")
	val lastMessageUpdatedAt: Long?,
	@ColumnInfo(name = "unread_count")
	val unreadCount: Int,
	@ColumnInfo(name = "last_read_message_id")
	val lastReadMessageId: Long,
	@ColumnInfo(name = "peer_last_read_message_id")
	val peerLastReadMessageId: Long?,
	@ColumnInfo(name = "created_at")
	val createdAt: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long
)
