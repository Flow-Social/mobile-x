package me.floow.domain.api

import me.floow.domain.api.models.CreateCommentData
import me.floow.domain.api.models.CreateCommentResponse
import me.floow.domain.api.models.DeleteCommentResponse
import me.floow.domain.api.models.GetCommentsResponse
import me.floow.domain.api.models.UpdateCommentResponse

interface CommentsApi {
	suspend fun getComments(postId: String, cursor: String?, limit: Int): GetCommentsResponse
	
	suspend fun createComment(data: CreateCommentData): CreateCommentResponse
	
	suspend fun updateComment(commentId: String, text: String): UpdateCommentResponse
	
	suspend fun deleteComment(commentId: String): DeleteCommentResponse
}
