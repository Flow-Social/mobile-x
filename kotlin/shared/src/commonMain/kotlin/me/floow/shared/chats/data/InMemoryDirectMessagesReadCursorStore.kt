package me.floow.shared.chats.data

import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.utils.currentTimeMillis

class InMemoryDirectMessagesReadCursorStore : DirectMessagesReadCursorStore {
    private val lastReadMap = mutableMapOf<Long, Long>()
    private val anchorSeqMap = mutableMapOf<Long, Long>()
    private val anchorOffsetMap = mutableMapOf<Long, Int>()
    private val bottomPinnedMap = mutableMapOf<Long, Boolean>()

    private val pendingReadUpToMap = mutableMapOf<Long, Long>()
    private val pendingEnqueuedAtMap = mutableMapOf<Long, Long>()
    private val pendingRetryCountMap = mutableMapOf<Long, Int>()

    override suspend fun getLocalLastReadMessageId(conversationId: Long): Long {
        return lastReadMap[conversationId] ?: 0L
    }

    override suspend fun setLocalLastReadMessageId(conversationId: Long, messageId: Long) {
        lastReadMap[conversationId] = messageId
    }

    override suspend fun getOpenAnchorSeq(conversationId: Long): Long {
        return anchorSeqMap[conversationId] ?: 0L
    }

    override suspend fun setOpenAnchorSeq(conversationId: Long, seq: Long) {
        anchorSeqMap[conversationId] = seq
    }

    override suspend fun getOpenAnchorMessageId(conversationId: Long): Long {
        return getOpenAnchorSeq(conversationId)
    }

    override suspend fun setOpenAnchorMessageId(conversationId: Long, messageId: Long) {
        setOpenAnchorSeq(conversationId, messageId)
    }

    override suspend fun getOpenAnchorOffsetPx(conversationId: Long): Int {
        return anchorOffsetMap[conversationId] ?: 0
    }

    override suspend fun setOpenAnchorOffsetPx(conversationId: Long, offsetPx: Int) {
        anchorOffsetMap[conversationId] = offsetPx
    }

    override suspend fun isOpenAnchorBottomPinned(conversationId: Long): Boolean {
        return bottomPinnedMap[conversationId] ?: false
    }

    override suspend fun setOpenAnchorBottomPinned(conversationId: Long, isBottomPinned: Boolean) {
        bottomPinnedMap[conversationId] = isBottomPinned
    }

    override suspend fun enqueueReadUpTo(conversationId: Long, messageId: Long) {
        pendingReadUpToMap[conversationId] = messageId
        pendingEnqueuedAtMap[conversationId] = currentTimeMillis()
    }

    override suspend fun getPendingReadUpTo(conversationId: Long): Long {
        return pendingReadUpToMap[conversationId] ?: 0L
    }

    override suspend fun markPendingReadUpToApplied(conversationId: Long, appliedMessageId: Long) {
        val current = pendingReadUpToMap[conversationId] ?: return
        if (appliedMessageId >= current) {
            pendingReadUpToMap.remove(conversationId)
            pendingEnqueuedAtMap.remove(conversationId)
            pendingRetryCountMap.remove(conversationId)
        }
    }

    override suspend fun getPendingConversationIds(limit: Int): List<Long> {
        return pendingReadUpToMap.keys.take(limit)
    }

    override suspend fun getPendingEnqueuedAtMillis(conversationId: Long): Long {
        return pendingEnqueuedAtMap[conversationId] ?: 0L
    }

    override suspend fun incrementPendingRetryCount(conversationId: Long): Int {
        val count = (pendingRetryCountMap[conversationId] ?: 0) + 1
        pendingRetryCountMap[conversationId] = count
        return count
    }

    override suspend fun resetPendingRetryCount(conversationId: Long) {
        pendingRetryCountMap[conversationId] = 0
    }
}
