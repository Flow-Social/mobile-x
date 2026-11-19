package me.floow.shared.chats.model

data class ChatPresenceState(
	val isOnline: Boolean = false,
	val lastSeenAtMillis: Long? = null,
    val isKnown: Boolean = isOnline || lastSeenAtMillis != null,
)

data class ChatTypingState(
	val isTyping: Boolean = false,
	val displayName: String = "",
	val typingTtlMs: Long = 0L,
	val action: String = "typing",
)
