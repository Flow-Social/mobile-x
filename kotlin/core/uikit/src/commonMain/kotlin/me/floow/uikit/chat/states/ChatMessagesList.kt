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

	val incomingUiKeys by remember(timelineItems) {
		derivedStateOf {
			timelineItems
				.asSequence()
				.filterIsInstance<ChatTimelineItem.MessageRow>()
				.flatMap { item ->
					val message = item.message
					if (message is PrimaryInMessage || message is ReplyInMessage) {
						sequenceOf(message.uiKey, message.id)
					} else {
						emptySequence()
					}
				}
				.toSet()
		}
	}

	val activeAnchorRestoreRequest by remember(anchorRequest, highlightRequest) {
		derivedStateOf {
			highlightRequest
				?.takeIf { it.keepAnchored }
				?.let { request ->
					ActiveAnchorRestoreRequest(
						messageId = request.messageId,
						requestToken = request.requestToken,
						initialOffsetPx = 0
					)
				}
				?: anchorRequest
					?.takeIf { it.keepAnchored }
					?.let { request ->
						ActiveAnchorRestoreRequest(
							messageId = request.messageId,
							requestToken = request.requestToken,
							initialOffsetPx = request.initialOffsetPx
						)
					}
		}
	}

	val highlightedItemIndex by remember(highlightRequest?.messageId, timelineItems) {
		derivedStateOf {
			findTimelineMessageIndex(
				timelineItems = timelineItems,
				targetMessageId = highlightRequest?.messageId
			)
		}
	}

	val lastMessage by remember(state.messages) {
		derivedStateOf {
			state.messages.lastOrNull()?.messages?.lastOrNull()
		}
	}
	val lastMessageKey = lastMessage?.uiKey

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

	var hasUserStartedScroll by remember(stableSessionKey) { mutableStateOf(false) }
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
	var anchorController by remember(stableSessionKey) { mutableStateOf(AnchorControllerState()) }
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

	fun beginAnchorRestore(request: ActiveAnchorRestoreRequest) {
		val now = currentChatTimeMillis()
		logAnchorRestore(
			"begin requestToken=${request.requestToken} message=${request.messageId} offset=${request.initialOffsetPx}"
		)
		followBottom = false
		suppressAutoFollowAfterRestore = true
		hasUserStartedScroll = false
		anchorController = anchorController.copy(
			activeRequest = request,
			phase = AnchorRestorePhase.WaitingForTarget,
			startedAtMs = now,
			lastLoadTriggerKey = null,
			lastProgressAtMs = now,
			noProgressPasses = 0
		)
	}

	fun markAnchorRestoreTimedOut(request: ActiveAnchorRestoreRequest) {
		logAnchorRestore(
			"timeout requestToken=${request.requestToken} message=${request.messageId} phase=${anchorController.phase}"
		)
		anchorController = anchorController.copy(
			activeRequest = request,
			phase = AnchorRestorePhase.TimedOut,
			startedAtMs = 0L,
			lastLoadTriggerKey = null,
			lastProgressAtMs = 0L,
			noProgressPasses = 0,
			lastHandledRequestToken = request.requestToken,
			lastHandledMessageId = request.messageId
		)
		onAnchorRestoreTimedOut(request.messageId)
		anchorController = anchorController.copy(
			activeRequest = request,
			phase = AnchorRestorePhase.Idle,
			startedAtMs = 0L,
			lastLoadTriggerKey = null,
			lastProgressAtMs = 0L,
			noProgressPasses = 0,
			lastHandledRequestToken = request.requestToken,
			lastHandledMessageId = request.messageId
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
					if (!hasUserStartedScroll && !isProgrammaticScrollInProgress) {
						hasUserStartedScroll = true
						onUserStartedScroll()
					}
					followBottom = false
					activeAnchorRestoreRequest?.messageId?.let { messageId ->
						if (anchorController.phase != AnchorRestorePhase.Idle) {
							anchorController = anchorController.copy(
								phase = AnchorRestorePhase.TimedOut,
								startedAtMs = 0L,
								lastLoadTriggerKey = null
							)
							onAnchorRestoreTimedOut(messageId)
						}
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
		targetMessageId: Long?,
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
					.firstOrNull { item ->
						when {
							targetMessageId != null -> extractMessageIdFromItemKey(item) == targetMessageId
							else -> item.index == targetIndex
						}
					}
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

	val latestIncomingUiKeys by rememberUpdatedState(incomingUiKeys)
	val latestMessageIdByKey by rememberUpdatedState(messageIdByKey)

	val isSelectionMode = state.selectionState.isSelectionMode
	var selectionAnchorSnapshot by remember(state.chatInterlocutorId) { mutableStateOf<Pair<Int, Int>?>(null) }
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

	val isAnchorRestoreInProgress by remember(anchorController, activeAnchorRestoreRequest) {
		derivedStateOf {
			activeAnchorRestoreRequest != null && anchorController.phase in setOf(
				AnchorRestorePhase.WaitingForTarget,
				AnchorRestorePhase.Jumping,
				AnchorRestorePhase.Settling
			)
		}
	}

	LaunchedEffect(isSelectionMode) {
		if (isSelectionMode) onContextMenuDismissRequest()
		selectionAnchorSnapshot = lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
		withFrameNanos { }
		selectionAnchorSnapshot?.let { (index, offset) ->
			runProgrammaticScroll {
				lazyListState.scrollToItem(index = index, scrollOffset = offset)
			}
		}
	}

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
				val incomingKeysSnapshot = latestIncomingUiKeys
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
				val readCandidateId = visibleItems
					.asSequence()
					.filter { item ->
						item.key in incomingKeysSnapshot && item.isVisibleEnoughToBeRead(
							viewportStart = layoutInfo.viewportStartOffset,
							viewportEnd = layoutInfo.viewportEndOffset,
						)
					}
					.mapNotNull { item -> messageIdByKeySnapshot[item.key] }
					.maxOrNull()
				val atBottom = if (isReverseLayout) {
					firstVisibleIndex == 0 && firstVisibleOffset <= 32
				} else {
					val total = layoutInfo.totalItemsCount
					if (total == 0) false else (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 1
				}
				val viewportAnchor = resolveViewportAnchorMessage(
					lazyListState = lazyListState,
					isReverseLayout = isReverseLayout,
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
					visibleReadCandidateId = readCandidateId,
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
					key !in visibleKeySet && !key.startsWith("cmid_") && !key.startsWith("local_vc_")
				}
				onViewportSnapshotChanged(
					ChatViewportSnapshot(
						visibleMessageIds = snapshot.visibleMessageIds,
						visibleItems = snapshot.visibleItems,
						firstVisibleMessageId = snapshot.viewportAnchorMessageId,
						firstVisibleOffsetPx = snapshot.viewportAnchorOffsetPx,
						firstVisibleItemIndex = index,
						firstVisibleItemScrollOffsetPx = offset,
						visibleReadCandidateId = snapshot.visibleReadCandidateId,
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

	LaunchedEffect(activeAnchorRestoreRequest?.requestToken) {
		val restoreRequest = activeAnchorRestoreRequest
		if (restoreRequest != null) {
			beginAnchorRestore(restoreRequest)
		} else if (anchorController.phase != AnchorRestorePhase.Idle || anchorController.activeRequest != null) {
			anchorController = anchorController.copy(
				activeRequest = null,
				phase = AnchorRestorePhase.Idle,
				startedAtMs = 0L,
				lastLoadTriggerKey = null,
				lastProgressAtMs = 0L,
				noProgressPasses = 0
			)
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
		val shouldAutoFollowBottom = followBottom &&
			!suppressAutoFollowAfterRestore &&
			!isAnchorRestoreInProgress &&
			!hasPendingJumpTarget
		if (shouldAutoFollowBottom) {
			scrollToBottomInternal(animate = false)
		}
	}

	LaunchedEffect(
		activeAnchorRestoreRequest?.messageId,
		activeAnchorRestoreRequest?.requestToken,
		timelineItems,
		state.canLoadMore,
		state.isLoadingMore,
		oldestLoadedEdgeMessageIds,
		hasUserStartedScroll,
		suspendInitialPlacement
	) {
		if (suspendInitialPlacement) return@LaunchedEffect
		val request = activeAnchorRestoreRequest ?: return@LaunchedEffect
		val shouldHandleAnchor = (
			request.requestToken > 0L && request.requestToken != anchorController.lastHandledRequestToken
			) || request.messageId != anchorController.lastHandledMessageId
		if (!shouldHandleAnchor) return@LaunchedEffect
		if (hasUserStartedScroll) {
			logAnchorRestore(
				"user_interrupted requestToken=${request.requestToken} message=${request.messageId}"
			)
			markAnchorRestoreTimedOut(request)
			return@LaunchedEffect
		}
		anchorController = anchorController.copy(
			activeRequest = request,
			phase = AnchorRestorePhase.WaitingForTarget
		)
		val itemIndex = findTimelineMessageIndex(
			timelineItems = timelineItems,
			targetMessageId = request.messageId
		)
		if (itemIndex == null) {
			val now = currentChatTimeMillis()
			val oldestLoadedKey = oldestLoadedEdgeMessageIds.minOrNull()
			if (oldestLoadedKey != null && oldestLoadedKey != anchorController.lastObservedOldestMessageId) {
				anchorController = anchorController.copy(
					lastObservedOldestMessageId = oldestLoadedKey,
					lastProgressAtMs = now,
					lastLoadTriggerKey = null,
					noProgressPasses = 0
				)
			}
			val noProgressPasses = if (!state.isLoadingMore) {
				anchorController.noProgressPasses + 1
			} else {
				anchorController.noProgressPasses
			}
			val didTimeout = anchorController.lastProgressAtMs > 0L &&
				now - anchorController.lastProgressAtMs >= 8_000L &&
				(noProgressPasses >= 2 || !state.canLoadMore)
			when {
				state.canLoadMore && !state.isLoadingMore && oldestLoadedKey != null && anchorController.lastLoadTriggerKey != oldestLoadedKey -> {
					logAnchorRestore(
						"load_more requestToken=${request.requestToken} message=${request.messageId} " +
							"oldest=$oldestLoadedKey visibleFirst=${lazyListState.firstVisibleItemIndex}"
					)
					anchorController = anchorController.copy(
						lastLoadTriggerKey = oldestLoadedKey,
						noProgressPasses = noProgressPasses
					)
					onLoadMore()
				}
				didTimeout || (!state.canLoadMore && !state.isLoadingMore) -> {
					logAnchorRestore(
						"target_missing_timeout requestToken=${request.requestToken} message=${request.messageId} " +
							"canLoadMore=${state.canLoadMore} noProgress=$noProgressPasses oldest=$oldestLoadedKey"
					)
					markAnchorRestoreTimedOut(request)
				}
				!state.isLoadingMore -> {
					logAnchorRestore(
						"waiting_target requestToken=${request.requestToken} message=${request.messageId} " +
							"noProgress=$noProgressPasses oldest=$oldestLoadedKey"
					)
					anchorController = anchorController.copy(noProgressPasses = noProgressPasses)
				}
			}
			return@LaunchedEffect
		}
		logAnchorRestore(
			"target_found requestToken=${request.requestToken} message=${request.messageId} itemIndex=$itemIndex " +
				"requestedOffset=${request.initialOffsetPx}"
		)
		anchorController = anchorController.copy(
			activeRequest = request,
			phase = AnchorRestorePhase.Jumping,
			settleStableFrames = 0
		)
		var anchorRestored = false
		for (attempt in 0 until 6) {
			val didJump = jumpToIndexInternal(
				targetIndex = itemIndex,
				targetMessageId = request.messageId,
				animate = false,
				scrollOffset = request.initialOffsetPx,
				centerInViewport = false,
				alignToViewportStart = true
			)
			logAnchorRestore(
				"jump_attempt requestToken=${request.requestToken} message=${request.messageId} attempt=${attempt + 1} " +
					"didJump=$didJump targetIndex=$itemIndex requestedOffset=${request.initialOffsetPx}"
			)
			if (!didJump) continue
			anchorController = anchorController.copy(
				activeRequest = request,
				phase = AnchorRestorePhase.Settling
			)
			withFrameNanos { }
			var resolvedAnchor = resolveViewportAnchorMessage(lazyListState, isReverseLayout, messageIdByKey)
			logAnchorRestore(
				"after_jump requestToken=${request.requestToken} message=${request.messageId} attempt=${attempt + 1} " +
					"resolvedMessage=${resolvedAnchor.messageId} resolvedOffset=${resolvedAnchor.offsetPx} " +
					"firstVisibleIndex=${lazyListState.firstVisibleItemIndex} firstVisibleOffset=${lazyListState.firstVisibleItemScrollOffset}"
			)
			if (resolvedAnchor.messageId != request.messageId) {
				anchorController = anchorController.copy(
					activeRequest = request,
					phase = AnchorRestorePhase.Settling,
					settleStableFrames = 0
				)
				continue
			}

			var remainedStable = true
			repeat(2) { settleIndex ->
				withFrameNanos { }
				resolvedAnchor = resolveViewportAnchorMessage(lazyListState, isReverseLayout, messageIdByKey)
				logAnchorRestore(
					"passive_settle requestToken=${request.requestToken} message=${request.messageId} " +
						"attempt=${attempt + 1} frame=${settleIndex + 1} resolvedMessage=${resolvedAnchor.messageId} " +
						"resolvedOffset=${resolvedAnchor.offsetPx} firstVisibleIndex=${lazyListState.firstVisibleItemIndex} " +
						"firstVisibleOffset=${lazyListState.firstVisibleItemScrollOffset}"
				)
				if (resolvedAnchor.messageId != request.messageId) {
					remainedStable = false
					return@repeat
				}
			}
			if (remainedStable) {
				anchorController = anchorController.copy(
					activeRequest = request,
					phase = AnchorRestorePhase.Settling,
					settleStableFrames = 2
				)
				anchorRestored = true
				break
			}
			anchorController = anchorController.copy(
				activeRequest = request,
				phase = AnchorRestorePhase.Settling,
				settleStableFrames = 0
			)
		}
		if (!anchorRestored) {
			markAnchorRestoreTimedOut(request)
		}
		if (anchorRestored) {
			val resolvedAnchor = resolveViewportAnchorMessage(lazyListState, isReverseLayout, messageIdByKey)
			logAnchorRestore(
				"settled requestToken=${request.requestToken} message=${request.messageId} " +
					"resolvedOffset=${resolvedAnchor.offsetPx} viewportFirstIndex=${lazyListState.firstVisibleItemIndex} " +
					"viewportFirstOffset=${lazyListState.firstVisibleItemScrollOffset}"
			)
			anchorController = anchorController.copy(
				activeRequest = request,
				phase = AnchorRestorePhase.Idle,
				startedAtMs = 0L,
				lastLoadTriggerKey = null,
				lastProgressAtMs = 0L,
				lastObservedOldestMessageId = oldestLoadedEdgeMessageIds.minOrNull(),
				noProgressPasses = 0,
				lastHandledRequestToken = request.requestToken,
				lastHandledMessageId = request.messageId,
				settleStableFrames = 0
			)
			followBottom = false
			onAnchorRestoreSettled(request.messageId, resolvedAnchor.offsetPx)
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
		if (request.keepAnchored) return@LaunchedEffect
		val itemIndex = highlightedItemIndex ?: return@LaunchedEffect
		val shouldHandleHighlight = (
			request.requestToken > 0L && request.requestToken != lastHandledHighlightRequestToken
			) || request.messageId != lastHandledHighlightMessageId
		if (!shouldHandleHighlight) return@LaunchedEffect
		followBottom = false
		val didJump = jumpToIndexInternal(
			targetIndex = itemIndex,
			targetMessageId = request.messageId,
			animate = scrollPolicy.animateJumpToHighlightedMessage,
			centerInViewport = scrollPolicy.jumpAlignment == ChatJumpAlignment.Center
		)
		if (didJump) {
			lastHandledHighlightRequestToken = request.requestToken
			lastHandledHighlightMessageId = request.messageId
			withFrameNanos { }
			val resolvedAnchor = resolveViewportAnchorMessage(lazyListState, isReverseLayout, messageIdByKey)
			onAnchorRestoreSettled(request.messageId, resolvedAnchor.offsetPx)
		}
	}

	LaunchedEffect(highlightRequest?.requestToken) {
		if (highlightRequest?.requestToken == null) return@LaunchedEffect
		delay(1400L)
		onHighlightDismissed?.invoke()
	}

	LaunchedEffect(scrollRequest?.requestToken) {
		val requestToken = scrollRequest?.requestToken ?: return@LaunchedEffect
		if (requestToken <= 0L || requestToken == lastHandledScrollRequestToken) return@LaunchedEffect
		if (!hasListLayout || isAnchorRestoreInProgress) return@LaunchedEffect
		lastHandledScrollRequestToken = requestToken
		followBottom = true
		scrollToBottomInternal(animate = false)
	}

	LaunchedEffect(lastMessageKey, isAtBottom, hasListLayout, isAnchorRestoreInProgress, suppressAutoFollowAfterRestore) {
		if (!hasListLayout || isAnchorRestoreInProgress || lastMessageKey == null) return@LaunchedEffect
		if (activeAnchorRestoreRequest != null) return@LaunchedEffect
		if (suppressAutoFollowAfterRestore) return@LaunchedEffect
		if (!isAtBottom) return@LaunchedEffect
		followBottom = true
		scrollToBottomInternal(animate = false)
	}

	LaunchedEffect(isAtBottom, hasListLayout, isAnchorRestoreInProgress) {
		if (!hasListLayout) return@LaunchedEffect
		if (isAtBottom) {
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

	LaunchedEffect(state.canLoadMore, state.isLoadingMore, isReverseLayout, isAnchorRestoreInProgress, olderMessagesPrefetchWindowSize) {
		if (!state.canLoadMore || state.isLoadingMore || isAnchorRestoreInProgress) return@LaunchedEffect
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
	onContextMenuOpenRequest: (ChatMessage, Rect?, Rect?) -> Unit,
	onContextMenuDismissRequest: () -> Unit,
	videoCircleContent: @Composable (VideoCircleOutMessage, Boolean, () -> Unit) -> Unit,
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
