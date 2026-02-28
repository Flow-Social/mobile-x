package me.floow.uikit.chat.model

import android.net.Uri

data class MessageFieldReply(
	val replyId: Long,
	val replyAuthorName: String,
	val replyMessageText: String,
)

interface ChatScreenUiState {
	val chatInterlocutorId: String
	val chatInterlocutorName: String
	val chatInterlocutorAvatarUrl: Uri?
	val messageFieldValue: String
	val messageFieldReply: MessageFieldReply?

	data class Loading(
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: Uri?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
	) : ChatScreenUiState

	data class Error(
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: Uri?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
	) : ChatScreenUiState

	data class NoMessages(
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: Uri?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
	) : ChatScreenUiState

	data class HasData(
		val messages: List<DatedChatMessages>,
		override val chatInterlocutorId: String,
		override val chatInterlocutorAvatarUrl: Uri?,
		override val messageFieldValue: String,
		override val chatInterlocutorName: String,
		override val messageFieldReply: MessageFieldReply?,
		val highlightedMessageId: Long? = null,
		val typingUserNames: List<String> = emptyList(),
		val pinnedMessages: List<ChatMessage> = emptyList(),
		val messageToEditId: Long? = null,
		val scrollToBottomRequestToken: Long = 0L,
		val canLoadMore: Boolean = false,
		val isLoadingMore: Boolean = false
	) : ChatScreenUiState
}
