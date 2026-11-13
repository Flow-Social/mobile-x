package me.floow.uikit.chat.states

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage

internal fun findFlattenedMessageIndex(
	datedMessages: List<DatedChatMessages>,
	targetMessageId: Long?,
	isReverseLayout: Boolean
): Int? {
	val highlightId = targetMessageId ?: return null
	var flatIndex = 0
	val groups = if (isReverseLayout) datedMessages.asReversed() else datedMessages

	for (group in groups) {
		val messages = if (isReverseLayout) group.messages.asReversed() else group.messages
		if (!isReverseLayout) {
			flatIndex += 1
		}
		for (message in messages) {
			if (message.id == highlightId || (message as? me.floow.uikit.chat.model.ImageMessage)?.images?.any { it.id == highlightId } == true) {
				return flatIndex
			}
			flatIndex += 1
		}
		if (isReverseLayout) {
			flatIndex += 1
		}
	}
	return null
}

internal sealed interface ChatTimelineItem {
	val key: Any

	data class DateHeader(val dayStartMillis: Long) : ChatTimelineItem {
		override val key: Any = "header_$dayStartMillis"
	}

	data class MessageRow(
		val dayStartMillis: Long,
		val message: ChatMessage,
	) : ChatTimelineItem {
		override val key: Any = message.uiKey
	}
}

internal data class ActiveAnchorRestoreRequest(
	val messageId: Long,
	val requestToken: Long,
	val initialOffsetPx: Int
)

internal data class AnchorControllerState(
	val activeRequest: ActiveAnchorRestoreRequest? = null,
	val phase: AnchorRestorePhase = AnchorRestorePhase.Idle,
	val startedAtMs: Long = 0L,
	val lastLoadTriggerKey: Long? = null,
	val lastObservedOldestMessageId: Long? = null,
	val lastProgressAtMs: Long = 0L,
	val noProgressPasses: Int = 0,
	val lastHandledRequestToken: Long = 0L,
	val lastHandledMessageId: Long? = null,
	val settleStableFrames: Int = 0
)

internal enum class AnchorRestorePhase {
	Idle,
	WaitingForTarget,
	Jumping,
	Settling,
	TimedOut
}

internal fun logAnchorRestore(message: String) {
	message.let { }
}

internal fun buildTimelineItems(
	datedMessages: List<DatedChatMessages>,
	isReverseLayout: Boolean,
	unreadBoundaryMessageId: Long?
): List<ChatTimelineItem> {
	unreadBoundaryMessageId?.let { }

	val items = mutableListOf<ChatTimelineItem>()
	val groups = if (isReverseLayout) datedMessages.asReversed() else datedMessages
	for (group in groups) {
		val messages = if (isReverseLayout) group.messages.asReversed() else group.messages
		if (!isReverseLayout) {
			items += ChatTimelineItem.DateHeader(group.dayStartMillis)
		}
		messages.forEach { message ->
			items += ChatTimelineItem.MessageRow(
				dayStartMillis = group.dayStartMillis,
				message = message,
			)
		}
		if (isReverseLayout) {
			items += ChatTimelineItem.DateHeader(group.dayStartMillis)
		}
	}
	return items
}

internal fun findTimelineMessageIndex(
	timelineItems: List<ChatTimelineItem>,
	targetMessageId: Long?
): Int? {
	val messageId = targetMessageId ?: return null
	return timelineItems.indexOfFirst { item ->
		if (item !is ChatTimelineItem.MessageRow) return@indexOfFirst false
		item.message.id == messageId || (item.message as? me.floow.uikit.chat.model.ImageMessage)?.images?.any { it.id == messageId } == true
	}.takeIf { it >= 0 }
}

internal data class ChatMessageLayoutState(
	val message: ChatMessage,
	val isOutgoing: Boolean,
	val isFirstInCluster: Boolean,
	val isMiddleInCluster: Boolean,
	val isLastInCluster: Boolean,
	val showTail: Boolean,
	val showTimestamp: Boolean,
	val showAuthorHeader: Boolean,
	val showAvatar: Boolean,
	val spacingAfter: Dp,
)

internal fun buildMessageLayoutStates(
	timelineItems: List<ChatTimelineItem>,
	isReverseLayout: Boolean,
	showAuthorHeaderForInMessages: Boolean,
): Map<Any, ChatMessageLayoutState> {
	val result = mutableMapOf<Any, ChatMessageLayoutState>()
	val messageIndices = mutableListOf<Int>()
	timelineItems.forEachIndexed { idx, item ->
		if (item is ChatTimelineItem.MessageRow) {
			messageIndices.add(idx)
		}
	}

	val count = messageIndices.size
	for (pos in 0 until count) {
		val itemIndex = messageIndices[pos]
		val currentMsg = (timelineItems[itemIndex] as ChatTimelineItem.MessageRow).message
		val isOut = isOutgoingMessage(currentMsg)

		val prevItemIdx = if (isReverseLayout) {
			if (pos + 1 < count) messageIndices[pos + 1] else null
		} else {
			if (pos - 1 >= 0) messageIndices[pos - 1] else null
		}
		val hasDateHeaderBefore = if (prevItemIdx != null) {
			val (from, to) = if (itemIndex < prevItemIdx) itemIndex to prevItemIdx else prevItemIdx to itemIndex
			(from + 1 until to).any { timelineItems[it] is ChatTimelineItem.DateHeader }
		} else false

		val prevMsg = if (!hasDateHeaderBefore && prevItemIdx != null) {
			(timelineItems[prevItemIdx] as ChatTimelineItem.MessageRow).message
		} else null

		val nextItemIdx = if (isReverseLayout) {
			if (pos - 1 >= 0) messageIndices[pos - 1] else null
		} else {
			if (pos + 1 < count) messageIndices[pos + 1] else null
		}
		val hasDateHeaderAfter = if (nextItemIdx != null) {
			val (from, to) = if (itemIndex < nextItemIdx) itemIndex to nextItemIdx else nextItemIdx to itemIndex
			(from + 1 until to).any { timelineItems[it] is ChatTimelineItem.DateHeader }
		} else false

		val nextMsg = if (!hasDateHeaderAfter && nextItemIdx != null) {
			(timelineItems[nextItemIdx] as ChatTimelineItem.MessageRow).message
		} else null

		val sameClusterAsPrev = canGroupMessages(older = prevMsg, newer = currentMsg)
		val sameClusterAsNext = canGroupMessages(older = currentMsg, newer = nextMsg)

		val isFirstInCluster = !sameClusterAsPrev
		val isLastInCluster = !sameClusterAsNext
		val isMiddleInCluster = !isFirstInCluster && !isLastInCluster

		val showTail = isLastInCluster
		val showTimestamp = isLastInCluster
		val showAuthorHeader = !isOut && showAuthorHeaderForInMessages && isFirstInCluster
		val showAvatar = !isOut && showAuthorHeaderForInMessages && isLastInCluster

		val visualNextItem = if (isReverseLayout) {
			if (itemIndex > 0) timelineItems[itemIndex - 1] else null
		} else {
			if (itemIndex < timelineItems.lastIndex) timelineItems[itemIndex + 1] else null
		}

		val spacingAfter = when (visualNextItem) {
			null -> 0.dp
			is ChatTimelineItem.DateHeader -> 16.dp
			is ChatTimelineItem.MessageRow -> {
				val nextVisualMsg = visualNextItem.message
				if (currentMsg is PostPreviewMessage || nextVisualMsg is PostPreviewMessage) {
					16.dp
				} else if (currentMsg is VideoCircleOutMessage && nextVisualMsg is VideoCircleOutMessage) {
					10.dp
				} else {
					if (canGroupMessages(older = currentMsg, newer = nextVisualMsg)) {
						2.dp
					} else {
						16.dp
					}
				}
			}
		}

		result[currentMsg.uiKey] = ChatMessageLayoutState(
			message = currentMsg,
			isOutgoing = isOut,
			isFirstInCluster = isFirstInCluster,
			isMiddleInCluster = isMiddleInCluster,
			isLastInCluster = isLastInCluster,
			showTail = showTail,
			showTimestamp = showTimestamp,
			showAuthorHeader = showAuthorHeader,
			showAvatar = showAvatar,
			spacingAfter = spacingAfter,
		)
	}

	return result
}

private fun isTextChatMessage(message: ChatMessage): Boolean =
	message is PrimaryOutMessage || message is ReplyOutMessage ||
		message is PrimaryInMessage || message is ReplyInMessage

internal fun canGroupMessages(older: ChatMessage?, newer: ChatMessage?): Boolean {
	if (older == null || newer == null) return false
	if (!isTextChatMessage(older) || !isTextChatMessage(newer)) return false
	if (older is PostPreviewMessage || newer is PostPreviewMessage) return false
	// Сообщение с реплаем не может входить в уже существующую группу от предыдущего сообщения
	if (newer is ChatReplyMessage) return false
	val out1 = isOutgoingMessage(older)
	val out2 = isOutgoingMessage(newer)
	if (out1 != out2) return false
	val timeDiff = kotlin.math.abs(older.createdAtMillis - newer.createdAtMillis)
	val hasExplicitTimestamps = older.createdAtMillis > 0L && newer.createdAtMillis > 0L
	if (hasExplicitTimestamps && timeDiff > 60 * 1000L) return false
	if (out1) return true
	return when {
		!older.authorUsername.isNullOrBlank() && !newer.authorUsername.isNullOrBlank() ->
			older.authorUsername == newer.authorUsername
		!older.authorName.isNullOrBlank() && !newer.authorName.isNullOrBlank() ->
			older.authorName == newer.authorName
		!older.authorAvatarUrl.isNullOrBlank() && !newer.authorAvatarUrl.isNullOrBlank() ->
			older.authorAvatarUrl == newer.authorAvatarUrl
		else -> true
	}
}

internal fun resolveInterMessageSpacing(
	currentMessage: ChatMessage,
	nextMessage: ChatMessage?
): Dp {
	if (nextMessage == null) return 0.dp
	if (currentMessage is PostPreviewMessage || nextMessage is PostPreviewMessage) return 16.dp
	if (currentMessage is VideoCircleOutMessage && nextMessage is VideoCircleOutMessage) return 10.dp
	return if (canGroupMessages(older = currentMessage, newer = nextMessage)) 2.dp else 16.dp
}

internal fun isOutgoingMessage(message: ChatMessage): Boolean =
	when (message) {
		is PrimaryOutMessage -> true
		is ReplyOutMessage -> true
		is VideoCircleOutMessage -> message.isOutgoing
		is me.floow.uikit.chat.model.ImageMessage -> message.isOutgoing
		is me.floow.uikit.chat.model.VoiceMessage -> message.isOutgoing
		else -> false
	}

internal fun extractMessageIdFromItemKey(
	item: LazyListItemInfo,
	messageIdByKey: Map<Any, Long>? = null
): Long? {
	val key = item.key
	return when (key) {
		is Long -> key.takeIf { it > 0L }
		is String -> {
			messageIdByKey?.get(key)
				?: run {
					val rawId = when {
						key.startsWith("msg_") -> key.removePrefix("msg_")
						key.startsWith("sid_") -> key.removePrefix("sid_")
						else -> null
					}
					rawId?.toLongOrNull()?.takeIf { it > 0L }
				}
		}
		else -> null
	}
}

internal data class ViewportTarget(
	val centerDelta: Float,
	val fullyVisible: Boolean
)

internal data class ViewportAnchorMessage(
	val messageId: Long?,
	val offsetPx: Int
)

internal fun resolveViewportTarget(
	lazyListState: LazyListState,
	targetIndex: Int
): ViewportTarget? {
	val targetItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex } ?: return null
	val viewportStart = lazyListState.layoutInfo.viewportStartOffset
	val viewportEnd = lazyListState.layoutInfo.viewportEndOffset
	val viewportCenter = (viewportStart + viewportEnd) / 2f
	val itemStart = targetItem.offset.toFloat()
	val itemEnd = targetItem.offset + targetItem.size.toFloat()
	val itemCenter = itemStart + (targetItem.size / 2f)
	val fullyVisible = itemStart >= viewportStart && itemEnd <= viewportEnd
	return ViewportTarget(
		centerDelta = itemCenter - viewportCenter,
		fullyVisible = fullyVisible
	)
}

internal fun resolveViewportAnchorMessage(
	lazyListState: LazyListState,
	isReverseLayout: Boolean,
	messageIdByKey: Map<Any, Long>? = null
): ViewportAnchorMessage {
	isReverseLayout.let { }
	val layoutInfo = lazyListState.layoutInfo
	val anchorItem = layoutInfo.visibleItemsInfo
		.mapNotNull { item ->
			extractMessageIdFromItemKey(item, messageIdByKey)?.let { messageId -> item to messageId }
		}
		.minByOrNull { (item, _) -> item.offset }

	return if (anchorItem == null) {
		ViewportAnchorMessage(null, 0)
	} else {
		ViewportAnchorMessage(
			messageId = anchorItem.second,
			offsetPx = anchorItem.first.offset - layoutInfo.viewportStartOffset
		)
	}
}

const val TELEGRAM_DUAL_SCROLL_THRESHOLD = 12
const val TELEGRAM_CONTAINER_SLIDE_DURATION_MS = 220
val TELEGRAM_CONTAINER_SLIDE_OFFSET_DP: Dp = 56.dp
const val TELEGRAM_CONTAINER_SLIDE_INITIAL_ALPHA = 0.6f

internal suspend fun animateContainerSlideIn(
	translationYAnimatable: Animatable<Float, *>,
	alphaAnimatable: Animatable<Float, *>,
	offsetPx: Float,
) {
	translationYAnimatable.snapTo(offsetPx)
	alphaAnimatable.snapTo(TELEGRAM_CONTAINER_SLIDE_INITIAL_ALPHA)
	coroutineScope {
		launch {
			translationYAnimatable.animateTo(
				targetValue = 0f,
				animationSpec = tween(
					durationMillis = TELEGRAM_CONTAINER_SLIDE_DURATION_MS,
					easing = FastOutSlowInEasing
				)
			)
		}
		launch {
			alphaAnimatable.animateTo(
				targetValue = 1.0f,
				animationSpec = tween(
					durationMillis = TELEGRAM_CONTAINER_SLIDE_DURATION_MS,
					easing = FastOutSlowInEasing
				)
			)
		}
	}
}

internal suspend fun LazyListState.scrollToBottom(
	isReverseLayout: Boolean,
	animate: Boolean,
	onTriggerSlideIn: (suspend () -> Unit)? = null
) {
	val totalItems = layoutInfo.totalItemsCount
	val targetIndex = if (isReverseLayout) 0 else (totalItems - 1).coerceAtLeast(0)
	if (!animate) {
		if (firstVisibleItemIndex == targetIndex && firstVisibleItemScrollOffset == 0) {
			return
		}
		scrollToItem(targetIndex, 0)
		return
	}
	if (firstVisibleItemIndex <= TELEGRAM_DUAL_SCROLL_THRESHOLD) {
		animateScrollToItem(targetIndex)
	} else {
		scrollToItem(targetIndex, 0)
		onTriggerSlideIn?.invoke()
	}
}
