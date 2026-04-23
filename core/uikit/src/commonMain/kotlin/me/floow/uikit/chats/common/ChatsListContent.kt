package me.floow.uikit.chats.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.LocalTypography

enum class ChatsListVisualType {
	Regular,
	RepliesInbox,
	SavedMessages,
}

enum class ChatsListDeliveryStatus {
	Sent,
	Read,
}

data class ChatsListItemUiModel(
	val id: String,
	val title: String,
	val previewText: String,
	val timeLabel: String,
	val unreadCount: Int,
	val isMuted: Boolean,
	val isOnline: Boolean,
	val avatarUrl: String?,
	val hasAttachmentPreview: Boolean,
	val deliveryStatus: ChatsListDeliveryStatus?,
	val visualType: ChatsListVisualType,
)

@Composable
fun ChatsListContent(
	items: List<ChatsListItemUiModel>,
	repliesInboxAvatar: Painter,
	mutedIcon: Painter,
	sentIcon: Painter,
	readIcon: Painter,
	onChatClick: (ChatsListItemUiModel) -> Unit,
	modifier: Modifier = Modifier,
) {
	LazyColumn(
		modifier = modifier,
	) {
		item { Spacer(Modifier.height(8.dp)) }
		items(
			items = items,
			key = ChatsListItemUiModel::id,
		) { chat ->
			ChatListItem(
				chat = chat,
				repliesInboxAvatar = repliesInboxAvatar,
				mutedIcon = mutedIcon,
				sentIcon = sentIcon,
				readIcon = readIcon,
				onClick = onChatClick,
				modifier = Modifier.fillMaxWidth(),
			)
		}
	}
}

@Composable
private fun ChatListItem(
	chat: ChatsListItemUiModel,
	repliesInboxAvatar: Painter,
	mutedIcon: Painter,
	sentIcon: Painter,
	readIcon: Painter,
	onClick: (ChatsListItemUiModel) -> Unit,
	modifier: Modifier = Modifier,
) {
	val showUnreadBadge = chat.unreadCount > 0
	val showDeliveryStatusIcon = !showUnreadBadge && chat.deliveryStatus != null

	Row(
		modifier = modifier
			.clickable { onClick(chat) }
			.padding(horizontal = 14.dp, vertical = 8.dp)
			.height(62.dp),
		verticalAlignment = Alignment.CenterVertically,
	) {
		AvatarBox(
			name = chat.title,
			avatarUrl = chat.avatarUrl,
			visualType = chat.visualType,
			isOnline = chat.isOnline,
			repliesInboxAvatar = repliesInboxAvatar,
		)

		Spacer(Modifier.width(12.dp))

		Column(modifier = Modifier.weight(1f)) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Text(
					text = chat.title,
					modifier = Modifier.weight(1f),
					style = LocalTypography.current.titleMedium.copy(
						fontSize = 18.sp,
						fontWeight = FontWeight.Medium,
					),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)

				if (chat.isMuted) {
					Spacer(Modifier.width(4.dp))
					Icon(
						painter = mutedIcon,
						contentDescription = null,
						tint = Color.Unspecified,
					)
				}

					if (showDeliveryStatusIcon) {
						Icon(
							painter = when (chat.deliveryStatus) {
								ChatsListDeliveryStatus.Sent -> sentIcon
								ChatsListDeliveryStatus.Read -> readIcon
							},
							contentDescription = null,
							tint = Color.Unspecified,
						)
					Spacer(Modifier.width(4.dp))
				}

				Text(
					text = chat.timeLabel,
					style = LocalTypography.current.captionMedium.copy(fontWeight = FontWeight.Medium),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)
			}

			Spacer(Modifier.height(4.dp))

			Row(verticalAlignment = Alignment.CenterVertically) {
				if (chat.hasAttachmentPreview) {
					Box(
						Modifier
							.clip(RoundedCornerShape(4.dp))
							.background(Color.LightGray)
							.size(18.dp),
					)
					Spacer(Modifier.width(6.dp))
				}

				Text(
					text = chat.previewText,
					modifier = Modifier.weight(1f),
					style = LocalTypography.current.bodyMedium.copy(fontSize = 16.sp),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
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
		contentAlignment = Alignment.Center,
	) {
		Text(
			text = text,
			style = LocalTypography.current.labelMedium.copy(fontWeight = FontWeight.Medium),
			color = MaterialTheme.colorScheme.onPrimary,
		)
	}
}

@Composable
private fun AvatarBox(
	name: String,
	avatarUrl: String?,
	visualType: ChatsListVisualType,
	isOnline: Boolean,
	repliesInboxAvatar: Painter,
) {
	Box {
		when (visualType) {
			ChatsListVisualType.SavedMessages -> {
				Box(
					modifier = Modifier
						.size(52.dp)
						.clip(CircleShape)
						.background(MaterialTheme.colorScheme.primary),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						imageVector = Icons.Default.Bookmark,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onPrimary,
						modifier = Modifier.size(26.dp),
					)
				}
			}

			ChatsListVisualType.RepliesInbox -> {
				Image(
					painter = repliesInboxAvatar,
					contentDescription = null,
					modifier = Modifier
						.size(52.dp)
						.clip(CircleShape),
				)
			}

			ChatsListVisualType.Regular -> {
				NetworkAvatar(
					name = name,
					avatarModel = avatarUrl,
					size = 52.dp,
					modifier = Modifier,
					shape = CircleShape,
				)
			}
		}

		if (isOnline && visualType == ChatsListVisualType.Regular) {
			Box(
				Modifier
					.clip(CircleShape)
					.background(MaterialTheme.colorScheme.background)
					.padding(3.dp)
					.clip(CircleShape)
					.size(12.dp)
					.background(Color(0xFF5ACD30))
					.align(Alignment.BottomEnd),
			)
		}
	}
}
