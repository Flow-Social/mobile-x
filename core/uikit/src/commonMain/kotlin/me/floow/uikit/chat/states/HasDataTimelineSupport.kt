package me.floow.uikit.chat.states

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage

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
			if (message.id == highlightId) return flatIndex
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

	data class MessageRow(val message: ChatMessage) : ChatTimelineItem {
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
			items += ChatTimelineItem.MessageRow(message)
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
		item is ChatTimelineItem.MessageRow && item.message.id == messageId
	}.takeIf { it >= 0 }
}

internal fun resolveInterMessageSpacing(
	currentMessage: ChatMessage,
	nextMessage: ChatMessage?
): Dp {
	if (nextMessage == null) return 8.dp
	if (currentMessage is PostPreviewMessage || nextMessage is PostPreviewMessage) return 8.dp
	val currentIsOutgoing = isOutgoingMessage(currentMessage)
	val nextIsOutgoing = isOutgoingMessage(nextMessage)
	return if (currentIsOutgoing == nextIsOutgoing) 2.dp else 6.dp
}

internal fun isOutgoingMessage(message: ChatMessage): Boolean =
	message is PrimaryOutMessage || message is ReplyOutMessage

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

internal suspend fun LazyListState.scrollToBottom(
	isReverseLayout: Boolean,
	animate: Boolean
) {
	val totalItems = layoutInfo.totalItemsCount
	val targetIndex = if (isReverseLayout) 0 else (totalItems - 1).coerceAtLeast(0)
	if (animate) animateScrollToItem(targetIndex) else scrollToItem(targetIndex)
}
