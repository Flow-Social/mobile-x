package me.floow.uikit.chat.components.replyable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ChatBubble
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.util.ComponentPreviewBox
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
internal fun ReplyableChatBubble(
	chatMessage: ChatMessage,
	onReplyClick: (ChatMessage) -> Unit,
	onReply: (ChatMessage) -> Unit,
	onRetrySendClick: ((ChatMessage) -> Unit)? = null,
	isHighlighted: Boolean = false,
	showAuthorHeaderForInMessages: Boolean = false,
	showUnreadDot: Boolean = false,
	showMessageStatus: Boolean = true,
	showReplyPreview: Boolean = true,
	modifier: Modifier = Modifier,
	bubbleBoundsModifier: Modifier = Modifier
) {
	val isOut = chatMessage is PrimaryOutMessage || chatMessage is ReplyOutMessage
	val currentViewConfiguration = LocalViewConfiguration.current
	val density = LocalDensity.current
	val state = remember {
		CustomReplyAnchoredDraggableState(
			initialValue = 0f,
			replyOffset = with(density) { -40.dp.toPx() }
		)
	}

	val coroutineScope = rememberCoroutineScope()

	CompositionLocalProvider(touchSlopConfiguration(currentViewConfiguration)) {
		Box(
			modifier = modifier
				.fillMaxWidth()
				.replyDraggable(
					state = state,
					coroutineScope = coroutineScope,
					onReply = { onReply(chatMessage) }
				)
		) {
			Box(
				modifier = Modifier
					.widthByBubbleType(chatMessage)
			) {
				Box(Modifier.align(Alignment.TopEnd)) {
					AnimatedVisibility(
						visible = state.currentValue != 0f,
						enter = scaleIn(tween(300)),
						exit = scaleOut(tween(300))
					) {
						ReplyMarker()
					}
				}

				Box(
					modifier = Modifier
						.offset {
							IntOffset(
								x = state
									.requireOffset()
									.roundToInt(),
								y = 0
							)
						},
				) {
					ChatBubble(
						chatMessage = chatMessage,
						onReplyClick = onReplyClick,
						onRetrySendClick = onRetrySendClick,
						isHighlighted = isHighlighted,
						showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
						showUnreadDot = showUnreadDot,
						showMessageStatus = showMessageStatus,
						showReplyPreview = showReplyPreview,
						modifier = if (isOut) Modifier.fillMaxWidth() else Modifier,
						bubbleBoundsModifier = bubbleBoundsModifier
					)
				}
			}
		}
	}
}

private fun Modifier.widthByBubbleType(chatMessage: ChatMessage): Modifier {
	return when (chatMessage) {
		is PrimaryOutMessage, is ReplyOutMessage -> {
			this.then(
				Modifier.fillMaxWidth()
			)
		}

		else -> this
	}
}

@Preview
@Composable
private fun ReplyableChatBubblePreview() {
	ComponentPreviewBox(Modifier.fillMaxWidth()) {
		ReplyableChatBubble(
			chatMessage = PrimaryOutMessage(
				id = 100L,
				messageText = "Some awesome!!! Message. See you later.. probably",
				dateTime = LocalDateTime.now(),
			),
			onReplyClick = {},
			onReply = {
				println("REPLY !!!")
				println("REPLY !!!")
				println("REPLY !!!")
			},
			modifier = Modifier
		)
	}
}
