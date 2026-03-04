package me.floow.mock.data

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.models.UserNotification
import me.floow.domain.models.UserNotificationActor
import me.floow.domain.models.UserNotificationsPage

class MockNotificationsRepository : NotificationsRepository {
	private val items = mutableListOf(
		UserNotification(
			id = "n_1",
			seq = 1L,
			type = "comment_on_post",
			channel = "replies",
			actor = UserNotificationActor(
				id = "user_anna",
				username = "anna",
				name = "Анна",
				avatarUrl = "https://picsum.photos/seed/anna/128/128"
			),
			postId = "post_1",
			commentId = "comment_11",
			threadId = "comment_11",
			replyToCommentId = null,
			commentText = "Классный пост, спасибо!",
			replyToCommentText = null,
			title = "Новый комментарий",
			body = "Анна оставила комментарий к вашему посту",
			isRead = false,
			readAt = null,
			createdAt = System.currentTimeMillis() - 60_000L,
			updatedAt = System.currentTimeMillis() - 60_000L
		),
		UserNotification(
			id = "n_2",
			seq = 2L,
			type = "reply_to_comment",
			channel = "replies",
			actor = UserNotificationActor(
				id = "user_ivan",
				username = "ivan",
				name = "Иван",
				avatarUrl = "https://picsum.photos/seed/ivan/128/128"
			),
			postId = "post_2",
			commentId = "comment_22",
			threadId = "comment_21",
			replyToCommentId = "comment_21",
			commentText = "Согласен, хорошая мысль",
			replyToCommentText = "Мне кажется, это спорно",
			title = "Ответ на комментарий",
			body = "Иван ответил на ваш комментарий",
			isRead = false,
			readAt = null,
			createdAt = System.currentTimeMillis() - 5 * 60_000L,
			updatedAt = System.currentTimeMillis() - 5 * 60_000L
		)
	)

	override suspend fun getNotifications(
		cursor: String?,
		limit: Int,
		types: Set<String>,
		channel: String?
	): GetDataResponse<UserNotificationsPage> {
		val normalizedLimit = limit.coerceAtLeast(1)
		val normalizedTypes = types.map(String::lowercase).toSet()
		val filteredByType = if (normalizedTypes.isEmpty()) {
			items
		} else {
			items.filter { notification -> notification.type.lowercase() in normalizedTypes }
		}
		val normalizedChannel = channel?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
		val filteredItems = if (normalizedChannel == null || normalizedChannel == "all") {
			filteredByType
		} else {
			filteredByType.filter { notification -> notification.channel.lowercase() == normalizedChannel }
		}
		return GetDataResponse.Success(
			UserNotificationsPage(
				items = filteredItems.take(normalizedLimit),
				nextCursor = null,
				unreadCount = filteredItems.count { !it.isRead },
				lastReadSeq = filteredItems.filter(UserNotification::isRead).maxOfOrNull(UserNotification::seq) ?: 0L,
				firstUnreadSeq = filteredItems.filterNot(UserNotification::isRead).minOfOrNull(UserNotification::seq),
				maxSeq = filteredItems.maxOfOrNull(UserNotification::seq) ?: 0L
			)
		)
	}

	override suspend fun getUnreadNotificationsCount(types: Set<String>, channel: String?): GetDataResponse<Int> {
		val normalizedTypes = types.map(String::lowercase).toSet()
		val filteredByType = if (normalizedTypes.isEmpty()) {
			items
		} else {
			items.filter { notification -> notification.type.lowercase() in normalizedTypes }
		}
		val normalizedChannel = channel?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
		val filteredItems = if (normalizedChannel == null || normalizedChannel == "all") {
			filteredByType
		} else {
			filteredByType.filter { notification -> notification.channel.lowercase() == normalizedChannel }
		}
		return GetDataResponse.Success(filteredItems.count { !it.isRead })
	}

	override suspend fun markNotificationRead(notificationId: String): UpdateDataResponse {
		val index = items.indexOfFirst { it.id == notificationId }
		if (index < 0) return UpdateDataResponse.Success

		val current = items[index]
		if (current.isRead) return UpdateDataResponse.Success

		items[index] = current.copy(
			isRead = true,
			readAt = System.currentTimeMillis(),
			updatedAt = System.currentTimeMillis()
		)
		return UpdateDataResponse.Success
	}

	override suspend fun markAllNotificationsRead(channel: String?): UpdateDataResponse {
		val now = System.currentTimeMillis()
		val normalizedChannel = channel?.trim()?.lowercase()?.takeIf(String::isNotEmpty)
		for (index in items.indices) {
			val current = items[index]
			if (normalizedChannel != null && normalizedChannel != "all" && current.channel.lowercase() != normalizedChannel) {
				continue
			}
			if (!current.isRead) {
				items[index] = current.copy(
					isRead = true,
					readAt = now,
					updatedAt = now
				)
			}
		}
		return UpdateDataResponse.Success
	}

	override suspend fun markNotificationsReadUpTo(readUpToSeq: Long, channel: String): GetDataResponse<Long> {
		if (readUpToSeq <= 0L) {
			return GetDataResponse.Error(error = me.floow.domain.data.GetDataError.Other)
		}
		val normalizedChannel = channel.trim().lowercase()
		if (normalizedChannel.isEmpty()) {
			return GetDataResponse.Error(error = me.floow.domain.data.GetDataError.Other)
		}
		val now = System.currentTimeMillis()
		var appliedReadSeq = 0L
		for (index in items.indices) {
			val current = items[index]
			if (current.channel.lowercase() != normalizedChannel) continue
			if (!current.isRead && current.seq <= readUpToSeq) {
				items[index] = current.copy(
					isRead = true,
					readAt = now,
					updatedAt = now
				)
				appliedReadSeq = maxOf(appliedReadSeq, current.seq)
			}
		}
		val currentLastRead = items
			.filter { notification -> notification.isRead && notification.channel.lowercase() == normalizedChannel }
			.maxOfOrNull(UserNotification::seq)
			?: 0L
		return GetDataResponse.Success(maxOf(appliedReadSeq, currentLastRead))
	}
}
