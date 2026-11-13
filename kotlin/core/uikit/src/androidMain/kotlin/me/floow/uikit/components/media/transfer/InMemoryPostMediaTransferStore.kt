package me.floow.uikit.components.media.transfer

import java.util.UUID

class InMemoryPostMediaTransferStore(
	private val ttlMs: Long = 90_000L
) : PostMediaTransferStore {
	private data class StoredEntry(
		val token: String,
		val snapshot: PostMediaSourceSnapshot
	)

	private val lock = Any()
	private var currentEntry: StoredEntry? = null

	override fun save(snapshot: PostMediaSourceSnapshot): String {
		val token = UUID.randomUUID().toString()
		synchronized(lock) {
			pruneExpiredLocked()
			currentEntry = StoredEntry(
				token = token,
				snapshot = snapshot.copy(
					createdAtMs = System.currentTimeMillis()
				)
			)
		}
		return token
	}

	override fun consume(token: String): PostMediaSourceSnapshot? {
		synchronized(lock) {
			pruneExpiredLocked()
			val entry = currentEntry ?: return null
			if (entry.token != token) return null
			currentEntry = null
			return entry.snapshot
		}
	}

	override fun peek(postId: String): PostMediaSourceSnapshot? {
		synchronized(lock) {
			pruneExpiredLocked()
			val entry = currentEntry ?: return null
			if (entry.snapshot.postId != postId) return null
			return entry.snapshot
		}
	}

	private fun pruneExpiredLocked(nowMs: Long = System.currentTimeMillis()) {
		val snapshot = currentEntry?.snapshot ?: return
		if (nowMs - snapshot.createdAtMs > ttlMs) {
			currentEntry = null
		}
	}
}
