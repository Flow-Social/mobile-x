package me.floow.comments

import me.floow.domain.models.CommentId
import me.floow.domain.models.PostImageVariant

data class CommentsRouteInitialData(
	val postId: String,
	val postAuthorId: String,
	val postAuthorName: String,
	val postAuthorAvatarUrl: String?,
	val postAuthorUsername: String?,
	val postImageUrls: List<String>,
	val postImageVariants: List<PostImageVariant>,
	val postDescription: String?,
	val postCreatedAt: Long,
	val postLikesCount: Int,
	val postIsSelf: Boolean,
	val mediaTransferToken: String? = null,
	val initialTargetCommentId: CommentId? = null,
	val fallbackTargetCommentId: CommentId? = null
)
