package me.floow.database.localstore

import me.floow.database.dao.FeedSyncCommandsDao
import me.floow.database.dbo.FeedSyncCommandEntity
import me.floow.domain.cache.FeedSyncCommand
import me.floow.domain.cache.FeedSyncCommandType
import me.floow.domain.cache.FeedSyncLocalStore

class FeedSyncLocalStoreImpl(
	private val dao: FeedSyncCommandsDao
) : FeedSyncLocalStore {
	override suspend fun enqueueSwipe(userId: String, postId: String, isLiked: Boolean) {
		val now = System.currentTimeMillis()
		dao.insert(
			FeedSyncCommandEntity(
				userId = userId,
				commandType = FeedSyncCommandType.SWIPE.name,
				postId = postId,
				isLiked = isLiked,
				createdAt = now,
				nextAttemptAt = now
			)
		)
	}

	override suspend fun enqueueUndo(userId: String, postId: String) {
		val now = System.currentTimeMillis()
		dao.insert(
			FeedSyncCommandEntity(
				userId = userId,
				commandType = FeedSyncCommandType.UNDO.name,
				postId = postId,
				isLiked = null,
				createdAt = now,
				nextAttemptAt = now
			)
		)
	}

	override suspend fun peekNext(userId: String, nowMs: Long): FeedSyncCommand? {
		return dao.peekNext(userId, nowMs)?.toDomain()
	}

	override suspend fun remove(id: Long) {
		dao.deleteById(id)
	}

	override suspend fun incrementAttempts(id: Long, nextAttemptAt: Long) {
		dao.incrementAttempts(id, nextAttemptAt)
	}

	override suspend fun nextAttemptAt(userId: String): Long? {
		return dao.nextAttemptAt(userId)
	}

	override suspend fun pendingCount(userId: String): Int {
		return dao.pendingCount(userId)
	}

	override suspend fun clearUser(userId: String) {
		dao.deleteByUser(userId)
	}
}

private fun FeedSyncCommandEntity.toDomain(): FeedSyncCommand {
	return FeedSyncCommand(
		id = id,
		userId = userId,
		type = when (commandType) {
			FeedSyncCommandType.UNDO.name -> FeedSyncCommandType.UNDO
			else -> FeedSyncCommandType.SWIPE
		},
		postId = postId,
		isLiked = isLiked,
		attempts = attempts,
		nextAttemptAt = nextAttemptAt
	)
}
