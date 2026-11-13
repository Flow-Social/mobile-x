package me.floow.database.dbo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "direct_chat_outbox_commands",
	indices = [
		Index(value = ["conversation_id", "state", "next_attempt_at", "created_at"]),
		Index(value = ["state", "next_attempt_at", "created_at"]),
		Index(value = ["upload_stage", "next_attempt_at", "created_at"])
	]
)
data class DirectChatOutboxCommandEntity(
	@PrimaryKey
	@ColumnInfo(name = "client_message_id")
	val clientMessageId: String,
	@ColumnInfo(name = "conversation_id")
	val conversationId: Long,
	@ColumnInfo(name = "text")
	val text: String,
	@ColumnInfo(name = "content_type")
	val contentType: String?,
	@ColumnInfo(name = "reply_to_message_id")
	val replyToMessageId: Long?,
	@ColumnInfo(name = "state")
	val state: String,
	@ColumnInfo(name = "attempt_count")
	val attemptCount: Int,
	@ColumnInfo(name = "next_attempt_at")
	val nextAttemptAt: Long,
	@ColumnInfo(name = "last_error")
	val lastError: String?,
	@ColumnInfo(name = "created_at")
	val createdAt: Long,
	@ColumnInfo(name = "updated_at")
	val updatedAt: Long,
	@ColumnInfo(name = "local_file_path")
	val localFilePath: String? = null,
	@ColumnInfo(name = "media_url")
	val mediaUrl: String? = null,
	@ColumnInfo(name = "media_thumbnail_url")
	val mediaThumbnailUrl: String? = null,
	@ColumnInfo(name = "media_mime_type")
	val mediaMimeType: String? = null,
	@ColumnInfo(name = "media_size_bytes")
	val mediaSizeBytes: Long = 0L,
	@ColumnInfo(name = "media_duration_ms")
	val mediaDurationMs: Long = 0L,
	@ColumnInfo(name = "media_width")
	val mediaWidth: Int = 0,
	@ColumnInfo(name = "media_height")
	val mediaHeight: Int = 0,
	@ColumnInfo(name = "media_waveform")
	val mediaWaveform: String? = null,
	@ColumnInfo(name = "group_id")
	val groupId: String? = null,
	@ColumnInfo(name = "media_position")
	val mediaPosition: Int = 0,
	@ColumnInfo(name = "upload_stage")
	val uploadStage: String = "QUEUED",
	@ColumnInfo(name = "idempotency_key")
	val idempotencyKey: String? = null,
)
