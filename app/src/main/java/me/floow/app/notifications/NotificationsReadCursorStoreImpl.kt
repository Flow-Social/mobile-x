package me.floow.app.notifications

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.domain.data.repos.NotificationsReadCursorStore
import kotlin.math.max

class NotificationsReadCursorStoreImpl(
	private val context: Context
) : NotificationsReadCursorStore {
	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}
	private val lock = Any()

	override suspend fun getLocalLastReadSeq(channel: String): Long = withContext(Dispatchers.IO) {
		val key = localKey(normalizeChannel(channel))
		synchronized(lock) {
			prefs.getLong(key, 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun setLocalLastReadSeq(channel: String, readSeq: Long) {
		if (readSeq <= 0L) return
		withContext(Dispatchers.IO) {
			val normalizedChannel = normalizeChannel(channel)
			val key = localKey(normalizedChannel)
			synchronized(lock) {
				val current = prefs.getLong(key, 0L).coerceAtLeast(0L)
				val next = max(current, readSeq)
				prefs.edit().putLong(key, next).apply()
			}
		}
	}

	override suspend fun getOpenAnchorSeq(channel: String): Long = withContext(Dispatchers.IO) {
		val key = openAnchorKey(normalizeChannel(channel))
		synchronized(lock) {
			prefs.getLong(key, 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun setOpenAnchorSeq(channel: String, anchorSeq: Long) {
		if (anchorSeq <= 0L) return
		withContext(Dispatchers.IO) {
			val normalizedChannel = normalizeChannel(channel)
			val key = openAnchorKey(normalizedChannel)
			synchronized(lock) {
				prefs.edit().putLong(key, anchorSeq.coerceAtLeast(0L)).apply()
			}
		}
	}

	override suspend fun enqueueReadUpTo(channel: String, readUpToSeq: Long) {
		if (readUpToSeq <= 0L) return
		withContext(Dispatchers.IO) {
			val normalizedChannel = normalizeChannel(channel)
			val localKey = localKey(normalizedChannel)
			val pendingKey = pendingKey(normalizedChannel)
			synchronized(lock) {
				val currentLocal = prefs.getLong(localKey, 0L).coerceAtLeast(0L)
				val currentPending = prefs.getLong(pendingKey, 0L).coerceAtLeast(0L)
				val next = max(max(currentLocal, currentPending), readUpToSeq)
				prefs.edit()
					.putLong(localKey, next)
					.putLong(pendingKey, next)
					.apply()
			}
			NotificationsReadSyncScheduler.enqueueNow(context, normalizedChannel)
		}
	}

	override suspend fun getPendingReadUpTo(channel: String): Long = withContext(Dispatchers.IO) {
		val key = pendingKey(normalizeChannel(channel))
		synchronized(lock) {
			prefs.getLong(key, 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun markPendingReadUpToApplied(channel: String, appliedReadSeq: Long) {
		withContext(Dispatchers.IO) {
			val normalizedChannel = normalizeChannel(channel)
			val localKey = localKey(normalizedChannel)
			val pendingKey = pendingKey(normalizedChannel)
			synchronized(lock) {
				val currentPending = prefs.getLong(pendingKey, 0L).coerceAtLeast(0L)
				val currentLocal = prefs.getLong(localKey, 0L).coerceAtLeast(0L)
				val normalizedApplied = appliedReadSeq.coerceAtLeast(0L)
				val nextLocal = max(currentLocal, normalizedApplied)
				val nextPending = if (normalizedApplied >= currentPending) 0L else currentPending
				val editor = prefs.edit()
					.putLong(localKey, nextLocal)
				if (nextPending <= 0L) {
					editor.remove(pendingKey)
				} else {
					editor.putLong(pendingKey, nextPending)
				}
				editor.apply()
			}
		}
	}

	private fun normalizeChannel(channel: String): String {
		return channel.trim().lowercase().ifEmpty { "replies" }
	}

	private fun localKey(channel: String): String = "local_last_read_seq:$channel"
	private fun openAnchorKey(channel: String): String = "open_anchor_seq:$channel"

	private fun pendingKey(channel: String): String = "pending_read_up_to_seq:$channel"

	private companion object {
		const val PREFS_NAME = "notifications_read_cursor_store"
	}
}
