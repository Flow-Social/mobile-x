package me.floow.shared.chats.model

data class ChatThreadSnapshot(
	val conversationId: Long?,
	val header: ChatThreadHeaderModel,
	val messages: List<ChatMessageItemModel>,
	val canLoadMore: Boolean,
	val nextBeforeMessageId: Long? = null,
	val peerLastReadMessageId: Long? = null,
	val highlightedMessageId: Long? = null,
	val openAnchorMessageId: Long? = null,
	val openAnchorOffsetPx: Int = 0,
	val keepOpenAnchorLocked: Boolean = false,
)
