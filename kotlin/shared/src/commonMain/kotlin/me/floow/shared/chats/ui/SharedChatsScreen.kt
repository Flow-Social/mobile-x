package me.floow.shared.chats.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.blank_girl
import flow.shared.generated.resources.but_it_will_be_soon_we_promise
import flow.shared.generated.resources.chat_muted_icon
import flow.shared.generated.resources.chat_read_icon
import flow.shared.generated.resources.chat_sent_icon
import flow.shared.generated.resources.chats_error_generic
import flow.shared.generated.resources.chats_outgoing_prefix
import flow.shared.generated.resources.chats_title
import flow.shared.generated.resources.replyplz
import flow.shared.generated.resources.search_icon
import flow.shared.generated.resources.there_is_nothing
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.uilogic.ChatsScreenState
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.sheet.FlowModalSheetOverlay
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
	onChatLongClick: (ChatListItemModel) -> Unit = {},
	onDeleteConversation: (ChatListItemModel, Boolean) -> Unit = { _, _ -> },
	onLoadMore: () -> Unit = {},
	modifier: Modifier = Modifier,
	playbackBar: @Composable () -> Unit = {},
) {
	val outgoingPrefix = stringResource(Res.string.chats_outgoing_prefix)
	var actionDialogTarget by remember { mutableStateOf<ChatListItemModel?>(null) }
	var deleteDialogTarget by remember { mutableStateOf<ChatListItemModel?>(null) }
	var deleteForEveryone by remember { mutableStateOf(false) }

	actionDialogTarget?.let { target ->
		FlowModalSheetOverlay(
			entryId = target.id.hashCode().toLong(),
			onClose = { actionDialogTarget = null },
			canDragToDismiss = true,
			sheetHeightFraction = 0.32f,
			minSheetHeight = 220.dp,
			containerColor = MaterialTheme.colorScheme.surface,
			sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 12.dp),
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.then(dragHandleModifier)
						.padding(bottom = 16.dp),
					contentAlignment = Alignment.Center,
				) {
					Box(
						modifier = Modifier
							.width(36.dp)
							.height(4.dp)
							.clip(RoundedCornerShape(2.dp))
							.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
					)
				}

				Text(
					text = target.title,
					style = LocalTypography.current.titleMedium.copy(
						fontSize = 18.sp,
						fontWeight = FontWeight.SemiBold,
					),
					color = MaterialTheme.colorScheme.onSurface,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.padding(bottom = 12.dp),
				)

				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(14.dp))
						.clickable {
							actionDialogTarget = null
							onChatLongClick(target)
						}
						.padding(vertical = 12.dp, horizontal = 8.dp),
				) {
					Icon(
						imageVector = Icons.Default.PushPin,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.primary,
						modifier = Modifier.size(22.dp),
					)
					Spacer(Modifier.width(14.dp))
					Text(
						text = if (target.isPinned) "Открепить диалог" else "Закрепить диалог",
						style = LocalTypography.current.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
						color = MaterialTheme.colorScheme.onSurface,
					)
				}

				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(14.dp))
						.clickable {
							val toDelete = target
							actionDialogTarget = null
							deleteForEveryone = false
							deleteDialogTarget = toDelete
						}
						.padding(vertical = 12.dp, horizontal = 8.dp),
				) {
					Icon(
						imageVector = Icons.Outlined.Delete,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.error,
						modifier = Modifier.size(22.dp),
					)
					Spacer(Modifier.width(14.dp))
					Text(
						text = "Удалить диалог",
						style = LocalTypography.current.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
						color = MaterialTheme.colorScheme.error,
					)
				}
			}
		}
	}

	deleteDialogTarget?.let { target ->
		FlowModalSheetOverlay(
			entryId = (target.id.hashCode() + 1).toLong(),
			onClose = { deleteDialogTarget = null },
			canDragToDismiss = true,
			sheetHeightFraction = 0.36f,
			minSheetHeight = 280.dp,
			containerColor = MaterialTheme.colorScheme.surface,
			sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 20.dp, vertical = 12.dp),
			) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.then(dragHandleModifier)
						.padding(bottom = 16.dp),
					contentAlignment = Alignment.Center,
				) {
					Box(
						modifier = Modifier
							.width(36.dp)
							.height(4.dp)
							.clip(RoundedCornerShape(2.dp))
							.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
					)
				}

				Text(
					text = "Удалить диалог?",
					style = LocalTypography.current.titleLarge.copy(
						fontSize = 20.sp,
						fontWeight = FontWeight.Bold,
					),
					color = MaterialTheme.colorScheme.onSurface,
				)

				Spacer(Modifier.height(8.dp))

				Text(
					text = "Вы действительно хотите удалить переписку с «${target.title}»? Это действие нельзя отменить.",
					style = LocalTypography.current.bodyMedium.copy(
						fontSize = 14.sp,
						lineHeight = 20.sp,
					),
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				Spacer(Modifier.height(14.dp))

				Row(
					verticalAlignment = Alignment.CenterVertically,
					modifier = Modifier
						.fillMaxWidth()
						.clip(RoundedCornerShape(12.dp))
						.clickable { deleteForEveryone = !deleteForEveryone }
						.padding(vertical = 4.dp),
				) {
					Checkbox(
						checked = deleteForEveryone,
						onCheckedChange = { deleteForEveryone = it },
					)
					Spacer(Modifier.width(8.dp))
					Text(
						text = "Также удалить для ${target.title}",
						style = LocalTypography.current.bodyMedium.copy(fontSize = 15.sp),
						color = MaterialTheme.colorScheme.onSurface,
					)
				}

				Spacer(Modifier.height(16.dp))

				Row(
					modifier = Modifier.fillMaxWidth(),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
				) {
					Button(
						onClick = { deleteDialogTarget = null },
						modifier = Modifier
							.weight(1f)
							.height(48.dp),
						shape = RoundedCornerShape(14.dp),
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.surfaceVariant,
							contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
						),
					) {
						Text(
							text = "Отмена",
							style = LocalTypography.current.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
						)
					}

					Button(
						onClick = {
							val item = target
							val forAll = deleteForEveryone
							deleteDialogTarget = null
							onDeleteConversation(item, forAll)
						},
						modifier = Modifier
							.weight(1f)
							.height(48.dp),
						shape = RoundedCornerShape(14.dp),
						colors = ButtonDefaults.buttonColors(
							containerColor = MaterialTheme.colorScheme.error,
							contentColor = MaterialTheme.colorScheme.onError,
						),
					) {
						Text(
							text = "Удалить",
							style = LocalTypography.current.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
						)
					}
				}
			}
		}
	}
	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			TitleTopBarWithActionButton(
				titleText = stringResource(Res.string.chats_title),
				titleTextStyle = LocalTypography.current.titleMedium.copy(
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
		Box(Modifier.fillMaxSize().padding(innerPadding)) {
			val commonModifier = Modifier.fillMaxSize()

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
						items = state.chats,
						outgoingPrefix = outgoingPrefix,
						repliesInboxAvatar = painterResource(Res.drawable.replyplz),
						mutedIcon = painterResource(Res.drawable.chat_muted_icon),
						sentIcon = painterResource(Res.drawable.chat_sent_icon),
						readIcon = painterResource(Res.drawable.chat_read_icon),
						onChatClick = onChatClick,
						onChatLongClick = { clicked ->
							if (clicked.visualType == ChatListItemVisualType.Regular) {
								actionDialogTarget = clicked
							}
						},
						onLoadMore = onLoadMore,
						isLoadingMore = state.isLoadingMore,
						hasMore = state.hasMore,
						loadMoreError = state.loadMoreError,
						modifier = commonModifier,
					)
				}
			}
			playbackBar()
		}
	}
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
