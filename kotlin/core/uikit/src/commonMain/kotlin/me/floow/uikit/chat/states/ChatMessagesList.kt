package me.floow.uikit.chat.states

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.floow.uikit.chat.components.DateSeparator
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatJumpAlignment
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.ChatVisibleItemSnapshot
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.chat.model.currentChatTimeMillis
import me.floow.uikit.components.loading.FlowLoadingIndicator

private const val DEFAULT_OLDER_MESSAGES_PREFETCH_WINDOW = 8
private const val MEDIUM_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW = 14
private const val FAST_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW = 24

/**
 * State holder and orchestrator for ChatMessagesList positioning, scrolling, anchor restoration,
 * and floating date / bottom button visibility.
 */
internal class ChatMessagesPositioningState(
	val lazyListState: LazyListState,
	val timelineSlideOffsetY: Animatable<Float, *>,
	val timelineSlideAlpha: Animatable<Float, *>,
	val keyboardDismissOnUserScrollConnection: NestedScrollConnection,
	val rowBoundsRegistry: RowBoundsRegistry,
	val visibleTimelineKeys: Set<Any>,
	val dateSeparatorVisible: Boolean,
	val floatingDateLabel: String,
	val isAtBottom: Boolean,
	val hasListLayout: Boolean,
	val suppressAutoFollowAfterRestore: Boolean,
	val shouldShowLoadingMoreIndicator: Boolean,
	val scrollToBottom: (animate: Boolean, engageFollowBottom: Boolean) -> Unit
)

/**
 * Coordinates LazyList positioning, viewport tracking, anchor restoration, and scroll state.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun rememberChatMessagesPositioningState(
	state: ChatScreenUiState.HasData,
	config: ChatScreenConfig,
	timelineItems: List<ChatTimelineItem>,
	isReverseLayout: Boolean,
	suspendInitialPlacement: Boolean,
	rowBoundsByMessageKey: MutableMap<String, Rect>,
	bubbleBoundsByMessageKey: MutableMap<String, Rect>,
	onUserStartedScroll: () -> Unit,
	onViewportSnapshotChanged: (ChatViewportSnapshot) -> Unit,
	onScrollSettled: () -> Unit,
	onAnchorRestoreSettled: (Long, Int) -> Unit,
	onAnchorRestoreTimedOut: (Long) -> Unit,
	onHighlightDismissed: (() -> Unit)?,
	onContextMenuDismissRequest: () -> Unit,
	onLoadMore: () -> Unit
): ChatMessagesPositioningState {
	val scrollPolicy = config.scrollPolicy
	val anchorRequest = state.anchorRequest
	val highlightRequest = state.highlightRequest
	val scrollRequest = state.scrollRequest
	val initialViewport = state.initialViewport

	val stableSessionKey = remember(state.chatInterlocutorId, state.chatInterlocutorName, state.timelineSessionToken) {
		"${state.chatInterlocutorId.ifBlank { state.chatInterlocutorName }}:${state.timelineSessionToken}"
	}

	val messageIdByKey by remember(timelineItems) {
		derivedStateOf {
			timelineItems
				.asSequence()
				.filterIsInstance<ChatTimelineItem.MessageRow>()
				.fold(mutableMapOf<Any, Long>()) { acc, item ->
					val message = item.message
					acc[message.uiKey] = message.id
					acc[message.id] = message.id
					acc
				}
		}
	}

    val incomingIdsByKey = remember(timelineItems) {
        buildMap {
            for (row in timelineItems.filterIsInstance<ChatTimelineItem.MessageRow>()) {
                val message = row.message
                val ids = when (message) {
                    is PrimaryInMessage, is ReplyInMessage -> listOf(message.id)
                    is me.floow.uikit.chat.model.ImageMessage -> if (message.isOutgoing) emptyList() else message.images.map { it.id }.ifEmpty { listOf(message.id) }
                    is me.floow.uikit.chat.model.VoiceMessage -> if (message.isOutgoing) emptyList() else listOf(message.id)
                    is me.floow.uikit.chat.model.VideoCircleOutMessage -> if (message.isOutgoing) emptyList() else listOf(message.id)
                    else -> emptyList()
                }.filter { it > 0L }
                if (ids.isNotEmpty()) put(message.uiKey, ids)
            }
        }
    }

    val activeAnchorRestoreRequest = anchorRequest?.takeIf { it.keepAnchored }?.let {
        ActiveAnchorRestoreRequest(it.messageId, it.requestToken, it.initialOffsetPx)
    } ?: highlightRequest?.takeIf { it.keepAnchored }?.let {
        ActiveAnchorRestoreRequest(it.messageId, it.requestToken, 0)
    }

	val highlightedItemIndex by remember(highlightRequest?.messageId, timelineItems) {
		derivedStateOf {
			findTimelineMessageIndex(
				timelineItems = timelineItems,
				targetMessageId = highlightRequest?.messageId
			)
		}
	}

	val lazyListState = rememberSaveable(stableSessionKey, saver = LazyListState.Saver) {
		LazyListState(
			firstVisibleItemIndex = initialViewport?.itemIndex ?: 0,
			firstVisibleItemScrollOffset = initialViewport?.itemScrollOffsetPx ?: 0
		)
	}

	var dateSeparatorVisible by remember(stableSessionKey) { mutableStateOf(false) }
	var floatingDateLabel by remember(stableSessionKey) { mutableStateOf("") }
	val coroutineScope = rememberCoroutineScope()
	val density = LocalDensity.current
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val rowBoundsRegistry = remember(state.chatInterlocutorId) { RowBoundsRegistry(maxEntries = 500) }

	var isProgrammaticScrollInProgress by remember(stableSessionKey) { mutableStateOf(false) }
	var followBottom by remember(stableSessionKey) {
		mutableStateOf(
			isReverseLayout &&
				anchorRequest == null &&
				highlightRequest == null &&
				initialViewport == null
		)
	}
	var suppressAutoFollowAfterRestore by remember(stableSessionKey) { mutableStateOf(false) }
    var handledAnchor by remember(stableSessionKey) { mutableStateOf<ActiveAnchorRestoreRequest?>(null) }
	var lastHandledHighlightRequestToken by rememberSaveable(stableSessionKey) { mutableStateOf(0L) }
	var lastHandledHighlightMessageId by rememberSaveable(stableSessionKey) { mutableStateOf<Long?>(null) }
	var lastHandledScrollRequestToken by rememberSaveable(stableSessionKey) { mutableStateOf(0L) }
	val timelineSlideOffsetY = remember { Animatable(0f) }
	val timelineSlideAlpha = remember { Animatable(1f) }

	suspend fun triggerTimelineSlideIn() {
		val offsetPx = with(density) { TELEGRAM_CONTAINER_SLIDE_OFFSET_DP.toPx() }
		animateContainerSlideIn(
			translationYAnimatable = timelineSlideOffsetY,
			alphaAnimatable = timelineSlideAlpha,
			offsetPx = offsetPx,
		)
	}

	val keyboardDismissOnUserScrollConnection = remember(
		focusManager,
		keyboardController,
		isProgrammaticScrollInProgress,
		activeAnchorRestoreRequest?.messageId,
		isReverseLayout
	) {
		object : NestedScrollConnection {
			override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
				if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    if (!isProgrammaticScrollInProgress) onUserStartedScroll()
                    followBottom = false
                    activeAnchorRestoreRequest?.let { request ->
                        handledAnchor = request
                        onAnchorRestoreTimedOut(request.messageId)
                    }
					focusManager.clearFocus(force = true)
					keyboardController?.hide()
				}
				return Offset.Zero
			}
		}
	}

	var hasInitializedMessageCounter by remember(stableSessionKey) { mutableStateOf(false) }
	var lastTotalMessagesCount by remember(stableSessionKey) { mutableStateOf(0) }

	LaunchedEffect(state.chatInterlocutorId) {
		rowBoundsRegistry.clear()
		rowBoundsByMessageKey.clear()
		bubbleBoundsByMessageKey.clear()
	}

	suspend fun runProgrammaticScroll(block: suspend () -> Unit) {
		isProgrammaticScrollInProgress = true
		try {
			block()
		} finally {
			isProgrammaticScrollInProgress = false
		}
	}

	suspend fun scrollToBottomInternal(animate: Boolean) {
		runProgrammaticScroll {
			lazyListState.scrollToBottom(
				isReverseLayout = isReverseLayout,
				animate = animate,
				onTriggerSlideIn = {
					triggerTimelineSlideIn()
				}
			)
		}
	}

	suspend fun jumpToIndexInternal(
		targetIndex: Int,
		animate: Boolean,
		scrollOffset: Int = 0,
		centerInViewport: Boolean = false,
		alignToViewportStart: Boolean = false
	): Boolean {
		var attempts = 0
		while (lazyListState.layoutInfo.totalItemsCount <= targetIndex && attempts < 24) {
			withFrameNanos { }
			attempts += 1
		}
		if (lazyListState.layoutInfo.totalItemsCount <= targetIndex) return false

		val thresholdPx = with(density) { 8.dp.toPx() }
		val preJumpViewportTarget = if (centerInViewport) {
			resolveViewportTarget(lazyListState, targetIndex)
		} else {
			null
		}
		if (centerInViewport && preJumpViewportTarget != null) {
			if (preJumpViewportTarget.fullyVisible) {
				return true
			}
			if (abs(preJumpViewportTarget.centerDelta) > thresholdPx) {
				runProgrammaticScroll {
					lazyListState.animateScrollBy(
						value = preJumpViewportTarget.centerDelta,
						animationSpec = tween(durationMillis = scrollPolicy.jumpAnimationDurationMs)
					)
				}
			}
			return true
		}

		runProgrammaticScroll {
			val initialJumpScrollOffset = if (alignToViewportStart && isReverseLayout) {
				0
			} else {
				scrollOffset.coerceAtLeast(0)
			}
			if (centerInViewport) {
				lazyListState.scrollToItem(index = targetIndex)
				withFrameNanos { }
				val viewportTarget = resolveViewportTarget(lazyListState, targetIndex) ?: return@runProgrammaticScroll
				if (!viewportTarget.fullyVisible || abs(viewportTarget.centerDelta) > thresholdPx) {
					if (animate) {
						lazyListState.animateScrollBy(
							value = viewportTarget.centerDelta,
							animationSpec = tween(durationMillis = scrollPolicy.jumpAnimationDurationMs)
						)
					} else {
						lazyListState.scrollBy(viewportTarget.centerDelta)
					}
				}
				return@runProgrammaticScroll
			}

			val currentIndex = lazyListState.firstVisibleItemIndex
			val distance = abs(currentIndex - targetIndex)
			if (animate && distance <= TELEGRAM_DUAL_SCROLL_THRESHOLD) {
				lazyListState.animateScrollToItem(
					index = targetIndex,
					scrollOffset = initialJumpScrollOffset
				)
			} else if (animate) {
				lazyListState.scrollToItem(
					index = targetIndex,
					scrollOffset = initialJumpScrollOffset
				)
				triggerTimelineSlideIn()
			} else {
				lazyListState.scrollToItem(
					index = targetIndex,
					scrollOffset = initialJumpScrollOffset
				)
			}
			if (alignToViewportStart) {
				withFrameNanos { }
				val targetItem = lazyListState.layoutInfo.visibleItemsInfo
					.firstOrNull { it.index == targetIndex }
				if (targetItem != null) {
					val viewportStart = lazyListState.layoutInfo.viewportStartOffset
					val startDelta = (targetItem.offset - viewportStart - scrollOffset).toFloat()
					if (abs(startDelta) > 0.5f) {
						lazyListState.scrollBy(startDelta)
					}
				}
			}
		}
		return true
	}

	// Floating date separator visibility
	LaunchedEffect(lazyListState.isScrollInProgress) {
		if (lazyListState.isScrollInProgress) {
			dateSeparatorVisible = floatingDateLabel.isNotBlank()
		} else {
			delay(300L)
			dateSeparatorVisible = false
		}
	}

	val isAtBottom by remember {
		derivedStateOf {
			if (isReverseLayout) {
				val firstIndex = lazyListState.firstVisibleItemIndex
				val firstOffset = lazyListState.firstVisibleItemScrollOffset
				(firstIndex == 0 && firstOffset == 0) ||
					(firstIndex == 0 && firstOffset <= 32)
			} else {
				val layoutInfo = lazyListState.layoutInfo
				val total = layoutInfo.totalItemsCount
				if (total == 0) return@derivedStateOf false
				val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
				lastVisible >= total - 1
			}
		}
	}

	val hasListLayout by remember {
		derivedStateOf {
			lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty()
		}
	}

	val latestIncomingIds by rememberUpdatedState(incomingIdsByKey)
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val latestPlacementSuspended by rememberUpdatedState(
        suspendInitialPlacement || state.provisionalHistory || activeAnchorRestoreRequest != null
    )
	val latestMessageIdByKey by rememberUpdatedState(messageIdByKey)

	var olderMessagesPrefetchWindowSize by remember(stableSessionKey) {
		mutableStateOf(DEFAULT_OLDER_MESSAGES_PREFETCH_WINDOW)
	}
	var visibleTimelineKeys by remember(stableSessionKey) { mutableStateOf(emptySet<Any>()) }

	val oldestLoadedEdgeMessageIds by remember(state.messages) {
		derivedStateOf {
			state.messages
				.asSequence()
				.flatMap { it.messages.asSequence() }
				.take(2)
				.map(ChatMessage::id)
				.toSet()
		}
	}

	val shouldShowLoadingMoreIndicator by remember(
		lazyListState,
		oldestLoadedEdgeMessageIds,
		state.isLoadingMore,
		messageIdByKey
	) {
		derivedStateOf {
			if (!state.isLoadingMore) return@derivedStateOf false
			lazyListState.layoutInfo.visibleItemsInfo
				.mapNotNull { item -> extractMessageIdFromItemKey(item, messageIdByKey) }
				.any { messageId -> messageId in oldestLoadedEdgeMessageIds }
		}
	}

    val isAnchorRestoreInProgress = activeAnchorRestoreRequest?.let { it != handledAnchor } == true

	DisposableEffect(Unit) {
		onDispose {
			onScrollSettled()
		}
	}

	LaunchedEffect(lazyListState.isScrollInProgress) {
		if (lazyListState.isScrollInProgress) {
			onContextMenuDismissRequest()
		} else {
			olderMessagesPrefetchWindowSize = DEFAULT_OLDER_MESSAGES_PREFETCH_WINDOW
			onScrollSettled()
		}
	}

	LaunchedEffect(lazyListState, isReverseLayout) {
		var lastIndex = lazyListState.firstVisibleItemIndex
		var lastOffset = lazyListState.firstVisibleItemScrollOffset
		var lastEventAtMs = 0L
		snapshotFlow {
			traceChatUiSection("chat.viewportCoordinator.snapshot") {
				val messageIdByKeySnapshot = latestMessageIdByKey
				val incomingIds = latestIncomingIds
				val layoutInfo = lazyListState.layoutInfo
				val visibleItems = layoutInfo.visibleItemsInfo
				val firstVisibleIndex = lazyListState.firstVisibleItemIndex
				val firstVisibleOffset = lazyListState.firstVisibleItemScrollOffset
				val visibleKeys = visibleItems.map { it.key }.toSet()
				val visibleMessageIds = visibleItems
					.mapNotNull { item -> messageIdByKeySnapshot[item.key] }
					.toSet()
				val visibleItemSnapshots = visibleItems
					.mapNotNull { item ->
						val messageId = messageIdByKeySnapshot[item.key] ?: return@mapNotNull null
						ChatVisibleItemSnapshot(
							itemKey = item.key,
							messageId = messageId,
							visibleFraction = item.visibleFraction(
								viewportStart = layoutInfo.viewportStartOffset,
								viewportEnd = layoutInfo.viewportEndOffset,
							),
						)
					}
                val readableIncomingIds = if (latestPlacementSuspended || isProgrammaticScrollInProgress ||
                    lazyListState.isScrollInProgress || !lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                ) {
                    emptySet()
                } else {
                    visibleItems.asSequence().filter { item ->
                        item.isVisibleEnoughToBeRead(layoutInfo.viewportStartOffset, layoutInfo.viewportEndOffset)
                    }.flatMap { item -> incomingIds[item.key].orEmpty().asSequence() }.toSet()
                }
				val atBottom = if (isReverseLayout) {
					firstVisibleIndex == 0 && firstVisibleOffset <= 32
				} else {
					val total = layoutInfo.totalItemsCount
					if (total == 0) false else (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 1
				}
				val viewportAnchor = resolveViewportAnchorMessage(
					lazyListState = lazyListState,
                    messageIdByKey = messageIdByKeySnapshot
				)
				val firstVisibleDayStartMillis = visibleItems
					.asSequence()
					.mapNotNull { item ->
						when (val timelineItem = timelineItems.getOrNull(item.index)) {
							is ChatTimelineItem.DateHeader -> timelineItem.dayStartMillis
							is ChatTimelineItem.MessageRow -> timelineItem.dayStartMillis
							else -> null
						}
					}
					.firstOrNull()
				ChatViewportCoordinatorSnapshot(
					firstVisibleItemIndex = firstVisibleIndex,
					firstVisibleItemScrollOffset = firstVisibleOffset,
					visibleItemKeys = visibleKeys,
					visibleMessageIds = visibleMessageIds,
					visibleItems = visibleItemSnapshots,
					viewportAnchorMessageId = viewportAnchor.messageId,
					viewportAnchorOffsetPx = viewportAnchor.offsetPx,
					readableIncomingIds = readableIncomingIds,
					isAtBottom = atBottom,
					firstVisibleDayStartMillis = firstVisibleDayStartMillis,
				)
			}
		}
			.distinctUntilChanged()
			.collectLatest { snapshot ->
				val index = snapshot.firstVisibleItemIndex
				val offset = snapshot.firstVisibleItemScrollOffset
				val now = currentChatTimeMillis()
				val elapsedMs = (now - lastEventAtMs).coerceAtLeast(1L)
				val indexDelta = index - lastIndex
				val offsetDelta = offset - lastOffset
				val scrollingTowardOlderMessages = if (isReverseLayout) {
					index > lastIndex || (index == lastIndex && offset > lastOffset)
				} else {
					index < lastIndex || (index == lastIndex && offset < lastOffset)
				}
				olderMessagesPrefetchWindowSize = when {
					scrollingTowardOlderMessages && (
						abs(indexDelta) >= 6 ||
							(abs(indexDelta) >= 3 && elapsedMs <= 120L) ||
							(abs(offsetDelta) >= 900 && elapsedMs <= 120L)
						) -> FAST_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW
					scrollingTowardOlderMessages && (
						abs(indexDelta) >= 2 ||
							abs(offsetDelta) >= 360
						) -> MEDIUM_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW
					else -> DEFAULT_OLDER_MESSAGES_PREFETCH_WINDOW
				}
				if (visibleTimelineKeys != snapshot.visibleItemKeys) {
					visibleTimelineKeys = snapshot.visibleItemKeys
				}
				floatingDateLabel = snapshot.firstVisibleDayStartMillis
					?.let(::getDateSeparatorText)
					.orEmpty()
				val visibleKeySet = snapshot.visibleItemKeys.filterIsInstance<String>().toSet()
				rowBoundsByMessageKey.keys.retainAll(visibleKeySet)
				bubbleBoundsByMessageKey.keys.removeAll { key ->
					key !in visibleKeySet && !key.startsWith("cmid_") && !key.startsWith("msg_") && !key.startsWith("local_vc_")
				}
				onViewportSnapshotChanged(
					ChatViewportSnapshot(
						visibleMessageIds = snapshot.visibleMessageIds,
						visibleItems = snapshot.visibleItems,
						firstVisibleMessageId = snapshot.viewportAnchorMessageId,
						firstVisibleOffsetPx = snapshot.viewportAnchorOffsetPx,
						firstVisibleItemIndex = index,
						firstVisibleItemScrollOffsetPx = offset,
						visibleReadCandidateId = snapshot.readableIncomingIds.maxOrNull(),
                        readableIncomingIds = snapshot.readableIncomingIds,
						isAtBottom = snapshot.isAtBottom
					)
				)
				lastIndex = index
				lastOffset = offset
				lastEventAtMs = now
			}
	}

	fun scrollToBottom(animate: Boolean = true, engageFollowBottom: Boolean = true) {
		coroutineScope.launch {
			if (engageFollowBottom) {
				followBottom = true
				suppressAutoFollowAfterRestore = false
			}
			scrollToBottomInternal(animate)
		}
	}

	LaunchedEffect(state.messages, isReverseLayout, suspendInitialPlacement, activeAnchorRestoreRequest?.requestToken) {
		val totalMessages = state.messages.sumOf { it.messages.size }
		if (!hasInitializedMessageCounter) {
			hasInitializedMessageCounter = true
			lastTotalMessagesCount = totalMessages
			val hasProgrammaticInitialTarget = activeAnchorRestoreRequest != null || highlightRequest != null
			if (
				isReverseLayout &&
				totalMessages > 0 &&
				!suspendInitialPlacement &&
				!hasProgrammaticInitialTarget &&
				initialViewport == null
			) {
				followBottom = true
				scrollToBottomInternal(animate = false)
			}
		}
		if (suspendInitialPlacement) return@LaunchedEffect
		val addedCount = (totalMessages - lastTotalMessagesCount).coerceAtLeast(0)
		lastTotalMessagesCount = totalMessages
		if (addedCount <= 0) return@LaunchedEffect
		val hasPendingJumpTarget = highlightRequest != null && highlightRequest.keepAnchored.not() && highlightedItemIndex == null
		val shouldAutoFollowBottom = !state.hasNewerHistory && followBottom &&
			!suppressAutoFollowAfterRestore &&
			!isAnchorRestoreInProgress &&
			!hasPendingJumpTarget
		val explicitRequest = scrollRequest?.requestToken
		val awaitingExplicitScroll = explicitRequest != null && explicitRequest > 0L &&
			explicitRequest != lastHandledScrollRequestToken
		if (shouldAutoFollowBottom && !awaitingExplicitScroll) {
			scrollToBottomInternal(animate = false)
		}
	}

    val anchorIndex = remember(activeAnchorRestoreRequest?.messageId, timelineItems) {
        findTimelineMessageIndex(timelineItems, activeAnchorRestoreRequest?.messageId)
    }
    LaunchedEffect(activeAnchorRestoreRequest, anchorIndex, suspendInitialPlacement) {
        val request = activeAnchorRestoreRequest ?: return@LaunchedEffect
        if (suspendInitialPlacement || handledAnchor == request) return@LaunchedEffect
        followBottom = false
        suppressAutoFollowAfterRestore = true
        // The feature loads the target window before asking Compose to place it.
        val index = anchorIndex
        if (index == null || !jumpToIndexInternal(
                targetIndex = index, animate = false,
                scrollOffset = request.initialOffsetPx, alignToViewportStart = true,
            )
        ) {
            handledAnchor = request
            onAnchorRestoreTimedOut(request.messageId)
            return@LaunchedEffect
        }
        withFrameNanos { }
        val layout = lazyListState.layoutInfo
        val placed = layout.visibleItemsInfo.firstOrNull { it.index == index }
        handledAnchor = request
        if (placed != null) {
            if (highlightRequest?.messageId == request.messageId) {
                lastHandledHighlightRequestToken = highlightRequest.requestToken
                lastHandledHighlightMessageId = request.messageId
            }
            onAnchorRestoreSettled(request.messageId, placed.offset - layout.viewportStartOffset)
        } else {
            onAnchorRestoreTimedOut(request.messageId)
        }
    }

	LaunchedEffect(
		highlightRequest?.messageId,
		highlightRequest?.requestToken,
		highlightedItemIndex,
		scrollPolicy.animateJumpToHighlightedMessage,
		scrollPolicy.jumpAnimationDurationMs,
		suspendInitialPlacement
	) {
		if (suspendInitialPlacement) return@LaunchedEffect
		val request = highlightRequest ?: return@LaunchedEffect
		if (request.keepAnchored || activeAnchorRestoreRequest != null) return@LaunchedEffect
		val itemIndex = highlightedItemIndex ?: return@LaunchedEffect
		val shouldHandleHighlight = (
			request.requestToken > 0L && request.requestToken != lastHandledHighlightRequestToken
			) || request.messageId != lastHandledHighlightMessageId
		if (!shouldHandleHighlight) return@LaunchedEffect
		followBottom = false
		val didJump = jumpToIndexInternal(
			targetIndex = itemIndex,
			animate = scrollPolicy.animateJumpToHighlightedMessage,
			centerInViewport = scrollPolicy.jumpAlignment == ChatJumpAlignment.Center
		)
		if (didJump) {
			lastHandledHighlightRequestToken = request.requestToken
			lastHandledHighlightMessageId = request.messageId
			withFrameNanos { }
			val resolvedAnchor = resolveViewportAnchorMessage(lazyListState, messageIdByKey)
			onAnchorRestoreSettled(request.messageId, resolvedAnchor.offsetPx)
		}
	}

	LaunchedEffect(highlightRequest?.requestToken) {
		if (highlightRequest?.requestToken == null) return@LaunchedEffect
		delay(1400L)
		onHighlightDismissed?.invoke()
	}

	LaunchedEffect(scrollRequest?.requestToken, hasListLayout, isAnchorRestoreInProgress) {
		val requestToken = scrollRequest?.requestToken ?: return@LaunchedEffect
		if (requestToken <= 0L || requestToken == lastHandledScrollRequestToken) return@LaunchedEffect
		if (!hasListLayout || isAnchorRestoreInProgress) return@LaunchedEffect
		lastHandledScrollRequestToken = requestToken
		followBottom = true
		scrollToBottomInternal(animate = false)
	}

	LaunchedEffect(isAtBottom, hasListLayout, isAnchorRestoreInProgress, state.hasNewerHistory) {
		if (!hasListLayout) return@LaunchedEffect
		if (isAtBottom && !state.hasNewerHistory) {
			if (!isAnchorRestoreInProgress) {
				followBottom = true
				suppressAutoFollowAfterRestore = false
			}
		}
	}

	LaunchedEffect(lazyListState, isReverseLayout, isAtBottom) {
		if (scrollPolicy.alwaysShowScrollToBottomWhenNotAtBottom) return@LaunchedEffect
		var lastIndex = 0
		var lastOffset = 0
		snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
			.distinctUntilChanged()
			.collectLatest { (index, offset) ->
				if (isProgrammaticScrollInProgress) {
					lastIndex = index
					lastOffset = offset
					return@collectLatest
				}

				val scrollingAwayFromBottom = if (isReverseLayout) {
					index > lastIndex || (index == lastIndex && offset > lastOffset)
				} else {
					index < lastIndex || (index == lastIndex && offset < lastOffset)
				}

				lastIndex = index
				lastOffset = offset

				if (isAtBottom) return@collectLatest

				if (scrollingAwayFromBottom) {
					followBottom = false
				}
			}
	}

	LaunchedEffect(state.canLoadMore, state.isLoadingMore, isReverseLayout, isAnchorRestoreInProgress, olderMessagesPrefetchWindowSize, state.provisionalHistory) {
		if (!state.canLoadMore || state.isLoadingMore || isAnchorRestoreInProgress || state.provisionalHistory) return@LaunchedEffect
		snapshotFlow {
			val layoutInfo = lazyListState.layoutInfo
			val totalItems = layoutInfo.totalItemsCount
			val lastVisibleIndex = layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
			Triple(totalItems, lazyListState.firstVisibleItemIndex, lastVisibleIndex)
		}
			.distinctUntilChanged()
			.collectLatest { (totalItems, firstVisibleIndex, lastVisibleIndex) ->
				if (totalItems <= 0) return@collectLatest
				val threshold = olderMessagesPrefetchWindowSize.coerceAtLeast(1)
				val remainingToTop = if (isReverseLayout) {
					(totalItems - 1 - lastVisibleIndex).coerceAtLeast(0)
				} else {
					firstVisibleIndex.coerceAtLeast(0)
				}
				if (remainingToTop <= threshold) {
					onLoadMore()
				}
			}
	}

	return remember(
		lazyListState,
		timelineSlideOffsetY,
		timelineSlideAlpha,
		keyboardDismissOnUserScrollConnection,
		rowBoundsRegistry,
		visibleTimelineKeys,
		dateSeparatorVisible,
		floatingDateLabel,
		isAtBottom,
		hasListLayout,
		suppressAutoFollowAfterRestore,
		shouldShowLoadingMoreIndicator
	) {
		ChatMessagesPositioningState(
			lazyListState = lazyListState,
			timelineSlideOffsetY = timelineSlideOffsetY,
			timelineSlideAlpha = timelineSlideAlpha,
			keyboardDismissOnUserScrollConnection = keyboardDismissOnUserScrollConnection,
			rowBoundsRegistry = rowBoundsRegistry,
			visibleTimelineKeys = visibleTimelineKeys,
			dateSeparatorVisible = dateSeparatorVisible,
			floatingDateLabel = floatingDateLabel,
			isAtBottom = isAtBottom,
			hasListLayout = hasListLayout,
			suppressAutoFollowAfterRestore = suppressAutoFollowAfterRestore,
			shouldShowLoadingMoreIndicator = shouldShowLoadingMoreIndicator,
			scrollToBottom = ::scrollToBottom
		)
	}
}

/**
 * LazyColumn for chat messages rendering items, keys, and loading indicators.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessagesList(
	positioningState: ChatMessagesPositioningState,
	timelineItems: List<ChatTimelineItem>,
	messageLayoutStates: Map<Any, ChatMessageLayoutState>,
	state: ChatScreenUiState.HasData,
	config: ChatScreenConfig,
	isReverseLayout: Boolean,
	messageListScrollEnabled: Boolean,
	listBottomPadding: Dp,
	rowBoundsByMessageKey: MutableMap<String, Rect>,
	bubbleBoundsByMessageKey: MutableMap<String, Rect>,
	hiddenPostImageIndex: Int?,
	hiddenPostImageRevealProgress: Float,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onChatBubbleLongClick: (ChatMessage) -> Unit,
	onToggleSelection: (Long) -> Unit,
	onMessageActionClick: ((ChatMessage) -> Unit)?,
	onAvatarClick: (ChatMessage) -> Unit,
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onRetrySendClick: ((ChatMessage) -> Unit)?,
	onImageRetry: ((String) -> Unit)?,
	onImageCancel: ((String) -> Unit)?,
	onPostImageClick: (PostPreviewMessage, Int, Rect?, Painter?) -> Unit,
	onPostImageBoundsChanged: ((PostPreviewMessage, Int, Rect, Painter?) -> Unit)?,
	resolveContextMenuActions: (ChatMessage) -> List<ChatContextMenuAction>,
	onContextMenuOpenRequest: (ChatMessage, Rect?, Rect?, me.floow.uikit.chat.common.ElevatedMessageData?) -> Unit,
	onContextMenuDismissRequest: () -> Unit,
	videoCircleContent: @Composable (VideoCircleOutMessage, Boolean, Dp, () -> Unit) -> Unit,
	voiceContent: @Composable (VoiceMessage, Boolean, () -> Unit) -> Unit,
	imageContent: @Composable (
		message: ImageMessage,
		isSelectionMode: Boolean,
		selectedImageIds: Set<Long>,
		onToggleImageSelection: (Long) -> Unit,
		onClick: () -> Unit,
		onLongClick: () -> Unit,
		onRetry: (String) -> Unit,
		onCancel: (String) -> Unit,
	) -> Unit,
	modifier: Modifier = Modifier
) {
	val lazyListState = positioningState.lazyListState
	val selectionState = state.selectionState
	val isSelectionMode = selectionState.isSelectionMode
	val highlightRequest = state.highlightRequest

    // Offscreen history and recycled rows must not replay an entrance.
    val playedEntrances = remember(state.timelineSessionToken) { mutableMapOf<String, Long>() }

	LazyColumn(
		state = lazyListState,
		reverseLayout = isReverseLayout,
		userScrollEnabled = messageListScrollEnabled,
		modifier = modifier
			.nestedScroll(positioningState.keyboardDismissOnUserScrollConnection)
			.fillMaxWidth()
			.fillMaxHeight()
			.graphicsLayer {
				translationY = positioningState.timelineSlideOffsetY.value
				alpha = positioningState.timelineSlideAlpha.value
			},
		contentPadding = PaddingValues(bottom = listBottomPadding)
	) {
		itemsIndexed(
			items = timelineItems,
			key = { _, item -> item.key },
			contentType = { _, item ->
				when (item) {
					is ChatTimelineItem.DateHeader -> "date"
					is ChatTimelineItem.MessageRow -> when (item.message) {
						is PrimaryInMessage, is PrimaryOutMessage -> "text"
						is ReplyInMessage, is ReplyOutMessage -> "reply"
						is ImageMessage -> "image"
						is VoiceMessage -> "voice"
						is VideoCircleOutMessage -> "video_circle"
						is PostPreviewMessage -> "post_preview"
						else -> "message"
					}
				}
			}
		) { index, timelineItem ->
			when (timelineItem) {
				is ChatTimelineItem.DateHeader -> {
					Spacer(Modifier.height(8.dp))
					Row(
						horizontalArrangement = Arrangement.Center,
						modifier = Modifier
							.fillMaxWidth()
							.height(32.dp),
					) {
						DateSeparator(text = getDateSeparatorText(timelineItem.dayStartMillis))
					}
					Spacer(Modifier.height(8.dp))
				}

				is ChatTimelineItem.MessageRow -> {
					val message = timelineItem.message
					val layoutState = messageLayoutStates[message.uiKey]
					val isRowHighlighted = highlightRequest?.messageId == message.id
					val isSelected = message.id in selectionState.selectedMessageIds
					val shouldMeasureBounds = (message is VideoCircleOutMessage) ||
						(message.uiKey in positioningState.visibleTimelineKeys)

					ChatMessageItemWrapper(
						message = message,
                        entryTimeMillis = state.messageEntryTimes[message.uiKey],
                        entryOrigin = state.messageEntryOrigins[message.uiKey],
                        playedEntrances = playedEntrances,
                        layoutState = layoutState,
						isReverseLayout = isReverseLayout,
						unreadBoundaryMessageId = state.unreadBoundaryMessageId,
						isRowHighlighted = isRowHighlighted,
						isSelected = isSelected,
						isSelectionMode = isSelectionMode,
						selectedMessageIds = selectionState.selectedMessageIds,
						showUnreadIndicators = state.showUnreadIndicators,
						peerLastReadMessageId = state.peerLastReadMessageId,
						config = config,
						isScrollBusy = lazyListState.isScrollInProgress,
						shouldMeasureBounds = shouldMeasureBounds,
						rowBoundsByMessageKey = rowBoundsByMessageKey,
						bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
						rowBoundsRegistry = positioningState.rowBoundsRegistry,
						hiddenPostImageIndex = hiddenPostImageIndex,
						hiddenPostImageRevealProgress = hiddenPostImageRevealProgress,
						onChatBubbleClick = onChatBubbleClick,
						onChatBubbleLongClick = onChatBubbleLongClick,
						onToggleSelection = onToggleSelection,
						onMessageActionClick = onMessageActionClick,
						onAvatarClick = onAvatarClick,
						onReply = onReply,
						onReplyClick = onReplyClick,
						onRetrySendClick = onRetrySendClick,
						onImageRetry = onImageRetry,
						onImageCancel = onImageCancel,
						onPostImageClick = onPostImageClick,
						onPostImageBoundsChanged = onPostImageBoundsChanged,
						resolveContextMenuActions = resolveContextMenuActions,
						onContextMenuOpenRequest = onContextMenuOpenRequest,
						onContextMenuDismissRequest = onContextMenuDismissRequest,
						videoCircleContent = videoCircleContent,
						voiceContent = voiceContent,
						imageContent = imageContent
					)
				}
			}
		}

		if (positioningState.shouldShowLoadingMoreIndicator) {
			item(key = "loading_more") {
				Row(
					horizontalArrangement = Arrangement.Center,
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 12.dp)
				) {
					FlowLoadingIndicator()
				}
			}
		}
	}
}
