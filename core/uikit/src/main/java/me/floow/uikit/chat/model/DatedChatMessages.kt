package me.floow.uikit.chat.model

import java.time.LocalDate

data class DatedChatMessages(
	val datetime: LocalDate,
	val messages: List<ChatMessage>
)
