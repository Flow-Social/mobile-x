package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.floow.database.dbo.FeedStackSnapshotEntity

@Dao
interface FeedStackSnapshotDao {
    @Query("SELECT * FROM feed_stack_snapshots WHERE userId = :userId LIMIT 1")
    suspend fun getSnapshot(userId: String): FeedStackSnapshotEntity?

    @Query("SELECT updatedAt FROM feed_stack_snapshots WHERE userId = :userId LIMIT 1")
    suspend fun getLastUpdatedAt(userId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FeedStackSnapshotEntity)

    @Query("DELETE FROM feed_stack_snapshots WHERE userId = :userId")
    suspend fun deleteByUser(userId: String)
}
