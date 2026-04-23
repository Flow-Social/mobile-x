package me.floow.uikit.chat.states

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ChatBubble
import me.floow.uikit.chat.components.replyable.ReplyableChatBubble
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.formatChatDayLabel
import me.floow.uikit.chat.model.isChatLocalToday
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.LocalTypography

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
			contentDescription = "Открыть тред",
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
	val animatedBackground by animateColorAsState(targetValue = targetColor, label = "MessageRowHighlight")

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
	interactionEnabled: Boolean = true,
	modifier: Modifier = Modifier,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	content: @Composable () -> Unit
) {
	Row(modifier = modifier, verticalAlignment = Alignment.Top) {
		if (showSelector) {
			MessageSelectionIndicator(
				selected = selected,
				modifier = Modifier.padding(end = 10.dp, top = 4.dp)
			)
		}
		Box(
			modifier = Modifier
				.weight(1f)
				.then(
					if (interactionEnabled) {
						Modifier.combinedClickable(
							interactionSource = remember { MutableInteractionSource() },
							indication = null,
							onClick = onClick,
							onLongClick = onLongClick
						)
					} else {
						Modifier
					}
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
	val indicatorColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
	Box(
		modifier = modifier
			.size(28.dp)
			.clip(CircleShape)
			.border(width = 2.dp, color = indicatorColor, shape = CircleShape)
			.background(color = if (selected) indicatorColor else Color.Transparent, shape = CircleShape)
	) {
		if (selected) {
			Icon(
				imageVector = Icons.Default.Check,
				contentDescription = null,
				tint = MaterialTheme.colorScheme.onPrimary,
				modifier = Modifier.align(Alignment.Center).size(16.dp)
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
			text = "Непрочитанные",
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
			showMessageStatus = config.showMessageStatusIndicators,
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
			showMessageStatus = config.showMessageStatusIndicators,
			showReplyPreview = showReplyPreview,
			modifier = modifier,
			bubbleBoundsModifier = bubbleBoundsModifier
		)
	}
}

@Composable
fun getDateSeparatorText(dayStartMillis: Long): String {
	return if (isChatLocalToday(dayStartMillis)) "Сегодня" else formatChatDayLabel(dayStartMillis)
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
		modifier = if (onClick != null) modifier.clickable { onClick() } else modifier
	)
}
