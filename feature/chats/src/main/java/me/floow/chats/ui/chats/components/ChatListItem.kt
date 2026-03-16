package me.floow.chats.ui.chats.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.chats.uilogic.chats.Chat
import me.floow.chats.uilogic.chats.ChatType
import me.floow.chats.uilogic.chats.LastSentMessageState
import me.floow.chats.uilogic.chats.isRepliesInboxChat
import me.floow.chats.uilogic.chats.isSavedMessages
import me.floow.domain.values.ProfileName
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.util.ComponentPreviewBox
import me.flowme.chats.R
import me.floow.domain.utils.toLocalDateTimeFromEpochMillis
import java.time.format.DateTimeFormatter

private const val DIALOG_CREATED_TEXT = "Диалог создан"
private const val OUTGOING_PREFIX = "Вы: "
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun ChatListItem(
	chat: Chat,
	onClick: (Chat) -> Unit,
	modifier: Modifier = Modifier
) {
	val showUnreadBadge = chat.unreadCount > 0
	val showDeliveryStatusIcon = !showUnreadBadge && chat.lastSentMessageState != null
	val previewText = if (
		chat.lastSentMessageState != null &&
		!chat.isRepliesInboxChat() &&
		chat.lastMessageText != DIALOG_CREATED_TEXT
	) {
		"$OUTGOING_PREFIX${chat.lastMessageText}"
	} else {
		chat.lastMessageText
	}

	Row(
		modifier = modifier
			.clickable { onClick(chat) }
			.padding(horizontal = 14.dp, vertical = 8.dp)
			.height(62.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		AvatarBox(
			name = chat.name.value,
			avatarUrl = chat.avatarUrl,
			isRepliesInbox = chat.isRepliesInboxChat(),
			isSavedMessages = chat.isSavedMessages(),
			isOnline = chat.isOnline,
			modifier = Modifier
		)

		Spacer(Modifier.width(12.dp))

		Column(
			modifier = Modifier.weight(1f)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = chat.name.value,
					modifier = Modifier.weight(1f),
					style = LocalTypography.current.titleMedium.copy(
						fontSize = 18.sp,
						fontWeight = FontWeight.Medium
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)

				if (chat.chatMuted) {
					Spacer(Modifier.width(4.dp))

					Icon(
						painter = painterResource(R.drawable.chat_muted_icon),
						contentDescription = null,
						tint = Color.Unspecified
					)
				}

				if (showDeliveryStatusIcon) {
					when (chat.lastSentMessageState) {
						LastSentMessageState.Sent -> {
							Icon(
								painter = painterResource(R.drawable.chat_sent_icon),
								contentDescription = null,
								tint = Color.Unspecified
							)
						}

						LastSentMessageState.Read -> {
							Icon(
								painter = painterResource(R.drawable.chat_read_icon),
								contentDescription = null,
								tint = Color.Unspecified
							)
						}
					}
					Spacer(Modifier.width(4.dp))
				}

				val timeLabel = chat.lastMessageTimeMillis
					.takeIf { it > 0L }
					?.toLocalDateTimeFromEpochMillis()
					?.format(TIME_FORMATTER)
					.orEmpty()
				Text(
					text = timeLabel,
					style = LocalTypography.current.captionMedium.copy(fontWeight = FontWeight.Medium),
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			Spacer(Modifier.height(4.dp))

			Row(
				modifier = Modifier,
				verticalAlignment = Alignment.CenterVertically
			) {
				if (chat.attachedMediaUrl != null) {
					Box(
						Modifier
							.clip(RoundedCornerShape(4.dp))
							.background(Color.LightGray)
							.size(18.dp)
					)

					Spacer(Modifier.width(6.dp))
				}

				Text(
					text = previewText,
					modifier = Modifier.weight(1f),
					style = LocalTypography.current.bodyMedium.copy(fontSize = 16.sp),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)

				if (showUnreadBadge) {
					Spacer(Modifier.width(8.dp))
					UnreadBadge(chat.unreadCount)
				}
			}
		}
	}
}

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
	val text = if (count > 99) "99+" else count.toString()
	Box(
		modifier = modifier
			.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
			.clip(CircleShape)
			.background(MaterialTheme.colorScheme.primary)
			.padding(horizontal = 10.dp, vertical = 4.dp),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = text,
			style = LocalTypography.current.labelMedium.copy(fontWeight = FontWeight.Medium),
			color = MaterialTheme.colorScheme.onPrimary
		)
	}
}

@Composable
private fun AvatarBox(
	name: String,
	avatarUrl: String?,
	isRepliesInbox: Boolean,
	isSavedMessages: Boolean,
	isOnline: Boolean,
	modifier: Modifier.Companion,
) {
	Box(modifier = modifier) {
		if (isSavedMessages) {
			Box(
				modifier = Modifier
					.size(52.dp)
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.primary),
				contentAlignment = Alignment.Center,
			) {
				Icon(
					painter = painterResource(me.floow.uikit.R.drawable.bookmark_icon),
					contentDescription = null,
					tint = MaterialTheme.colorScheme.onPrimary,
					modifier = Modifier.size(26.dp),
				)
			}
		} else if (isRepliesInbox) {
			Image(
				painter = painterResource(R.drawable.replyplz),
				contentDescription = null,
				modifier = Modifier
					.size(52.dp)
					.clip(CircleShape)
			)
		} else {
			NetworkAvatar(
				name = name,
				avatarModel = avatarUrl,
				size = 52.dp,
				modifier = Modifier,
				shape = CircleShape
			)
		}

		if (isOnline) {
			Box(
				Modifier
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.background)
					.padding(3.dp)
					.clip(CircleShape)
					.size(12.dp)
					.background(Color(0xFF5ACD30))
					.align(Alignment.BottomEnd)
			)
		}
	}
}

@Preview
@Composable
private fun ChatListItemPreview() {
	ComponentPreviewBox(Modifier.fillMaxSize()) {
		ChatListItem(
			chat = Chat(
				id = "2",
				conversationId = 2L,
				type = ChatType.DIRECT,
				name = ProfileName.create("Demn"),
				lastMessageText = "Some message text idk",
				isOnline = true,
				lastMessageTimeMillis = System.currentTimeMillis() - 3 * 60 * 60 * 1000L,
				avatarUrl = null,
				attachedMediaUrl = null,
				chatMuted = true,
				unreadCount = 3,
				lastSentMessageState = LastSentMessageState.Read
			),
			onClick = {},
			modifier = Modifier.fillMaxWidth()
		)
	}
}
