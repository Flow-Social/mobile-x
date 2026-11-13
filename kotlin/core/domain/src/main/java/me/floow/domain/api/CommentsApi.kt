package me.floow.domain.api

import me.floow.domain.api.models.CreateCommentData
import me.floow.domain.api.models.CreateCommentResponse
import me.floow.domain.api.models.DeleteCommentResponse
import me.floow.domain.api.models.GetCommentsResponse
import me.floow.domain.api.models.MarkCommentsReadUpToResponse
import me.floow.domain.api.models.UpdateCommentResponse

interface CommentsApi {
	suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long? = null,
		anchorBefore: Int? = null,
		anchorAfter: Int? = null
	): GetCommentsResponse

	suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int? = null,
		anchorAfter: Int? = null
	): GetCommentsResponse
	
	suspend fun createComment(data: CreateCommentData): CreateCommentResponse
	
	suspend fun updateComment(commentId: String, text: String): UpdateCommentResponse
	
	suspend fun deleteComment(commentId: String): DeleteCommentResponse

	suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): MarkCommentsReadUpToResponse
}
