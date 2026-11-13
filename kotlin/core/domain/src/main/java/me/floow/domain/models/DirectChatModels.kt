package me.floow.domain.models

data class DirectChatPeer(
	val id: String,
	val username: String?,
	val name: String?,
	val avatarUrl: String?,
	val avatarThumb: String? = null
)

enum class MessageDeliveryStatus {
	SENDING,
	SENT,
	FAILED
}

enum class DirectChatOutboxState {
	QUEUED,
	SENDING,
	FAILED,
	CANCELLED,
	SENT
}

enum class DirectChatUploadStage {
	QUEUED,
	UPLOADING,
	UPLOADED,
	SENDING,
	SENT,
	FAILED,
	CANCELLED
}

data class DirectChatOutboxCommand(
	val clientMessageId: String,
	val conversationId: Long,
	val text: String,
	val contentType: String?,
	val replyToMessageId: Long?,
	val state: DirectChatOutboxState,
	val attemptCount: Int,
	val nextAttemptAt: Long,
	val lastError: String?,
	val createdAt: Long,
	val updatedAt: Long,
	val localFilePath: String? = null,
	val mediaUrl: String? = null,
	val mediaThumbnailUrl: String? = null,
	val mediaMimeType: String? = null,
	val mediaSizeBytes: Long = 0L,
	val mediaDurationMs: Long = 0L,
	val mediaWidth: Int = 0,
	val mediaHeight: Int = 0,
	val mediaWaveform: String? = null,
	val groupId: String? = null,
	val mediaPosition: Int = 0,
	val uploadStage: DirectChatUploadStage = DirectChatUploadStage.QUEUED,
	val idempotencyKey: String? = null,
)

data class DirectChatMessage(
	val id: Long,
	val conversationId: Long,
	val sender: DirectChatPeer,
	val text: String,
	val contentType: String? = null,
	val media: DirectChatMessageMedia? = null,
	val clientMessageId: String? = null,
	val replyToMessageId: Long?,
	val replyToMessageText: String?,
	val isPinned: Boolean,
	val pinnedAt: Long?,
	val pinnedByUserId: String?,
	val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT,
	val createdAt: Long,
	val updatedAt: Long,
	val groupId: String? = null,
	val position: Int = 0
)

data class DirectChatMessageMedia(
	val url: String,
	val objectKey: String,
	val mimeType: String,
	val sizeBytes: Long,
	val durationMs: Long = 0L,
	val localPath: String? = null,
	val width: Int? = null,
	val height: Int? = null,
	val thumbnailUrl: String? = null,
	val waveform: List<Int>? = null,
	val groupId: String? = null,
	val position: Int = 0
)

data class DirectChatConversation(
	val id: Long,
	val kind: String,
	val peer: DirectChatPeer,
	val lastMessage: DirectChatMessage?,
	val unreadCount: Int,
	val lastReadMessageId: Long,
	val peerLastReadMessageId: Long? = null,
	val createdAt: Long,
	val updatedAt: Long,
	val isPinned: Boolean = false,
	val pinnedAt: Long? = null
)

data class DirectChatConversationsPage(
	val items: List<DirectChatConversation>,
	val nextCursor: String?,
	val hasMore: Boolean
)

data class DirectChatMessagesPage(
	val items: List<DirectChatMessage>,
	val nextBeforeId: Long?,
	val peerLastReadMessageId: Long?
)

data class DirectChatInitialPage(
	val conversation: DirectChatConversation,
	val messages: DirectChatMessagesPage,
	val readStateVersion: Long = 0L,
	val pinnedMessages: List<DirectChatMessage> = emptyList()
)

data class DirectChatAnchoredMessagesWindow(
	val items: List<DirectChatMessage>,
	val anchorMessageId: Long,
	val anchorIndex: Int,
	val hasOlderMessages: Boolean,
	val newerCachedCount: Int,
	val latestCachedMessageId: Long,
	val peerLastReadMessageId: Long?
)

data class DirectChatReadState(
	val conversationId: Long,
	val lastReadMessageId: Long,
	val unreadCount: Int,
	val firstUnreadId: Long?,
	val maxMessageId: Long,
	val readStateVersion: Long = 0L
)

sealed interface DirectChatRealtimeEvent {
	data class Hello(
		val conversationId: Long,
		val eventId: String?,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class MessageCreated(
		val conversationId: Long,
		val eventId: String?,
		val message: DirectChatMessage,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val isReplay: Boolean,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class ReadUpToUpdated(
		val conversationId: Long,
		val eventId: String?,
		val actorUserId: String,
		val actorLastReadMessageId: Long?,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class MessageDeleted(
		val conversationId: Long,
		val eventId: String?,
		val deletedMessageId: Long,
		val actorUserId: String,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class MessageUpdated(
		val conversationId: Long,
		val eventId: String?,
		val message: DirectChatMessage,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class MessagePinnedUpdated(
		val conversationId: Long,
		val eventId: String?,
		val message: DirectChatMessage,
		val pinnedMessageId: Long,
		val isPinned: Boolean,
		val pinnedAt: Long?,
		val pinnedByUserId: String?,
		val actorUserId: String,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class Typing(
		val conversationId: Long,
		val eventId: String?,
		val actorUserId: String,
		val isTyping: Boolean,
		val action: String = "typing",
		val typingTtlMs: Long,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent

	data class ConversationDeleted(
		val conversationId: Long,
		val actorUserId: String
	) : DirectChatRealtimeEvent

	data class ResyncRequired(
		val conversationId: Long,
		val eventId: String?,
		val seq: Long,
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val firstUnreadId: Long?,
		val maxMessageId: Long,
		val readStateVersion: Long = 0L
	) : DirectChatRealtimeEvent
}
