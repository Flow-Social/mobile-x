package me.floow.uikit.chat.model

enum class ChatMessageTapOutcome {
	OpenContextMenu,
	ToggleSelection
}

fun resolveMessageTapOutcome(
	isSelectionMode: Boolean
): ChatMessageTapOutcome = if (isSelectionMode) {
	ChatMessageTapOutcome.ToggleSelection
} else {
	ChatMessageTapOutcome.OpenContextMenu
}

fun resolveReplyTargetId(message: ChatMessage): Long {
	return if (message is ChatReplyMessage) {
		message.replyMessageId
	} else {
		message.id
	}
}
