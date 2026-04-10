package me.floow.shared.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.replyplz
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.uilogic.replies.RepliesScreenState
import me.floow.uikit.chat.common.ChatScreenBody
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.states.HasDataState
import me.floow.uikit.components.loading.FlowLoadingIndicator
import org.jetbrains.compose.resources.painterResource

@Composable
fun SharedRepliesScreen(
	state: RepliesScreenState,
	onBackClick: () -> Unit,
	onThreadClick: (RepliesThreadItemModel) -> Unit,
	onActorClick: (RepliesThreadItemModel) -> Unit,
	onSeeAllClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val strings = rememberSharedChatStrings()
	val config = remember { sharedRepliesChatConfig() }
	val uiState = remember(state) { state.toSharedRepliesUiState() }
	val hasDataState = uiState as? me.floow.uikit.chat.model.ChatScreenUiState.HasData
	val repliesAvatarPainter = painterResource(Res.drawable.replyplz)
	val itemsById = remember(state) {
		(state as? RepliesScreenState.HasData)?.items?.associateBy(RepliesThreadItemModel::messageId).orEmpty()
	}
	val rowBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }
	val bubbleBoundsByMessageKey = remember(hasDataState?.timelineSessionToken) { mutableStateMapOf<String, Rect>() }

	ChatScreenBody(
		title = strings.repliesTitle,
		subtitle = null,
		avatarUrl = null,
		isSavedMessages = false,
		onBackClick = onBackClick,
		isSelectionMode = false,
		selectedCount = 0,
		canCopySelection = false,
		canDeleteSelection = false,
		onSelectionCloseClick = {},
		onCopySelectionClick = {},
		onDeleteSelectionClick = {},
		selectedCountLabel = sharedSelectionCountLabel(0),
		closeContentDescription = strings.selectionClose,
		copyContentDescription = strings.selectionCopy,
		deleteContentDescription = strings.selectionDelete,
			showInputBar = false,
			inputController = me.floow.uikit.chat.input.rememberChatInputController(
			text = "",
			maxLength = null,
			fallbackPanelHeightPx = 0,
			onTextChanged = {},
		),
			actualImeHeightPx = 0,
			onSendClick = {},
			sendButtonActive = false,
			isEditMode = false,
			showEmojiButton = false,
			headerAvatarPainter = repliesAvatarPainter,
			dividerColor = config.dividerColor,
			content = { contentModifier ->
			when (uiState) {
				is me.floow.uikit.chat.model.ChatScreenUiState.Loading -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center,
				) {
					FlowLoadingIndicator()
				}

				is me.floow.uikit.chat.model.ChatScreenUiState.Error -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center,
				) {
					Text(
						text = (state as? RepliesScreenState.Error)?.message ?: strings.repliesLoadFailed,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
					)
				}

				is me.floow.uikit.chat.model.ChatScreenUiState.NoMessages -> Box(
					modifier = contentModifier,
					contentAlignment = Alignment.Center,
				) {
					Text(strings.repliesNoMessages, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}

				is me.floow.uikit.chat.model.ChatScreenUiState.HasData -> {
					HasDataState(
						state = uiState,
						onChatBubbleClick = {},
						onChatBubbleLongClick = {},
						onToggleSelection = {},
						onMessageActionClick = { message ->
							itemsById[message.id]?.let(onThreadClick)
						},
						onAvatarClick = { message ->
							itemsById[message.id]?.let(onActorClick)
						},
						onReply = {},
						onReplyClick = {},
						onRetrySendClick = null,
						onPostImageClick = { _, _, _, _ -> },
						hiddenPostImageIndex = null,
						hiddenPostImageRevealProgress = 0f,
						onRequestScrollToBottom = onSeeAllClick,
						onUserStartedScroll = {},
						onViewportSnapshotChanged = {},
						onAnchorRestoreSettled = { _, _ -> },
						onAnchorRestoreTimedOut = {},
						resolveContextMenuActions = { emptyList() },
						onContextMenuOpenRequest = { _, _, _ -> },
						onContextMenuDismissRequest = {},
						rowBoundsByMessageKey = rowBoundsByMessageKey,
						bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
						onLoadMore = {},
						config = config,
						modifier = contentModifier.background(MaterialTheme.colorScheme.surfaceContainer),
					)
				}
			}
		},
		modifier = modifier.fillMaxSize(),
	)
}
