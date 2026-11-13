package me.floow.shared.media

import me.floow.domain.utils.currentTimeMillis
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore

class CommonPostMediaTransferStore(
    private val ttlMs: Long = 90_000L
) : PostMediaTransferStore {
    private data class StoredEntry(
        val token: String,
        val snapshot: PostMediaSourceSnapshot
    )

    private var currentEntry: StoredEntry? = null
    private var counter = 0L

    override fun save(snapshot: PostMediaSourceSnapshot): String {
        pruneExpired()
        val token = "transfer-${++counter}-${currentTimeMillis()}"
        currentEntry = StoredEntry(
            token = token,
            snapshot = snapshot.copy(createdAtMs = currentTimeMillis())
        )
        return token
    }

    override fun consume(token: String): PostMediaSourceSnapshot? {
        pruneExpired()
        val entry = currentEntry ?: return null
        if (entry.token != token) return null
        currentEntry = null
        return entry.snapshot
    }

    override fun peek(postId: String): PostMediaSourceSnapshot? {
        pruneExpired()
        val entry = currentEntry ?: return null
        return if (entry.snapshot.postId == postId) entry.snapshot else null
    }

    private fun pruneExpired() {
        val entry = currentEntry ?: return
        if (currentTimeMillis() - entry.snapshot.createdAtMs > ttlMs) {
            currentEntry = null
        }
    }
}
