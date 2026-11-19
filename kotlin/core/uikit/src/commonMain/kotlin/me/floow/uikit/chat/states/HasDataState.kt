package me.floow.uikit.chat.states

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VoiceMessage

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

/**
 * Chat timeline state rendering messages, sticky date headers, fast-scroll controls, and anchor restoration.
 * Decomposed into [ChatMessagesList], [ChatMessageItemWrapper], [ChatStickyDateHeader], and [ChatScrollControls].
 */
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
	onContextMenuOpenRequest: (ChatMessage, Rect?, Rect?, me.floow.uikit.chat.common.ElevatedMessageData?) -> Unit,
	onContextMenuDismissRequest: () -> Unit,
	rowBoundsByMessageKey: MutableMap<String, Rect>,
	bubbleBoundsByMessageKey: MutableMap<String, Rect>,
	onLoadMore: () -> Unit,
	config: ChatScreenConfig,
	composerBottomInset: Dp? = null,
	messageListScrollEnabled: Boolean = true,
	videoCircleContent: @Composable (VideoCircleOutMessage, Boolean, Dp, () -> Unit) -> Unit = { _, _, _, _ -> },
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
	val isReverseLayout = config.layoutMode == ChatLayoutMode.NewestAtBottom
	val timelineItems by remember(state.messages, state.unreadBoundaryMessageId, isReverseLayout) {
		derivedStateOf {
			buildTimelineItems(
				datedMessages = state.messages,
				isReverseLayout = isReverseLayout,
				unreadBoundaryMessageId = state.unreadBoundaryMessageId
			)
		}
	}
	val messageLayoutStates by remember(timelineItems, isReverseLayout, config.interactionPolicy.showAuthorHeaderForInMessages) {
		derivedStateOf {
			buildMessageLayoutStates(
				timelineItems = timelineItems,
				isReverseLayout = isReverseLayout,
				showAuthorHeaderForInMessages = config.interactionPolicy.showAuthorHeaderForInMessages
			)
		}
	}

	val density = LocalDensity.current
	val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
	val listBottomPadding = composerBottomInset?.plus(12.dp) ?: (101.dp + navBarsBottom)
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	val scrollButtonBottomPadding = composerBottomInset?.plus(12.dp)
		?: if (imeBottomPx > 0) 74.dp else (navBarsBottom + 74.dp)

	val positioningState = rememberChatMessagesPositioningState(
		state = state,
		config = config,
		timelineItems = timelineItems,
		isReverseLayout = isReverseLayout,
		suspendInitialPlacement = suspendInitialPlacement,
		rowBoundsByMessageKey = rowBoundsByMessageKey,
		bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
		onUserStartedScroll = onUserStartedScroll,
		onViewportSnapshotChanged = onViewportSnapshotChanged,
		onScrollSettled = onScrollSettled,
		onAnchorRestoreSettled = onAnchorRestoreSettled,
		onAnchorRestoreTimedOut = onAnchorRestoreTimedOut,
		onHighlightDismissed = onHighlightDismissed,
		onContextMenuDismissRequest = onContextMenuDismissRequest,
		onLoadMore = onLoadMore
	)

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.weight(1f)
		) {
			ChatMessagesList(
				positioningState = positioningState,
				timelineItems = timelineItems,
				messageLayoutStates = messageLayoutStates,
				state = state,
				config = config,
				isReverseLayout = isReverseLayout,
				messageListScrollEnabled = messageListScrollEnabled,
				listBottomPadding = listBottomPadding,
				rowBoundsByMessageKey = rowBoundsByMessageKey,
				bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
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
				imageContent = imageContent,
			)

			ChatStickyDateHeader(
				visible = positioningState.dateSeparatorVisible,
				dateText = positioningState.floatingDateLabel,
			)

			val scrollBadgeCount = state.scrollToBottomBadgeCount.coerceAtLeast(0)
			val shouldForceShowScrollToBottom = config.scrollPolicy.alwaysShowScrollToBottomWhenNotAtBottom &&
				positioningState.hasListLayout &&
				!positioningState.isAtBottom
			val shouldShowForAnchoredReturn = positioningState.suppressAutoFollowAfterRestore &&
				positioningState.hasListLayout &&
				!positioningState.isAtBottom

			ChatScrollControls(
				visible = shouldForceShowScrollToBottom ||
					shouldShowForAnchoredReturn ||
					(positioningState.hasListLayout && !positioningState.isAtBottom) ||
					scrollBadgeCount > 0,
				badgeCount = scrollBadgeCount,
				isLoading = state.isLoadingMore,
				bottomPadding = scrollButtonBottomPadding,
				onClick = {
					positioningState.scrollToBottom(true, true)
					onRequestScrollToBottom()
				}
			)
		}
	}
}
