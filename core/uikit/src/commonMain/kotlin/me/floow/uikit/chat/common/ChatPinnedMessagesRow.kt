package me.floow.uikit.chat.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class ChatPinnedMessageUiModel(
	val id: Long,
	val text: String,
)

@Composable
fun ChatPinnedMessagesRow(
	messages: List<ChatPinnedMessageUiModel>,
	onMessageClick: (Long) -> Unit,
	onUnpinClick: (Long) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (messages.isEmpty()) return
	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface),
	) {
		LazyRow(
			modifier = Modifier
				.fillMaxWidth()
				.padding(vertical = 8.dp),
			contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			items(messages, key = ChatPinnedMessageUiModel::id) { message ->
				Surface(
					color = MaterialTheme.colorScheme.surfaceContainerHigh,
					shape = RoundedCornerShape(10.dp),
					modifier = Modifier.width(220.dp),
					onClick = { onMessageClick(message.id) },
				) {
					Row(
						modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Icon(
							imageVector = Icons.Default.PushPin,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.primary,
						)
						Spacer(Modifier.width(8.dp))
						Column(modifier = Modifier.weight(1f)) {
							Text(
								text = "Закрепленное",
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.primary,
							)
							Text(
								text = message.text,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis,
								style = MaterialTheme.typography.bodySmall,
							)
						}
						Spacer(Modifier.width(8.dp))
						Text(
							text = "Снять",
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							style = MaterialTheme.typography.labelSmall,
							modifier = Modifier.clickable { onUnpinClick(message.id) },
						)
					}
				}
			}
		}
		Spacer(
			modifier = Modifier
				.height(1.dp)
				.fillMaxWidth()
				.background(MaterialTheme.colorScheme.outlineVariant),
		)
	}
}
