package me.floow.uikit.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.layout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.formatChatClockTime
import me.floow.uikit.components.misc.textwrap.TextWrap
import me.floow.uikit.components.misc.textwrap.TextWrapObstacleAlignment
import me.floow.uikit.components.misc.textwrap.buildFlowAnnotatedText
import me.floow.uikit.theme.LocalTypography

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle

data class ChatBubbleColors(
	val backgroundColor: Color,
	val borderColor: Color,
	val textColor: Color,
	val timeColor: Color,
	val replyColor: Color,
) {
	companion object {
		val Default: ChatBubbleColors
			@Composable get() = ChatBubbleColors(
				backgroundColor = Color(0xFF7A73F8),
				borderColor = Color.White.copy(alpha = 0.5f),
				textColor = Color.White,
				timeColor = Color(0xFF89898D),
				replyColor = Color.White.copy(alpha = 0.6f),
			)

		val Outlined: ChatBubbleColors
			@Composable get() {
				val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
				return ChatBubbleColors(
					backgroundColor = if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3),
					borderColor = Color.Transparent,
					textColor = if (isDark) Color.White else Color(0xFF000000),
					timeColor = Color(0xFF89898D),
					replyColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color(0xFF121213).copy(alpha = 0.6f),
				)
			}
	}
}

@Composable
fun ChatBubble(
	chatMessage: ChatMessage,
	onReplyClick: (ChatMessage) -> Unit,
	isHighlighted: Boolean = false,
	showAuthorHeaderForInMessages: Boolean = false,
	showUnreadDot: Boolean = false,
	showMessageStatus: Boolean = true,
	showReplyPreview: Boolean = true,
	showTail: Boolean = true,
	showTimestamp: Boolean = true,
	onRetrySendClick: ((ChatMessage) -> Unit)? = null,
	onProfileTagClick: ((String) -> Unit)? = null,
	onPostLinkClick: ((String, String) -> Unit)? = null,
	onUrlClick: ((String) -> Unit)? = null,
	modifier: Modifier = Modifier,
	bubbleBoundsModifier: Modifier = Modifier
) {
	val isOut = chatMessage is PrimaryOutMessage || chatMessage is ReplyOutMessage
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	val defaultBubbleColor = if (isOut) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
	val defaultTextColor = if (isOut || isDark) Color.White else Color(0xFF000000)
	val timeColor = Color(0xFF89898D)
	val authorColor = Color(0xFF83858F)

	val authorLabel = when {
		chatMessage.authorName?.isNotBlank() == true -> chatMessage.authorName
		chatMessage.authorUsername?.isNotBlank() == true -> "@${chatMessage.authorUsername}"
		else -> null
	}
	val formattedMessageTime = remember(chatMessage.createdAtMillis) {
		formatChatClockTime(chatMessage.createdAtMillis)
	}

	val backgroundColor by animateColorAsState(
		targetValue = if (isHighlighted) MaterialTheme.colorScheme.tertiaryContainer else defaultBubbleColor,
		animationSpec = tween(durationMillis = 500),
		label = "BubbleHighlight"
	)

	Column(
		horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
		modifier = modifier,
	) {
		if (showAuthorHeaderForInMessages && !isOut && !authorLabel.isNullOrBlank()) {
			Text(
				text = authorLabel,
				style = TextStyle(
					fontFamily = FontFamily.Default,
					fontWeight = FontWeight.Medium,
					fontSize = 12.sp,
				),
				color = authorColor,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
			)
		}

		val quote = chatMessage.quote
        if (showReplyPreview && quote != null) {
            val replyTitle = quote.preview.trim().ifBlank { "Сообщение недоступно" }
			ReplyPill(
				replyText = replyTitle,
				isDark = isDark,
				isOut = isOut,
				onClick = { onReplyClick(chatMessage) },
                enabled = quote.isAvailable,
				modifier = Modifier
					.widthIn(max = 263.dp)
					.layout { measurable, constraints ->
						val placeable = measurable.measure(constraints)
						layout(placeable.width, maxOf(0, placeable.height - 4.dp.roundToPx())) {
							placeable.placeRelative(0, 0)
						}
					}
			)
		}

		Box(
			modifier = bubbleBoundsModifier
		) {
			Box(
				modifier = Modifier
					.clip(RoundedCornerShape(20.dp))
					.background(backgroundColor)
					.then(
						if (isOut) {
							Modifier.border(
								width = 1.dp,
								brush = Brush.verticalGradient(
									listOf(
										Color.White.copy(alpha = 0.5f),
										Color.White.copy(alpha = 0.0f)
									)
								),
								shape = RoundedCornerShape(20.dp)
							)
						} else Modifier
					)
					.defaultMinSize(
						minWidth = 68.dp,
						minHeight = 36.dp
					)
					.widthIn(max = 263.dp)
					.padding(horizontal = 13.dp, vertical = 9.dp),
				contentAlignment = Alignment.Center
			) {
				val hasSpoiler = remember(chatMessage.messageText) {
					chatMessage.messageText.contains("||")
				}
				var isSpoilerRevealed by remember(chatMessage.uiKey) { mutableStateOf(false) }
				val linkColor = if (isOut) Color.White else MaterialTheme.colorScheme.primary
				val annotatedMessageText = remember(chatMessage.messageText, isSpoilerRevealed, defaultTextColor, linkColor) {
					buildFlowAnnotatedText(
						text = chatMessage.messageText,
						linkColor = linkColor,
						isSpoilerRevealed = isSpoilerRevealed
					)
				}

				TextWrap(
					text = annotatedMessageText,
					color = defaultTextColor,
					style = LocalTypography.current.bodyMedium.copy(
						fontWeight = FontWeight.Normal,
						fontSize = 15.sp,
						lineHeight = 18.sp,
						letterSpacing = 0.sp,
					),
					isSpoilerRevealed = isSpoilerRevealed,
					onRevealSpoiler = if (hasSpoiler && !isSpoilerRevealed) {
						{ isSpoilerRevealed = true }
					} else null,
					onProfileTagClick = onProfileTagClick,
					onPostLinkClick = onPostLinkClick,
					onUrlClick = onUrlClick,
				)
			}

			if (showTail) {
				ChatTailVector(
					color = backgroundColor,
					isOutgoing = isOut,
					modifier = Modifier
						.align(if (isOut) Alignment.BottomEnd else Alignment.BottomStart)
						.offset(
							x = if (isOut) (-7.6).dp else 7.dp,
							y = 4.5.dp
						)
				)
			}
		}

		if (showTimestamp) {
			Spacer(Modifier.height(3.dp))
			ChatMessageTimestamp(
				message = chatMessage,
				isOutgoing = isOut,
				showUnreadDot = showUnreadDot,
				showMessageStatus = showMessageStatus,
				onRetryClick = onRetrySendClick?.let { handler -> { handler(chatMessage) } },
				modifier = Modifier.chatMessageTimestampPadding(isOut),
			)
		}
	}
}

@Composable
fun ReplyPill(
	replyText: String,
	isDark: Boolean,
	isOut: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
	val haptic = LocalHapticFeedback.current
	val textColor = if (isDark) Color.White.copy(alpha = 0.85f) else Color(0xFF121213).copy(alpha = 0.85f)
	val backgroundGradient = if (isDark) {
		listOf(
			Color.White.copy(alpha = 0.22f),
			Color.Transparent
		)
	} else {
		listOf(
			Color(0xFFC7C8D1).copy(alpha = 0.75f),
			Color.Transparent
		)
	}
	val borderGradient = if (isDark) {
		listOf(
			Color.White.copy(alpha = 0.14f),
			Color.Transparent
		)
	} else {
		listOf(
			Color(0xFF000000).copy(alpha = 0.12f),
			Color.Transparent
		)
	}

	val annotatedReplyText = remember(replyText, textColor) {
		buildFlowAnnotatedText(
			text = replyText,
			linkColor = textColor,
			isSpoilerRevealed = false
		)
	}

	Box(
		modifier = modifier
			.height(31.dp)
			.defaultMinSize(minWidth = 51.dp)
			.clip(RoundedCornerShape(9999.dp))
			.background(
				brush = Brush.verticalGradient(backgroundGradient)
			)
			.border(
				width = 1.dp,
				brush = Brush.verticalGradient(borderGradient),
				shape = RoundedCornerShape(9999.dp)
			)
			.clickable(enabled = enabled) {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			}
			.padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
		contentAlignment = Alignment.Center
	) {
		TextWrap(
			text = annotatedReplyText,
			color = textColor,
			style = TextStyle(
				fontFamily = FontFamily.Default,
				fontWeight = FontWeight.Normal,
				fontSize = 12.sp,
			),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			isSpoilerRevealed = false,
			onRevealSpoiler = null,
			onTextClick = {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
			onUrlClick = {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
			onProfileTagClick = {
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
			onPostLinkClick = { _, _ ->
				haptic.performHapticFeedback(HapticFeedbackType.LongPress)
				onClick()
			},
		)
	}
}

@Composable
internal fun ReplyContent(
	replyMessageText: String,
	color: Color,
	mirrored: Boolean = false,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically
	) {
		ChatReplyIcon(
			tint = color,
			mirrored = mirrored,
			modifier = Modifier
		)

		Spacer(Modifier.width(6.dp))

		Text(
			text = replyMessageText,
			color = color,
			style = LocalTypography.current.bodyMedium.copy(fontSize = 16.sp),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
		)
	}
}

internal fun parseChatMessageMarkdown(text: String, isSpoilerRevealed: Boolean = false): AnnotatedString {
	return buildFlowAnnotatedText(text, isSpoilerRevealed = isSpoilerRevealed)
}
