package me.floow.uikit.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.currentChatTimeMillis
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.ComponentPreviewBox
import me.floow.uikit.util.overlayHorizontalSwipeZone

@Composable
fun PinnedMessagesBar(
    pinnedMessages: List<ChatMessage>,
    onMessageClick: (ChatMessage) -> Unit,
    onUnpinClick: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pinnedMessages.isEmpty()) return
    val pinnedRowState = rememberLazyListState()
    val pinnedRowAtStart =
        pinnedRowState.firstVisibleItemIndex == 0 &&
            pinnedRowState.firstVisibleItemScrollOffset == 0
    val pinnedZoneKey = remember { "chat_pinned_messages_row_zone" }

    Column(modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        LazyRow(
            state = pinnedRowState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("chat_pinned_messages_row")
                .overlayHorizontalSwipeZone(
                    zoneKey = pinnedZoneKey,
                    atStart = pinnedRowAtStart
                ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pinnedMessages) { message ->
                PinnedMessageItem(
                    message = message,
                    onClick = { onMessageClick(message) },
                    onUnpin = { onUnpinClick(message) }
                )
            }
        }
        Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(MaterialTheme.colorScheme.outlineVariant))
    }
}

@Composable
private fun PinnedMessageItem(
    message: ChatMessage,
    onClick: () -> Unit,
    onUnpin: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Pinned Message",
                style = LocalTypography.current.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message.messageText,
                style = LocalTypography.current.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(me.floow.uikit.R.drawable.ic_delete),
            contentDescription = "Unpin",
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onUnpin),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview
@Composable
private fun PinnedMessagesBarPreview() {
	ComponentPreviewBox {
		PinnedMessagesBar(
			pinnedMessages = listOf(
				PrimaryOutMessage(
					id = 1L,
					messageText = "Pinned message one",
					createdAtMillis = currentChatTimeMillis()
				),
				PrimaryOutMessage(
					id = 2L,
					messageText = "Pinned message two",
					createdAtMillis = currentChatTimeMillis()
				)
			),
			onMessageClick = {},
			onUnpinClick = {}
		)
	}
}
