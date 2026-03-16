package me.floow.chats.uilogic.chats

import me.floow.domain.values.ProfileName

enum class LastSentMessageState {
	Sent,
	Read,
}

enum class ChatType {
	DIRECT,
	SYSTEM,
	GROUP,
	SAVED,
}

data class Chat(
	val id: String,
	val conversationId: Long? = null,
	val type: ChatType,
	val name: ProfileName,
	val lastMessageText: String,
	val lastMessageTimeMillis: Long,
	val isOnline: Boolean,
	val unreadCount: Int,
	val chatMuted: Boolean,
	val avatarUrl: String?,
	val attachedMediaUrl: String?,
	val lastSentMessageState: LastSentMessageState?
)
