package me.floow.database.localstore

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import me.floow.database.AppDatabase
import me.floow.database.dao.RepliesInboxDao
import me.floow.database.dbo.RepliesInboxMetaEntity
import me.floow.database.dbo.RepliesInboxNotificationEntity
import me.floow.domain.cache.RepliesInboxLocalStore
import me.floow.domain.cache.RepliesInboxMeta
import me.floow.domain.models.UserNotification
import me.floow.domain.models.UserNotificationActor
import me.floow.domain.models.UserNotificationsPage

private const val REPLIES_CHANNEL = "replies"
private const val MAX_CACHED_REPLIES = 500

class RepliesInboxLocalStoreImpl(
	private val database: AppDatabase,
	private val dao: RepliesInboxDao
) : RepliesInboxLocalStore {
	override fun observeRepliesPage(): Flow<UserNotificationsPage> {
		return combine(
			dao.observeReplies(),
			dao.observeMeta(REPLIES_CHANNEL)
		) { replies, meta ->
			replies.toDomainPage(meta)
		}
	}

	override suspend fun getRepliesPage(): UserNotificationsPage {
		val replies = dao.getReplies()
		val meta = dao.getMeta(REPLIES_CHANNEL)
		return replies.toDomainPage(meta)
	}

	override suspend fun replaceRepliesPage(page: UserNotificationsPage) {
		val normalizedItems = page.items
			.sortedBy(UserNotification::seq)
			.distinctBy(UserNotification::seq)
			.toList()
			.takeLast(MAX_CACHED_REPLIES)

		database.withTransaction {
			val currentMeta = dao.getMeta(REPLIES_CHANNEL)
			dao.clearReplies()
			if (normalizedItems.isNotEmpty()) {
				dao.upsertReplies(normalizedItems.map { notification -> notification.toEntity() })
			}
			val incomingMaxSeq = maxOf(page.maxSeq, normalizedItems.maxOfOrNull(UserNotification::seq) ?: 0L)
			dao.upsertMeta(
				recomputeMeta(
					currentMeta = currentMeta,
					incomingLastReadSeq = page.lastReadSeq,
					incomingMaxSeq = incomingMaxSeq
				)
			)
		}
	}

	override suspend fun upsertReplyNotification(notification: UserNotification, meta: RepliesInboxMeta) {
		database.withTransaction {
			val currentMeta = dao.getMeta(REPLIES_CHANNEL)
			dao.upsertReply(notification.toEntity())
			dao.trimToLatest(MAX_CACHED_REPLIES)
			dao.upsertMeta(
				recomputeMeta(
					currentMeta = currentMeta,
					incomingLastReadSeq = meta.lastReadSeq,
					incomingMaxSeq = maxOf(meta.maxSeq, notification.seq)
				)
			)
		}
	}

	override suspend fun applyRepliesMeta(meta: RepliesInboxMeta) {
		database.withTransaction {
			dao.upsertMeta(
				recomputeMeta(
					currentMeta = dao.getMeta(REPLIES_CHANNEL),
					incomingLastReadSeq = meta.lastReadSeq,
					incomingMaxSeq = meta.maxSeq
				)
			)
		}
	}

	override suspend fun applyReadCursor(lastReadSeq: Long) {
		if (lastReadSeq <= 0L) return

		database.withTransaction {
			dao.upsertMeta(
				recomputeMeta(
					currentMeta = dao.getMeta(REPLIES_CHANNEL),
					incomingLastReadSeq = lastReadSeq,
					incomingMaxSeq = null
				)
			)
		}
	}

	override suspend fun clear() {
		database.withTransaction {
			dao.clearReplies()
			dao.deleteMeta(REPLIES_CHANNEL)
		}
	}

	private fun List<RepliesInboxNotificationEntity>.toDomainPage(
		meta: RepliesInboxMetaEntity?
	): UserNotificationsPage {
		val items = map { entity -> entity.toDomain() }
		val effectiveLastReadSeq = (meta?.lastReadSeq ?: items
			.asSequence()
			.filter(UserNotification::isRead)
			.maxOfOrNull(UserNotification::seq)
			?: 0L).coerceAtLeast(0L)
		val unreadCount = items.count { notification ->
			notification.seq > effectiveLastReadSeq
		}
		val firstUnreadSeq = items
			.asSequence()
			.map(UserNotification::seq)
			.filter { seq -> seq > effectiveLastReadSeq }
			.minOrNull()
		val maxSeq = maxOf(meta?.maxSeq ?: 0L, items.maxOfOrNull(UserNotification::seq) ?: 0L)
		return UserNotificationsPage(
			items = items,
			nextCursor = null,
			unreadCount = unreadCount.coerceAtLeast(0),
			lastReadSeq = effectiveLastReadSeq,
			firstUnreadSeq = firstUnreadSeq,
			maxSeq = maxSeq
		)
	}

	private fun UserNotification.toEntity(): RepliesInboxNotificationEntity {
		val normalizedReadAt = if (isRead) readAt ?: updatedAt else readAt
		return RepliesInboxNotificationEntity(
			seq = seq,
			id = id,
			type = type,
			channel = channel,
			actorId = actor.id,
			actorUsername = actor.username,
			actorName = actor.name,
			actorAvatarUrl = actor.avatarUrl,
			postId = postId,
			commentId = commentId,
			threadId = threadId,
			replyToCommentId = replyToCommentId,
			commentText = commentText,
			replyToCommentText = replyToCommentText,
			title = title,
			body = body,
			isRead = isRead,
			readAt = normalizedReadAt,
			createdAt = createdAt,
			updatedAt = updatedAt
		)
	}

	private fun RepliesInboxNotificationEntity.toDomain(): UserNotification {
		return UserNotification(
			id = id,
			seq = seq,
			type = type,
			channel = channel,
			actor = UserNotificationActor(
				id = actorId,
				username = actorUsername,
				name = actorName,
				avatarUrl = actorAvatarUrl
			),
			postId = postId,
			commentId = commentId,
			threadId = threadId,
			replyToCommentId = replyToCommentId,
			commentText = commentText,
			replyToCommentText = replyToCommentText,
			title = title,
			body = body,
			isRead = isRead,
			readAt = readAt,
			createdAt = createdAt,
			updatedAt = updatedAt
		)
	}

	private suspend fun recomputeMeta(
		currentMeta: RepliesInboxMetaEntity?,
		incomingLastReadSeq: Long,
		incomingMaxSeq: Long?
	): RepliesInboxMetaEntity {
		val baseMeta = currentMeta ?: defaultMeta()
		val normalizedLastReadSeq = maxOf(
			baseMeta.lastReadSeq.coerceAtLeast(0L),
			incomingLastReadSeq.coerceAtLeast(0L)
		)
		if (normalizedLastReadSeq > 0L) {
			dao.markReadUpTo(normalizedLastReadSeq, now())
		}
		val dbMaxSeq = dao.maxSeq() ?: 0L
		val normalizedMaxSeq = maxOf(
			baseMeta.maxSeq.coerceAtLeast(0L),
			incomingMaxSeq?.coerceAtLeast(0L) ?: 0L,
			dbMaxSeq.coerceAtLeast(0L)
		)
		val unreadCount = dao.countBySeqGreaterThan(normalizedLastReadSeq).coerceAtLeast(0)
		val firstUnreadSeq = if (unreadCount <= 0) {
			null
		} else {
			dao.firstSeqGreaterThan(normalizedLastReadSeq)
		}
		return baseMeta.copy(
			lastReadSeq = normalizedLastReadSeq,
			unreadCount = unreadCount,
			firstUnreadSeq = firstUnreadSeq,
			maxSeq = normalizedMaxSeq,
			updatedAt = now()
		)
	}

	private fun defaultMeta(): RepliesInboxMetaEntity {
		return RepliesInboxMetaEntity(
			channel = REPLIES_CHANNEL,
			lastReadSeq = 0L,
			unreadCount = 0,
			firstUnreadSeq = null,
			maxSeq = 0L,
			updatedAt = now()
		)
	}

	private fun now(): Long = System.currentTimeMillis()
}
