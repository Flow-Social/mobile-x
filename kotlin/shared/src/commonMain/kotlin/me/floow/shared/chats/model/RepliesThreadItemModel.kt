package me.floow.shared.chats.model

data class RepliesThreadItemModel(
	val messageId: Long,
	val actorUserId: String? = null,
	val actorDisplayName: String,
	val actorAvatarUrl: String? = null,
	val text: String,
	val createdAtMillis: Long,
	val targetPostId: String? = null,
	val targetCommentId: String? = null,
	val targetReplyToCommentId: String? = null,
	val threadId: String? = null,
	val isUnread: Boolean = false,
)

fun RepliesThreadItemModel.toNavigationTargetOrNull(): RepliesNavigationTarget? {
	val resolvedPostId = targetPostId?.takeIf(String::isNotBlank) ?: return null
	val resolvedCommentId = targetCommentId?.takeIf(String::isNotBlank) ?: return null
	return RepliesNavigationTarget(
		postId = resolvedPostId,
		commentId = resolvedCommentId,
		replyToCommentId = targetReplyToCommentId?.takeIf(String::isNotBlank),
		threadId = threadId?.takeIf(String::isNotBlank),
	)
}

fun RepliesThreadItemModel.toActorTargetOrNull(): RepliesActorTarget? {
	val userId = actorUserId?.takeIf(String::isNotBlank) ?: return null
	return RepliesActorTarget(userId = userId)
}
