package me.floow.comments.uilogic

import me.floow.domain.models.Comment
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.chat.model.ChatHighlightRequest
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatScrollRequest
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.MessageFieldReply

internal data class CommentsVmState(
	val postId: String = "",
	val postAuthorId: String = "",
	val postAuthorName: String = "",
	val defaultTitle: String = "",
	val postAuthorAvatarUrl: String? = null,
	val postAuthorUsername: String? = null,
	val postImageUrls: List<String> = emptyList(),
	val postImageVariants: List<PostImageVariant> = emptyList(),
	val postDescription: String? = null,
	val postCreatedAt: Long = 0L,
	val postLikesCount: Int = 0,
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val comments: List<Comment> = emptyList(),
	val commentOrderById: Map<String, Long> = emptyMap(),
	val nextCommentOrder: Long = 0L,
	val commentIdByMessageId: Map<Long, Long> = emptyMap(),
	val messageIdByCommentId: Map<Long, Long> = emptyMap(),
	val nextCursor: String? = null,
	val isLoadingMore: Boolean = false,
	val messageFieldValue: String = "",
	val messageFieldReply: MessageFieldReply? = null,
	val highlightedMessageId: Long? = null,
	val messageToEditId: Long? = null,
	val scrollToBottomRequestToken: Long = 0L
) {
	fun toUiState(groupedMessages: List<DatedChatMessages>): ChatScreenUiState {
		val title = postAuthorName.ifBlank { defaultTitle.ifBlank { "Comments" } }
		return when {
			isLoading && comments.isEmpty() -> {
				ChatScreenUiState.Loading(
					chatInterlocutorId = postAuthorId,
					chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
					messageFieldValue = messageFieldValue,
					chatInterlocutorName = title,
					messageFieldReply = messageFieldReply,
					peerIsOnline = false,
					peerLastSeenAtMillis = null
				)
			}

			isError && comments.isEmpty() -> {
				ChatScreenUiState.Error(
					chatInterlocutorId = postAuthorId,
					chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
					messageFieldValue = messageFieldValue,
					chatInterlocutorName = title,
					messageFieldReply = messageFieldReply,
					peerIsOnline = false,
					peerLastSeenAtMillis = null
				)
			}

			else -> {
				if (groupedMessages.isEmpty()) {
					return ChatScreenUiState.NoMessages(
						chatInterlocutorId = postAuthorId,
						chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
						messageFieldValue = messageFieldValue,
						chatInterlocutorName = title,
						messageFieldReply = messageFieldReply,
						peerIsOnline = false,
						peerLastSeenAtMillis = null
					)
				}

				ChatScreenUiState.HasData(
					chatInterlocutorId = postAuthorId,
					chatInterlocutorName = title,
					chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messages = groupedMessages,
					messageFieldReply = messageFieldReply,
					highlightRequest = highlightedMessageId?.let { messageId ->
						ChatHighlightRequest(messageId = messageId, requestToken = messageId)
					},
					typingUserNames = emptyList(),
					pinnedMessages = emptyList(),
					messageToEditId = messageToEditId,
					scrollRequest = scrollToBottomRequestToken
						.takeIf { token -> token > 0L }
						?.let(::ChatScrollRequest),
					scrollToBottomRequestToken = scrollToBottomRequestToken,
					canLoadMore = nextCursor != null,
					isLoadingMore = isLoadingMore
				)
			}
		}
	}
}
