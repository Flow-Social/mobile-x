package me.floow.shared.chats.model

data class ChatListMediaPreview(
	val thumbnailUrl: String,
	val isCircle: Boolean = false,
)

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
	val avatarThumb: String? = null,
	val mediaPreview: ChatListMediaPreview? = null,
	val isOutgoingPreview: Boolean,
	val deliveryStatus: ChatDeliveryStatus?,
	val visualType: ChatListItemVisualType,
	val activityState: ConversationActivityState = ConversationActivityState.None,
	val isPinned: Boolean = false,
) {
	val hasAttachmentPreview: Boolean
		get() = mediaPreview != null
}

sealed interface ConversationActivityState {
	data object None : ConversationActivityState
	data class Typing(
		val displayName: String,
		val expiresAtMillis: Long,
	) : ConversationActivityState
	data class Sending(
		val startedAtMillis: Long,
	) : ConversationActivityState
	data class RecordingVideo(
		val startedAtMillis: Long,
	) : ConversationActivityState
	data class Draft(
		val updatedAtMillis: Long,
		val text: String = "",
	) : ConversationActivityState
}

typealias ChatListActivityState = ConversationActivityState
