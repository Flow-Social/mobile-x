package me.floow.chats.uilogic.chats

import me.floow.domain.values.ProfileName
import java.time.LocalDateTime

const val REPLIES_INBOX_CHAT_ID = "system_replies_inbox"

fun Chat.isRepliesInboxChat(): Boolean = id == REPLIES_INBOX_CHAT_ID

fun buildRepliesInboxChat(
	lastMessageText: String,
	lastMessageDateTime: LocalDateTime,
	unreadCount: Int
): Chat {
	return Chat(
		id = REPLIES_INBOX_CHAT_ID,
		name = ProfileName.create("Ответы"),
		lastMessageText = lastMessageText,
		lastMessageDateTime = lastMessageDateTime,
		isOnline = false,
		unreadCount = unreadCount.coerceAtLeast(0),
		chatMuted = false,
		avatarUrl = null,
		attachedMediaUrl = null,
		lastSentMessageState = null
	)
}
