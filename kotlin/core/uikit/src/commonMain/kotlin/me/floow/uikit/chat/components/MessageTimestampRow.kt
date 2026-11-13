package me.floow.uikit.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus

@Composable
fun MessageTimestampRow(
	formattedTime: String,
	isOutgoing: Boolean,
	deliveryStatus: ChatMessageDeliveryStatus,
	isPinned: Boolean = false,
	showMessageStatus: Boolean = true,
	showUnreadDot: Boolean = false,
	onRetrySendClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val timeColor = Color(0xFF89898D)
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		modifier = modifier
	) {
		if (isPinned) {
			ChatPinnedIcon(
				tint = timeColor,
				modifier = Modifier
					.height(11.dp)
					.padding(end = 2.dp)
			)
		}
		Text(
			text = formattedTime,
			color = timeColor,
			style = TextStyle(
				fontFamily = FontFamily.Default,
				fontWeight = FontWeight.Normal,
				fontSize = 12.sp,
				letterSpacing = 0.2.sp
			)
		)
		if (isOutgoing && showMessageStatus) {
			MessageDeliveryStatusIcon(
				status = deliveryStatus,
				isRead = !showUnreadDot,
				onRetryClick = onRetrySendClick
			)
		}
	}
}
