package me.floow.data.repos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.floow.domain.api.CommentsApi
import me.floow.domain.api.CommentsRealtimeApi
import me.floow.domain.api.models.CommentItem
import me.floow.domain.api.models.CommentsRealtimeEvent
import me.floow.domain.api.models.CreateCommentResponse
import me.floow.domain.api.models.DeleteCommentResponse
import me.floow.domain.api.models.GetCommentsResponse
import me.floow.domain.api.models.MarkCommentsReadUpToResponse
import me.floow.domain.api.models.UpdateCommentResponse
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentAuthor
import me.floow.domain.models.CommentRealtimeEvent
import me.floow.domain.models.CommentReply
import me.floow.domain.models.CommentsPage
import me.floow.domain.utils.Logger
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class CommentsRepositoryImpl(
	private val logger: Logger,
	private val commentsApi: CommentsApi,
	private val commentsRealtimeApi: CommentsRealtimeApi
) : CommentsRepository {
	@OptIn(RawValueObjectCreate::class)
	override suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long?,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		return when (
			val response = commentsApi.getComments(
				postId = postId,
				cursor = cursor,
				limit = limit,
				anchorCommentId = anchorCommentId,
				anchorBefore = anchorBefore,
				anchorAfter = anchorAfter
			)
		) {
			is GetCommentsResponse.Success -> mapCommentsSuccessResponse(response)
			GetCommentsResponse.Error -> {
				logger.d("CommentsRepositoryImpl.getComments", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		return when (
			val response = commentsApi.getCommentsContext(
				postId = postId,
				targetCommentId = targetCommentId,
				anchorBefore = anchorBefore,
				anchorAfter = anchorAfter
			)
		) {
			is GetCommentsResponse.Success -> mapCommentsSuccessResponse(response)
			GetCommentsResponse.Error -> {
				logger.d("CommentsRepositoryImpl.getCommentsContext", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment> {
		return when (val response = commentsApi.createComment(me.floow.domain.api.models.CreateCommentData(postId, text, replyToId))) {
			is CreateCommentResponse.Success -> GetDataResponse.Success(response.comment.toDomainComment())
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

	override suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): GetDataResponse<Long> {
		return when (val response = commentsApi.markCommentsReadUpTo(postId = postId, readUpToSeq = readUpToSeq)) {
			is MarkCommentsReadUpToResponse.Success -> GetDataResponse.Success(response.lastReadSeq.coerceAtLeast(0L))
			MarkCommentsReadUpToResponse.Error -> GetDataResponse.Error(error = GetDataError.Other)
		}
	}

	override fun subscribePostComments(
		postId: String,
		afterSeq: Long,
		replayLimit: Int
	): Flow<CommentRealtimeEvent> {
		val postIdLong = postId.toLongOrNull()
			?.takeIf { value -> value > 0L }
			?: return kotlinx.coroutines.flow.emptyFlow()
		return commentsRealtimeApi.subscribePostComments(
			postId = postIdLong,
			afterSeq = afterSeq,
			replayLimit = replayLimit
		).map(::mapRealtimeEvent)
	}

	@OptIn(RawValueObjectCreate::class)
	private fun mapRealtimeEvent(event: CommentsRealtimeEvent): CommentRealtimeEvent {
		val postId = event.postId.toString()
		return when (event) {
			is CommentsRealtimeEvent.Hello -> CommentRealtimeEvent.Hello(
				postId = postId,
				eventId = event.eventId,
				seq = event.seq,
				lastReadSeq = event.lastReadSeq,
				unreadCount = event.unreadCount,
				firstUnreadSeq = event.firstUnreadSeq,
				maxSeq = event.maxSeq
			)
			is CommentsRealtimeEvent.CommentCreated -> CommentRealtimeEvent.CommentCreated(
				postId = postId,
				eventId = event.eventId,
				comment = event.comment.toDomainComment(),
				seq = event.seq,
				lastReadSeq = event.lastReadSeq,
				unreadCount = event.unreadCount,
				firstUnreadSeq = event.firstUnreadSeq,
				maxSeq = event.maxSeq,
				isReplay = event.isReplay
			)
			is CommentsRealtimeEvent.CommentUpdated -> CommentRealtimeEvent.CommentUpdated(
				postId = postId,
				eventId = event.eventId,
				comment = event.comment.toDomainComment(),
				seq = event.seq,
				lastReadSeq = event.lastReadSeq,
				unreadCount = event.unreadCount,
				firstUnreadSeq = event.firstUnreadSeq,
				maxSeq = event.maxSeq
			)
			is CommentsRealtimeEvent.CommentDeleted -> CommentRealtimeEvent.CommentDeleted(
				postId = postId,
				eventId = event.eventId,
				deletedCommentId = event.deletedCommentId.toString(),
				actorUserId = event.actorUserId,
				seq = event.seq,
				lastReadSeq = event.lastReadSeq,
				unreadCount = event.unreadCount,
				firstUnreadSeq = event.firstUnreadSeq,
				maxSeq = event.maxSeq
			)
			is CommentsRealtimeEvent.ReadUpToUpdated -> CommentRealtimeEvent.ReadUpToUpdated(
				postId = postId,
				eventId = event.eventId,
				readUpToSeq = event.readUpToSeq,
				actorUserId = event.actorUserId,
				seq = event.seq,
				lastReadSeq = event.lastReadSeq,
				unreadCount = event.unreadCount,
				firstUnreadSeq = event.firstUnreadSeq,
				maxSeq = event.maxSeq
			)
			is CommentsRealtimeEvent.ResyncRequired -> CommentRealtimeEvent.ResyncRequired(
				postId = postId,
				eventId = event.eventId,
				seq = event.seq,
				lastReadSeq = event.lastReadSeq,
				unreadCount = event.unreadCount,
				firstUnreadSeq = event.firstUnreadSeq,
				maxSeq = event.maxSeq
			)
		}
	}

	@OptIn(RawValueObjectCreate::class)
	private fun mapCommentsSuccessResponse(response: GetCommentsResponse.Success): GetDataResponse<CommentsPage> {
		val items = response.items.map { item -> item.toDomainComment() }
		return GetDataResponse.Success(
			CommentsPage(
				items = items,
				nextCursor = response.nextCursor,
				unreadCount = response.unreadCount,
				lastReadSeq = response.lastReadSeq,
				firstUnreadSeq = response.firstUnreadSeq,
				maxSeq = response.maxSeq
			)
		)
	}

	@OptIn(RawValueObjectCreate::class)
	private fun CommentItem.toDomainComment(): Comment {
		return Comment(
			id = id,
			seq = seq,
			postId = postId,
			author = CommentAuthor(
				id = authorId,
				name = authorName?.let(ProfileName::createRaw),
				username = authorUsername?.let(ProfileUsername::createRaw),
				avatarUrl = authorAvatarUrl
			),
			text = text,
			isRead = isRead,
			createdAt = createdAt,
			updatedAt = updatedAt,
			replyTo = replyTo?.let { reply ->
				CommentReply(
					id = reply.id,
					text = reply.text,
					authorId = reply.authorId,
					authorName = reply.authorName
				)
			}
		)
	}
}
