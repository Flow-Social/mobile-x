package me.floow.uikit.chat.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

enum class ChatBubbleDeliveryUiState {
	SENDING,
	SENT,
	READ,
	FAILED,
}

data class ChatMessageBubbleUiModel(
	val id: Long,
	val text: String,
	val timeLabel: String,
	val isOutgoing: Boolean,
	val isHighlighted: Boolean = false,
	val isPinned: Boolean = false,
	val isDeleted: Boolean = false,
	val isSelected: Boolean = false,
	val senderDisplayName: String? = null,
	val deliveryState: ChatBubbleDeliveryUiState? = null,
	val replyAuthorName: String? = null,
	val replyText: String? = null,
)

@Composable
fun ChatMessageBubble(
	message: ChatMessageBubbleUiModel,
	onClick: (Long) -> Unit,
	onLongClick: (Long) -> Unit,
	onMenuClick: (Long) -> Unit,
	showMenuButton: Boolean,
	modifier: Modifier = Modifier,
) {
	Column(
		modifier = modifier.fillMaxWidth(),
		horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
	) {
		Card(
			shape = RoundedCornerShape(
				topStart = 20.dp,
				topEnd = 20.dp,
				bottomStart = if (message.isOutgoing) 20.dp else 6.dp,
				bottomEnd = if (message.isOutgoing) 6.dp else 20.dp,
			),
			colors = CardDefaults.cardColors(
				containerColor = when {
					message.isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer
					message.isSelected -> MaterialTheme.colorScheme.secondaryContainer
					message.isOutgoing -> MaterialTheme.colorScheme.inversePrimary
					else -> MaterialTheme.colorScheme.surfaceVariant
				},
			),
			modifier = Modifier
				.widthIn(max = 324.dp)
				.then(
					if (message.isOutgoing) Modifier else {
						Modifier.border(
							width = 1.dp,
							color = Color.Transparent,
							shape = RoundedCornerShape(
								topStart = 20.dp,
								topEnd = 20.dp,
								bottomStart = 6.dp,
								bottomEnd = 20.dp,
							),
						)
					}
				)
				.combinedClickable(
					onClick = { onClick(message.id) },
					onLongClick = { onLongClick(message.id) },
				),
		) {
			Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
				if (message.isPinned) {
					Row(verticalAlignment = Alignment.CenterVertically) {
						Icon(
							imageVector = Icons.Default.PushPin,
							contentDescription = null,
							modifier = Modifier.size(14.dp),
							tint = MaterialTheme.colorScheme.primary,
						)
						Spacer(Modifier.width(6.dp))
						Text(
							text = "Закреплено",
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.primary,
						)
					}
					Spacer(Modifier.size(6.dp))
				}
				if (!message.isOutgoing && !message.senderDisplayName.isNullOrBlank()) {
					Text(
						text = message.senderDisplayName,
						style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
					Spacer(Modifier.size(4.dp))
				}
				Text(
					text = message.text,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurface,
				)
				Spacer(Modifier.size(6.dp))
				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.End,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = message.timeLabel,
						style = MaterialTheme.typography.labelSmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.End,
					)
					if (message.isOutgoing) {
						Spacer(Modifier.width(6.dp))
						Text(
							text = when (message.deliveryState) {
								ChatBubbleDeliveryUiState.READ -> "Прочитано"
								ChatBubbleDeliveryUiState.SENDING -> "..."
								ChatBubbleDeliveryUiState.FAILED -> "Ошибка"
								ChatBubbleDeliveryUiState.SENT, null -> "Отправлено"
							},
							style = MaterialTheme.typography.labelSmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}
			}
		}

		if (!message.replyAuthorName.isNullOrBlank() && !message.replyText.isNullOrBlank()) {
			Spacer(Modifier.size(3.dp))
			Row(
				horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
				modifier = Modifier.widthIn(max = 324.dp),
			) {
				ChatReplyPreview(
					authorName = message.replyAuthorName,
					text = message.replyText,
					isOutgoing = message.isOutgoing,
					modifier = Modifier
						.combinedClickable(
							onClick = { onClick(message.id) },
							onLongClick = { onLongClick(message.id) },
						),
				)
			}
		}
	}
}

@Composable
private fun ChatReplyPreview(
	authorName: String,
	text: String,
	isOutgoing: Boolean,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier
			.clip(RoundedCornerShape(12.dp))
			.background(Color.Transparent)
			.padding(horizontal = 2.dp, vertical = 2.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Box(
			modifier = Modifier
				.size(width = 3.dp, height = 28.dp)
				.clip(RoundedCornerShape(99.dp))
				.background(
					if (isOutgoing) MaterialTheme.colorScheme.outline
					else MaterialTheme.colorScheme.outlineVariant
				),
		)
		Spacer(Modifier.width(8.dp))
		Column {
			Text(
				text = authorName,
				style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				text = text,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
	}
}
