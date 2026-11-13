package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse
import me.floow.domain.models.UserNotificationsPage
import me.floow.domain.data.UpdateDataResponse

interface NotificationsRepository {
	suspend fun getNotifications(
		cursor: String?,
		limit: Int,
		types: Set<String> = emptySet(),
		channel: String? = null
	): GetDataResponse<UserNotificationsPage>

	suspend fun getUnreadNotificationsCount(
		types: Set<String> = emptySet(),
		channel: String? = null
	): GetDataResponse<Int>

	suspend fun markNotificationRead(notificationId: String): UpdateDataResponse

	suspend fun markNotificationsReadUpTo(readUpToSeq: Long, channel: String): GetDataResponse<Long>

	suspend fun markAllNotificationsRead(channel: String? = null): UpdateDataResponse
}
