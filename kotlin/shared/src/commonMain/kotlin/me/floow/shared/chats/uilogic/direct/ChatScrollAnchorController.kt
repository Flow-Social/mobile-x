package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.utils.Logger
import me.floow.domain.data.repos.DirectChatWindowBounds
import me.floow.shared.chats.model.ChatMessageItemModel
import kotlinx.coroutines.Job
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
    private var jumpJob: Job? = null
    var windowGeneration: Long = 0L
        private set

    fun invalidateWindow(): Long {
        jumpJob?.cancel()
        return ++windowGeneration
    }
	private var pendingAnchorDiagnostics: AnchorDiagnostics? = null

	fun nextScrollRequestToken(): Long = nextAnchorRequestToken()

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
        val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
        val viewport = if (current.hasNewerMessages) snapshot.copy(isAtBottom = false) else snapshot
        latestViewportSnapshot = viewport
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
            snapshot = viewport,
            conversationId = current.conversationId,
            keepAnchorRequest = current.keepAnchorRequest || current.provisionalHistory,
			onBadgeCountChanged = { badgeCount ->
				stateFlow.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> state.copy(scrollToBottomBadgeCount = badgeCount)
						else -> state
					}
				}
			}
		)
	    if (!current.keepAnchorRequest && !current.provisionalHistory && !current.header.isSavedMessages) {
            val cursor = maxOf(readTracker.lastMarkedReadMessageId, readTracker.pendingReadUpToMessageId ?: 0L)
            val readable = viewport.readableIncomingIds
            if (readable.isEmpty()) return
            val boundary = current.firstUnreadMessageId?.takeIf { it > cursor }
            val unread = current.messages.asSequence()
                .filter { !it.isOutgoing && !it.isDeleted && it.id > cursor && it.id > 0L }
                .map { it.id }.distinct().sorted().toList()
            // A cumulative receipt cannot cross an unviewed incoming row or an unknown older page.
            if (boundary != null && boundary !in unread && unread.any { it > boundary }) return
            if (boundary == null && cursor == 0L && current.canLoadMore) return
            unread.takeWhile { it in readable }.lastOrNull()?.let {
                readTracker.markReadUpToIfNeeded(current.conversationId, it)
            }
        }
    }

	fun onScrollSettled() {
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
        prepareRestorableWindow(current)
        readTracker.onScrollSettled(current.conversationId)
	}

	fun onAnchorRestoreSettled(messageId: Long, offsetPx: Int) {
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return
		if (current.conversationId <= 0L || messageId <= 0L ||
            !current.keepAnchorRequest || current.anchorRequestMessageId != messageId) return
		val settledSnapshot = DirectChatOpenViewportSnapshot(
			anchorMessageId = messageId,
			anchorOffsetPx = offsetPx,
            isBottomPinned = false,
            history = current.verifiedWindow(messageId, atBottom = false),
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
				stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					scrollToBottomRequestToken = requestToken,
				)
				is DirectChatScreenState.NoMessages -> current.copy(scrollToBottomRequestToken = requestToken)
				else -> current
			}
		}
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
        if (!shouldAutoScrollToBottom && !isVisible) {
			readTracker.registerIncomingMessage(message.id, message.isOutgoing) { badgeCount ->
				updateScrollBadgeCount(badgeCount)
			}
		}
	}

	fun flushReadAndOpenAnchorOnClose() {
		val current = stateFlow.value as? DirectChatScreenState.HasData
        current?.let(::prepareRestorableWindow)
        readTracker.flushReadAndOpenAnchorOnClose(current?.conversationId)
    }

    private fun prepareRestorableWindow(current: DirectChatScreenState.HasData) {
        val stored = readTracker.pendingOpenViewportSnapshot ?: return
        readTracker.pendingOpenViewportSnapshot = stored.copy(
            history = current.verifiedWindow(stored.anchorMessageId, stored.isBottomPinned) ?: stored.history,
        )
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
        val generation = invalidateWindow()
        stateFlow.update { if (it is DirectChatScreenState.HasData) it.copy(isLoadingMore = false) else it }
        val nextToken = nextAnchorRequestToken()
        if (!current.provisionalHistory && current.messages.any { it.id == messageId }) {
            stateFlow.update { state ->
                (state as? DirectChatScreenState.HasData)?.copy(
                    highlightedMessageId = messageId,
                    anchorRequestToken = nextToken,
                    keepAnchorRequest = false,
                    isLoadingMore = false,
                ) ?: state
            }
            return
        }
        jumpJob = scope.launch {
            chatLoader.loadAroundMessage(current.conversationId, messageId)
                .onSuccess { page ->
                    if (generation != windowGeneration) return@onSuccess
                    if (page.items.none { it.id == messageId }) {
                        eventsFlow.tryEmit(DirectChatStateHolder.Event.ShowMessage("Сообщение недоступно"))
                        return@onSuccess
                    }
                    stateFlow.update { state ->
                        if (state !is DirectChatScreenState.HasData || state.conversationId != current.conversationId) return@update state
                        state.copy(
                            messages = timelineReducer.applyPeerReadState(
                                timelineReducer.mergeMessages(page.items + state.messages.filter { it.id <= 0L }),
                                maxOf(page.peerLastReadMessageId ?: 0L, state.peerLastReadMessageId),
                            ),
                            canLoadMore = page.canLoadMore,
                            nextBeforeMessageId = page.nextBeforeMessageId,
                            hasNewerMessages = page.hasNewerMessages,
                            historyUpperBound = page.items.maxOfOrNull { it.id }?.takeIf { page.hasNewerMessages },
                            provisionalHistory = false,
                            isLoadingMore = false,
                            peerLastReadMessageId = maxOf(page.peerLastReadMessageId ?: 0L, state.peerLastReadMessageId),
                            highlightedMessageId = messageId,
                            anchorRequestMessageId = messageId,
                            anchorRequestOffsetPx = 0,
                            anchorRequestToken = nextToken,
                            keepAnchorRequest = true,
                        )
                    }
                }
                .onFailure {
                    if (generation == windowGeneration) {
                        eventsFlow.tryEmit(DirectChatStateHolder.Event.ShowMessage(it.message ?: "Сообщение недоступно"))
                    }
                }
        }
    }

	fun reset() {
        invalidateWindow()
		latestViewportSnapshot = null
		pendingAnchorDiagnostics = null
	}
}

private fun DirectChatScreenState.HasData.verifiedWindow(anchorId: Long?, atBottom: Boolean): DirectChatWindowBounds? {
    if (provisionalHistory) return null
    val ids = messages.asSequence().map { it.id }
        .filter { it > 0 && (historyUpperBound == null || it <= historyUpperBound) }
        .distinct().sorted().toList()
    if (ids.isEmpty()) return null
    val anchorIndex = if (atBottom) ids.lastIndex else ids.indexOf(anchorId)
    if (anchorIndex < 0) return null
    val start = (anchorIndex - 30).coerceAtLeast(0)
    val end = (anchorIndex + 30).coerceAtMost(ids.lastIndex)
    return DirectChatWindowBounds(ids[start], ids[end], canLoadMore || start > 0, hasNewerMessages || end < ids.lastIndex)
}
