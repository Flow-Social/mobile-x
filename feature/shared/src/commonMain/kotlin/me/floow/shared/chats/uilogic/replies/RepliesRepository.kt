package me.floow.shared.chats.uilogic.replies

import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesThreadItemModel

data class RepliesScreenData(
	val items: List<RepliesThreadItemModel>,
	val unreadCount: Int,
)

interface RepliesRepository {
	suspend fun load(openMode: ChatOpenMode = ChatOpenMode.FROM_UNREAD, anchorSeq: Long? = null): Result<RepliesScreenData>
	suspend fun markAllRead(): Result<Unit>
}
