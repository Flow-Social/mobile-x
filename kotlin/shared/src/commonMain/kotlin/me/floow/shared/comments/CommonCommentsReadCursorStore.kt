package me.floow.shared.comments

import me.floow.domain.data.repos.CommentsReadCursorStore

class CommonCommentsReadCursorStore : CommentsReadCursorStore {
    private val localSeqs = mutableMapOf<String, Long>()
    private val pendingSeqs = mutableMapOf<String, Long>()

    override suspend fun getLocalLastReadSeq(postId: String): Long {
        return localSeqs[normalize(postId)] ?: 0L
    }

    override suspend fun setLocalLastReadSeq(postId: String, readSeq: Long) {
        val key = normalize(postId)
        localSeqs[key] = maxOf(localSeqs[key] ?: 0L, readSeq)
    }

    override suspend fun enqueueReadUpTo(postId: String, readUpToSeq: Long) {
        if (readUpToSeq <= 0L) return
        val key = normalize(postId)
        pendingSeqs[key] = maxOf(pendingSeqs[key] ?: 0L, readUpToSeq)
        localSeqs[key] = maxOf(localSeqs[key] ?: 0L, readUpToSeq)
    }

    override suspend fun getPendingReadUpTo(postId: String): Long {
        return pendingSeqs[normalize(postId)] ?: 0L
    }

    override suspend fun markPendingReadUpToApplied(postId: String, appliedReadSeq: Long) {
        val key = normalize(postId)
        if ((pendingSeqs[key] ?: 0L) <= appliedReadSeq) {
            pendingSeqs.remove(key)
        }
    }

    private fun normalize(postId: String): String = postId.trim().ifEmpty { "unknown" }
}
