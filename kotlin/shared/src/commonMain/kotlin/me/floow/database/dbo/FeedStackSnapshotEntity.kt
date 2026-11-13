package me.floow.database.dbo

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feed_stack_snapshots")
data class FeedStackSnapshotEntity(
    @PrimaryKey
    val userId: String,
    val snapshotJson: String,
    val updatedAt: Long,
)
