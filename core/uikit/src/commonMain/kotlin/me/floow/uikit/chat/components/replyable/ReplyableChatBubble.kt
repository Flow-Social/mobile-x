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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.floow.uikit.chat.components.ChatBubble
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage

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
	ReplyableChatContent(
		chatMessage = chatMessage,
		onReply = onReply,
		modifier = modifier,
		contentModifier = if (isOut) Modifier.fillMaxWidth() else Modifier,
	) { contentModifier ->
		ChatBubble(
			chatMessage = chatMessage,
			onReplyClick = onReplyClick,
			onRetrySendClick = onRetrySendClick,
			isHighlighted = isHighlighted,
			showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
			showUnreadDot = showUnreadDot,
			showMessageStatus = showMessageStatus,
			showReplyPreview = showReplyPreview,
			modifier = contentModifier,
			bubbleBoundsModifier = bubbleBoundsModifier
		)
	}
}

@Composable
internal fun ReplyableChatContent(
	chatMessage: ChatMessage,
	onReply: (ChatMessage) -> Unit,
	modifier: Modifier = Modifier,
	contentModifier: Modifier = Modifier,
	content: @Composable (Modifier) -> Unit,
) {
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
			Box(modifier = Modifier.widthByBubbleType(chatMessage)) {
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
					modifier = Modifier.offset {
						IntOffset(x = state.requireOffset().roundToInt(), y = 0)
					},
				) {
					content(contentModifier)
				}
			}
		}
	}
}

private fun Modifier.widthByBubbleType(chatMessage: ChatMessage): Modifier {
	return when (chatMessage) {
		is PrimaryOutMessage, is ReplyOutMessage -> this.then(Modifier.fillMaxWidth())
		is me.floow.uikit.chat.model.VideoCircleOutMessage -> this.then(Modifier.fillMaxWidth())
		else -> this
	}
}
