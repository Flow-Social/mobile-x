package me.floow.shared.chats.model

data class ChatListItemModel(
	val id: String,
	val peerUserId: String? = null,
	val conversationId: Long? = null,
	val title: String,
	val previewText: String,
	val timeLabel: String,
	val unreadCount: Int,
	val isMuted: Boolean,
	val isOnline: Boolean,
	val avatarUrl: String?,
	val hasAttachmentPreview: Boolean,
	val isOutgoingPreview: Boolean,
	val deliveryStatus: ChatDeliveryStatus?,
	val visualType: ChatListItemVisualType,
)
