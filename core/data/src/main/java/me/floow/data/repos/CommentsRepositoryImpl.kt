package me.floow.data.repos

import me.floow.domain.api.CommentsApi
import me.floow.domain.api.models.CreateCommentResponse
import me.floow.domain.api.models.DeleteCommentResponse
import me.floow.domain.api.models.GetCommentsResponse
import me.floow.domain.api.models.UpdateCommentResponse
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentAuthor
import me.floow.domain.models.CommentReply
import me.floow.domain.models.CommentsPage
import me.floow.domain.utils.Logger
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class CommentsRepositoryImpl(
	private val logger: Logger,
	private val commentsApi: CommentsApi
) : CommentsRepository {
	@OptIn(RawValueObjectCreate::class)
	override suspend fun getComments(postId: String, cursor: String?, limit: Int): GetDataResponse<CommentsPage> {
		return when (val response = commentsApi.getComments(postId, cursor, limit)) {
			is GetCommentsResponse.Success -> {
				val items = response.items.map { item ->
					Comment(
						id = item.id,
						postId = item.postId,
						author = CommentAuthor(
							id = item.authorId,
							name = item.authorName?.let(ProfileName::createRaw),
							username = item.authorUsername?.let(ProfileUsername::createRaw),
							avatarUrl = item.authorAvatarUrl
						),
						text = item.text,
						createdAt = item.createdAt,
						updatedAt = item.updatedAt,
						replyTo = item.replyTo?.let { reply ->
							CommentReply(
								id = reply.id,
								text = reply.text,
								authorId = reply.authorId,
								authorName = reply.authorName
							)
						}
					)
				}
				GetDataResponse.Success(CommentsPage(items = items, nextCursor = response.nextCursor))
			}

			GetCommentsResponse.Error -> {
				logger.d("CommentsRepositoryImpl.getComments", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment> {
		return when (val response = commentsApi.createComment(me.floow.domain.api.models.CreateCommentData(postId, text, replyToId))) {
			is CreateCommentResponse.Success -> {
				val item = response.comment
				GetDataResponse.Success(
					Comment(
						id = item.id,
						postId = item.postId,
						author = CommentAuthor(
							id = item.authorId,
							name = item.authorName?.let(ProfileName::createRaw),
							username = item.authorUsername?.let(ProfileUsername::createRaw),
							avatarUrl = item.authorAvatarUrl
						),
						text = item.text,
						createdAt = item.createdAt,
						updatedAt = item.updatedAt,
						replyTo = item.replyTo?.let { reply ->
							CommentReply(
								id = reply.id,
								text = reply.text,
								authorId = reply.authorId,
								authorName = reply.authorName
							)
						}
					)
				)
			}

			CreateCommentResponse.Error -> {
				logger.d("CommentsRepositoryImpl.createComment", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun updateComment(commentId: String, text: String): UpdateDataResponse {
		return when (commentsApi.updateComment(commentId, text)) {
			UpdateCommentResponse.Success -> UpdateDataResponse.Success
			UpdateCommentResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}

	override suspend fun deleteComment(commentId: String): UpdateDataResponse {
		return when (commentsApi.deleteComment(commentId)) {
			DeleteCommentResponse.Success -> UpdateDataResponse.Success
			DeleteCommentResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}
}
