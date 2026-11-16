package me.floow.uikit.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus
import me.floow.uikit.chat.model.formatChatClockTime

/**
 * Common timestamp padding for chat bubbles aligned to outgoing/incoming side.
 */
fun Modifier.chatMessageTimestampPadding(isOutgoing: Boolean): Modifier = this.padding(
	start = if (isOutgoing) 0.dp else 20.dp,
	end = if (isOutgoing) 20.dp else 0.dp,
)

/**
 * Unified timestamp and delivery/read status component for all chat message types.
 */
@Composable
fun ChatMessageTimestamp(
	message: ChatMessage,
	isOutgoing: Boolean,
	showUnreadDot: Boolean = false,
	showMessageStatus: Boolean = true,
	isOverlay: Boolean = false,
	onRetryClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	MessageTimestampRow(
		formattedTime = formatChatClockTime(message.createdAtMillis),
		isOutgoing = isOutgoing,
		deliveryStatus = message.deliveryStatus,
		isPinned = message.isPinned,
		showMessageStatus = showMessageStatus,
		showUnreadDot = showUnreadDot,
		isOverlay = isOverlay,
		onRetrySendClick = onRetryClick,
		modifier = modifier,
	)
}

@Composable
fun MessageTimestampRow(
	formattedTime: String,
	isOutgoing: Boolean,
	deliveryStatus: ChatMessageDeliveryStatus,
	isPinned: Boolean = false,
	showMessageStatus: Boolean = true,
	showUnreadDot: Boolean = false,
	isOverlay: Boolean = false,
	onRetrySendClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val isRead = !showUnreadDot
	val contentColor = if (isOverlay) Color.White else Color(0xFF89898D)
	val containerModifier = if (isOverlay) {
		modifier
			.background(
				color = Color.Black.copy(alpha = 0.50f),
				shape = CircleShape,
			)
			.padding(horizontal = 7.dp, vertical = 2.5.dp)
	} else {
		modifier
	}

	Box(
		modifier = containerModifier,
		contentAlignment = Alignment.Center,
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(if (isOverlay) 3.dp else 4.dp),
		) {
			if (isPinned) {
				ChatPinnedIcon(
					tint = contentColor,
					modifier = Modifier
						.height(11.dp)
						.padding(end = 2.dp)
				)
			}
			Text(
				text = formattedTime,
				color = contentColor,
				style = TextStyle(
					fontFamily = FontFamily.Default,
					fontWeight = FontWeight.Normal,
					fontSize = if (isOverlay) 11.sp else 12.sp,
					letterSpacing = 0.2.sp
				)
			)
			if (isOutgoing && showMessageStatus) {
				MessageDeliveryStatusIcon(
					status = deliveryStatus,
					isRead = isRead,
					tint = contentColor,
					onRetryClick = onRetrySendClick
				)
			}
		}
	}
}
