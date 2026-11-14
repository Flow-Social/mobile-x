package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.utils.Logger
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.shared.chats.uilogic.direct.coordinator.ChatLoader
import me.floow.shared.chats.uilogic.direct.coordinator.ChatReadTracker
import me.floow.uikit.chat.model.ChatViewportSnapshot

class ChatScrollAnchorController(
	private val repository: ChatThreadRepository,
	private val readTracker: ChatReadTracker,
	private val chatLoader: ChatLoader,
	private val timelineReducer: ChatTimelineReducer,
	private val stateFlow: MutableStateFlow<DirectChatScreenState>,
	private val eventsFlow: MutableSharedFlow<DirectChatStateHolder.Event>,
	private val scope: CoroutineScope,
	private val logger: Logger? = null,
) {
	data class AnchorDiagnostics(
		val conversationId: Long,
		val anchorMessageId: Long,
		val requestedOffsetPx: Int,
	)

	var latestViewportSnapshot: ChatViewportSnapshot? = null
		private set
	private var anchorRequestTokenSeed: Long = 0L
	private var pendingAnchorDiagnostics: AnchorDiagnostics? = null

	fun nextScrollRequestToken(): Long = currentChatEpochMillis()

	fun nextAnchorRequestToken(): Long = ++anchorRequestTokenSeed

	fun setPendingAnchorDiagnostics(conversationId: Long?, anchorMessageId: Long?, requestedOffsetPx: Int) {
		pendingAnchorDiagnostics = conversationId
			?.takeIf { it > 0L }
			?.let { convId ->
				anchorMessageId
					?.takeIf { it > 0L }
					?.let { anchorId ->
						AnchorDiagnostics(
							conversationId = convId,
							anchorMessageId = anchorId,
							requestedOffsetPx = requestedOffsetPx,
						)
					}
			}
	}

	fun onUserStartedScroll() {
		latestViewportSnapshot = latestViewportSnapshot?.copy(isAtBottom = false)
	}

	fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
		latestViewportSnapshot = snapshot
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		val anchorRestoreActive = current.keepAnchorRequest &&
			current.anchorRequestMessageId?.takeIf { it > 0L } != null
		pendingAnchorDiagnostics
			?.takeIf { diagnostics ->
				diagnostics.conversationId == current.conversationId &&
					current.keepAnchorRequest &&
					current.anchorRequestMessageId == diagnostics.anchorMessageId
			}
			?.let { diagnostics ->
				logger?.d(
					"DirectChatStateHolder.anchor",
					"anchor_first_frame conversation_id=${diagnostics.conversationId} requested_message_id=${diagnostics.anchorMessageId} requested_offset_px=${diagnostics.requestedOffsetPx} first_visible_message_id=${snapshot.firstVisibleMessageId} first_visible_offset_px=${snapshot.firstVisibleOffsetPx}"
				)
				pendingAnchorDiagnostics = null
			}
		readTracker.onViewportSnapshotChanged(
			snapshot = snapshot,
			conversationId = current.conversationId,
			keepAnchorRequest = current.keepAnchorRequest,
			onBadgeCountChanged = { badgeCount ->
				stateFlow.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> state.copy(scrollToBottomBadgeCount = badgeCount)
						else -> state
					}
				}
			}
		)
	}

	fun onScrollSettled() {
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		readTracker.onScrollSettled(current.conversationId)
	}

	fun onAnchorRestoreSettled(messageId: Long, offsetPx: Int) {
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		if (current.conversationId <= 0L || messageId <= 0L) return
		val settledSnapshot = DirectChatOpenViewportSnapshot(
			anchorMessageId = messageId,
			anchorOffsetPx = offsetPx,
			isBottomPinned = false,
		)
		readTracker.pendingOpenViewportSnapshot = settledSnapshot
		scope.launch {
			repository.setOpenViewportSnapshot(current.conversationId, settledSnapshot)
		}
		logger?.d(
			"DirectChatStateHolder.anchor",
			"anchor_settled conversation_id=${current.conversationId} message_id=$messageId settled_offset_px=$offsetPx"
		)
		clearAnchorRestoreLock(messageId)
	}

	fun onAnchorRestoreTimedOut(messageId: Long) {
		clearAnchorRestoreLock(messageId)
	}

	fun clearAnchorRestoreLock(messageId: Long) {
		if (messageId <= 0L) return
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> {
					if (current.anchorRequestMessageId == messageId && current.keepAnchorRequest) {
						current.copy(keepAnchorRequest = false)
					} else {
						current
					}
				}
				else -> current
			}
		}
	}

	fun requestScrollToBottom() {
		val requestToken = nextScrollRequestToken()
		readTracker.onBottomReached { badgeCount -> updateScrollBadgeCount(badgeCount) }
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					scrollToBottomRequestToken = requestToken,
					scrollToBottomBadgeCount = 0,
				)
				is DirectChatScreenState.NoMessages -> current.copy(scrollToBottomRequestToken = requestToken)
				else -> current
			}
		}
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		current.messages.lastOrNull()?.id
			?.takeIf { it > 0L }
			?.let { messageId -> markReadUpToIfNeeded(current.conversationId, messageId) }
	}

	fun updateScrollBadgeCount(badgeCount: Int = readTracker.unseenIncomingMessageIds.size) {
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					scrollToBottomBadgeCount = badgeCount,
				)
				else -> current
			}
		}
	}

	fun updateUnseenIncomingMessages(
		message: ChatMessageItemModel,
		shouldAutoScrollToBottom: Boolean,
	) {
		if (message.isOutgoing || message.isDeleted || message.id <= 0L) return
		val isVisible = latestViewportSnapshot?.visibleMessageIds?.contains(message.id) == true
		if (shouldAutoScrollToBottom || isVisible) {
			readTracker.onBottomReached { badgeCount ->
				updateScrollBadgeCount(badgeCount)
			}
		} else {
			readTracker.registerIncomingMessage(message.id, message.isOutgoing) { badgeCount ->
				updateScrollBadgeCount(badgeCount)
			}
		}
	}

	fun markReadUpToIfNeeded(
		conversationId: Long,
		messageId: Long,
	) {
		readTracker.markReadUpToIfNeeded(conversationId, messageId)
	}

	fun markLatestVisibleMessagesReadIfAppropriate() {
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		if (current.keepAnchorRequest) return
		current.messages
			.lastOrNull { message ->
				!message.isOutgoing &&
					!message.isDeleted &&
					message.id > 0L
			}
			?.id
			?.let { messageId ->
				markReadUpToIfNeeded(current.conversationId, messageId)
			}
	}

	fun flushReadAndOpenAnchorOnClose() {
		val current = stateFlow.value as? DirectChatScreenState.HasData
		readTracker.flushReadAndOpenAnchorOnClose(current?.conversationId)
	}

	fun clearHighlight() {
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> if (state.highlightedMessageId != null) state.copy(highlightedMessageId = null) else state
				else -> state
			}
		}
	}

	fun highlightRequest(messageId: Long) {
		jumpToMessage(messageId)
	}

	fun highlightRequest(messageId: String) {
		messageId.toLongOrNull()?.let { jumpToMessage(it) }
	}

	fun jumpToMessage(messageId: Long) {
		if (messageId <= 0L) return
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		val nextToken = nextAnchorRequestToken()
		if (current.messages.any { it.id == messageId }) {
			eventsFlow.tryEmit(DirectChatStateHolder.Event.JumpToMessage(messageId))
			stateFlow.update { state ->
				when (state) {
					is DirectChatScreenState.HasData -> state.copy(
						highlightedMessageId = messageId,
						anchorRequestToken = nextToken
					)
					else -> state
				}
			}
			return
		}
		scope.launch {
			chatLoader.loadAroundMessage(current.conversationId, messageId)
				.onSuccess { page ->
					stateFlow.update { state ->
						when (state) {
							is DirectChatScreenState.HasData -> state.copy(
								messages = timelineReducer.mergeMessages(page.items + state.messages),
								canLoadMore = page.canLoadMore,
								nextBeforeMessageId = page.nextBeforeMessageId,
								peerLastReadMessageId = page.peerLastReadMessageId ?: state.peerLastReadMessageId,
								highlightedMessageId = messageId,
								anchorRequestMessageId = messageId,
								anchorRequestOffsetPx = 0,
								anchorRequestToken = nextToken,
								keepAnchorRequest = true,
							)
							else -> state
						}
					}
					eventsFlow.tryEmit(DirectChatStateHolder.Event.JumpToMessage(messageId))
				}
				.onFailure {
					eventsFlow.tryEmit(DirectChatStateHolder.Event.ShowMessage(it.message ?: "message not found"))
				}
		}
	}

	fun reset() {
		latestViewportSnapshot = null
		pendingAnchorDiagnostics = null
	}
}
