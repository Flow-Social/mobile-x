package me.floow.shared.chats.uilogic.direct.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DirectChatOpenViewportSnapshot
import me.floow.uikit.chat.model.ChatViewportSnapshot

class ChatReadTracker(
	private val repository: ChatThreadRepository,
	private val scope: CoroutineScope,
) {
	companion object {
		const val READ_DEBOUNCE_DELAY_MS = 3_000L
	}

	var latestViewportSnapshot: ChatViewportSnapshot? = null
		private set
	var unseenIncomingMessageIds: Set<Long> = emptySet()
		private set
	var lastMarkedReadMessageId: Long = 0L
		private set
	var pendingReadUpToMessageId: Long? = null
		private set
	var pendingOpenViewportSnapshot: DirectChatOpenViewportSnapshot? = null
	private var pendingReadDebounceJob: Job? = null

	fun initializeLastMarkedRead(messageId: Long) {
		lastMarkedReadMessageId = messageId
	}

	fun markReadUpToIfNeeded(
		conversationId: Long,
		messageId: Long,
	) {
		if (conversationId <= 0L || messageId <= 0L || messageId <= lastMarkedReadMessageId) return
		lastMarkedReadMessageId = messageId
		pendingReadUpToMessageId = messageId
		pendingReadDebounceJob?.cancel()
		pendingReadDebounceJob = scope.launch {
			delay(READ_DEBOUNCE_DELAY_MS)
			val targetMessageId = pendingReadUpToMessageId ?: return@launch
			repository.markReadUpTo(conversationId, targetMessageId)
				.onSuccess {
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
		latestViewportSnapshot = snapshot
		if (conversationId == null || conversationId <= 0L) return

		val anchorRestoreActive = keepAnchorRequest
		val openSnapshot = DirectChatOpenViewportSnapshot(
			anchorMessageId = snapshot.firstVisibleMessageId,
			anchorOffsetPx = snapshot.firstVisibleOffsetPx,
			isBottomPinned = snapshot.isAtBottom,
		)
		if (!anchorRestoreActive) {
			pendingOpenViewportSnapshot = openSnapshot
		}
		unseenIncomingMessageIds = when {
			snapshot.isAtBottom -> emptySet()
			else -> unseenIncomingMessageIds - snapshot.visibleMessageIds
		}
		onBadgeCountChanged(unseenIncomingMessageIds.size)

		snapshot.visibleReadCandidateId
			?.takeIf { it > 0L }
			?.let { candidateId -> markReadUpToIfNeeded(conversationId, candidateId) }
	}

	fun registerIncomingMessage(
		messageId: Long,
		isOutgoing: Boolean,
		onBadgeCountChanged: (Int) -> Unit,
	) {
		if (messageId <= 0L || isOutgoing) return
		val atBottom = latestViewportSnapshot?.isAtBottom == true
		if (!atBottom) {
			unseenIncomingMessageIds = unseenIncomingMessageIds + messageId
			onBadgeCountChanged(unseenIncomingMessageIds.size)
		}
	}

	fun onScrollSettled(conversationId: Long?) {
		if (conversationId == null || conversationId <= 0L) return
		val snapshot = pendingOpenViewportSnapshot ?: return
		scope.launch {
			repository.setOpenViewportSnapshot(conversationId, snapshot)
		}
	}

	fun onBottomReached(onBadgeCountChanged: (Int) -> Unit) {
		unseenIncomingMessageIds = emptySet()
		onBadgeCountChanged(0)
	}

	fun flushReadAndOpenAnchorOnClose(conversationId: Long?) {
		pendingReadDebounceJob?.cancel()
		pendingReadDebounceJob = null
		if (conversationId == null || conversationId <= 0L) return
		val pendingRead = pendingReadUpToMessageId
		val pendingAnchor = pendingOpenViewportSnapshot
		if (pendingRead == null && pendingAnchor == null) return
		pendingReadUpToMessageId = null
		scope.launch(NonCancellable) {
			withContext(NonCancellable) {
				pendingAnchor?.let { snapshot ->
					repository.setOpenViewportSnapshot(conversationId, snapshot)
				}
				pendingRead?.let { messageId ->
					repository.markReadUpTo(conversationId, messageId)
				}
			}
		}
	}

	fun reset() {
		pendingReadDebounceJob?.cancel()
		pendingReadDebounceJob = null
		latestViewportSnapshot = null
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
