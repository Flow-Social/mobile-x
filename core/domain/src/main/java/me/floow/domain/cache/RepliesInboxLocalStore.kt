package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow
import me.floow.domain.models.UserNotification
import me.floow.domain.models.UserNotificationsPage

data class RepliesInboxMeta(
	val lastReadSeq: Long,
	val unreadCount: Int,
	val firstUnreadSeq: Long?,
	val maxSeq: Long
)

interface RepliesInboxLocalStore {
	fun observeRepliesPage(): Flow<UserNotificationsPage>

	suspend fun getRepliesPage(): UserNotificationsPage

	suspend fun replaceRepliesPage(page: UserNotificationsPage)

	suspend fun upsertReplyNotification(
		notification: UserNotification,
		meta: RepliesInboxMeta
	)

	suspend fun applyRepliesMeta(meta: RepliesInboxMeta)

	suspend fun applyReadCursor(lastReadSeq: Long)

	suspend fun clear()
}
