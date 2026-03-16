package me.floow.uikit.chat.states

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.R
import me.floow.uikit.chat.components.ChatBubble
import me.floow.uikit.chat.components.replyable.ReplyableChatBubble
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.LocalTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun MessageActionArrowButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	IconButton(
		onClick = onClick,
		modifier = modifier.size(32.dp)
	) {
		Icon(
			imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = stringResource(R.string.chat_open_thread),
			tint = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

@Composable
internal fun HighlightedMessageRow(
	highlighted: Boolean,
	selected: Boolean,
	content: @Composable () -> Unit
) {
	val targetColor = when {
		selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
		highlighted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
		else -> Color.Transparent
	}
	val animatedBackground by animateColorAsState(
		targetValue = targetColor,
		label = "MessageRowHighlight"
	)

	Box(
		modifier = Modifier
			.fillMaxWidth()
			.background(animatedBackground)
	) {
		content()
	}
}

@Composable
internal fun SelectableMessageRow(
	showSelector: Boolean,
	selected: Boolean,
	modifier: Modifier = Modifier,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	content: @Composable () -> Unit
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.Top
	) {
		if (showSelector) {
			MessageSelectionIndicator(
				selected = selected,
				modifier = Modifier
					.padding(end = 10.dp, top = 4.dp)
			)
		}
		Box(
			modifier = Modifier
				.weight(1f)
				.combinedClickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
					onClick = onClick,
					onLongClick = onLongClick
				)
		) {
			content()
		}
	}
}

@Composable
internal fun MessageSelectionIndicator(
	selected: Boolean,
	modifier: Modifier = Modifier
) {
	val indicatorColor = if (selected) {
		MaterialTheme.colorScheme.primary
	} else {
		MaterialTheme.colorScheme.outline
	}
	Box(
		modifier = modifier
			.size(28.dp)
			.clip(CircleShape)
			.border(
				width = 2.dp,
				color = indicatorColor,
				shape = CircleShape
			)
			.background(
				color = if (selected) indicatorColor else Color.Transparent,
				shape = CircleShape
			)
	) {
		if (selected) {
			Icon(
				imageVector = Icons.Default.Check,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimary,
				modifier = Modifier
					.align(Alignment.Center)
					.size(16.dp)
			)
		}
	}
}

@Composable
internal fun UnreadBoundaryRow(
	modifier: Modifier = Modifier
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier
	) {
		HorizontalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = 1.dp,
			modifier = Modifier.weight(1f)
		)
		Text(
			text = stringResource(R.string.chat_unread_boundary),
			style = LocalTypography.current.labelMedium,
			color = MaterialTheme.colorScheme.primary
		)
		HorizontalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = 1.dp,
			modifier = Modifier.weight(1f)
		)
	}
}

@Composable
internal fun MessageBubble(
	chatMessage: ChatMessage,
	onReplyClick: (ChatMessage) -> Unit,
	onReply: (ChatMessage) -> Unit,
	onRetrySendClick: ((ChatMessage) -> Unit)?,
	isHighlighted: Boolean,
	showAuthorHeaderForInMessages: Boolean,
	showUnreadDot: Boolean,
	config: ChatScreenConfig,
	modifier: Modifier = Modifier,
	bubbleBoundsModifier: Modifier = Modifier,
	showReplyPreview: Boolean = true
	) {
		if (config.showReplyInteractions) {
		ReplyableChatBubble(
			chatMessage = chatMessage,
			onReplyClick = onReplyClick,
			onReply = onReply,
			onRetrySendClick = onRetrySendClick,
			isHighlighted = isHighlighted,
			showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
			showUnreadDot = showUnreadDot,
			showReplyPreview = showReplyPreview,
			modifier = modifier,
			bubbleBoundsModifier = bubbleBoundsModifier
		)
	} else {
		ChatBubble(
			chatMessage = chatMessage,
			onReplyClick = onReplyClick,
			onRetrySendClick = onRetrySendClick,
			isHighlighted = isHighlighted,
			showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
			showUnreadDot = showUnreadDot,
			showReplyPreview = showReplyPreview,
			modifier = modifier,
			bubbleBoundsModifier = bubbleBoundsModifier
		)
	}
}

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
			flatIndex += 1 // Header goes before messages.
		}
		for (message in messages) {
			if (message.id == highlightId) {
				return flatIndex
			}
			flatIndex += 1
		}
		if (isReverseLayout) {
			flatIndex += 1 // Header goes after messages.
		}
	}
	return null
}

internal sealed interface ChatTimelineItem {
	val key: Any

	data class DateHeader(val date: LocalDate) : ChatTimelineItem {
		override val key: Any = "header_$date"
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

private const val CHAT_ANCHOR_DEBUG_TAG = "FlowChatAnchorUI"

internal fun logAnchorRestore(message: String) {
	Log.d(CHAT_ANCHOR_DEBUG_TAG, message)
}

internal fun buildTimelineItems(
	datedMessages: List<DatedChatMessages>,
	isReverseLayout: Boolean,
	unreadBoundaryMessageId: Long?
): List<ChatTimelineItem> {
	// `unreadBoundaryMessageId` is intentionally accepted here so anchor math stays tied
	// to the exact timeline source that drives the UI state, even though the divider is
	// rendered inside the message row and doesn't add a standalone lazy item.
	unreadBoundaryMessageId?.let { }

	val items = mutableListOf<ChatTimelineItem>()
	val groups = if (isReverseLayout) datedMessages.asReversed() else datedMessages
	for (group in groups) {
		val messages = if (isReverseLayout) group.messages.asReversed() else group.messages
		if (!isReverseLayout) {
			items += ChatTimelineItem.DateHeader(group.datetime)
		}
		messages.forEach { message ->
			items += ChatTimelineItem.MessageRow(message)
		}
		if (isReverseLayout) {
			items += ChatTimelineItem.DateHeader(group.datetime)
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
	return if (currentIsOutgoing == nextIsOutgoing) {
		2.dp
	} else {
		6.dp
	}
}

internal fun isOutgoingMessage(message: ChatMessage): Boolean {
	return message is PrimaryOutMessage || message is ReplyOutMessage
}

internal fun extractMessageIdFromItemKey(
	item: androidx.compose.foundation.lazy.LazyListItemInfo,
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
	lazyListState: androidx.compose.foundation.lazy.LazyListState,
	targetIndex: Int
): ViewportTarget? {
	val targetItem = lazyListState.layoutInfo.visibleItemsInfo
		.firstOrNull { item -> item.index == targetIndex }
		?: return null
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
	lazyListState: androidx.compose.foundation.lazy.LazyListState,
	isReverseLayout: Boolean,
	messageIdByKey: Map<Any, Long>? = null
): ViewportAnchorMessage {
	val layoutInfo = lazyListState.layoutInfo
	val anchorItem = layoutInfo.visibleItemsInfo
		.mapNotNull { item ->
			extractMessageIdFromItemKey(item, messageIdByKey)?.let { messageId ->
				item to messageId
			}
		}
		.let { messageItems ->
			// Always use the visual top edge of the viewport as the canonical anchor source.
			// For reverse layout the list order changes, but the screen coordinate system does not.
			messageItems.minByOrNull { (item, _) -> item.offset }
		}

	return if (anchorItem == null) {
		ViewportAnchorMessage(
			messageId = null,
			offsetPx = 0
		)
	} else {
		ViewportAnchorMessage(
			messageId = anchorItem.second,
			offsetPx = anchorItem.first.offset - layoutInfo.viewportStartOffset
		)
	}
}

internal suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToBottom(
	isReverseLayout: Boolean,
	animate: Boolean
) {
	val totalItems = layoutInfo.totalItemsCount
	val targetIndex = if (isReverseLayout) 0 else (totalItems - 1).coerceAtLeast(0)
	if (animate) {
		animateScrollToItem(targetIndex)
	} else {
		scrollToItem(targetIndex)
	}
}

@Composable
fun getDateSeparatorText(date: LocalDate): String {
	return if (date == LocalDate.now()) {
		stringResource(R.string.today)
	} else {
		date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
	}
}

@Composable
internal fun ChatAvatar(
	placeholderText: String?,
	avatarUrl: String?,
	onClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier
) {
	NetworkAvatar(
		name = placeholderText.orEmpty(),
		avatarModel = avatarUrl,
		modifier = if (onClick != null) {
			modifier.clickable { onClick() }
		} else {
			modifier
		}
	)
}
