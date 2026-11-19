package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot

data class ChatThreadPage(
	val items: List<ChatMessageItemModel>,
	val nextBeforeMessageId: Long? = null,
	val canLoadMore: Boolean = false,
	val peerLastReadMessageId: Long? = null,
	val hasNewerMessages: Boolean = false,
)

data class DirectChatInitialRequest(
	val peerUserId: String,
	val peerDisplayName: String,
	val peerAvatarUrl: String? = null,
	val peerAvatarThumb: String? = null,
	val conversationId: Long? = null,
	val anchorMessageId: Long? = null,
	val openMode: ChatOpenMode = ChatOpenMode.FROM_LAST_SEEN,
	val isSavedMessages: Boolean = false,
	val allowCreateFromPeerUserId: Boolean = false,
)

data class DirectChatOpenViewportSnapshot(
	val anchorMessageId: Long?,
	val anchorOffsetPx: Int = 0,
	val isBottomPinned: Boolean = false,
    val history: me.floow.domain.data.repos.DirectChatWindowBounds? = null,
)

fun DirectChatInitialRequest.hasValidConversationTarget(): Boolean {
	return isSavedMessages || (conversationId != null && conversationId > 0L)
}

fun DirectChatInitialRequest.hasValidPeerCreateTarget(): Boolean {
	return allowCreateFromPeerUserId && peerUserId.isNotBlank()
}

internal class ChatMessageUnavailableException : IllegalStateException("Сообщение недоступно")

interface ChatThreadRepository {
    val networkOnline: kotlinx.coroutines.flow.StateFlow<Boolean>? get() = null
	suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> = Result.success(null)
	suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot>
	fun observeMessages(conversationId: Long): Flow<ChatThreadPage> = emptyFlow()
    fun observeReadState(conversationId: Long): Flow<me.floow.domain.models.DirectChatReadState?> = emptyFlow()
	fun observePinnedMessages(conversationId: Long): Flow<List<ChatMessageItemModel>> = emptyFlow()
	suspend fun loadPinnedMessages(conversationId: Long): Result<List<ChatMessageItemModel>> = Result.success(emptyList())
	suspend fun storeOptimisticOutgoingMessage(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		message: ChatMessageItemModel,
	): Result<Unit> = Result.success(Unit)
	suspend fun deleteLocalMessageByClientMessageId(conversationId: Long, clientMessageId: String): Result<Unit> =
		Result.success(Unit)
	suspend fun getLocalLastReadMessageId(conversationId: Long): Long = 0L
	suspend fun getOpenViewportSnapshot(conversationId: Long): DirectChatOpenViewportSnapshot =
		DirectChatOpenViewportSnapshot(anchorMessageId = null)
	suspend fun setOpenViewportSnapshot(conversationId: Long, snapshot: DirectChatOpenViewportSnapshot): Result<Unit> =
		Result.success(Unit)
	suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage>
	suspend fun loadAroundMessage(conversationId: Long, messageId: Long): Result<ChatThreadPage> =
		Result.failure(UnsupportedOperationException())
    suspend fun loadNewer(conversationId: Long, afterMessageId: Long): Result<ChatThreadPage> =
        Result.failure(UnsupportedOperationException())
    suspend fun sendMessage(
		conversationId: Long,
		text: String,
		replyToMessageId: Long? = null,
	): Result<ChatMessageItemModel> = Result.failure(UnsupportedOperationException())
	suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long? = null,
	): Result<ChatMessageItemModel> = sendMessage(conversationId, text, replyToMessageId)
	suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel>
	suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit>
	suspend fun deleteMessagesBatch(conversationId: Long, messageIds: List<Long>): Result<Unit> = Result.success(Unit)
	suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit>
	suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit>
	suspend fun setTyping(conversationId: Long, isTyping: Boolean, action: String = "typing"): Result<Unit> = Result.success(Unit)
}
