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
import androidx.compose.ui.graphics.graphicsLayer
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
	showTail: Boolean = true,
	showTimestamp: Boolean = true,
	onProfileTagClick: ((String) -> Unit)? = null,
	onPostLinkClick: ((String, String) -> Unit)? = null,
	onUrlClick: ((String) -> Unit)? = null,
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
			showTail = showTail,
			showTimestamp = showTimestamp,
			onProfileTagClick = onProfileTagClick,
			onPostLinkClick = onPostLinkClick,
			onUrlClick = onUrlClick,
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
					val replyProgress = (kotlin.math.abs(state.currentValue) / kotlin.math.abs(state.replyOffset).coerceAtLeast(1f)).coerceIn(0f, 1f)
					if (replyProgress > 0.05f) {
						ReplyMarker(
							modifier = Modifier.graphicsLayer {
								scaleX = 0.5f + 0.5f * replyProgress
								scaleY = 0.5f + 0.5f * replyProgress
								alpha = (replyProgress * 1.6f).coerceIn(0f, 1f)
								rotationZ = (1f - replyProgress) * -45f
								translationX = (1f - replyProgress) * 20f
							}
						)
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
