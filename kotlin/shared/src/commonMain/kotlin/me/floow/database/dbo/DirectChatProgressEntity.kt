package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "direct_chat_progress", primaryKeys = ["account_id", "conversation_id"])
data class DirectChatProgressEntity(
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    @ColumnInfo(name = "anchor_message_id") val anchorMessageId: Long? = null,
    @ColumnInfo(name = "anchor_offset_px") val anchorOffsetPx: Int = 0,
    @ColumnInfo(name = "bottom_pinned") val bottomPinned: Boolean = true,
    @ColumnInfo(name = "oldest_cached_id") val oldestCachedId: Long? = null,
    @ColumnInfo(name = "newest_cached_id") val newestCachedId: Long? = null,
    @ColumnInfo(name = "cache_has_older") val cacheHasOlder: Boolean = false,
    @ColumnInfo(name = "cache_has_newer") val cacheHasNewer: Boolean = false,
    @ColumnInfo(name = "pending_read") val pendingRead: Long = 0L,
)
