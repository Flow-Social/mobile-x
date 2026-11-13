package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class DatedChatMessages(
	val dayStartMillis: Long,
	val messages: List<ChatMessage>
)
