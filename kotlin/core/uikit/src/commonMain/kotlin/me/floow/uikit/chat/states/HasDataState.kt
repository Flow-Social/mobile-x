package me.floow.uikit.chat.states
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.luminance
import me.floow.uikit.chat.components.ChatTailVector
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.zIndex
import me.floow.uikit.chat.components.ChatBubble
import me.floow.uikit.chat.components.DateSeparator
import androidx.compose.material3.HorizontalDivider
import me.floow.uikit.chat.components.MessageTimestampRow
import me.floow.uikit.chat.components.ScrollToBottomButton
import me.floow.uikit.chat.components.ChatAlbumBubble
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.components.replyable.ReplyableChatContent
import me.floow.uikit.chat.components.replyable.ReplyableChatBubble
import me.floow.uikit.chat.model.formatChatClockTime
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatVisibleItemSnapshot
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.ChatJumpAlignment
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.ChatMessageTapOutcome
import me.floow.uikit.chat.model.resolveMessageTapOutcome
import me.floow.uikit.chat.model.currentChatTimeMillis
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.loading.FlowLoadingIndicator

private const val DEFAULT_OLDER_MESSAGES_PREFETCH_WINDOW = 8
private const val MEDIUM_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW = 14
private const val FAST_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW = 24

@Composable
private fun Modifier.chatMessageArrivalMotion(
	key: String,
	isOutgoing: Boolean,
	enabled: Boolean,
): Modifier {
	if (!enabled) return this
	val alpha = remember(key) { Animatable(0.72f) }
	val offset = remember(key) { Animatable(if (isOutgoing) 18f else 12f) }
	LaunchedEffect(key) {
		alpha.animateTo(
			targetValue = 1f,
			animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
		)
	}
	LaunchedEffect(key, isOutgoing) {
		offset.animateTo(
			targetValue = 0f,
			animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
		)
	}
	return graphicsLayer {
		this.alpha = alpha.value
		translationY = offset.value
	}
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
fun HasDataState(
	state: ChatScreenUiState.HasData,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onChatBubbleLongClick: (ChatMessage) -> Unit,
	onToggleSelection: (Long) -> Unit,
	onMessageActionClick: ((ChatMessage) -> Unit)? = null,
	onAvatarClick: (ChatMessage) -> Unit = {},
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onRetrySendClick: ((ChatMessage) -> Unit)? = null,
	onImageRetry: ((String) -> Unit)? = null,
	onImageCancel: ((String) -> Unit)? = null,
	onPostImageClick: (PostPreviewMessage, Int, Rect?, Painter?) -> Unit,
	onPostImageBoundsChanged: ((PostPreviewMessage, Int, Rect, Painter?) -> Unit)? = null,
	hiddenPostImageIndex: Int?,
	hiddenPostImageRevealProgress: Float,
	onRequestScrollToBottom: () -> Unit = {},
	onUserStartedScroll: () -> Unit = {},
	onViewportSnapshotChanged: (ChatViewportSnapshot) -> Unit = {},
	onScrollSettled: () -> Unit = {},
	onAnchorRestoreSettled: (Long, Int) -> Unit = { _, _ -> },
	onAnchorRestoreTimedOut: (Long) -> Unit = {},
	suspendInitialPlacement: Boolean = false,
	resolveContextMenuActions: (ChatMessage) -> List<ChatContextMenuAction>,
	onContextMenuOpenRequest: (ChatMessage, Rect?, Rect?) -> Unit,
	onContextMenuDismissRequest: () -> Unit,
	rowBoundsByMessageKey: MutableMap<String, Rect>,
	bubbleBoundsByMessageKey: MutableMap<String, Rect>,
	onLoadMore: () -> Unit,
	config: ChatScreenConfig,
	messageListScrollEnabled: Boolean = true,
	videoCircleContent: @Composable (VideoCircleOutMessage, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
	voiceContent: @Composable (VoiceMessage, Boolean, () -> Unit) -> Unit = { _, _, _ -> },
	imageContent: @Composable (
		message: ImageMessage,
		isSelectionMode: Boolean,
		selectedImageIds: Set<Long>,
		onToggleImageSelection: (Long) -> Unit,
		onClick: () -> Unit,
		onLongClick: () -> Unit,
		onRetry: (String) -> Unit,
		onCancel: (String) -> Unit,
	) -> Unit = { _, _, _, _, _, _, _, _ -> },
	onHighlightDismissed: (() -> Unit)? = null,
	modifier: Modifier = Modifier
) {
	val interactionPolicy = config.interactionPolicy
	val scrollPolicy = config.scrollPolicy
	val isReverseLayout = config.layoutMode == ChatLayoutMode.NewestAtBottom
	val anchorRequest = state.anchorRequest
	val highlightRequest = state.highlightRequest
	val scrollRequest = state.scrollRequest
	val initialViewport = state.initialViewport
	val stableSessionKey = remember(state.chatInterlocutorId, state.chatInterlocutorName, state.timelineSessionToken) {
		"${state.chatInterlocutorId.ifBlank { state.chatInterlocutorName }}:${state.timelineSessionToken}"
	}
	val timelineItems by remember(state.messages, state.unreadBoundaryMessageId, isReverseLayout) {
		derivedStateOf {
			buildTimelineItems(
				datedMessages = state.messages,
				isReverseLayout = isReverseLayout,
				unreadBoundaryMessageId = state.unreadBoundaryMessageId
			)
		}
	}
	val messageLayoutStates by remember(timelineItems, isReverseLayout, interactionPolicy.showAuthorHeaderForInMessages) {
		derivedStateOf {
			buildMessageLayoutStates(
				timelineItems = timelineItems,
				isReverseLayout = isReverseLayout,
				showAuthorHeaderForInMessages = interactionPolicy.showAuthorHeaderForInMessages
			)
		}
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
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
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
			// If target is already on screen, only do a small correction in screen coordinates.
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
			val distance = kotlin.math.abs(currentIndex - targetIndex)
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

	val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
	val listBottomPadding = 101.dp + navBarsBottom
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	val scrollButtonBottomPadding = if (imeBottomPx > 0) 74.dp else (navBarsBottom + 74.dp)

	val isAtBottom by remember {
		derivedStateOf {
			if (isReverseLayout) {
				val layoutInfo = lazyListState.layoutInfo
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

	var scrollButtonVisible by remember { mutableStateOf(false) }
	var scrollButtonJob by remember { mutableStateOf<Job?>(null) }
	val selectionState = state.selectionState
	val isSelectionMode = selectionState.isSelectionMode
	var selectionAnchorSnapshot by remember(state.chatInterlocutorId) { mutableStateOf<Pair<Int, Int>?>(null) }
	var olderMessagesPrefetchWindowSize by remember(stableSessionKey) {
		mutableStateOf(DEFAULT_OLDER_MESSAGES_PREFETCH_WINDOW)
	}
	var visibleTimelineKeys by remember(stableSessionKey) { mutableStateOf(emptySet<Any>()) }
	val oldestLoadedPrefetchMessageIds by remember(state.messages, olderMessagesPrefetchWindowSize) {
		derivedStateOf {
			state.messages
				.asSequence()
				.flatMap { it.messages.asSequence() }
				.take(olderMessagesPrefetchWindowSize)
				.map(ChatMessage::id)
				.toSet()
		}
	}
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
						kotlin.math.abs(indexDelta) >= 6 ||
							(kotlin.math.abs(indexDelta) >= 3 && elapsedMs <= 120L) ||
							(kotlin.math.abs(offsetDelta) >= 900 && elapsedMs <= 120L)
						) -> FAST_SCROLL_OLDER_MESSAGES_PREFETCH_WINDOW
					scrollingTowardOlderMessages && (
						kotlin.math.abs(indexDelta) >= 2 ||
							kotlin.math.abs(offsetDelta) >= 360
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
			// Auto-follow immediately when at bottom to settle new bubble instantly.
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
				scrollButtonVisible = false
				scrollButtonJob?.cancel()
			scrollButtonJob = null
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

				val scrollingTowardBottom = if (isReverseLayout) {
					index < lastIndex || (index == lastIndex && offset < lastOffset)
				} else {
					index > lastIndex || (index == lastIndex && offset > lastOffset)
				}

				lastIndex = index
				lastOffset = offset

				if (isAtBottom) return@collectLatest

				if (scrollingAwayFromBottom) {
					followBottom = false
				}
			}
	}

		LaunchedEffect(
			followBottom,
			hasListLayout,
			isAtBottom,
			isAnchorRestoreInProgress,
			suppressAutoFollowAfterRestore,
			lazyListState.layoutInfo.viewportEndOffset
		) {
			// Disabled for anchored restore stability:
			// this path was causing background jumps to bottom.
			return@LaunchedEffect
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

	val dateSeparatorModifier = Modifier
		.fillMaxWidth()
		.height(32.dp)

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.weight(1f)
		) {
			LazyColumn(
				state = lazyListState,
				reverseLayout = isReverseLayout,
				userScrollEnabled = messageListScrollEnabled,
				modifier = Modifier
					.nestedScroll(keyboardDismissOnUserScrollConnection)
					.fillMaxWidth()
					.fillMaxHeight()
					.graphicsLayer {
						translationY = timelineSlideOffsetY.value
						alpha = timelineSlideAlpha.value
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
								modifier = dateSeparatorModifier,
							) {
								DateSeparator(text = getDateSeparatorText(timelineItem.dayStartMillis))
							}
							Spacer(Modifier.height(8.dp))
						}

						is ChatTimelineItem.MessageRow -> {
							val message = timelineItem.message
							val layoutState = messageLayoutStates[message.uiKey]
							val isFirstInGroup = layoutState?.isFirstInCluster ?: true
							val isLastInGroup = layoutState?.isLastInCluster ?: true
							val showTail = layoutState?.showTail ?: isLastInGroup
							val showTimestamp = layoutState?.showTimestamp ?: isLastInGroup
							val showAuthorHeader = layoutState?.showAuthorHeader ?: false
							val showAvatar = layoutState?.showAvatar ?: false

							val shouldShowUnreadBoundaryBeforeMessage = !isReverseLayout &&
								state.unreadBoundaryMessageId == message.id
							val shouldShowUnreadBoundaryAfterMessage = isReverseLayout &&
								state.unreadBoundaryMessageId == message.id

							val isPostPreview = message is PostPreviewMessage
							val isVideoCircle = message is VideoCircleOutMessage
							val isVoice = message is VoiceMessage
							val isImage = message is ImageMessage
							val isOut = message is PrimaryOutMessage || message is ReplyOutMessage || (message is ImageMessage && message.isOutgoing) || (message is VideoCircleOutMessage && message.isOutgoing) || (message is VoiceMessage && message.isOutgoing)
							val isRowHighlighted = highlightRequest?.messageId == message.id
							val isSelected = message.id in selectionState.selectedMessageIds
							val showUnreadDot = isOut &&
								state.showUnreadIndicators &&
								message.id > 0L &&
								message.id > state.peerLastReadMessageId
							val showIncomingAvatar = !isOut && interactionPolicy.showAuthorHeaderForInMessages
							val showMessageAction = !isSelectionMode && !isOut && !isPostPreview && onMessageActionClick != null
							val shouldMeasureBounds = isVideoCircle || message.uiKey in visibleTimelineKeys
							val isScrollBusy = lazyListState.isScrollInProgress
							val contextMenuAnchorModifier = if (shouldMeasureBounds) {
								Modifier.onGloballyPositioned { coordinates ->
									if (!isScrollBusy || isVideoCircle) {
										val bounds = coordinates.boundsInRoot()
										rowBoundsByMessageKey[message.uiKey] = bounds
										message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
											rowBoundsByMessageKey["cmid_$cmid"] = bounds
										}
									}
								}
							} else {
								Modifier
							}
							val contextMenuHighlightModifier = if (shouldMeasureBounds) {
								Modifier.onGloballyPositioned { coordinates ->
									if (!isScrollBusy || isVideoCircle) {
										val bounds = coordinates.boundsInRoot()
										bubbleBoundsByMessageKey[message.uiKey] = bounds
										message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
											bubbleBoundsByMessageKey["cmid_$cmid"] = bounds
										}
									}
								}
							} else {
								Modifier
							}
							val incomingBubbleModifier = if (showMessageAction) {
								Modifier
									.widthIn(max = 220.dp)
									.wrapContentWidth()
							} else {
								Modifier.wrapContentWidth()
							}

							Column(modifier = Modifier.fillMaxWidth()) {
								if (shouldShowUnreadBoundaryBeforeMessage) {
									UnreadBoundaryRow(
										modifier = Modifier
											.fillMaxWidth()
											.padding(horizontal = 16.dp, vertical = 6.dp)
									)
								}

								HighlightedMessageRow(
									highlighted = isRowHighlighted,
									selected = isSelected
								) {
									SelectableMessageRow(
										showSelector = isSelectionMode,
										selected = isSelected,
										interactionEnabled = true,
										modifier = Modifier
											.fillMaxWidth()
											.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
											.then(contextMenuAnchorModifier)
											.onSizeChanged { size ->
												rowBoundsRegistry.putHeight(
													messageId = message.id,
													rowHeightPx = size.height
												)
											}
											.padding(horizontal = 16.dp),
									onClick = {
										when (resolveMessageTapOutcome(isSelectionMode)) {
											ChatMessageTapOutcome.ToggleSelection -> onToggleSelection(message.id)
											ChatMessageTapOutcome.OpenContextMenu -> {
												if (resolveContextMenuActions(message).isNotEmpty()) {
													onContextMenuOpenRequest(
														message,
														rowBoundsByMessageKey[message.uiKey],
														bubbleBoundsByMessageKey[message.uiKey]
													)
												}
												onChatBubbleClick(message)
											}
										}
									},
									onLongClick = {
										if (isSelectionMode) {
											onToggleSelection(message.id)
										} else {
											onContextMenuDismissRequest()
											onChatBubbleLongClick(message)
										}
									}
									) {
										if (isPostPreview) {
											val preview = message
											Row(
												verticalAlignment = Alignment.Bottom,
												horizontalArrangement = Arrangement.spacedBy(8.dp)
											) {
												ChatAvatar(
													placeholderText = preview.authorName?.takeIf { it.isNotBlank() },
													avatarUrl = preview.authorAvatarUrl,
													avatarThumb = preview.authorAvatarThumb,
													modifier = Modifier.size(36.dp),
													onClick = { onAvatarClick(preview) }
												)
												if (preview.imageVariants.isNotEmpty()) {
													val albumMessage = remember(preview) {
														ImageMessage(
															id = preview.id,
															uiKey = preview.uiKey,
															clientMessageId = preview.clientMessageId,
															messageText = preview.messageText,
															createdAtMillis = preview.createdAtMillis,
															isOutgoing = false,
															authorName = preview.authorName,
															authorUsername = preview.authorUsername,
															authorAvatarUrl = preview.authorAvatarUrl,
															deliveryStatus = preview.deliveryStatus,
															images = preview.imageVariants.mapIndexed { index, variant ->
																ChatImageItem(
																	id = index.toLong(),
																	remoteUrl = variant.fullUrl ?: variant.previewUrl,
																	thumbnailUrl = variant.lqUrl ?: variant.previewUrl,
																	width = variant.width ?: 0,
																	height = variant.height ?: 0
																)
															}
														)
													}
													Column(
														horizontalAlignment = Alignment.Start
													) {
														Box(modifier = contextMenuHighlightModifier) {
															if (showTail) {
																val tailColor = if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
																ChatTailVector(
																	color = tailColor,
																	isOutgoing = false,
																	modifier = Modifier
																		.align(Alignment.BottomStart)
																		.offset(
																			x = 7.0.dp,
																			y = 4.5.dp
																		)
																)
															}
															ChatAlbumBubble(
																message = albumMessage,
																isSelectionMode = isSelectionMode,
																selectedImageIds = selectionState.selectedMessageIds,
																onToggleImageSelection = onToggleSelection,
																onClick = { onChatBubbleClick(preview) },
																onLongClick = { onChatBubbleLongClick(preview) },
																onImageClick = { imageIndex, bounds, painter ->
																	if (!isSelectionMode) {
																		onPostImageClick(preview, imageIndex, bounds, painter)
																	}
																},
																onImageBoundsChanged = { imageIndex, bounds, painter ->
																	onPostImageBoundsChanged?.invoke(preview, imageIndex, bounds, painter)
																},
																hiddenImageIndex = hiddenPostImageIndex,
																hiddenImageRevealProgress = hiddenPostImageRevealProgress,
																modifier = Modifier.widthIn(max = 280.dp)
															)
														}
														if (showTimestamp) {
															Spacer(Modifier.height(3.dp))
															MessageTimestampRow(
																formattedTime = formatChatClockTime(preview.createdAtMillis),
																isOutgoing = false,
																deliveryStatus = preview.deliveryStatus,
																isPinned = preview.isPinned,
																showMessageStatus = true,
																showUnreadDot = false,
																modifier = Modifier.padding(start = 20.dp)
															)
														}
													}
												} else {
													MessageBubble(
														chatMessage = PrimaryInMessage(
															id = preview.id,
															uiKey = preview.uiKey,
															clientMessageId = preview.clientMessageId,
															messageText = preview.messageText,
															createdAtMillis = preview.createdAtMillis,
															authorName = preview.authorName,
															authorUsername = preview.authorUsername,
															authorAvatarUrl = preview.authorAvatarUrl,
															deliveryStatus = preview.deliveryStatus,
															isPinned = preview.isPinned
														),
														onReplyClick = { if (!isSelectionMode) onReplyClick(it) },
														onReply = { if (!isSelectionMode) onReply(it) },
														onRetrySendClick = null,
														isHighlighted = isRowHighlighted,
														showAuthorHeaderForInMessages = false,
														showUnreadDot = false,
														config = config,
														modifier = incomingBubbleModifier,
														bubbleBoundsModifier = contextMenuHighlightModifier,
														showReplyPreview = false,
														showTail = showTail,
														showTimestamp = showTimestamp
													)
												}
											}
										} else if (isVideoCircle) {
										ReplyableChatContent(
											chatMessage = message,
											onReply = {
												if (!isSelectionMode) onReply(it)
											},
											modifier = Modifier.fillMaxWidth(),
										) {
												Row(
													modifier = Modifier.fillMaxWidth(),
													horizontalArrangement = if (message.isOutgoing) {
														Arrangement.End
													} else {
														Arrangement.Start
													},
												) {
													Box(modifier = contextMenuHighlightModifier) {
														videoCircleContent(
															message,
															isSelectionMode,
															{
																if (isSelectionMode) {
																	onToggleSelection(message.id)
																} else {
																	onContextMenuDismissRequest()
																	onChatBubbleLongClick(message)
																}
															},
														)
													}
												}
											}
									} else if (isVoice) {
										ReplyableChatContent(
											chatMessage = message,
											onReply = {
												if (!isSelectionMode) onReply(it)
											},
											modifier = Modifier.fillMaxWidth(),
										) {
											Row(
												modifier = Modifier.fillMaxWidth(),
												horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
											) {
												Column(
													horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
												) {
													Box(modifier = contextMenuHighlightModifier) {
														voiceContent(
															message as VoiceMessage,
															isSelectionMode,
															{
																if (isSelectionMode) {
																	onToggleSelection(message.id)
																} else {
																	onContextMenuDismissRequest()
																	onChatBubbleLongClick(message)
																}
															}
														)
														if (showTail) {
															val tailColor = if (message.isOutgoing) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
															ChatTailVector(
																color = tailColor,
																isOutgoing = message.isOutgoing,
																modifier = Modifier
																	.align(if (message.isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
																	.offset(
																		x = if (message.isOutgoing) (-7.6).dp else 7.0.dp,
																		y = 4.5.dp
																	)
															)
														}
													}
													if (showTimestamp) {
														Spacer(Modifier.height(3.dp))
														MessageTimestampRow(
															formattedTime = formatChatClockTime(message.createdAtMillis),
															isOutgoing = message.isOutgoing,
															deliveryStatus = message.deliveryStatus,
															isPinned = message.isPinned,
															showMessageStatus = true,
															showUnreadDot = false,
															modifier = Modifier.padding(
																start = if (message.isOutgoing) 0.dp else 20.dp,
																end = if (message.isOutgoing) 20.dp else 0.dp
															)
														)
													}
												}
											}
										}
									} else if (isImage) {
										ReplyableChatContent(
											chatMessage = message,
											onReply = {
												if (!isSelectionMode) onReply(it)
											},
											modifier = Modifier.fillMaxWidth(),
										) {
											Row(
												modifier = Modifier.fillMaxWidth(),
												horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
											) {
												Column(
													horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
												) {
													Box(modifier = contextMenuHighlightModifier) {
														if (showTail) {
															val tailColor = if (message.isOutgoing) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
															ChatTailVector(
																color = tailColor,
																isOutgoing = message.isOutgoing,
																modifier = Modifier
																	.align(if (message.isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
																	.offset(
																		x = if (message.isOutgoing) (-7.6).dp else 7.0.dp,
																		y = 4.5.dp
																	)
															)
														}
														imageContent(
															message as ImageMessage,
															isSelectionMode,
															selectionState.selectedMessageIds,
															onToggleSelection,
															{
																when (resolveMessageTapOutcome(isSelectionMode)) {
																	ChatMessageTapOutcome.ToggleSelection -> onToggleSelection(message.id)
																	ChatMessageTapOutcome.OpenContextMenu -> {
																		if (resolveContextMenuActions(message).isNotEmpty()) {
																			onContextMenuOpenRequest(
																				message,
																				rowBoundsByMessageKey[message.uiKey],
																				bubbleBoundsByMessageKey[message.uiKey]
																			)
																		}
																		onChatBubbleClick(message)
																	}
																}
															},
															{
																if (isSelectionMode) {
																	onToggleSelection(message.id)
																} else {
																	onContextMenuDismissRequest()
																	onChatBubbleLongClick(message)
																}
															},
															{ clientMessageId -> onImageRetry?.invoke(clientMessageId) },
															{ clientMessageId -> onImageCancel?.invoke(clientMessageId) }
														)
													}
													if (showTimestamp) {
														Spacer(Modifier.height(3.dp))
														MessageTimestampRow(
															formattedTime = formatChatClockTime(message.createdAtMillis),
															isOutgoing = message.isOutgoing,
															deliveryStatus = message.deliveryStatus,
															isPinned = message.isPinned,
															showMessageStatus = true,
															showUnreadDot = false,
															modifier = Modifier.padding(
																start = if (message.isOutgoing) 0.dp else 20.dp,
																end = if (message.isOutgoing) 20.dp else 0.dp
															)
														)
													}
												}
											}
										}
									} else if (isOut) {
										Row(
											modifier = Modifier.fillMaxWidth(),
											horizontalArrangement = Arrangement.End
										) {
											MessageBubble(
												chatMessage = message,
												onReplyClick = {
													if (!isSelectionMode) onReplyClick(it)
												},
												onReply = {
													if (!isSelectionMode) onReply(it)
												},
												onRetrySendClick = if (!isSelectionMode) onRetrySendClick else null,
												isHighlighted = isRowHighlighted,
												showAuthorHeaderForInMessages = false,
												showUnreadDot = showUnreadDot,
												showTail = showTail,
												showTimestamp = showTimestamp,
												modifier = Modifier,
												bubbleBoundsModifier = contextMenuHighlightModifier,
												config = config
											)
										}
									} else {
										Row(
											verticalAlignment = Alignment.Top,
											horizontalArrangement = Arrangement.spacedBy(6.dp)
										) {
											if (showIncomingAvatar) {
												if (showAvatar) {
													ChatAvatar(
														placeholderText = message.authorName?.takeIf { it.isNotBlank() },
														avatarUrl = message.authorAvatarUrl,
														avatarThumb = message.authorAvatarThumb,
														modifier = Modifier.size(36.dp),
														onClick = {
															if (!isSelectionMode) {
																onAvatarClick(message)
															}
														}
													)
												} else {
													Spacer(Modifier.width(36.dp))
												}
											}
											MessageBubble(
												chatMessage = message,
												onReplyClick = {
													if (!isSelectionMode) onReplyClick(it)
												},
												onReply = {
													if (!isSelectionMode) onReply(it)
												},
												onRetrySendClick = if (!isSelectionMode) onRetrySendClick else null,
												isHighlighted = isRowHighlighted,
												showAuthorHeaderForInMessages = showAuthorHeader,
												showUnreadDot = false,
												showReplyPreview = !isSelectionMode,
												showTail = showTail,
												showTimestamp = showTimestamp,
												modifier = incomingBubbleModifier,
												bubbleBoundsModifier = contextMenuHighlightModifier,
												config = config
											)
											if (showMessageAction) {
												MessageActionArrowButton(
													onClick = { onMessageActionClick(message) }
												)
											}
										}
									}
								}
							}

							if (isPostPreview) {
								Spacer(Modifier.height(12.dp))
								HorizontalDivider(
									color = config.dividerColor ?: MaterialTheme.colorScheme.outlineVariant,
									thickness = 1.dp,
									modifier = Modifier.fillMaxWidth()
								)
								Spacer(Modifier.height(8.dp))
							} else {
								val defaultSpacing = if (message is ImageMessage || message is PostPreviewMessage) 16.dp else 2.dp
								val spacing = layoutState?.spacingAfter ?: defaultSpacing
								if (spacing > 0.dp) {
									Spacer(Modifier.height(spacing))
								}
							}

							if (shouldShowUnreadBoundaryAfterMessage) {
								UnreadBoundaryRow(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 16.dp, vertical = 6.dp)
								)
							}
							}
						}
					}
				}

				if (shouldShowLoadingMoreIndicator) {
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

			Column {
				Spacer(Modifier.height(8.dp))

				Row(
					horizontalArrangement = Arrangement.Center,
					modifier = dateSeparatorModifier,
				) {
					AnimatedVisibility(
						visible = dateSeparatorVisible && floatingDateLabel.isNotBlank(),
						enter = fadeIn(),
						exit = fadeOut()
					) {
						DateSeparator(text = floatingDateLabel)
					}
				}

				Spacer(Modifier.height(8.dp))
			}

					val scrollBadgeCount = state.scrollToBottomBadgeCount.coerceAtLeast(0)
					val shouldForceShowScrollToBottom = scrollPolicy.alwaysShowScrollToBottomWhenNotAtBottom &&
						hasListLayout &&
						!isAtBottom
					val shouldShowForAnchoredReturn = suppressAutoFollowAfterRestore &&
						hasListLayout &&
						!isAtBottom

					ScrollToBottomButton(
						visible = shouldForceShowScrollToBottom ||
							shouldShowForAnchoredReturn ||
							(hasListLayout && !isAtBottom) ||
							scrollBadgeCount > 0,
						badgeCount = scrollBadgeCount,
						isLoading = state.isLoadingMore,
						onClick = {
							scrollToBottom()
							onRequestScrollToBottom()
						},
						modifier = Modifier
							.align(Alignment.BottomEnd)
							.padding(end = 16.dp, bottom = scrollButtonBottomPadding)
					)

		}
	}
}
