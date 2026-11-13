package me.floow.domain.data.repos

interface CommentsReadCursorStore {
	suspend fun getLocalLastReadSeq(postId: String): Long

	suspend fun setLocalLastReadSeq(postId: String, readSeq: Long)

	suspend fun enqueueReadUpTo(postId: String, readUpToSeq: Long)

	suspend fun getPendingReadUpTo(postId: String): Long

	suspend fun markPendingReadUpToApplied(postId: String, appliedReadSeq: Long)
}
