package me.floow.app.notifications

import android.content.Context
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.data.repos.ReadCursorScopes
import me.floow.domain.data.repos.ScopedReadCursorStore

class NotificationsReadCursorStoreImpl(
	private val context: Context,
	private val scopedStore: ScopedReadCursorStore
) : NotificationsReadCursorStore {
	override suspend fun getLocalLastReadSeq(channel: String): Long {
		return scopedStore.getLocalLastReadSeq(scope = scope(channel))
	}

	override suspend fun setLocalLastReadSeq(channel: String, readSeq: Long) {
		scopedStore.setLocalLastReadSeq(scope = scope(channel), readSeq = readSeq)
	}

	override suspend fun getOpenAnchorSeq(channel: String): Long {
		return scopedStore.getOpenAnchorSeq(scope = scope(channel))
	}

	override suspend fun setOpenAnchorSeq(channel: String, anchorSeq: Long) {
		scopedStore.setOpenAnchorSeq(scope = scope(channel), anchorSeq = anchorSeq)
	}

	override suspend fun enqueueReadUpTo(channel: String, readUpToSeq: Long) {
		val normalizedChannel = normalizeChannel(channel)
		if (readUpToSeq <= 0L) return
		scopedStore.enqueueReadUpTo(scope = scope(normalizedChannel), readUpToSeq = readUpToSeq)
		NotificationsReadSyncScheduler.enqueueNow(context, normalizedChannel)
	}

	override suspend fun getPendingReadUpTo(channel: String): Long {
		return scopedStore.getPendingReadUpTo(scope = scope(channel))
	}

	override suspend fun markPendingReadUpToApplied(channel: String, appliedReadSeq: Long) {
		scopedStore.markPendingReadUpToApplied(scope = scope(channel), appliedReadSeq = appliedReadSeq)
	}

	private fun scope(channel: String): String = ReadCursorScopes.replies(normalizeChannel(channel)).value

	private fun normalizeChannel(channel: String): String {
		return channel.trim().lowercase().ifEmpty { "replies" }
	}
}
