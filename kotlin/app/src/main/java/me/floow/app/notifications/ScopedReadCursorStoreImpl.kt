package me.floow.app.notifications

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.domain.data.repos.ScopedReadCursorStore
import kotlin.math.max

class ScopedReadCursorStoreImpl(
	private val context: Context
) : ScopedReadCursorStore {
	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}
	private val lock = Any()

	override suspend fun getLocalLastReadSeq(scope: String): Long = withContext(Dispatchers.IO) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return@withContext 0L
		synchronized(lock) {
			prefs.getLong(localKey(normalizedScope), 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun setLocalLastReadSeq(scope: String, readSeq: Long) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty() || readSeq <= 0L) return
		withContext(Dispatchers.IO) {
			synchronized(lock) {
				val current = prefs.getLong(localKey(normalizedScope), 0L).coerceAtLeast(0L)
				val next = max(current, readSeq)
				prefs.edit().putLong(localKey(normalizedScope), next).apply()
			}
		}
	}

	override suspend fun getOpenAnchorSeq(scope: String): Long = withContext(Dispatchers.IO) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return@withContext 0L
		synchronized(lock) {
			prefs.getLong(openAnchorKey(normalizedScope), 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun setOpenAnchorSeq(scope: String, anchorSeq: Long) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty() || anchorSeq <= 0L) return
		withContext(Dispatchers.IO) {
			synchronized(lock) {
				prefs.edit().putLong(openAnchorKey(normalizedScope), anchorSeq).apply()
			}
		}
	}

	override suspend fun enqueueReadUpTo(scope: String, readUpToSeq: Long) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty() || readUpToSeq <= 0L) return
		withContext(Dispatchers.IO) {
			synchronized(lock) {
				val localKey = localKey(normalizedScope)
				val pendingKey = pendingKey(normalizedScope)
				val currentLocal = prefs.getLong(localKey, 0L).coerceAtLeast(0L)
				val currentPending = prefs.getLong(pendingKey, 0L).coerceAtLeast(0L)
				val next = max(max(currentLocal, currentPending), readUpToSeq)

				val pendingScopes = loadPendingScopesLocked().toMutableSet()
				pendingScopes.add(normalizedScope)

				val editor = prefs.edit()
					.putLong(localKey, next)
					.putLong(pendingKey, next)
					.putStringSet(PENDING_SCOPES_KEY, pendingScopes)

				if (currentPending <= 0L) {
					editor.putLong(pendingEnqueuedAtKey(normalizedScope), System.currentTimeMillis())
				}
				editor.apply()
			}
		}
	}

	override suspend fun getPendingReadUpTo(scope: String): Long = withContext(Dispatchers.IO) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return@withContext 0L
		synchronized(lock) {
			prefs.getLong(pendingKey(normalizedScope), 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun markPendingReadUpToApplied(scope: String, appliedReadSeq: Long) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return
		withContext(Dispatchers.IO) {
			synchronized(lock) {
				val localKey = localKey(normalizedScope)
				val pendingKey = pendingKey(normalizedScope)
				val currentPending = prefs.getLong(pendingKey, 0L).coerceAtLeast(0L)
				val currentLocal = prefs.getLong(localKey, 0L).coerceAtLeast(0L)
				val normalizedApplied = appliedReadSeq.coerceAtLeast(0L)
				val nextLocal = max(currentLocal, normalizedApplied)
				val nextPending = if (normalizedApplied >= currentPending) 0L else currentPending
				val pendingScopes = loadPendingScopesLocked().toMutableSet()
				if (nextPending <= 0L) {
					pendingScopes.remove(normalizedScope)
				} else {
					pendingScopes.add(normalizedScope)
				}

				val editor = prefs.edit()
					.putLong(localKey, nextLocal)
					.putStringSet(PENDING_SCOPES_KEY, pendingScopes)
				if (nextPending <= 0L) {
					editor.remove(pendingKey)
					editor.remove(pendingEnqueuedAtKey(normalizedScope))
					editor.remove(pendingRetryCountKey(normalizedScope))
				} else {
					editor.putLong(pendingKey, nextPending)
				}
				editor.apply()
			}
		}
	}

	override suspend fun getPendingScopes(limit: Int): List<String> = withContext(Dispatchers.IO) {
		val safeLimit = limit.coerceAtLeast(1)
		synchronized(lock) {
			loadPendingScopesLocked()
				.map(::normalizeScope)
				.filter(String::isNotEmpty)
				.sorted()
				.take(safeLimit)
		}
	}

	override suspend fun getPendingEnqueuedAtMs(scope: String): Long = withContext(Dispatchers.IO) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return@withContext 0L
		synchronized(lock) {
			prefs.getLong(pendingEnqueuedAtKey(normalizedScope), 0L).coerceAtLeast(0L)
		}
	}

	override suspend fun incrementPendingRetryCount(scope: String): Int = withContext(Dispatchers.IO) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return@withContext 0
		synchronized(lock) {
			val key = pendingRetryCountKey(normalizedScope)
			val current = prefs.getInt(key, 0).coerceAtLeast(0)
			val next = current + 1
			prefs.edit().putInt(key, next).apply()
			next
		}
	}

	override suspend fun resetPendingRetryCount(scope: String) {
		val normalizedScope = normalizeScope(scope)
		if (normalizedScope.isEmpty()) return
		withContext(Dispatchers.IO) {
			synchronized(lock) {
				prefs.edit().remove(pendingRetryCountKey(normalizedScope)).apply()
			}
		}
	}

	private fun loadPendingScopesLocked(): Set<String> {
		return prefs.getStringSet(PENDING_SCOPES_KEY, emptySet()).orEmpty()
	}

	private fun normalizeScope(scope: String): String {
		return scope.trim().lowercase()
	}

	private fun localKey(scope: String): String = "scope_local_last_read_seq:$scope"
	private fun openAnchorKey(scope: String): String = "scope_open_anchor_seq:$scope"
	private fun pendingKey(scope: String): String = "scope_pending_read_up_to_seq:$scope"
	private fun pendingEnqueuedAtKey(scope: String): String = "scope_pending_enqueued_at_ms:$scope"
	private fun pendingRetryCountKey(scope: String): String = "scope_pending_retry_count:$scope"

	private companion object {
		const val PREFS_NAME = "scoped_read_cursor_store"
		const val PENDING_SCOPES_KEY = "pending_scopes"
	}
}
