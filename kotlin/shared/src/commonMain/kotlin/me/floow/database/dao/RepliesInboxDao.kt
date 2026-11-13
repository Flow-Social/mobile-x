package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.floow.database.dbo.RepliesInboxMetaEntity
import me.floow.database.dbo.RepliesInboxNotificationEntity

@Dao
interface RepliesInboxDao {
	@Query(
		"""
		SELECT *
		FROM replies_inbox_notifications
		ORDER BY seq ASC
		"""
	)
	fun observeReplies(): Flow<List<RepliesInboxNotificationEntity>>

	@Query(
		"""
		SELECT *
		FROM replies_inbox_notifications
		ORDER BY seq ASC
		"""
	)
	suspend fun getReplies(): List<RepliesInboxNotificationEntity>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertReplies(items: List<RepliesInboxNotificationEntity>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertReply(item: RepliesInboxNotificationEntity)

	@Query("DELETE FROM replies_inbox_notifications")
	suspend fun clearReplies()

	@Query(
		"""
		UPDATE replies_inbox_notifications
		SET is_read = 1,
		    read_at = :readAt,
		    updated_at = CASE WHEN updated_at < :readAt THEN :readAt ELSE updated_at END
		WHERE is_read = 0
		  AND seq <= :lastReadSeq
		"""
	)
	suspend fun markReadUpTo(lastReadSeq: Long, readAt: Long): Int

	@Query(
		"""
		SELECT COUNT(1)
		FROM replies_inbox_notifications
		WHERE seq > :lastReadSeq
		"""
	)
	suspend fun countBySeqGreaterThan(lastReadSeq: Long): Int

	@Query(
		"""
		SELECT MIN(seq)
		FROM replies_inbox_notifications
		WHERE seq > :lastReadSeq
		"""
	)
	suspend fun firstSeqGreaterThan(lastReadSeq: Long): Long?

	@Query(
		"""
		SELECT MAX(seq)
		FROM replies_inbox_notifications
		"""
	)
	suspend fun maxSeq(): Long?

	@Query(
		"""
		DELETE FROM replies_inbox_notifications
		WHERE seq NOT IN (
			SELECT seq
			FROM replies_inbox_notifications
			ORDER BY seq DESC
			LIMIT :limit
		)
		"""
	)
	suspend fun trimToLatest(limit: Int)

	@Query(
		"""
		SELECT *
		FROM replies_inbox_meta
		WHERE channel = :channel
		LIMIT 1
		"""
	)
	fun observeMeta(channel: String): Flow<RepliesInboxMetaEntity?>

	@Query(
		"""
		SELECT *
		FROM replies_inbox_meta
		WHERE channel = :channel
		LIMIT 1
		"""
	)
	suspend fun getMeta(channel: String): RepliesInboxMetaEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMeta(meta: RepliesInboxMetaEntity)

	@Query("DELETE FROM replies_inbox_meta WHERE channel = :channel")
	suspend fun deleteMeta(channel: String)
}
