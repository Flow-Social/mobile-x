package me.floow.uikit.chat.components

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import me.floow.uikit.components.misc.textwrap.TextWrap
import me.floow.uikit.components.misc.textwrap.TextWrapObstacleAlignment
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.R
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.ComponentPreviewBox
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date

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
				backgroundColor = MaterialTheme.colorScheme.inversePrimary,
				borderColor = Color(0xFFBEBEBE),
				textColor = MaterialTheme.colorScheme.onBackground,
				timeColor = MaterialTheme.colorScheme.onBackground,
				replyColor = Color(0xFFBEBEBE),
			)

		val Outlined: ChatBubbleColors
			@Composable get() = ChatBubbleColors(
				backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
				borderColor = Color.Transparent,
				textColor = MaterialTheme.colorScheme.onBackground,
				timeColor = Color(0xFFBEBEBE),
				replyColor = Color(0xFFBEBEBE),
			)
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
	chatMessage: ChatMessage,
	onClick: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	isHighlighted: Boolean = false,
	onOptionClick: ((ChatBubbleOption, ChatMessage) -> Unit)? = null,
	showAuthorHeaderForInMessages: Boolean = false,
	showPinAction: Boolean = true,
	showReplyPreview: Boolean = true,
	modifier: Modifier = Modifier
) {
	val isOut: Boolean = chatMessage is PrimaryOutMessage || chatMessage is ReplyOutMessage
	val colors = if (!isOut) ChatBubbleColors.Outlined else ChatBubbleColors.Default
	val haptic = LocalHapticFeedback.current
	val context = LocalContext.current
	val authorLabel = when {
		chatMessage.authorName?.isNotBlank() == true -> chatMessage.authorName
		chatMessage.authorUsername?.isNotBlank() == true -> "@${chatMessage.authorUsername}"
		else -> null
	}
	val formattedMessageTime = remember(chatMessage.dateTime, context) {
		val messageInstant = chatMessage.dateTime
			.atZone(ZoneId.systemDefault())
			.toInstant()
		DateFormat.getTimeFormat(context).format(Date.from(messageInstant))
	}

	val backgroundColor by animateColorAsState(
		targetValue = if (isHighlighted) MaterialTheme.colorScheme.tertiaryContainer else colors.backgroundColor,
		animationSpec = tween(durationMillis = 500),
		label = "BubbleHighlight"
	)

	var showMenu by remember { mutableStateOf(false) }

	Column(
		horizontalAlignment = if (isOut) Alignment.End else Alignment.Start,
		modifier = modifier,
	) {
		Column(
			modifier = Modifier
				.clip(RoundedCornerShape(20.dp))
				.background(backgroundColor)
				.addBorderIfIn(isOut, colors)
				.clickable {
					showMenu = onOptionClick != null
					onClick(chatMessage)
				}
				.padding(vertical = 8.dp, horizontal = 10.dp)
				.widthIn(4.dp, 324.dp)
		) {
			if (showAuthorHeaderForInMessages && !isOut && !authorLabel.isNullOrBlank()) {
				Text(
					text = authorLabel,
					style = LocalTypography.current.labelMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Spacer(Modifier.height(4.dp))
			}

			TextWrap(
				text = chatMessage.messageText,
				color = colors.textColor,
				style = LocalTypography.current.bodyMedium,
				forcedObstacleOffset = IntOffset(0, 6),
				obstacleAlignment = TextWrapObstacleAlignment.BottomEnd,
				obstacleContent = {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.padding(start = 6.dp)
					) {
						if (chatMessage.isPinned) {
							Icon(
								painter = painterResource(R.drawable.notification_bell),
								contentDescription = stringResource(R.string.chat_pinned),
								tint = colors.timeColor,
								modifier = Modifier
									.height(12.dp)
									.padding(end = 4.dp)
							)
						}
						Text(
							text = formattedMessageTime,
							color = colors.timeColor,
							style = LocalTypography.current.labelMedium,
							textAlign = TextAlign.End,
						)
					}
				}
			)

			if (onOptionClick != null) {
				DropdownMenu(
					expanded = showMenu,
					onDismissRequest = { showMenu = false }
				) {
					if (showPinAction) {
						DropdownMenuItem(
							text = {
								Text(
									if (chatMessage.isPinned) {
										stringResource(R.string.chat_menu_unpin)
									} else {
										stringResource(R.string.chat_menu_pin)
									}
								)
							},
							onClick = {
								onOptionClick(ChatBubbleOption.Pin, chatMessage)
								showMenu = false
							}
						)
					}
					if (isOut) {
						DropdownMenuItem(
							text = { Text(stringResource(R.string.chat_menu_edit)) },
							onClick = {
								onOptionClick(ChatBubbleOption.Edit, chatMessage)
								showMenu = false
							},
							leadingIcon = { Icon(painterResource(R.drawable.edit_icon), null) }
						)
						DropdownMenuItem(
							text = { Text(stringResource(R.string.chat_menu_delete)) },
							onClick = {
								onOptionClick(ChatBubbleOption.Delete, chatMessage)
								showMenu = false
							},
							leadingIcon = { Icon(painterResource(R.drawable.ic_delete), null) }
						)
					}
				}
			}
		}

		if (showReplyPreview && chatMessage is ChatReplyMessage) {
			Spacer(Modifier.height(3.dp))

			Row(
				horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
				modifier = Modifier
					.widthIn(74.dp, 324.dp)
			) {
				ReplyContent(
					replyMessageText = chatMessage.replyMessageText,
					color = colors.replyColor,
					modifier = Modifier
						.height(25.dp)
						.clickable {
							haptic.performHapticFeedback(HapticFeedbackType.LongPress)
							onReplyClick(chatMessage)
						}
				)
			}
		}
	}
}

enum class ChatBubbleOption {
	Pin, Edit, Delete
}

private fun Modifier.addBorderIfIn(out: Boolean, colors: ChatBubbleColors): Modifier {
	return if (out) this
	else this.then(Modifier.border(1.dp, colors.borderColor, RoundedCornerShape(20.dp)))
}

@Composable
internal fun ReplyContent(
	replyMessageText: String,
	color: Color,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(
			painter = painterResource(R.drawable.reply_out_icon),
			contentDescription = null,
			tint = color,
		)

		Spacer(Modifier.width(6.dp))

		Text(
			text = replyMessageText,
			color = color,
			style = LocalTypography.current.bodyMedium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			modifier = Modifier
		)
	}
}

@Preview
@Composable
fun ChatBubblePreview_OutReply() {
	val mockMessage = ReplyOutMessage(
		id = 1L,
		messageText = "This is a reply out message",
		dateTime = LocalDateTime.now(),
		replyMessageId = 2L,
		replyMessageText = "This is the message being replied to"
	)

	ComponentPreviewBox(Modifier.fillMaxWidth()) {
		ChatBubble(
			chatMessage = mockMessage,
			onClick = {},
			onReplyClick = {}
		)
	}
}

@Preview
@Composable
fun ChatBubblePreview_InReply() {
	val mockMessage = ReplyInMessage(
		id = 1L,
		replyMessageId = 2L,
		replyMessageText = "This is the message being replied to",
		messageText = "This is a reply in message",
		dateTime = LocalDateTime.now()
	)

	ComponentPreviewBox(Modifier.fillMaxWidth()) {
		ChatBubble(
			chatMessage = mockMessage,
			onClick = {},
			onReplyClick = {}
		)
	}
}

@Preview
@Composable
fun ChatBubblePreview_OutPrimary() {
	val mockMessage = PrimaryOutMessage(
		id = 1L,
		messageText = "This is a primary out message",
		dateTime = LocalDateTime.now()
	)

	ComponentPreviewBox(Modifier.fillMaxWidth()) {
		ChatBubble(
			chatMessage = mockMessage,
			onClick = {},
			onReplyClick = {}
		)
	}
}

@Preview
@Composable
fun ChatBubblePreview_InPrimary() {
	val mockMessage = PrimaryInMessage(
		id = 1L,
		messageText = "This is a primary in message. By the way, This is a primary in message. Lorem ipsum dolor sit amet.. Yeahhh",
		dateTime = LocalDateTime.now()
	)

	ComponentPreviewBox(Modifier.fillMaxWidth()) {
		ChatBubble(
			chatMessage = mockMessage,
			onClick = {},
			onReplyClick = {}
		)
	}
}
