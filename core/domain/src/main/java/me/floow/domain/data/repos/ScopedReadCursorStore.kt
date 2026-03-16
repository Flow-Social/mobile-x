package me.floow.domain.data.repos

@JvmInline
value class ReadCursorStoreScope(val value: String)

object ReadCursorScopes {
	fun replies(channel: String): ReadCursorStoreScope {
		val normalizedChannel = channel.trim().lowercase().ifEmpty { "replies" }
		return ReadCursorStoreScope("replies:$normalizedChannel")
	}

	fun comments(postId: String): ReadCursorStoreScope {
		val normalizedPostId = postId.trim().ifEmpty { "unknown" }
		return ReadCursorStoreScope("comments:$normalizedPostId")
	}

	fun conversation(conversationId: Long): ReadCursorStoreScope {
		val normalizedId = conversationId.coerceAtLeast(0L)
		return ReadCursorStoreScope("conversation:$normalizedId")
	}
}

interface ScopedReadCursorStore {
	suspend fun getLocalLastReadSeq(scope: String): Long

	suspend fun setLocalLastReadSeq(scope: String, readSeq: Long)

	suspend fun getOpenAnchorSeq(scope: String): Long

	suspend fun setOpenAnchorSeq(scope: String, anchorSeq: Long)

	suspend fun enqueueReadUpTo(scope: String, readUpToSeq: Long)

	suspend fun getPendingReadUpTo(scope: String): Long

	suspend fun markPendingReadUpToApplied(scope: String, appliedReadSeq: Long)

	suspend fun getPendingScopes(limit: Int = 200): List<String>

	suspend fun getPendingEnqueuedAtMs(scope: String): Long

	suspend fun incrementPendingRetryCount(scope: String): Int

	suspend fun resetPendingRetryCount(scope: String)
}
