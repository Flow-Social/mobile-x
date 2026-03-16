package me.floow.domain.data.repos

data class DirectChatViewportSnapshot(
	val anchorMessageId: Long?,
	val anchorOffsetPx: Int,
	val isBottomPinned: Boolean
)

interface DirectMessagesReadCursorStore {
	suspend fun getLocalLastReadMessageId(conversationId: Long): Long

	suspend fun setLocalLastReadMessageId(conversationId: Long, messageId: Long)

	suspend fun getOpenAnchorSeq(conversationId: Long): Long

	suspend fun setOpenAnchorSeq(conversationId: Long, seq: Long)

	suspend fun getOpenAnchorMessageId(conversationId: Long): Long

	suspend fun setOpenAnchorMessageId(conversationId: Long, messageId: Long)

	suspend fun getOpenAnchorOffsetPx(conversationId: Long): Int

	suspend fun setOpenAnchorOffsetPx(conversationId: Long, offsetPx: Int)

	suspend fun isOpenAnchorBottomPinned(conversationId: Long): Boolean

	suspend fun setOpenAnchorBottomPinned(conversationId: Long, isBottomPinned: Boolean)

	suspend fun getOpenViewportSnapshot(conversationId: Long): DirectChatViewportSnapshot {
		val anchorMessageId = getOpenAnchorMessageId(conversationId)
			.takeIf { it > 0L }
		return DirectChatViewportSnapshot(
			anchorMessageId = anchorMessageId,
			anchorOffsetPx = getOpenAnchorOffsetPx(conversationId),
			isBottomPinned = isOpenAnchorBottomPinned(conversationId)
		)
	}

	suspend fun setOpenViewportSnapshot(conversationId: Long, snapshot: DirectChatViewportSnapshot) {
		setOpenAnchorMessageId(
			conversationId = conversationId,
			messageId = snapshot.anchorMessageId ?: 0L
		)
		setOpenAnchorOffsetPx(
			conversationId = conversationId,
			offsetPx = snapshot.anchorOffsetPx
		)
		setOpenAnchorBottomPinned(
			conversationId = conversationId,
			isBottomPinned = snapshot.isBottomPinned
		)
	}

	suspend fun enqueueReadUpTo(conversationId: Long, messageId: Long)

	suspend fun getPendingReadUpTo(conversationId: Long): Long

	suspend fun markPendingReadUpToApplied(conversationId: Long, appliedMessageId: Long)

	suspend fun getPendingConversationIds(limit: Int = 200): List<Long>

	suspend fun getPendingEnqueuedAtMillis(conversationId: Long): Long

	suspend fun incrementPendingRetryCount(conversationId: Long): Int

	suspend fun resetPendingRetryCount(conversationId: Long)
}
