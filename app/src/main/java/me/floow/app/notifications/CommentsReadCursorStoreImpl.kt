package me.floow.app.notifications

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.domain.data.repos.CommentsReadCursorStore
import kotlin.math.max

class CommentsReadCursorStoreImpl(
	private val context: Context
) : CommentsReadCursorStore {
	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}
	private val lock = Any()

	override suspend fun getLocalLastReadSeq(postId: String): Long = withContext(Dispatchers.IO) {
		val key = localKey(normalizePostId(postId))
		synchronized(lock) {
			prefs.getLong(key, 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun setLocalLastReadSeq(postId: String, readSeq: Long) {
		if (readSeq <= 0L) return
		withContext(Dispatchers.IO) {
			val normalizedPostId = normalizePostId(postId)
			val key = localKey(normalizedPostId)
			synchronized(lock) {
				val current = prefs.getLong(key, 0L).coerceAtLeast(0L)
				val next = max(current, readSeq)
				prefs.edit().putLong(key, next).apply()
			}
		}
	}

	override suspend fun enqueueReadUpTo(postId: String, readUpToSeq: Long) {
		if (readUpToSeq <= 0L) return
		withContext(Dispatchers.IO) {
			val normalizedPostId = normalizePostId(postId)
			val localKey = localKey(normalizedPostId)
			val pendingKey = pendingKey(normalizedPostId)
			synchronized(lock) {
				val currentLocal = prefs.getLong(localKey, 0L).coerceAtLeast(0L)
				val currentPending = prefs.getLong(pendingKey, 0L).coerceAtLeast(0L)
				val next = max(max(currentLocal, currentPending), readUpToSeq)
				prefs.edit()
					.putLong(localKey, next)
					.putLong(pendingKey, next)
					.apply()
			}
			CommentsReadSyncScheduler.enqueueNow(context, normalizedPostId)
		}
	}

	override suspend fun getPendingReadUpTo(postId: String): Long = withContext(Dispatchers.IO) {
		val key = pendingKey(normalizePostId(postId))
		synchronized(lock) {
			prefs.getLong(key, 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun markPendingReadUpToApplied(postId: String, appliedReadSeq: Long) {
		withContext(Dispatchers.IO) {
			val normalizedPostId = normalizePostId(postId)
			val localKey = localKey(normalizedPostId)
			val pendingKey = pendingKey(normalizedPostId)
			synchronized(lock) {
				val currentPending = prefs.getLong(pendingKey, 0L).coerceAtLeast(0L)
				val currentLocal = prefs.getLong(localKey, 0L).coerceAtLeast(0L)
				val normalizedApplied = appliedReadSeq.coerceAtLeast(0L)
				val nextLocal = max(currentLocal, normalizedApplied)
				val nextPending = if (normalizedApplied >= currentPending) 0L else currentPending
				val editor = prefs.edit().putLong(localKey, nextLocal)
				if (nextPending <= 0L) {
					editor.remove(pendingKey)
				} else {
					editor.putLong(pendingKey, nextPending)
				}
				editor.apply()
			}
		}
	}

	private fun normalizePostId(postId: String): String {
		return postId.trim().ifEmpty { "unknown" }
	}

	private fun localKey(postId: String): String = "comments_local_last_read_seq:$postId"

	private fun pendingKey(postId: String): String = "comments_pending_read_up_to_seq:$postId"

	private companion object {
		const val PREFS_NAME = "comments_read_cursor_store"
	}
}
