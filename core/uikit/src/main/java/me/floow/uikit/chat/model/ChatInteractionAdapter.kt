package me.floow.uikit.chat.model

object ChatInteractionAdapter {
	fun onReplyClick(message: ChatMessage, onJumpToMessage: (Long) -> Unit) {
		onJumpToMessage(resolveReplyTargetId(message))
	}

	fun onCurrentReplyClick(state: ChatScreenUiState, onJumpToMessage: (Long) -> Unit) {
		state.messageFieldReply?.replyId?.let(onJumpToMessage)
	}

	fun onSendClick(
		state: ChatScreenUiState,
		onEditMessage: (messageId: Long, text: String) -> Unit,
		onSendMessage: () -> Unit
	) {
		val messageToEditId = (state as? ChatScreenUiState.HasData)?.messageToEditId
		if (messageToEditId != null) {
			onEditMessage(messageToEditId, state.messageFieldValue)
		} else {
			onSendMessage()
		}
	}
}
