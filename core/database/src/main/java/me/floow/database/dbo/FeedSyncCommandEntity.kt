package me.floow.database.dbo

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "feed_sync_commands",
	indices = [
		Index(value = ["userId", "id"]),
		Index(value = ["userId", "nextAttemptAt", "id"])
	]
)
data class FeedSyncCommandEntity(
	@PrimaryKey(autoGenerate = true)
	val id: Long = 0,
	val userId: String,
	val commandType: String,
	val postId: String,
	val isLiked: Boolean?,
	val createdAt: Long,
	val attempts: Int = 0,
	val nextAttemptAt: Long
)
