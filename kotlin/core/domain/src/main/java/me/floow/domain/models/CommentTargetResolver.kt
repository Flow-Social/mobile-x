package me.floow.domain.models

fun resolveCommentTargetCandidates(
	primaryTargetCommentId: CommentId?,
	fallbackTargetCommentId: CommentId?
): List<CommentId> {
	return listOfNotNull(primaryTargetCommentId, fallbackTargetCommentId)
		.distinctBy(CommentId::value)
}

fun resolveReplyTargetCommentCandidates(
	commentId: String,
	replyToCommentId: String?,
	threadId: String?
): List<CommentId> {
	return listOf(commentId, replyToCommentId, threadId)
		.mapNotNull(String?::toCommentIdOrNull)
		.distinctBy(CommentId::value)
}
