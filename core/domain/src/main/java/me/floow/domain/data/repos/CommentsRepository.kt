package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentsPage

interface CommentsRepository {
	suspend fun getComments(postId: String, cursor: String?, limit: Int): GetDataResponse<CommentsPage>

	suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment>

	suspend fun updateComment(commentId: String, text: String): UpdateDataResponse

	suspend fun deleteComment(commentId: String): UpdateDataResponse
}
