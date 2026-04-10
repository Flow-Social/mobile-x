package me.floow.shared.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.ic_delete
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.overlayHorizontalSwipeZone
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SharedDeleteConfirmationDialog(
	title: String,
	text: String,
	confirmText: String,
	cancelText: String,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit,
) {
	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(title) },
		text = { Text(text) },
		confirmButton = {
			TextButton(onClick = onConfirm) {
				Text(confirmText)
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(cancelText)
			}
		},
	)
}

@Composable
internal fun SharedPinnedMessagesBar(
	pinnedMessages: List<ChatMessage>,
	pinnedLabel: String,
	unpinContentDescription: String,
	onMessageClick: (ChatMessage) -> Unit,
	onUnpinClick: (ChatMessage) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (pinnedMessages.isEmpty()) return
	val pinnedRowState = rememberLazyListState()
	val pinnedRowAtStart =
		pinnedRowState.firstVisibleItemIndex == 0 &&
			pinnedRowState.firstVisibleItemScrollOffset == 0
	val pinnedZoneKey = remember { "shared_chat_pinned_messages_row_zone" }

	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface),
	) {
		LazyRow(
			state = pinnedRowState,
			modifier = Modifier
				.fillMaxWidth()
				.padding(vertical = 8.dp)
				.testTag("chat_pinned_messages_row")
				.overlayHorizontalSwipeZone(
					zoneKey = pinnedZoneKey,
					atStart = pinnedRowAtStart,
				),
			contentPadding = PaddingValues(horizontal = 16.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			items(
				items = pinnedMessages,
				key = ChatMessage::id,
			) { message ->
				SharedPinnedMessageItem(
					message = message,
					pinnedLabel = pinnedLabel,
					unpinContentDescription = unpinContentDescription,
					onClick = { onMessageClick(message) },
					onUnpin = { onUnpinClick(message) },
				)
			}
		}
		HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
	}
}

@Composable
private fun SharedPinnedMessageItem(
	message: ChatMessage,
	pinnedLabel: String,
	unpinContentDescription: String,
	onClick: () -> Unit,
	onUnpin: () -> Unit,
) {
	Row(
		modifier = Modifier
			.width(200.dp)
			.clip(RoundedCornerShape(8.dp))
			.background(MaterialTheme.colorScheme.surfaceContainerHigh)
			.clickable(onClick = onClick)
			.padding(8.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = pinnedLabel,
				style = LocalTypography.current.labelMedium,
				color = MaterialTheme.colorScheme.primary,
			)
			Spacer(modifier = Modifier.height(2.dp))
			Text(
				text = message.messageText,
				style = LocalTypography.current.bodyMedium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Spacer(modifier = Modifier.width(8.dp))
		IconButton(
			onClick = onUnpin,
			modifier = Modifier.size(24.dp),
		) {
			Icon(
				painter = painterResource(Res.drawable.ic_delete),
				contentDescription = unpinContentDescription,
				tint = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}
