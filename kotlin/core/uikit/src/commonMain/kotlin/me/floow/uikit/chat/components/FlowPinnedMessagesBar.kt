package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.theme.LocalTypography

@Composable
fun FlowPinnedMessagesBar(
	pinnedMessages: List<ChatMessage>,
	onMessageClick: (messageId: String) -> Unit,
	onUnpinClick: (messageId: String) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (pinnedMessages.isEmpty()) return

	var currentIndex by remember(pinnedMessages) { mutableStateOf(0) }
	val safeIndex = if (pinnedMessages.isNotEmpty()) {
		(currentIndex % pinnedMessages.size + pinnedMessages.size) % pinnedMessages.size
	} else {
		0
	}
	val currentMessage = pinnedMessages[safeIndex]

	Column(
		modifier = modifier
			.fillMaxWidth()
			.height(44.dp)
			.background(MaterialTheme.colorScheme.surface)
	) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
				.padding(start = 12.dp, end = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			// 3dp accent indicator (primary color)
			Box(
				modifier = Modifier
					.width(3.dp)
					.height(32.dp)
					.clip(RoundedCornerShape(1.5.dp))
					.background(MaterialTheme.colorScheme.primary)
			)

			Spacer(Modifier.width(10.dp))

			// Center content with vertical slide animation
			AnimatedContent(
				targetState = currentMessage,
				transitionSpec = {
					(slideInVertically { height -> height } + fadeIn())
						.togetherWith(slideOutVertically { height -> -height } + fadeOut())
				},
				modifier = Modifier
					.weight(1f)
					.fillMaxHeight()
					.clickable {
						onMessageClick(currentMessage.id.toString())
						if (pinnedMessages.size > 1) {
							currentIndex = (safeIndex + 1) % pinnedMessages.size
						}
					},
				label = "PinnedMessageSlide",
			) { msg ->
				val title = when {
					pinnedMessages.size > 1 -> "Pinned message #${safeIndex + 1}"
					!msg.authorName.isNullOrBlank() -> msg.authorName ?: "Pinned message"
					!msg.authorUsername.isNullOrBlank() -> "@${msg.authorUsername}"
					else -> "Pinned message"
				}

				Column(
					modifier = Modifier
						.fillMaxWidth()
						.fillMaxHeight(),
					verticalArrangement = Arrangement.Center,
				) {
					Text(
						text = title,
						style = LocalTypography.current.labelMedium.copy(
							fontSize = 12.sp,
							fontWeight = FontWeight.SemiBold,
						),
						color = MaterialTheme.colorScheme.primary,
						maxLines = 1,
						overflow = TextOverflow.Ellipsis,
					)

					PinnedMessagePreview(message = msg)
				}
			}

			// Unpin button
			IconButton(
				onClick = { onUnpinClick(currentMessage.id.toString()) },
				modifier = Modifier.size(36.dp),
			) {
				Icon(
					imageVector = Icons.Default.Close,
					contentDescription = "Unpin",
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(18.dp),
				)
			}
		}

		HorizontalDivider(
			thickness = 0.5.dp,
			color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
		)
	}
}

@Composable
private fun PinnedMessagePreview(message: ChatMessage) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(4.dp),
		modifier = Modifier.fillMaxWidth()
	) {
		when (message) {
			is ImageMessage -> {
				Icon(
					imageVector = Icons.Outlined.Image,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(14.dp)
				)
				Text(
					text = message.messageText.ifBlank {
						if (message.images.size > 1) "Photos (${message.images.size})" else "Photo"
					},
					style = LocalTypography.current.bodyMedium.copy(fontSize = 13.sp),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			is VoiceMessage -> {
				Icon(
					imageVector = Icons.Outlined.Audiotrack,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(14.dp)
				)
				Text(
					text = "Voice message",
					style = LocalTypography.current.bodyMedium.copy(fontSize = 13.sp),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			is VideoCircleOutMessage -> {
				Icon(
					imageVector = Icons.Outlined.Videocam,
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.size(14.dp)
				)
				Text(
					text = "Video message",
					style = LocalTypography.current.bodyMedium.copy(fontSize = 13.sp),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
			else -> {
				Text(
					text = message.messageText.ifBlank { "Message" },
					style = LocalTypography.current.bodyMedium.copy(fontSize = 13.sp),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}
