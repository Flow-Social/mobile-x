package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import me.floow.database.dbo.FeedSyncCommandEntity

@Dao
interface FeedSyncCommandsDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insert(entity: FeedSyncCommandEntity)

	@Query(
		"""
		SELECT * FROM feed_sync_commands
		WHERE userId = :userId
		  AND nextAttemptAt <= :nowMs
		ORDER BY nextAttemptAt ASC, id ASC
		LIMIT 1
		"""
	)
	suspend fun peekNext(userId: String, nowMs: Long): FeedSyncCommandEntity?

	@Query("DELETE FROM feed_sync_commands WHERE id = :id")
	suspend fun deleteById(id: Long)

	@Query("UPDATE feed_sync_commands SET attempts = attempts + 1, nextAttemptAt = :nextAttemptAt WHERE id = :id")
	suspend fun incrementAttempts(id: Long, nextAttemptAt: Long)

	@Query("SELECT MIN(nextAttemptAt) FROM feed_sync_commands WHERE userId = :userId")
	suspend fun nextAttemptAt(userId: String): Long?

	@Query("SELECT COUNT(*) FROM feed_sync_commands WHERE userId = :userId")
	suspend fun pendingCount(userId: String): Int

	@Query("DELETE FROM feed_sync_commands WHERE userId = :userId")
	suspend fun deleteByUser(userId: String)
}
