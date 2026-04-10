package me.floow.chats.uilogic.replies

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.uilogic.replies.RepliesRepository
import me.floow.shared.chats.uilogic.replies.RepliesScreenData

class AndroidRepliesRepository(
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository,
	private val notificationsRepository: NotificationsRepository,
) : RepliesRepository {
	override suspend fun load(
		openMode: ChatOpenMode,
		anchorSeq: Long?,
	): Result<RepliesScreenData> = runCatching {
		notificationsRealtimeRepository.start()
		notificationsRealtimeRepository.refresh()
		val page = notificationsRealtimeRepository.repliesState.value.page
		RepliesScreenData(
			items = page.items
				.sortedBy { it.createdAt }
				.map { notification ->
					RepliesThreadItemModel(
						messageId = notification.seq,
						actorUserId = notification.actor.id,
						actorDisplayName = notification.actor.name
							?: notification.actor.username
							?: notification.actor.id,
						actorAvatarUrl = notification.actor.avatarUrl,
						text = notification.commentText?.takeIf(String::isNotBlank)
							?: notification.replyToCommentText?.takeIf(String::isNotBlank)
							?: notification.body.ifBlank { notification.title },
						createdAtMillis = notification.createdAt,
						targetPostId = notification.postId,
						targetCommentId = notification.commentId,
						targetReplyToCommentId = notification.replyToCommentId,
						threadId = notification.threadId,
						isUnread = !notification.isRead,
					)
				},
			unreadCount = page.unreadCount.coerceAtLeast(0),
		)
	}

	override suspend fun markAllRead(): Result<Unit> = runCatching {
		when (notificationsRepository.markAllNotificationsRead(channel = REPLIES_CHANNEL)) {
			UpdateDataResponse.Success -> {
				val maxSeq = notificationsRealtimeRepository.repliesState.value.page.maxSeq
				if (maxSeq > 0L) {
					notificationsRealtimeRepository.applyLocalReadState(maxSeq)
				}
				notificationsRealtimeRepository.refresh()
				Unit
			}
			is UpdateDataResponse.Failure -> error("failed to mark replies as read")
		}
	}

	private companion object {
		const val REPLIES_CHANNEL = "replies"
	}
}
