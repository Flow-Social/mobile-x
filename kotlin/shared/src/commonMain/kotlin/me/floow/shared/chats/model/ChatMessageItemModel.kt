package me.floow.shared.chats.model

data class ChatMessageItemModel(
	val id: Long,
	val clientMessageId: String? = null,
	val senderUserId: String? = null,
	val senderDisplayName: String? = null,
	val text: String,
	val createdAtMillis: Long,
	val isOutgoing: Boolean,
	val replyToMessageId: Long? = null,
	val replyToMessageText: String? = null,
	val isPinned: Boolean = false,
	val isDeleted: Boolean = false,
	val deliveryState: ChatDeliveryState? = null,
	val content: ChatMessageContent = ChatMessageContent.Text,
	val pinnedAtMillis: Long? = null,
	val authorAvatarUrl: String? = null,
	val authorAvatarThumb: String? = null,
)


data class ChatReplyDraft(val messageId: Long, val author: String, val preview: String)

internal fun ChatMessageItemModel.toReplyDraft(): ChatReplyDraft = ChatReplyDraft(
    messageId = id,
    author = senderDisplayName?.takeIf { it.isNotBlank() } ?: if (isOutgoing) "Ты" else "Собеседник",
    preview = text.takeIf { it.isNotBlank() } ?: when (val payload = content) {
        is ChatMessageContent.Image -> if (payload.groupId == null) "Фотография" else "Альбом"
        is ChatMessageContent.Voice -> "Голосовое сообщение"
        is ChatMessageContent.VideoCircle -> "Видеосообщение"
        is ChatMessageContent.PostPreview -> "Запись на стене"
        else -> "Сообщение"
    },
)
