package me.floow.shared.runtime

import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.login.auth.flowAuthWriteLocalStorage
import org.koin.core.context.GlobalContext

class WasmCommentsReadCursorStore : CommentsReadCursorStore {
    private val flushInFlight = mutableSetOf<String>()

    override suspend fun getLocalLastReadSeq(postId: String): Long {
        return readLong(localLastReadKey(postId))
    }

    override suspend fun setLocalLastReadSeq(postId: String, readSeq: Long) {
        writeLong(localLastReadKey(postId), readSeq)
    }

    override suspend fun enqueueReadUpTo(postId: String, readUpToSeq: Long) {
        val normalizedPostId = postId.trim()
        if (normalizedPostId.isEmpty() || readUpToSeq <= 0L) return
        val currentPending = readLong(pendingReadKey(normalizedPostId))
        if (readUpToSeq <= currentPending) return
        writeLong(pendingReadKey(normalizedPostId), readUpToSeq)
        flushPendingReadIfNeeded(normalizedPostId)
    }

    override suspend fun getPendingReadUpTo(postId: String): Long {
        return readLong(pendingReadKey(postId))
    }

    override suspend fun markPendingReadUpToApplied(postId: String, appliedReadSeq: Long) {
        writeLong(localLastReadKey(postId), appliedReadSeq)
        val currentPending = readLong(pendingReadKey(postId))
        if (currentPending <= appliedReadSeq) {
            writeLong(pendingReadKey(postId), 0L)
        }
    }

    private fun readLong(key: String): Long {
        return flowAuthReadLocalStorage(key)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.toLongOrNull()
            ?: 0L
    }

    private fun writeLong(key: String, value: Long) {
        flowAuthWriteLocalStorage(key, value.coerceAtLeast(0L).toString())
    }

    private fun localLastReadKey(postId: String): String = "flow.comments.last_read.${postId.trim()}"
    private fun pendingReadKey(postId: String): String = "flow.comments.pending_read.${postId.trim()}"

    private suspend fun flushPendingReadIfNeeded(postId: String) {
        if (!flushInFlight.add(postId)) return
        try {
            val koin = runCatching { GlobalContext.get() }.getOrNull() ?: return
            val authManager = koin.get<AuthenticationManager>()
            if (!authManager.isSignedIn()) return
            val commentsRepository = koin.get<CommentsRepository>()

            while (true) {
                val pendingReadUpTo = readLong(pendingReadKey(postId))
                val localLastRead = readLong(localLastReadKey(postId))
                if (pendingReadUpTo <= localLastRead) {
                    if (pendingReadUpTo > 0L) {
                        markPendingReadUpToApplied(postId, localLastRead)
                    }
                    break
                }
                when (val result = commentsRepository.markCommentsReadUpTo(postId = postId, readUpToSeq = pendingReadUpTo)) {
                    is GetDataResponse.Success -> {
                        val appliedSeq = result.data.coerceAtLeast(0L)
                        markPendingReadUpToApplied(postId, appliedSeq)
                    }
                    is GetDataResponse.Error -> break
                }
            }
        } finally {
            flushInFlight.remove(postId)
        }
    }
}
