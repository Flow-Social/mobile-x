package me.floow.shared.chats.uilogic.replies

import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesThreadItemModel

data class RepliesScreenData(
	val items: List<RepliesThreadItemModel>,
	val unreadCount: Int,
	val serverReadUpToSeq: Long = 0L,
	val localReadUpToSeq: Long = 0L,
	val storedOpenAnchorSeq: Long = 0L,
)

interface RepliesRepository {
	suspend fun load(openMode: ChatOpenMode = ChatOpenMode.FROM_UNREAD, anchorSeq: Long? = null): Result<RepliesScreenData>
	suspend fun markReadUpTo(seq: Long): Result<Unit> = Result.success(Unit)
	suspend fun setOpenAnchorSeq(seq: Long): Result<Unit> = Result.success(Unit)
	suspend fun markAllRead(): Result<Unit>
}
