package me.floow.shared.chats.model

data class ChatThreadSnapshot(
	val conversationId: Long?,
	val header: ChatThreadHeaderModel,
	val messages: List<ChatMessageItemModel>,
	val canLoadMore: Boolean,
	val pinnedMessages: List<ChatMessageItemModel> = emptyList(),
	val nextBeforeMessageId: Long? = null,
	val peerLastReadMessageId: Long? = null,
	val highlightedMessageId: Long? = null,
	val openAnchorMessageId: Long? = null,
	val openAnchorOffsetPx: Int = 0,
    val keepOpenAnchorLocked: Boolean = false,
    val hasNewerMessages: Boolean = false,
    val provisionalHistory: Boolean = false,
    val firstUnreadMessageId: Long? = null,
    val lastReadMessageId: Long = 0L,
)
