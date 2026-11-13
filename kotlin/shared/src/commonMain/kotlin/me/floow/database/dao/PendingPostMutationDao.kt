package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.floow.database.dbo.PendingPostMutationEntity

@Dao
interface PendingPostMutationDao {
	@Query(
		"""
		SELECT * FROM pending_post_mutations
		WHERE state != 'DONE' AND state != 'CANCELED'
		ORDER BY createdAt ASC
		"""
	)
	fun observeAllPendingForSelf(): Flow<List<PendingPostMutationEntity>>

	@Query(
		"""
		SELECT * FROM pending_post_mutations
		WHERE state IN ('PENDING', 'FAILED')
		  AND nextRetryAt <= :nowMs
		ORDER BY nextRetryAt ASC, createdAt ASC
		LIMIT :limit
		"""
	)
	suspend fun getDueCommands(nowMs: Long, limit: Int): List<PendingPostMutationEntity>

	@Query("SELECT * FROM pending_post_mutations WHERE commandId = :commandId LIMIT 1")
	suspend fun getByCommandId(commandId: String): PendingPostMutationEntity?

	@Query("SELECT * FROM pending_post_mutations WHERE localPostId = :localPostId LIMIT 1")
	suspend fun getByLocalPostId(localPostId: String): PendingPostMutationEntity?

	@Query("SELECT * FROM pending_post_mutations WHERE remotePostId = :remotePostId LIMIT 1")
	suspend fun getByRemotePostId(remotePostId: String): PendingPostMutationEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entity: PendingPostMutationEntity)

	@Query("UPDATE pending_post_mutations SET state = :state, updatedAt = :updatedAt WHERE commandId = :commandId")
	suspend fun markState(commandId: String, state: String, updatedAt: Long)

	@Query(
		"""
		UPDATE pending_post_mutations
		SET state = 'FAILED', lastError = :error, retryCount = :retryCount, nextRetryAt = :nextRetryAt, updatedAt = :updatedAt
		WHERE commandId = :commandId
		"""
	)
	suspend fun markFailed(commandId: String, error: String?, retryCount: Int, nextRetryAt: Long, updatedAt: Long)

	@Query("UPDATE pending_post_mutations SET imagePayloadJson = :imagePayloadJson, updatedAt = :updatedAt WHERE commandId = :commandId")
	suspend fun updateUploadedPayload(commandId: String, imagePayloadJson: String, updatedAt: Long)

	@Query("DELETE FROM pending_post_mutations WHERE commandId = :commandId")
	suspend fun delete(commandId: String)

	@Query("DELETE FROM pending_post_mutations WHERE localPostId = :localPostId AND remotePostId IS NULL")
	suspend fun deleteByLocalPostId(localPostId: String)

	@Query(
		"""
		UPDATE pending_post_mutations
		SET state = 'PENDING', lastError = NULL, nextRetryAt = :nextRetryAt, updatedAt = :updatedAt
		WHERE commandId = :commandId
		  AND state = 'FAILED'
		"""
	)
	suspend fun retry(commandId: String, nextRetryAt: Long, updatedAt: Long): Int

	@Query(
		"""
		UPDATE pending_post_mutations
		SET state = 'PENDING', lastError = NULL, nextRetryAt = :nextRetryAt, updatedAt = :updatedAt
		WHERE localPostId = :localPostId
		  AND remotePostId IS NULL
		  AND state = 'FAILED'
		"""
	)
	suspend fun retryByLocalPostId(localPostId: String, nextRetryAt: Long, updatedAt: Long): Int

	@Query("DELETE FROM pending_post_mutations WHERE state = 'FAILED' AND updatedAt < :cutoffMs")
	suspend fun cleanupExpiredFailed(cutoffMs: Long)
}
