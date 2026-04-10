package me.floow.data.repos

import me.floow.domain.api.NotificationsApi
import me.floow.domain.api.models.GetNotificationsResponse
import me.floow.domain.api.models.GetUnreadNotificationsCountResponse
import me.floow.domain.api.models.MarkAllNotificationsReadResponse
import me.floow.domain.api.models.MarkNotificationReadResponse
import me.floow.domain.api.models.MarkNotificationsReadUpToResponse
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.models.UserNotification
import me.floow.domain.models.UserNotificationActor
import me.floow.domain.models.UserNotificationsPage
import me.floow.domain.utils.Logger

class NotificationsRepositoryImpl(
	private val logger: Logger,
	private val notificationsApi: NotificationsApi
) : NotificationsRepository {
	override suspend fun getNotifications(
		cursor: String?,
		limit: Int,
		types: Set<String>,
		channel: String?
	): GetDataResponse<UserNotificationsPage> {
		return when (
			val response = notificationsApi.getNotifications(
				cursor = cursor,
				limit = limit,
				types = types,
				channel = channel
			)
		) {
			is GetNotificationsResponse.Success -> {
				val items = response.items.map { item ->
					UserNotification(
						id = item.id,
						seq = item.seq,
						type = item.type,
						channel = item.channel,
						actor = UserNotificationActor(
							id = item.actor.id,
							username = item.actor.username,
							name = item.actor.name,
							avatarUrl = item.actor.avatar
						),
						postId = item.postId,
						commentId = item.commentId,
						threadId = item.threadId,
						replyToCommentId = item.replyToCommentId,
						commentText = item.commentText,
						replyToCommentText = item.replyToCommentText,
						title = item.title,
						body = item.body,
						isRead = item.isRead,
						readAt = item.readAt,
						createdAt = item.createdAt,
						updatedAt = item.updatedAt
					)
				}
				GetDataResponse.Success(
					UserNotificationsPage(
						items = items,
						nextCursor = response.nextCursor,
						unreadCount = response.unreadCount,
						lastReadSeq = response.lastReadSeq,
						firstUnreadSeq = response.firstUnreadSeq,
						maxSeq = response.maxSeq
					)
				)
			}

			GetNotificationsResponse.Error -> {
				logger.d("NotificationsRepositoryImpl.getNotifications", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getUnreadNotificationsCount(types: Set<String>, channel: String?): GetDataResponse<Int> {
		return when (val response = notificationsApi.getUnreadNotificationsCount(types = types, channel = channel)) {
			is GetUnreadNotificationsCountResponse.Success -> GetDataResponse.Success(response.unreadCount)
			GetUnreadNotificationsCountResponse.Error -> {
				logger.d("NotificationsRepositoryImpl.getUnreadNotificationsCount", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun markNotificationRead(notificationId: String): UpdateDataResponse {
		return when (val response = notificationsApi.markNotificationRead(notificationId.trim())) {
			MarkNotificationReadResponse.Success,
			MarkNotificationReadResponse.NotFound -> UpdateDataResponse.Success
			MarkNotificationReadResponse.Error -> {
				logger.d("NotificationsRepositoryImpl.markNotificationRead", "Failure response")
				UpdateDataResponse.Failure(FailureError.Other)
			}
		}
	}

	override suspend fun markAllNotificationsRead(channel: String?): UpdateDataResponse {
		return when (val response = notificationsApi.markAllNotificationsRead(channel = channel)) {
			is MarkAllNotificationsReadResponse.Success -> UpdateDataResponse.Success
			MarkAllNotificationsReadResponse.Error -> {
				logger.d("NotificationsRepositoryImpl.markAllNotificationsRead", "Failure response")
				UpdateDataResponse.Failure(FailureError.Other)
			}
		}
	}

	override suspend fun markNotificationsReadUpTo(readUpToSeq: Long, channel: String): GetDataResponse<Long> {
		return when (val response = notificationsApi.markNotificationsReadUpTo(readUpToSeq, channel = channel)) {
			is MarkNotificationsReadUpToResponse.Success -> GetDataResponse.Success(response.lastReadSeq.coerceAtLeast(0L))
			MarkNotificationsReadUpToResponse.Error -> {
				logger.d("NotificationsRepositoryImpl.markNotificationsReadUpTo", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}
}
