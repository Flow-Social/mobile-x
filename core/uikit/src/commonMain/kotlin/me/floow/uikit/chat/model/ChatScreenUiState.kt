package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class ChatAnchorRequest(
	val messageId: Long,
	val requestToken: Long = 0L,
	val initialOffsetPx: Int = 0,
	val keepAnchored: Boolean = false
)

@Immutable
data class ChatHighlightRequest(
	val messageId: Long,
	val requestToken: Long = 0L,
	val keepAnchored: Boolean = false
)

@Immutable
data class ChatScrollRequest(
	val requestToken: Long = 0L
)

@Immutable
data class ChatInitialViewport(
	val itemIndex: Int,
	val itemScrollOffsetPx: Int
)

@Immutable
data class MessageFieldReply(
	val replyId: Long,
	val replyAuthorName: String,
	val replyMessageText: String,
)

@Immutable
data class ChatSelectionState(
	val selectedMessageIds: Set<Long> = emptySet(),
	val selectedCount: Int = 0,
	val canCopy: Boolean = false,
	val canDelete: Boolean = false,
	val copyText: String = ""
) {
	val isSelectionMode: Boolean
		get() = selectedCount > 0
}

@Immutable
interface ChatScreenUiState {
	val chatInterlocutorId: String
	val chatInterlocutorName: String
	val chatInterlocutorAvatarUrl: String?
	val messageFieldValue: String
	val messageFieldReply: MessageFieldReply?
	val peerIsOnline: Boolean
	val peerLastSeenAtMillis: Long?

	@Immutable
	data class Loading(
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: String?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
		override val peerIsOnline: Boolean = false,
		override val peerLastSeenAtMillis: Long? = null,
	) : ChatScreenUiState

	@Immutable
	data class Error(
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: String?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
		override val peerIsOnline: Boolean = false,
		override val peerLastSeenAtMillis: Long? = null,
	) : ChatScreenUiState

	@Immutable
	data class NoMessages(
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: String?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
		override val peerIsOnline: Boolean = false,
		override val peerLastSeenAtMillis: Long? = null,
	) : ChatScreenUiState

	@Immutable
	data class HasData(
		val messages: List<DatedChatMessages>,
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: String?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
		override val peerIsOnline: Boolean = false,
		override val peerLastSeenAtMillis: Long? = null,
		val timelineSessionToken: Long = 0L,
		val initialViewport: ChatInitialViewport? = null,
		val anchorRequest: ChatAnchorRequest? = null,
		val highlightRequest: ChatHighlightRequest? = null,
		val scrollRequest: ChatScrollRequest? = null,
		val unreadBoundaryMessageId: Long? = null,
		val typingUserNames: List<String> = emptyList(),
		val pinnedMessages: List<ChatMessage> = emptyList(),
		val messageToEditId: Long? = null,
		val scrollToBottomRequestToken: Long = 0L,
		val scrollToBottomBadgeCount: Int = 0,
		val peerLastReadMessageId: Long = 0L,
		val canLoadMore: Boolean = false,
		val isLoadingMore: Boolean = false,
		val selectionState: ChatSelectionState = ChatSelectionState()
	) : ChatScreenUiState
}
