package me.floow.chats.uilogic.chats

import me.floow.domain.values.ProfileName
const val REPLIES_INBOX_CHAT_ID = "system_replies_inbox"

fun Chat.isRepliesInboxChat(): Boolean = type == ChatType.SYSTEM

fun buildRepliesInboxChat(
	lastMessageText: String,
	lastMessageTimeMillis: Long,
	unreadCount: Int
): Chat {
	return Chat(
		id = REPLIES_INBOX_CHAT_ID,
		conversationId = null,
		type = ChatType.SYSTEM,
		name = ProfileName.create("Ответы"),
		lastMessageText = lastMessageText,
		lastMessageTimeMillis = lastMessageTimeMillis,
		isOnline = false,
		unreadCount = unreadCount.coerceAtLeast(0),
		chatMuted = false,
		avatarUrl = null,
		attachedMediaUrl = null,
		lastSentMessageState = null
	)
}
