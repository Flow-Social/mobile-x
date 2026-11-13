package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "replies_inbox_meta")
data class RepliesInboxMetaEntity(
	@PrimaryKey
	@ColumnInfo(name = "channel")
	val channel: String,
	@ColumnInfo(name = "last_read_seq")
	val lastReadSeq: Long,
	@ColumnInfo(name = "unread_count")
	val unreadCount: Int,
	@ColumnInfo(name = "first_unread_seq")
	val firstUnreadSeq: Long?,
	@ColumnInfo(name = "max_seq")
	val maxSeq: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long
)
