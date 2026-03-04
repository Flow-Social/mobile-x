package me.floow.domain.data.repos

interface NotificationsReadCursorStore {
	suspend fun getLocalLastReadSeq(channel: String): Long

	suspend fun setLocalLastReadSeq(channel: String, readSeq: Long)

	suspend fun getOpenAnchorSeq(channel: String): Long

	suspend fun setOpenAnchorSeq(channel: String, anchorSeq: Long)

	suspend fun enqueueReadUpTo(channel: String, readUpToSeq: Long)

	suspend fun getPendingReadUpTo(channel: String): Long

	suspend fun markPendingReadUpToApplied(channel: String, appliedReadSeq: Long)
}
