package me.floow.shared.chats.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.blank_girl
import flow.feature.shared.generated.resources.but_it_will_be_soon_we_promise
import flow.feature.shared.generated.resources.chat_muted_icon
import flow.feature.shared.generated.resources.chat_read_icon
import flow.feature.shared.generated.resources.chat_sent_icon
import flow.feature.shared.generated.resources.chats_error_generic
import flow.feature.shared.generated.resources.chats_outgoing_prefix
import flow.feature.shared.generated.resources.chats_title
import flow.feature.shared.generated.resources.replyplz
import flow.feature.shared.generated.resources.search_icon
import flow.feature.shared.generated.resources.there_is_nothing
import me.floow.shared.chats.model.ChatDeliveryStatus
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.uilogic.ChatsScreenState
import me.floow.uikit.chats.common.ChatsListContent
import me.floow.uikit.chats.common.ChatsListDeliveryStatus
import me.floow.uikit.chats.common.ChatsListItemUiModel
import me.floow.uikit.chats.common.ChatsListVisualType
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.topbar.TitleTopBarWithActionButton
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedChatsScreen(
	onSearchClick: () -> Unit,
	state: ChatsScreenState,
	onChatClick: (ChatListItemModel) -> Unit,
	modifier: Modifier = Modifier,
) {
	val outgoingPrefix = stringResource(Res.string.chats_outgoing_prefix)
	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			TitleTopBarWithActionButton(
				titleText = stringResource(Res.string.chats_title),
				titleTextStyle = MaterialTheme.typography.titleMedium.copy(
					fontSize = 18.sp,
					fontWeight = FontWeight.Medium,
				),
				onActionButtonClick = onSearchClick,
				icon = {
					Icon(
						painter = painterResource(Res.drawable.search_icon),
						contentDescription = null,
					)
				},
			)
		},
		contentWindowInsets = WindowInsets(0.dp),
		modifier = modifier,
	) { innerPadding ->
		val commonModifier = Modifier
			.fillMaxSize()
			.padding(innerPadding)

		when (state) {
			is ChatsScreenState.Loading -> {
				Box(commonModifier, Alignment.Center) { FlowLoadingIndicator() }
			}

			is ChatsScreenState.Error -> {
				Box(commonModifier, Alignment.Center) {
					Text(text = stringResource(Res.string.chats_error_generic))
				}
			}

			is ChatsScreenState.NoChats -> {
				SharedBlankContentBox(commonModifier)
			}

			is ChatsScreenState.HasData -> {
				ChatsListContent(
					items = state.chats.map { it.toUiModel(outgoingPrefix) },
					repliesInboxAvatar = painterResource(Res.drawable.replyplz),
					mutedIcon = painterResource(Res.drawable.chat_muted_icon),
					sentIcon = painterResource(Res.drawable.chat_sent_icon),
					readIcon = painterResource(Res.drawable.chat_read_icon),
					onChatClick = { clicked ->
						state.chats.firstOrNull { it.id == clicked.id }?.let(onChatClick)
					},
					modifier = commonModifier,
				)
			}
		}
	}
}

private fun ChatListItemModel.toUiModel(outgoingPrefix: String): ChatsListItemUiModel {
	return ChatsListItemUiModel(
		id = id,
		title = title,
		previewText = if (isOutgoingPreview) {
			outgoingPrefix + previewText
		} else {
			previewText
		},
		timeLabel = timeLabel,
		unreadCount = unreadCount,
		isMuted = isMuted,
		isOnline = isOnline,
		avatarUrl = avatarUrl,
		hasAttachmentPreview = hasAttachmentPreview,
		deliveryStatus = when (deliveryStatus) {
			ChatDeliveryStatus.Sent -> ChatsListDeliveryStatus.Sent
			ChatDeliveryStatus.Read -> ChatsListDeliveryStatus.Read
			null -> null
		},
		visualType = when (visualType) {
			ChatListItemVisualType.Regular -> ChatsListVisualType.Regular
			ChatListItemVisualType.RepliesInbox -> ChatsListVisualType.RepliesInbox
			ChatListItemVisualType.SavedMessages -> ChatsListVisualType.SavedMessages
		},
	)
}

@Composable
private fun SharedBlankContentBox(
	modifier: Modifier = Modifier,
) {
	Box(
		contentAlignment = Alignment.Center,
		modifier = modifier,
	) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Image(
				painter = painterResource(Res.drawable.blank_girl),
				contentDescription = null,
			)
			Spacer(Modifier.height(24.dp))
			Text(
				text = stringResource(Res.string.there_is_nothing),
				style = LocalTypography.current.titleLarge.copy(fontSize = 24.sp),
			)
			Spacer(Modifier.height(10.dp))
			Text(
				text = stringResource(Res.string.but_it_will_be_soon_we_promise),
				style = LocalTypography.current.bodyMedium,
			)
		}
	}
}
