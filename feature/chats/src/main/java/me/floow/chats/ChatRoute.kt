package me.floow.chats

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.floow.uikit.chat.ChatScreen
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.chats.uilogic.chat.ChatScreenViewModel
import me.floow.uikit.chat.model.ChatScreenUiState

data class ChatRouteInitialData(
	val chatInterlocutorId: String,
	val chatInterlocutorName: String,
	val chatInterlocutorAvatarUrl: Uri?
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
	val state by vm.state.collectAsState()
	val context = LocalContext.current

	LaunchedEffect(Unit) {
		vm.setUseMockData(isMockBuild)
		vm.setInitialData(
			initialData.chatInterlocutorId,
			initialData.chatInterlocutorName,
			initialData.chatInterlocutorAvatarUrl,
		)

		vm.loadData()
	}

	ChatScreen(
		onBackClick = onBackClick,
		onProfileClick = {
			onProfileClick(state.chatInterlocutorId)
		},
		onTopBarDropdownClick = {},
		onChatBubbleClick = {},
		onJumpToMessage = vm::jumpToMessage,
		onReply = vm::addCurrentReply,
		onReplyClick = {
			val targetId = if (it is ChatReplyMessage) it.replyMessageId else it.id
			vm.jumpToMessage(targetId)
		},
		onCurrentReplyClose = vm::closeCurrentReply,
		onCurrentReplyClick = {
			vm.state.value.messageFieldReply?.replyId?.let { vm.jumpToMessage(it) }
		},
		onCancelEdit = vm::cancelEditing,
		onMessageInputFieldValueChange = vm::updateMessageInputField,
		onEmojiPickerClick = {
			showTodoToast(context)
			vm.simulateTyping()
		},
		onSendClick = {
			val currentState = state
			val messageToEditId = if (currentState is ChatScreenUiState.HasData) currentState.messageToEditId else null
			
			if (messageToEditId != null) {
				vm.editMessage(messageToEditId, currentState.messageFieldValue)
			} else {
				vm.sendMessage()
			}
		},
		onRequestScrollToBottom = vm::requestScrollToBottom,
		onPostImageClick = { _, _ -> },
		onPinMessage = vm::togglePinMessage,
		onUnpinMessage = vm::togglePinMessage,
		onDeleteMessage = vm::deleteMessage,
		onEditMessage = { id, text ->
			vm.startEditingMessage(id, text)
		},
		onUndoDelete = vm::undoDeleteMessage,
		state = state,
		modifier = modifier,
	)

}

private fun showTodoToast(context: Context) {
	Toast.makeText(context, "feature currently unavailable", Toast.LENGTH_SHORT).show()
}
