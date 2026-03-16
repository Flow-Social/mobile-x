package me.floow.app.notifications

import android.content.Context
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.ReadCursorScopes
import me.floow.domain.data.repos.ScopedReadCursorStore

class CommentsReadCursorStoreImpl(
	private val context: Context,
	private val scopedStore: ScopedReadCursorStore
) : CommentsReadCursorStore {
	override suspend fun getLocalLastReadSeq(postId: String): Long =
		scopedStore.getLocalLastReadSeq(scope = scope(postId))

	override suspend fun setLocalLastReadSeq(postId: String, readSeq: Long) {
		scopedStore.setLocalLastReadSeq(scope = scope(postId), readSeq = readSeq)
	}

	override suspend fun enqueueReadUpTo(postId: String, readUpToSeq: Long) {
		val normalizedPostId = normalizePostId(postId)
		if (readUpToSeq <= 0L) return
		scopedStore.enqueueReadUpTo(scope = scope(normalizedPostId), readUpToSeq = readUpToSeq)
		CommentsReadSyncScheduler.enqueueNow(context, normalizedPostId)
	}

	override suspend fun getPendingReadUpTo(postId: String): Long =
		scopedStore.getPendingReadUpTo(scope = scope(postId))

	override suspend fun markPendingReadUpToApplied(postId: String, appliedReadSeq: Long) {
		scopedStore.markPendingReadUpToApplied(scope = scope(postId), appliedReadSeq = appliedReadSeq)
	}

	private fun scope(postId: String): String = ReadCursorScopes.comments(normalizePostId(postId)).value

	private fun normalizePostId(postId: String): String {
		return postId.trim().ifEmpty { "unknown" }
	}
}
