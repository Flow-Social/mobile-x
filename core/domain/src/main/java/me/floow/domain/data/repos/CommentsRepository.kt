package me.floow.domain.data.repos

import kotlinx.coroutines.flow.Flow
import me.floow.domain.data.GetDataResponse
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentRealtimeEvent
import me.floow.domain.models.CommentsPage
import me.floow.domain.data.UpdateDataResponse

interface CommentsRepository {
	suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long? = null,
		anchorBefore: Int? = null,
		anchorAfter: Int? = null
	): GetDataResponse<CommentsPage>

	suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int? = null,
		anchorAfter: Int? = null
	): GetDataResponse<CommentsPage>

	suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment>

	suspend fun updateComment(commentId: String, text: String): UpdateDataResponse

	suspend fun deleteComment(commentId: String): UpdateDataResponse

	suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): GetDataResponse<Long>

	fun subscribePostComments(
		postId: String,
		afterSeq: Long,
		replayLimit: Int = 200
	): Flow<CommentRealtimeEvent>
}
