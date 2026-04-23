package me.floow.domain.api.models

data class ChatUserItem(
	val id: String,
	val username: String?,
	val name: String?,
	val avatar: String?
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
	val updatedAt: Long
)

data class ChatMessageMediaItem(
	val url: String,
	val objectKey: String,
	val mimeType: String,
	val sizeBytes: Long,
	val durationMs: Long,
	val width: Int? = null,
	val height: Int? = null,
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
	val updatedAt: Long
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
	data object Conflict : ChatsSendMessageResponse
	data object Error : ChatsSendMessageResponse
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
