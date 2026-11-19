package me.floow.uikit.chat.model

enum class ChatMessageTapOutcome {
	OpenContextMenu,
	ToggleSelection,
	HandleMedia
}

fun resolveMessageTapOutcome(
	isSelectionMode: Boolean,
	isMedia: Boolean = false
): ChatMessageTapOutcome = when {
	isSelectionMode -> ChatMessageTapOutcome.ToggleSelection
	isMedia -> ChatMessageTapOutcome.HandleMedia
	else -> ChatMessageTapOutcome.OpenContextMenu
}

fun resolveReplyTargetId(message: ChatMessage): Long = message.quote?.messageId ?: message.id
