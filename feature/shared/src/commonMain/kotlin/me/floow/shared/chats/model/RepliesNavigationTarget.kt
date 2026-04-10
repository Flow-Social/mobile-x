package me.floow.shared.chats.model

data class RepliesNavigationTarget(
	val postId: String,
	val commentId: String,
	val replyToCommentId: String? = null,
	val threadId: String? = null,
)

data class RepliesActorTarget(
	val userId: String,
)
