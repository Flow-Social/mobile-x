package me.floow.shared.chats.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.shared.chats.model.ChatDeliveryStatus
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.model.ConversationActivityState
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.theme.LocalTypography

@Composable
fun ChatsListContent(
	items: List<ChatListItemModel>,
	outgoingPrefix: String,
	repliesInboxAvatar: Painter,
	mutedIcon: Painter,
	sentIcon: Painter,
	readIcon: Painter,
	onChatClick: (ChatListItemModel) -> Unit,
	onChatLongClick: (ChatListItemModel) -> Unit = {},
	onLoadMore: () -> Unit = {},
	isLoadingMore: Boolean = false,
	hasMore: Boolean = false,
	loadMoreError: Boolean = false,
	modifier: Modifier = Modifier,
) {
	LazyColumn(
		modifier = modifier,
	) {
		item { Spacer(Modifier.height(8.dp)) }
		items(
			items = items,
			key = ChatListItemModel::id,
		) { chat ->
			ChatListItem(
				chat = chat,
				outgoingPrefix = outgoingPrefix,
				repliesInboxAvatar = repliesInboxAvatar,
				mutedIcon = mutedIcon,
				sentIcon = sentIcon,
				readIcon = readIcon,
				onClick = onChatClick,
				onLongClick = onChatLongClick,
				modifier = Modifier
					.fillMaxWidth()
					.animateItem(),
			)
		}
		if (hasMore || isLoadingMore || loadMoreError) {
			item(key = "chats_load_more") {
				LaunchedEffect(hasMore, isLoadingMore, loadMoreError, items.size) {
					if (hasMore && !isLoadingMore && !loadMoreError) {
						onLoadMore()
					}
				}
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.padding(vertical = 16.dp),
					contentAlignment = Alignment.Center,
				) {
					when {
						isLoadingMore -> {
							CircularProgressIndicator(
								modifier = Modifier.size(24.dp),
								strokeWidth = 2.dp,
							)
						}
						loadMoreError -> {
							TextButton(onClick = onLoadMore) {
								Text("Повторить")
							}
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
	chat: ChatListItemModel,
	outgoingPrefix: String,
	repliesInboxAvatar: Painter,
	mutedIcon: Painter,
	sentIcon: Painter,
	readIcon: Painter,
	onClick: (ChatListItemModel) -> Unit,
	onLongClick: (ChatListItemModel) -> Unit,
	modifier: Modifier = Modifier,
) {
	val activityPreview = when (val activity = chat.activityState) {
		is ConversationActivityState.Typing -> "печатает..."
		is ConversationActivityState.Sending -> "отправка..."
		is ConversationActivityState.RecordingVideo -> "записывает видео..."
		is ConversationActivityState.Draft -> activity.text
			.takeIf(String::isNotBlank)
			?.let { "Черновик: $it" }
			?: "Черновик"
		ConversationActivityState.None -> null
	}
	val showUnreadBadge = chat.unreadCount > 0
	val showDeliveryStatusIcon = !showUnreadBadge && activityPreview == null && chat.deliveryStatus != null

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
			avatarThumb = chat.avatarThumb,
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
					Spacer(Modifier.width(4.dp))
					when (chat.deliveryStatus) {
						ChatDeliveryStatus.Sending -> Icon(
							imageVector = Icons.Outlined.Schedule,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.size(16.dp),
						)
						ChatDeliveryStatus.Failed -> Icon(
							imageVector = Icons.Outlined.ErrorOutline,
							contentDescription = null,
							tint = MaterialTheme.colorScheme.error,
							modifier = Modifier.size(16.dp),
						)
						ChatDeliveryStatus.Sent,
						ChatDeliveryStatus.Read -> Icon(
							painter = if (chat.deliveryStatus == ChatDeliveryStatus.Read) readIcon else sentIcon,
							contentDescription = null,
							tint = Color.Unspecified,
						)
					}
				}

				if (chat.isPinned) {
					Spacer(Modifier.width(4.dp))
					Icon(
						imageVector = Icons.Default.PushPin,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(15.dp),
					)
				}

				if (showDeliveryStatusIcon || chat.isPinned) {
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

				val rawPreviewText = activityPreview ?: if (chat.isOutgoingPreview) {
					outgoingPrefix + chat.previewText
				} else {
					chat.previewText
				}
				val isDraft = chat.activityState is ConversationActivityState.Draft

				val previewTextColor = MaterialTheme.colorScheme.onSurfaceVariant
				val previewStyle = LocalTypography.current.bodyMedium.copy(fontSize = 16.sp)
				val previewText = if (isDraft) {
					buildAnnotatedString {
						withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) {
							append(DRAFT_PREVIEW_LABEL)
						}
						append(rawPreviewText.removePrefix(DRAFT_PREVIEW_LABEL))
					}
				} else {
					buildAnnotatedString {
						append(rawPreviewText)
					}
				}
				Text(
					text = previewText,
					modifier = Modifier.weight(1f),
					style = previewStyle,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					color = previewTextColor,
				)

				if (showUnreadBadge) {
					Spacer(Modifier.width(8.dp))
					UnreadBadge(chat.unreadCount)
				}
			}
		}
	}
}

private const val DRAFT_PREVIEW_LABEL = "Черновик"

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
	avatarThumb: String?,
	visualType: ChatListItemVisualType,
	isOnline: Boolean,
	repliesInboxAvatar: Painter,
) {
	Box {
		when (visualType) {
			ChatListItemVisualType.SavedMessages -> {
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

			ChatListItemVisualType.RepliesInbox -> {
				Image(
					painter = repliesInboxAvatar,
					contentDescription = null,
					modifier = Modifier
						.size(52.dp)
						.clip(CircleShape),
				)
			}

			ChatListItemVisualType.Regular -> {
				NetworkAvatar(
					name = name,
					thumbUrl = avatarThumb,
					targetUrl = avatarUrl,
					size = 52.dp,
					modifier = Modifier,
					shape = CircleShape,
				)
			}
		}

		if (isOnline && visualType == ChatListItemVisualType.Regular) {
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
