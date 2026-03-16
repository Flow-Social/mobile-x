package me.floow.chats.uilogic.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.floow.domain.data.repos.DirectChatViewportSnapshot
import me.floow.domain.data.repos.DirectMessagesReadCursorStore

internal data class PersistedAnchor(
	val messageId: Long,
	val offsetPx: Int,
	val isBottomPinned: Boolean,
)

internal class ChatScrollAnchorController(
	private val scope: CoroutineScope,
	private val readCursorStore: DirectMessagesReadCursorStore,
) {
	var currentAnchorMessageId: Long = 0L
		private set
	var currentAnchorOffsetPx: Int = 0
		private set
	var currentAnchorBottomPinned: Boolean = false
		private set

	var lastViewportItemIndex: Int = 0
		private set
	var lastViewportItemScrollOffsetPx: Int = 0
		private set
	var hasLastViewportSnapshot: Boolean = false
		private set

	private var pendingAnchorMessageId: Long? = null
	private var pendingAnchorOffsetPx: Int? = null
	private var pendingAnchorBottomPinned: Boolean? = null
	private var persistAnchorJob: Job? = null

	fun reset() {
		currentAnchorMessageId = 0L
		currentAnchorOffsetPx = 0
		currentAnchorBottomPinned = false
		lastViewportItemIndex = 0
		lastViewportItemScrollOffsetPx = 0
		hasLastViewportSnapshot = false
		pendingAnchorMessageId = null
		pendingAnchorOffsetPx = null
		pendingAnchorBottomPinned = null
		persistAnchorJob?.cancel()
		persistAnchorJob = null
	}

	fun restoreFrom(snapshot: DirectChatViewportSnapshot) {
		currentAnchorMessageId = snapshot.anchorMessageId?.coerceAtLeast(0L) ?: 0L
		currentAnchorOffsetPx = snapshot.anchorOffsetPx.coerceAtLeast(0)
		currentAnchorBottomPinned = snapshot.isBottomPinned
	}

	fun remember(messageId: Long, offsetPx: Int = 0, isBottomPinned: Boolean = false) {
		if (messageId <= 0L) return
		currentAnchorMessageId = messageId
		currentAnchorOffsetPx = offsetPx.coerceAtLeast(0)
		currentAnchorBottomPinned = isBottomPinned
		enqueuePersist(messageId, offsetPx, isBottomPinned)
	}

	fun updateViewportSnapshot(itemIndex: Int, itemScrollOffsetPx: Int) {
		lastViewportItemIndex = itemIndex.coerceAtLeast(0)
		lastViewportItemScrollOffsetPx = itemScrollOffsetPx.coerceAtLeast(0)
		hasLastViewportSnapshot = true
	}

	fun resolveDurableAnchor(explicitMessageId: Long? = null): PersistedAnchor? {
		val explicitId = explicitMessageId?.takeIf { it > 0L }
		if (explicitId != null) {
			return PersistedAnchor(messageId = explicitId, offsetPx = 0, isBottomPinned = false)
		}
		val storedId = currentAnchorMessageId.takeIf { it > 0L } ?: return null
		return PersistedAnchor(
			messageId = storedId,
			offsetPx = currentAnchorOffsetPx.coerceAtLeast(0),
			isBottomPinned = currentAnchorBottomPinned,
		)
	}

	fun cancelPersist(): Triple<Long?, Int?, Boolean?> {
		val messageId = pendingAnchorMessageId
		val offsetPx = pendingAnchorOffsetPx
		val bottomPinned = pendingAnchorBottomPinned
		pendingAnchorMessageId = null
		pendingAnchorOffsetPx = null
		pendingAnchorBottomPinned = null
		persistAnchorJob?.cancel()
		persistAnchorJob = null
		return Triple(messageId, offsetPx, bottomPinned)
	}

	suspend fun flushPersist(conversationId: Long) {
		val messageId = pendingAnchorMessageId ?: return
		val offsetPx = pendingAnchorOffsetPx ?: currentAnchorOffsetPx
		val bottomPinned = pendingAnchorBottomPinned ?: currentAnchorBottomPinned
		pendingAnchorMessageId = null
		pendingAnchorOffsetPx = null
		pendingAnchorBottomPinned = null
		persistNow(conversationId, messageId, offsetPx, bottomPinned)
	}

	suspend fun persistNow(
		conversationId: Long,
		messageId: Long,
		offsetPx: Int,
		isBottomPinned: Boolean,
	) {
		if (conversationId <= 0L || messageId <= 0L) return
		currentAnchorMessageId = messageId
		currentAnchorOffsetPx = offsetPx.coerceAtLeast(0)
		currentAnchorBottomPinned = isBottomPinned
		readCursorStore.setOpenViewportSnapshot(
			conversationId = conversationId,
			snapshot = DirectChatViewportSnapshot(
				anchorMessageId = messageId,
				anchorOffsetPx = currentAnchorOffsetPx,
				isBottomPinned = currentAnchorBottomPinned,
			)
		)
	}

	fun enqueuePersist(messageId: Long, offsetPx: Int, isBottomPinned: Boolean) {
		pendingAnchorMessageId = messageId
		pendingAnchorOffsetPx = offsetPx.coerceAtLeast(0)
		pendingAnchorBottomPinned = isBottomPinned
		persistAnchorJob?.cancel()
		persistAnchorJob = null
	}

	private companion object {
		const val OPEN_ANCHOR_SAVE_DELAY_MS = 200L
	}
}
