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

import me.floow.uikit.chat.components.ChatBubbleOption

@Composable
internal fun ReplyableChatBubble(
	chatMessage: ChatMessage,
	onClick: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onReply: (ChatMessage) -> Unit,
	isHighlighted: Boolean = false,
	onOptionClick: ((ChatBubbleOption, ChatMessage) -> Unit)? = null,
	showAuthorHeaderForInMessages: Boolean = false,
	showPinAction: Boolean = true,
	showReplyPreview: Boolean = true,
	modifier: Modifier = Modifier
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
						onClick = onClick,
						onReplyClick = onReplyClick,
						isHighlighted = isHighlighted,
						onOptionClick = onOptionClick,
						showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
						showPinAction = showPinAction,
						showReplyPreview = showReplyPreview,
						modifier = if (isOut) Modifier.fillMaxWidth() else Modifier
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
			onClick = {},
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
