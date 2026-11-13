package me.floow.domain.cache

enum class FeedSyncCommandType {
	SWIPE,
	UNDO
}

data class FeedSyncCommand(
	val id: Long,
	val userId: String,
	val type: FeedSyncCommandType,
	val postId: String,
	val isLiked: Boolean?,
	val attempts: Int,
	val nextAttemptAt: Long
)

interface FeedSyncLocalStore {
	suspend fun enqueueSwipe(userId: String, postId: String, isLiked: Boolean)
	suspend fun enqueueUndo(userId: String, postId: String)
	suspend fun peekNext(userId: String, nowMs: Long): FeedSyncCommand?
	suspend fun nextAttemptAt(userId: String): Long?
	suspend fun remove(id: Long)
	suspend fun incrementAttempts(id: Long, nextAttemptAt: Long)
	suspend fun pendingCount(userId: String): Int
	suspend fun clearUser(userId: String)
}
