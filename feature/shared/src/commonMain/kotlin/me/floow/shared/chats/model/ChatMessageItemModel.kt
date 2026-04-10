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
)
