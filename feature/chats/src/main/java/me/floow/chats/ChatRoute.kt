package me.floow.chats

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.floow.uikit.chat.ChatScreen
import me.floow.chats.uilogic.chat.ChatScreenViewModel
import me.floow.chats.uilogic.chat.DirectChatOpenMode
import me.floow.uikit.chat.model.ChatInteractionAdapter
import me.floow.uikit.chat.model.DEFAULT_CHAT_MESSAGE_MAX_LENGTH
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.util.SetStatusBarStyle

data class ChatRouteInitialData(
	val chatInterlocutorId: String,
	val chatInterlocutorName: String,
	val chatInterlocutorAvatarUrl: Uri?,
	val conversationId: Long? = null,
	val messageAnchorId: Long? = null,
	val openMode: DirectChatOpenMode = DirectChatOpenMode.FROM_LAST_SEEN,
	val isSavedMessages: Boolean = false,
)

@Composable
fun ChatRoute(
	initialData: ChatRouteInitialData,
	onBackClick: () -> Unit,
	onProfileClick: (String) -> Unit = {},
	isMockBuild: Boolean = false,
	vm: ChatScreenViewModel,
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsStateWithLifecycle()
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
	val config = remember(initialData.isSavedMessages) {
		if (initialData.isSavedMessages) {
			ChatScreenConfig(
				maxInputLength = DEFAULT_CHAT_MESSAGE_MAX_LENGTH,
				topBarMode = me.floow.uikit.chat.model.ChatTopBarMode.Standard,
				topBarTitle = "Избранное",
				topBarAvatarResId = me.floow.uikit.R.drawable.bookmark_icon,
				showTopBarSubtitle = false,
				showTypingIndicator = false,
				showPinActions = true,
			)
		} else {
			ChatScreenConfig(maxInputLength = DEFAULT_CHAT_MESSAGE_MAX_LENGTH)
		}
	}
	val lifecycleOwner = LocalLifecycleOwner.current

	LaunchedEffect(
		initialData.chatInterlocutorId,
		initialData.chatInterlocutorName,
		initialData.chatInterlocutorAvatarUrl,
		initialData.conversationId,
		initialData.messageAnchorId,
		initialData.openMode,
		isMockBuild
	) {
		vm.setUseMockData(isMockBuild)
		val shouldLoad = vm.setInitialData(
			initialData.chatInterlocutorId,
			initialData.chatInterlocutorName,
			initialData.chatInterlocutorAvatarUrl,
			initialData.conversationId,
			initialData.messageAnchorId,
			initialData.openMode,
		)

		if (shouldLoad) {
			vm.loadData()
		}
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_RESUME -> vm.onChatScreenVisible()
				Lifecycle.Event.ON_PAUSE -> vm.onChatScreenHidden()
				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}

	DisposableEffect(Unit) {
		vm.onChatScreenVisible()
		onDispose {
			vm.onScreenClosed()
		}
	}

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons
	)

	ChatScreen(
		onBackClick = onBackClick,
		onProfileClick = {
			onProfileClick(state.chatInterlocutorId)
		},
		onTopBarDropdownClick = {},
		onChatBubbleClick = {},
		onChatBubbleLongClick = { message ->
			vm.enterSelectionMode(message.id)
		},
		onToggleSelection = vm::toggleMessageSelection,
		onJumpToMessage = vm::jumpToMessage,
		onPinnedMessageClick = vm::onPinnedMessageClick,
		onReply = vm::addCurrentReply,
		onReplyClick = {
			ChatInteractionAdapter.onReplyClick(it, vm::jumpToMessage)
		},
		onCurrentReplyClose = vm::closeCurrentReply,
		onCurrentReplyClick = {
			ChatInteractionAdapter.onCurrentReplyClick(vm.state.value, vm::jumpToMessage)
		},
		onCancelEdit = vm::cancelEditing,
		onMessageInputFieldValueChange = vm::updateMessageInputField,
		onSendClick = {
			ChatInteractionAdapter.onSendClick(
				state = vm.state.value,
				onEditMessage = vm::editMessage,
				onSendMessage = vm::sendMessage
			)
		},
		onRequestScrollToBottom = vm::requestScrollToBottom,
		onRetryClick = vm::loadData,
		onViewportSnapshotChanged = vm::onViewportSnapshotChanged,
		onAnchorRestoreSettled = vm::onAnchorRestoreSettled,
		onAnchorRestoreTimedOut = vm::onAnchorRestoreTimedOut,
		onLoadMore = vm::loadMore,
		onPostImageClick = { _, _, _, _ -> },
		onPinMessage = vm::pinMessage,
		onUnpinMessage = vm::unpinMessage,
		onDeleteMessage = vm::deleteMessage,
		onEditMessage = { id, text ->
			vm.startEditingMessage(id, text)
		},
		onRetryMessage = vm::retryFailedMessage,
		onUndoDelete = vm::undoDeleteMessage,
		onClearSelection = vm::clearSelection,
		onDeleteSelectedMessages = vm::deleteSelectedMessages,
		resolveContextMenuActions = vm::resolveContextMenuActions,
		config = config,
		state = state,
		modifier = modifier,
	)

}
