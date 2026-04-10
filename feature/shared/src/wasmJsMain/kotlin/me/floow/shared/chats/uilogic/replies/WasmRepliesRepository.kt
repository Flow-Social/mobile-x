package me.floow.shared.chats.uilogic.replies

import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.uilogic.WasmChatsApiSupport
import me.floow.shared.chats.uilogic.WasmNotificationItem
import me.floow.shared.chats.uilogic.parseNotificationsPage

class WasmRepliesRepository : RepliesRepository {
	override suspend fun load(
		openMode: ChatOpenMode,
		anchorSeq: Long?,
	): Result<RepliesScreenData> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		val query = buildString {
			append("/notifications?limit=50&channel=replies&types=comment_on_post,reply_to_comment,comment_reply")
			if (anchorSeq != null && anchorSeq > 0L) {
				append("&cursor=$anchorSeq")
			}
		}
		val page = parseNotificationsPage(
			WasmChatsApiSupport.apiRequestV1(
				method = "GET",
				path = query,
				authToken = authToken,
			)
		)
		RepliesScreenData(
			items = page.items
				.sortedBy(WasmNotificationItem::createdAt)
				.map(WasmNotificationItem::toRepliesItem),
			unreadCount = page.unreadCount,
		)
	}

	override suspend fun markAllRead(): Result<Unit> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		WasmChatsApiSupport.apiRequestV1(
			method = "POST",
			path = "/notifications/read-all?channel=replies",
			authToken = authToken,
			contentType = "application/json",
			body = "{}",
		)
	}
}

private fun WasmNotificationItem.toRepliesItem(): RepliesThreadItemModel {
	return RepliesThreadItemModel(
		messageId = seq,
		actorUserId = actorId,
		actorDisplayName = actorName ?: actorId,
		actorAvatarUrl = WasmChatsApiSupport.toAbsoluteMediaUrl(actorAvatar),
		text = commentText?.takeIf(String::isNotBlank)
			?: replyToCommentText?.takeIf(String::isNotBlank)
			?: body.ifBlank { title },
		createdAtMillis = createdAt,
		targetPostId = postId,
		targetCommentId = commentId,
		targetReplyToCommentId = replyToCommentId,
		threadId = threadId,
		isUnread = !isRead,
	)
}
