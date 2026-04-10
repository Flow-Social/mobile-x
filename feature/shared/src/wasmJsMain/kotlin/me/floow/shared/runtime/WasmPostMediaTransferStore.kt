package me.floow.shared.runtime

import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore

class WasmPostMediaTransferStore : PostMediaTransferStore {
    private data class StoredEntry(
        val token: String,
        val snapshot: PostMediaSourceSnapshot,
    )

    private var currentEntry: StoredEntry? = null
    private var nextTokenId: Long = 1L

    override fun save(snapshot: PostMediaSourceSnapshot): String {
        val token = "wasm-media-${nextTokenId++}"
        currentEntry = StoredEntry(token = token, snapshot = snapshot)
        return token
    }

    override fun consume(token: String): PostMediaSourceSnapshot? {
        val entry = currentEntry ?: return null
        if (entry.token != token) return null
        currentEntry = null
        return entry.snapshot
    }

    override fun peek(postId: String): PostMediaSourceSnapshot? {
        val entry = currentEntry ?: return null
        if (entry.snapshot.postId != postId) return null
        return entry.snapshot
    }
}
