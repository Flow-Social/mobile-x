package me.floow.domain.api.models

data class ChatUserItem(
	val id: String,
	val username: String?,
	val name: String?,
	val avatar: String?,
	val avatarThumb: String? = null
)

data class ChatMessageItem(
	val id: Long,
	val conversationId: Long,
	val sender: ChatUserItem,
	val text: String,
	val contentType: String? = null,
	val media: ChatMessageMediaItem? = null,
	val clientMessageId: String? = null,
	val replyToMessageId: Long?,
	val replyToMessageText: String?,
	val isPinned: Boolean,
	val pinnedAt: Long?,
	val pinnedByUserId: String?,
	val createdAt: Long,
	val updatedAt: Long,
	val groupId: String? = null,
	val position: Int = 0
)

data class ChatMessageMediaItem(
	val url: String,
	val objectKey: String,
	val mimeType: String,
	val sizeBytes: Long,
	val durationMs: Long = 0L,
	val width: Int? = null,
	val height: Int? = null,
	val thumbnailUrl: String? = null,
	val waveform: List<Int>? = null,
	val groupId: String? = null,
	val position: Int = 0
)

data class ChatConversationItem(
	val id: Long,
	val kind: String,
	val peer: ChatUserItem,
	val lastMessage: ChatMessageItem?,
	val unreadCount: Int,
	val lastReadMessageId: Long,
	val peerLastReadMessageId: Long?,
	val createdAt: Long,
	val updatedAt: Long,
	val isPinned: Boolean = false,
	val pinnedAt: Long? = null
)

data class ChatReadStateItem(
	val conversationId: Long,
	val lastReadMessageId: Long,
	val unreadCount: Int,
	val firstUnreadId: Long?,
	val maxMessageId: Long,
	val readStateVersion: Long = 0L
)

sealed interface ChatsGetOrCreateDirectResponse {
	data class Success(val conversation: ChatConversationItem) : ChatsGetOrCreateDirectResponse
	data object Error : ChatsGetOrCreateDirectResponse
}

sealed interface ChatsGetConversationsResponse {
	data class Success(
		val items: List<ChatConversationItem>,
		val nextCursor: String?,
		val hasMore: Boolean
	) : ChatsGetConversationsResponse
	data object Error : ChatsGetConversationsResponse
}

sealed interface ChatsGetConversationResponse {
	data class Success(
		val conversation: ChatConversationItem
	) : ChatsGetConversationResponse
	data object NotFound : ChatsGetConversationResponse
	data object Error : ChatsGetConversationResponse
}

sealed interface ChatsGetConversationInitialResponse {
	data class Success(
		val conversation: ChatConversationItem,
		val items: List<ChatMessageItem>,
		val nextBeforeId: Long?,
		val peerLastReadMessageId: Long?,
		val readStateVersion: Long,
		val pinnedItems: List<ChatMessageItem> = emptyList(),
	) : ChatsGetConversationInitialResponse

	data object NotFound : ChatsGetConversationInitialResponse
	data object Error : ChatsGetConversationInitialResponse
}

sealed interface ChatsGetMessagesResponse {
	data class Success(
		val items: List<ChatMessageItem>,
		val nextBeforeId: Long?,
		val peerLastReadMessageId: Long?
	) : ChatsGetMessagesResponse

	data object NotFound : ChatsGetMessagesResponse
	data object Error : ChatsGetMessagesResponse
}

sealed interface ChatsGetMessagesAroundResponse {
	data class Success(
		val items: List<ChatMessageItem>,
		val anchorId: Long,
		val anchorIndex: Int,
		val hasOlder: Boolean,
		val hasNewer: Boolean,
		val peerLastReadMessageId: Long?
	) : ChatsGetMessagesAroundResponse

	data object NotFound : ChatsGetMessagesAroundResponse
	data object Error : ChatsGetMessagesAroundResponse
}

sealed interface ChatsSendMessageResponse {
	data class Success(
		val message: ChatMessageItem,
		val deduped: Boolean
	) : ChatsSendMessageResponse

	data object NotFound : ChatsSendMessageResponse
	data class Conflict(val existingMessage: ChatMessageItem? = null) : ChatsSendMessageResponse
	data object Error : ChatsSendMessageResponse
}

data class ChatAlbumItemPayload(
	val clientMessageId: String?,
	val mediaUrl: String,
	val mediaObjectKey: String,
	val mediaSizeBytes: Long,
	val mediaWidth: Int,
	val mediaHeight: Int,
	val mediaThumbnailUrl: String? = null,
)

sealed interface ChatsSendAlbumMessageResponse {
	data class Success(
		val messages: List<ChatMessageItem>,
		val deduped: Boolean,
	) : ChatsSendAlbumMessageResponse

	data object NotFound : ChatsSendAlbumMessageResponse
	data class Conflict(val existingMessages: List<ChatMessageItem> = emptyList()) : ChatsSendAlbumMessageResponse
	data object Error : ChatsSendAlbumMessageResponse
}

sealed interface ChatsGetMessageByClientMessageIdResponse {
	data class Success(val message: ChatMessageItem) : ChatsGetMessageByClientMessageIdResponse
	data object NotFound : ChatsGetMessageByClientMessageIdResponse
	data object Error : ChatsGetMessageByClientMessageIdResponse
}

sealed interface ChatsMarkReadUpToResponse {
	data class Success(val readState: ChatReadStateItem) : ChatsMarkReadUpToResponse
	data object NotFound : ChatsMarkReadUpToResponse
	data object Error : ChatsMarkReadUpToResponse
}

sealed interface ChatsTypingResponse {
	data object Success : ChatsTypingResponse
	data object NotFound : ChatsTypingResponse
	data object Error : ChatsTypingResponse
}

sealed interface ChatsDeleteMessageResponse {
	data class Success(
		val deletedMessageId: Long,
		val readState: ChatReadStateItem?
	) : ChatsDeleteMessageResponse

	data object Forbidden : ChatsDeleteMessageResponse
	data object NotFound : ChatsDeleteMessageResponse
	data object Error : ChatsDeleteMessageResponse
}

sealed interface ChatsDeleteMessagesBatchResponse {
	data class Success(
		val deletedMessageIds: List<Long>,
		val readState: ChatReadStateItem?
	) : ChatsDeleteMessagesBatchResponse

	data object Forbidden : ChatsDeleteMessagesBatchResponse
	data object NotFound : ChatsDeleteMessagesBatchResponse
	data object Error : ChatsDeleteMessagesBatchResponse
}

sealed interface ChatsUpdateMessageResponse {
	data class Success(
		val message: ChatMessageItem
	) : ChatsUpdateMessageResponse

	data object Forbidden : ChatsUpdateMessageResponse
	data object NotFound : ChatsUpdateMessageResponse
	data object Error : ChatsUpdateMessageResponse
}

sealed interface ChatsGetPinnedMessagesResponse {
	data class Success(
		val items: List<ChatMessageItem>
	) : ChatsGetPinnedMessagesResponse

	data object NotFound : ChatsGetPinnedMessagesResponse
	data object Error : ChatsGetPinnedMessagesResponse
}

sealed interface ChatsPinMessageResponse {
	data class Success(
		val message: ChatMessageItem,
		val pinned: Boolean
	) : ChatsPinMessageResponse

	data object NotFound : ChatsPinMessageResponse
	data object Conflict : ChatsPinMessageResponse
	data object Error : ChatsPinMessageResponse
}

sealed interface ChatsUnpinMessageResponse {
	data class Success(
		val message: ChatMessageItem,
		val pinned: Boolean
	) : ChatsUnpinMessageResponse

	data object NotFound : ChatsUnpinMessageResponse
	data object Error : ChatsUnpinMessageResponse
}

sealed interface ChatsDeleteConversationResponse {
	data object Success : ChatsDeleteConversationResponse
	data object NotFound : ChatsDeleteConversationResponse
	data object Error : ChatsDeleteConversationResponse
}
