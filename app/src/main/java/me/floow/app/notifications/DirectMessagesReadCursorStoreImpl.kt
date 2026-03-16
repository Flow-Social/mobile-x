package me.floow.app.notifications

import android.content.Context
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.data.repos.ReadCursorScopes
import me.floow.domain.data.repos.ScopedReadCursorStore

class DirectMessagesReadCursorStoreImpl(
	private val context: Context,
	private val scopedStore: ScopedReadCursorStore
) : DirectMessagesReadCursorStore {
	private val prefs by lazy {
		context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	}

	override suspend fun getLocalLastReadMessageId(conversationId: Long): Long {
		return scopedStore.getLocalLastReadSeq(scope = scope(conversationId))
	}

	override suspend fun setLocalLastReadMessageId(conversationId: Long, messageId: Long) {
		scopedStore.setLocalLastReadSeq(scope = scope(conversationId), readSeq = messageId)
	}

	override suspend fun getOpenAnchorSeq(conversationId: Long): Long {
		return scopedStore.getOpenAnchorSeq(scope = scope(conversationId))
	}

	override suspend fun setOpenAnchorSeq(conversationId: Long, seq: Long) {
		scopedStore.setOpenAnchorSeq(scope = scope(conversationId), anchorSeq = seq)
	}

	override suspend fun getOpenAnchorMessageId(conversationId: Long): Long {
		return getOpenAnchorSeq(conversationId)
	}

	override suspend fun setOpenAnchorMessageId(conversationId: Long, messageId: Long) {
		setOpenAnchorSeq(conversationId, messageId)
	}

	override suspend fun getOpenAnchorOffsetPx(conversationId: Long): Int {
		val key = openAnchorOffsetKey(conversationId)
		if (key.isEmpty()) return 0
		return prefs.getInt(key, 0).coerceAtLeast(0)
	}

	override suspend fun setOpenAnchorOffsetPx(conversationId: Long, offsetPx: Int) {
		val key = openAnchorOffsetKey(conversationId)
		if (key.isEmpty()) return
		prefs.edit().putInt(key, offsetPx.coerceAtLeast(0)).apply()
	}

	override suspend fun isOpenAnchorBottomPinned(conversationId: Long): Boolean {
		val key = openAnchorBottomPinnedKey(conversationId)
		if (key.isEmpty()) return false
		return prefs.getBoolean(key, false)
	}

	override suspend fun setOpenAnchorBottomPinned(conversationId: Long, isBottomPinned: Boolean) {
		val key = openAnchorBottomPinnedKey(conversationId)
		if (key.isEmpty()) return
		prefs.edit().putBoolean(key, isBottomPinned).apply()
	}

	override suspend fun enqueueReadUpTo(conversationId: Long, messageId: Long) {
		if (conversationId <= 0L || messageId <= 0L) return
		scopedStore.enqueueReadUpTo(scope = scope(conversationId), readUpToSeq = messageId)
		DirectMessagesReadSyncScheduler.enqueueNow(context, conversationId)
	}

	override suspend fun getPendingReadUpTo(conversationId: Long): Long {
		return scopedStore.getPendingReadUpTo(scope = scope(conversationId))
	}

	override suspend fun markPendingReadUpToApplied(conversationId: Long, appliedMessageId: Long) {
		scopedStore.markPendingReadUpToApplied(scope = scope(conversationId), appliedReadSeq = appliedMessageId)
	}

	override suspend fun getPendingConversationIds(limit: Int): List<Long> {
		return scopedStore.getPendingScopes(limit = limit)
			.mapNotNull { scoped ->
				if (!scoped.startsWith(CONVERSATION_SCOPE_PREFIX)) {
					null
				} else {
					scoped.removePrefix(CONVERSATION_SCOPE_PREFIX).toLongOrNull()
				}
			}
			.filter { id -> id > 0L }
	}

	override suspend fun getPendingEnqueuedAtMillis(conversationId: Long): Long {
		return scopedStore.getPendingEnqueuedAtMs(scope = scope(conversationId))
	}

	override suspend fun incrementPendingRetryCount(conversationId: Long): Int {
		return scopedStore.incrementPendingRetryCount(scope = scope(conversationId))
	}

	override suspend fun resetPendingRetryCount(conversationId: Long) {
		scopedStore.resetPendingRetryCount(scope = scope(conversationId))
	}

	private fun scope(conversationId: Long): String {
		val normalizedConversationId = conversationId.coerceAtLeast(0L)
		if (normalizedConversationId <= 0L) return ""
		return ReadCursorScopes.conversation(normalizedConversationId).value
	}

	private fun openAnchorOffsetKey(conversationId: Long): String {
		val normalizedConversationId = conversationId.coerceAtLeast(0L)
		if (normalizedConversationId <= 0L) return ""
		return "dm_open_anchor_offset_px:$normalizedConversationId"
	}

	private fun openAnchorBottomPinnedKey(conversationId: Long): String {
		val normalizedConversationId = conversationId.coerceAtLeast(0L)
		if (normalizedConversationId <= 0L) return ""
		return "dm_open_anchor_bottom_pinned:$normalizedConversationId"
	}

	private companion object {
		const val PREFS_NAME = "dm_read_cursor_store"
		const val CONVERSATION_SCOPE_PREFIX = "conversation:"
	}
}
