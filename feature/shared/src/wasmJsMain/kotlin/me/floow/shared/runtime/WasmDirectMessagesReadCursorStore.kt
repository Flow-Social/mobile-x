package me.floow.shared.runtime

import me.floow.domain.data.repos.DirectChatViewportSnapshot
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.login.auth.flowAuthWriteLocalStorage

class WasmDirectMessagesReadCursorStore : DirectMessagesReadCursorStore {

    override suspend fun getLocalLastReadMessageId(conversationId: Long): Long {
        return readLong(localLastReadKey(conversationId))
    }

    override suspend fun setLocalLastReadMessageId(conversationId: Long, messageId: Long) {
        writeLong(localLastReadKey(conversationId), messageId)
    }

    override suspend fun getOpenAnchorSeq(conversationId: Long): Long {
        return readLong(openAnchorSeqKey(conversationId))
    }

    override suspend fun setOpenAnchorSeq(conversationId: Long, seq: Long) {
        writeLong(openAnchorSeqKey(conversationId), seq)
    }

    override suspend fun getOpenAnchorMessageId(conversationId: Long): Long {
        return readLong(openAnchorMessageIdKey(conversationId))
    }

    override suspend fun setOpenAnchorMessageId(conversationId: Long, messageId: Long) {
        writeLong(openAnchorMessageIdKey(conversationId), messageId)
    }

    override suspend fun getOpenAnchorOffsetPx(conversationId: Long): Int {
        return readInt(openAnchorOffsetKey(conversationId))
    }

    override suspend fun setOpenAnchorOffsetPx(conversationId: Long, offsetPx: Int) {
        writeInt(openAnchorOffsetKey(conversationId), offsetPx)
    }

    override suspend fun isOpenAnchorBottomPinned(conversationId: Long): Boolean {
        return readBoolean(openAnchorBottomPinnedKey(conversationId), default = true)
    }

    override suspend fun setOpenAnchorBottomPinned(conversationId: Long, isBottomPinned: Boolean) {
        writeBoolean(openAnchorBottomPinnedKey(conversationId), isBottomPinned)
    }

    override suspend fun enqueueReadUpTo(conversationId: Long, messageId: Long) {
        val currentLocal = getLocalLastReadMessageId(conversationId)
        if (messageId > currentLocal) {
            setLocalLastReadMessageId(conversationId, messageId)
        }
    }

    override suspend fun getPendingReadUpTo(conversationId: Long): Long = 0L

    override suspend fun markPendingReadUpToApplied(conversationId: Long, appliedMessageId: Long) {}

    override suspend fun getPendingConversationIds(limit: Int): List<Long> = emptyList()

    override suspend fun getPendingEnqueuedAtMillis(conversationId: Long): Long = 0L

    override suspend fun incrementPendingRetryCount(conversationId: Long): Int = 0

    override suspend fun resetPendingRetryCount(conversationId: Long) {}

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

    private fun readInt(key: String): Int {
        return flowAuthReadLocalStorage(key)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.toIntOrNull()
            ?: 0
    }

    private fun writeInt(key: String, value: Int) {
        flowAuthWriteLocalStorage(key, value.toString())
    }

    private fun readBoolean(key: String, default: Boolean): Boolean {
        return flowAuthReadLocalStorage(key)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.toBooleanStrictOrNull()
            ?: default
    }

    private fun writeBoolean(key: String, value: Boolean) {
        flowAuthWriteLocalStorage(key, value.toString())
    }

    private fun localLastReadKey(id: Long) = "flow.direct.last_read.$id"
    private fun openAnchorSeqKey(id: Long) = "flow.direct.anchor_seq.$id"
    private fun openAnchorMessageIdKey(id: Long) = "flow.direct.anchor_msg.$id"
    private fun openAnchorOffsetKey(id: Long) = "flow.direct.anchor_offset.$id"
    private fun openAnchorBottomPinnedKey(id: Long) = "flow.direct.anchor_bottom.$id"
}
