package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

enum class ChatContextMenuAction {
	Reply,
	Pin,
	Unpin,
	CopyText,
	Edit,
	Delete,
	Retry
}

@Immutable
data class ChatContextMenuRequest(
	val messageId: Long,
	val requestToken: Long = 0L
)
