package me.floow.shared.chats.uilogic.direct.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DirectChatOpenViewportSnapshot
import me.floow.uikit.chat.model.ChatViewportSnapshot

class ChatReadTracker(
	private val repository: ChatThreadRepository,
	private val scope: CoroutineScope,
) {
	companion object {
		const val READ_DEBOUNCE_DELAY_MS = 350L
	}

	var unseenIncomingMessageIds: Set<Long> = emptySet()
		private set
	var lastMarkedReadMessageId: Long = 0L
		private set
	var pendingReadUpToMessageId: Long? = null
		private set
	var pendingOpenViewportSnapshot: DirectChatOpenViewportSnapshot? = null
	private var pendingReadDebounceJob: Job? = null

	fun initializeLastMarkedRead(messageId: Long) {
		lastMarkedReadMessageId = maxOf(lastMarkedReadMessageId, messageId)
	}

	fun markReadUpToIfNeeded(
		conversationId: Long,
		messageId: Long,
	) {
		if (conversationId <= 0L || messageId <= 0L || messageId <= maxOf(lastMarkedReadMessageId, pendingReadUpToMessageId ?: 0L)) return
		pendingReadUpToMessageId = messageId
		pendingReadDebounceJob?.cancel()
		pendingReadDebounceJob = scope.launch {
			delay(READ_DEBOUNCE_DELAY_MS)
			val targetMessageId = pendingReadUpToMessageId ?: return@launch
			repository.markReadUpTo(conversationId, targetMessageId)
				.onSuccess {
                    initializeLastMarkedRead(targetMessageId)
					if (pendingReadUpToMessageId == targetMessageId) {
						pendingReadUpToMessageId = null
					}
				}
		}
	}

	fun onViewportSnapshotChanged(
		snapshot: ChatViewportSnapshot,
		conversationId: Long?,
		keepAnchorRequest: Boolean,
		onBadgeCountChanged: (Int) -> Unit,
	) {
		if (conversationId == null || conversationId <= 0L) return

		val anchorRestoreActive = keepAnchorRequest
		val openSnapshot = DirectChatOpenViewportSnapshot(
			anchorMessageId = snapshot.firstVisibleMessageId,
			anchorOffsetPx = snapshot.firstVisibleOffsetPx,
			isBottomPinned = snapshot.isAtBottom,
		)
		if (!anchorRestoreActive && (openSnapshot.anchorMessageId != null || openSnapshot.isBottomPinned)) {
			pendingOpenViewportSnapshot = openSnapshot
		}
        unseenIncomingMessageIds -= snapshot.readableIncomingIds
		onBadgeCountChanged(unseenIncomingMessageIds.size)

	}

	fun registerIncomingMessage(
		messageId: Long,
		isOutgoing: Boolean,
		onBadgeCountChanged: (Int) -> Unit,
	) {
		if (messageId <= 0L || isOutgoing) return
        unseenIncomingMessageIds += messageId
        onBadgeCountChanged(unseenIncomingMessageIds.size)
	}

	fun onScrollSettled(conversationId: Long?) {
		if (conversationId == null || conversationId <= 0L) return
		val snapshot = pendingOpenViewportSnapshot ?: return
		scope.launch {
			repository.setOpenViewportSnapshot(conversationId, snapshot)
		}
	}

	fun flushReadAndOpenAnchorOnClose(conversationId: Long?) {
		pendingReadDebounceJob?.cancel()
		pendingReadDebounceJob = null
		if (conversationId == null || conversationId <= 0L) return
		val pendingRead = pendingReadUpToMessageId
		val pendingAnchor = pendingOpenViewportSnapshot
		if (pendingRead == null && pendingAnchor == null) return
		pendingReadUpToMessageId = null
        scope.launch(NonCancellable, start = CoroutineStart.UNDISPATCHED) {
            pendingAnchor?.let { repository.setOpenViewportSnapshot(conversationId, it) }
            pendingRead?.let { repository.markReadUpTo(conversationId, it) }
        }
	}

	fun reset() {
		pendingReadDebounceJob?.cancel()
		pendingReadDebounceJob = null
		unseenIncomingMessageIds = emptySet()
		lastMarkedReadMessageId = 0L
		pendingReadUpToMessageId = null
		pendingOpenViewportSnapshot = null
	}

	fun dispose(conversationId: Long?) {
		flushReadAndOpenAnchorOnClose(conversationId)
		reset()
	}
}
