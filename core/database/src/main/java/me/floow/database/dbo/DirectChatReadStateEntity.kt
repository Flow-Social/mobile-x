package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "direct_chat_read_state")
data class DirectChatReadStateEntity(
	@PrimaryKey
	@ColumnInfo(name = "conversation_id")
	val conversationId: Long,
	@ColumnInfo(name = "last_read_message_id")
	val lastReadMessageId: Long,
	@ColumnInfo(name = "unread_count")
	val unreadCount: Int,
	@ColumnInfo(name = "first_unread_id")
	val firstUnreadId: Long?,
	@ColumnInfo(name = "max_message_id")
	val maxMessageId: Long,
	@ColumnInfo(name = "read_state_version")
	val readStateVersion: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long
)
