package me.floow.shared.chats.uilogic.replies

import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesThreadItemModel

class StaticRepliesRepository : RepliesRepository {
	private var items = listOf(
		RepliesThreadItemModel(
			messageId = 1L,
			actorUserId = "anya",
			actorDisplayName = "Anya",
			actorAvatarUrl = null,
			text = "Классный пост, добавь еще детали.",
			createdAtMillis = 1_741_000_000_000,
			targetPostId = "post_1",
			targetCommentId = "comment_1",
			threadId = "thread_1",
			isUnread = true,
		),
		RepliesThreadItemModel(
			messageId = 2L,
			actorUserId = "bogdan",
			actorDisplayName = "Bogdan",
			actorAvatarUrl = null,
			text = "Ответил тебе в треде.",
			createdAtMillis = 1_741_000_100_000,
			targetPostId = "post_2",
			targetCommentId = "comment_2",
			threadId = "thread_2",
			isUnread = true,
		),
	)

	override suspend fun load(
		openMode: ChatOpenMode,
		anchorSeq: Long?,
	): Result<RepliesScreenData> = Result.success(
		RepliesScreenData(
			items = items,
			unreadCount = items.count { it.isUnread },
		)
	)

	override suspend fun markAllRead(): Result<Unit> = runCatching {
		items = items.map { it.copy(isUnread = false) }
	}
}
