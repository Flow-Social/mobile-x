package me.floow.chats.uilogic.chats

import me.floow.domain.values.ProfileName

const val SAVED_MESSAGES_CHAT_KIND = "saved_messages"

fun Chat.isSavedMessages(): Boolean = type == ChatType.SAVED

fun buildSavedMessagesChat(
	peerId: String,
	conversationId: Long,
	lastMessageText: String,
	lastMessageTimeMillis: Long,
): Chat {
	return Chat(
		id = peerId,
		conversationId = conversationId,
		type = ChatType.SAVED,
		name = ProfileName.create("Избранное"),
		lastMessageText = lastMessageText,
		lastMessageTimeMillis = lastMessageTimeMillis,
		isOnline = false,
		unreadCount = 0,
		chatMuted = false,
		avatarUrl = null,
		attachedMediaUrl = null,
		lastSentMessageState = null,
	)
}
