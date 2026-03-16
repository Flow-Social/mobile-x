package me.floow.chats.uilogic.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.chats.uilogic.shared.mergePendingReadCursor
import me.floow.chats.uilogic.shared.shouldApplyVisibleReadCandidate
import me.floow.chats.uilogic.shared.shouldEnqueueReadCursor
import me.floow.domain.data.repos.DirectMessagesReadCursorStore

internal class ChatReadController(
	private val scope: CoroutineScope,
	private val state: MutableStateFlow<ChatScreenVmState>,
	private val readCursorStore: DirectMessagesReadCursorStore,
	private val onFlushRead: suspend (conversationId: Long, messageId: Long) -> Unit,
	private val onApplyReadLocally: (messageId: Long) -> Unit,
) {
	var confirmedReadUpToMessageId: Long = 0L
		private set
	var localReadUpToMessageId: Long = 0L
		private set
	var lastAppliedReadUpToMessageId: Long = 0L
		private set
	var maxVisibleUnreadMessageIdCandidate: Long = 0L
		private set
	var lastAppliedVisibleUnreadMessageId: Long = 0L
		private set

	private var pendingReadUpToMessageId: Long? = null
	private var flushReadJob: Job? = null

	fun reset() {
		confirmedReadUpToMessageId = 0L
		localReadUpToMessageId = 0L
		lastAppliedReadUpToMessageId = 0L
		maxVisibleUnreadMessageIdCandidate = 0L
		lastAppliedVisibleUnreadMessageId = 0L
		pendingReadUpToMessageId = null
		flushReadJob?.cancel()
		flushReadJob = null
	}

	fun applyConfirmedRead(messageId: Long) {
		confirmedReadUpToMessageId = maxOf(confirmedReadUpToMessageId, messageId.coerceAtLeast(0L))
	}

	fun applyLocalRead(messageId: Long) {
		localReadUpToMessageId = maxOf(localReadUpToMessageId, messageId.coerceAtLeast(0L))
		lastAppliedVisibleUnreadMessageId = maxOf(lastAppliedVisibleUnreadMessageId, messageId)
		maxVisibleUnreadMessageIdCandidate = maxOf(maxVisibleUnreadMessageIdCandidate, messageId)
	}

	fun applyVisibleReadCandidate(readUpToMessageId: Long, isVisible: Boolean) {
		if (!isVisible) return
		val baseline = maxOf(localReadUpToMessageId, confirmedReadUpToMessageId)
		if (readUpToMessageId <= baseline) return
		if (!shouldApplyVisibleReadCandidate(readUpToMessageId, lastAppliedVisibleUnreadMessageId)) return
		lastAppliedVisibleUnreadMessageId = readUpToMessageId
		onApplyReadLocally(readUpToMessageId)
	}

	fun enqueueReadUpTo(conversationId: Long, messageId: Long) {
		if (!shouldEnqueueReadCursor(messageId, lastAppliedReadUpToMessageId)) return
		lastAppliedReadUpToMessageId = messageId
		localReadUpToMessageId = maxOf(localReadUpToMessageId, messageId)
		pendingReadUpToMessageId = mergePendingReadCursor(pendingReadUpToMessageId, messageId)
		scope.launch(Dispatchers.IO) {
			persistReadCursorLocally(conversationId, messageId)
		}
		flushReadJob?.cancel()
		flushReadJob = scope.launch {
			delay(READ_PIPELINE_FLUSH_DELAY_MS)
			flushPendingRead(conversationId)
		}
	}

	suspend fun flushPendingRead(conversationId: Long) {
		val messageId = pendingReadUpToMessageId ?: return
		pendingReadUpToMessageId = null
		onFlushRead(conversationId, messageId)
	}

	fun forceEnqueueReadUpTo(conversationId: Long, messageId: Long) {
		if (messageId <= 0L || conversationId <= 0L) return
		lastAppliedReadUpToMessageId = maxOf(lastAppliedReadUpToMessageId, messageId)
		localReadUpToMessageId = maxOf(localReadUpToMessageId, messageId)
		pendingReadUpToMessageId = mergePendingReadCursor(pendingReadUpToMessageId, messageId)
		scope.launch(Dispatchers.IO) {
			persistReadCursorLocally(conversationId, messageId)
		}
		flushReadJob?.cancel()
		flushReadJob = scope.launch {
			delay(READ_PIPELINE_FLUSH_DELAY_MS)
			flushPendingRead(conversationId)
		}
	}

	fun cancelPendingFlush(): Long? {
		val pending = pendingReadUpToMessageId
		pendingReadUpToMessageId = null
		flushReadJob?.cancel()
		flushReadJob = null
		return pending
	}

	private suspend fun persistReadCursorLocally(conversationId: Long, messageId: Long) {
		if (conversationId <= 0L || messageId <= 0L) return
		readCursorStore.enqueueReadUpTo(conversationId = conversationId, messageId = messageId)
	}

	private companion object {
		const val READ_PIPELINE_FLUSH_DELAY_MS = 250L
	}
}
