package me.floow.domain.api.models

sealed interface GetCommentsResponse {
	data class Success(
		val items: List<CommentItem>,
		val nextCursor: String?,
		val unreadCount: Int,
		val lastReadSeq: Long,
		val firstUnreadSeq: Long?,
		val maxSeq: Long
	) : GetCommentsResponse

	data object Error : GetCommentsResponse
}

data class CommentItem(
	val id: String,
	val seq: Long,
	val postId: String,
	val authorId: String,
	val authorUsername: String?,
	val authorName: String?,
	val authorAvatarUrl: String?,
	val text: String,
	val isRead: Boolean,
	val createdAt: Long,
	val updatedAt: Long,
	val replyTo: CommentReplyItem? = null
)

data class CommentReplyItem(
	val id: String,
	val text: String,
	val authorId: String,
	val authorName: String?
)

data class CreateCommentData(
	val postId: String,
	val text: String,
	val replyToId: Long? = null
)

sealed interface CreateCommentResponse {
	data class Success(
		val comment: CommentItem
	) : CreateCommentResponse

	data object Error : CreateCommentResponse
}

sealed interface UpdateCommentResponse {
	data object Success : UpdateCommentResponse
	data object Error : UpdateCommentResponse
}

sealed interface DeleteCommentResponse {
	data object Success : DeleteCommentResponse
	data object Error : DeleteCommentResponse
}

sealed interface MarkCommentsReadUpToResponse {
	data class Success(
		val lastReadSeq: Long
	) : MarkCommentsReadUpToResponse

	data object Error : MarkCommentsReadUpToResponse
}
