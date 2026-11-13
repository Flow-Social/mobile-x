package me.floow.domain.api

import me.floow.domain.api.models.GetNotificationsResponse
import me.floow.domain.api.models.GetUnreadNotificationsCountResponse
import me.floow.domain.api.models.MarkAllNotificationsReadResponse
import me.floow.domain.api.models.MarkNotificationReadResponse
import me.floow.domain.api.models.MarkNotificationsReadUpToResponse

interface NotificationsApi {
	suspend fun getNotifications(
		cursor: String?,
		limit: Int,
		types: Set<String> = emptySet(),
		channel: String? = null
	): GetNotificationsResponse

	suspend fun getUnreadNotificationsCount(
		types: Set<String> = emptySet(),
		channel: String? = null
	): GetUnreadNotificationsCountResponse

	suspend fun markNotificationRead(notificationId: String): MarkNotificationReadResponse

	suspend fun markNotificationsReadUpTo(
		readUpToSeq: Long,
		channel: String
	): MarkNotificationsReadUpToResponse

	suspend fun markAllNotificationsRead(channel: String? = null): MarkAllNotificationsReadResponse
}
