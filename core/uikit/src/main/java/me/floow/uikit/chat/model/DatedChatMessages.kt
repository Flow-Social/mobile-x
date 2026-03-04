package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate

@Immutable
data class DatedChatMessages(
	val datetime: LocalDate,
	val messages: List<ChatMessage>
)
