package me.floow.shared.chats.uilogic

import kotlinx.coroutines.flow.StateFlow
import me.floow.shared.chats.model.ChatListItemModel

interface ChatsListRepository {
	fun observeChats(): StateFlow<List<ChatListItemModel>?>
	fun observePagingState(): StateFlow<ChatsPagingState> = EmptyChatsPagingState
	suspend fun refreshChats(): Result<List<ChatListItemModel>>
	suspend fun loadMoreChats(): Result<List<ChatListItemModel>> = Result.success(observeChats().value.orEmpty())
	suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean): Result<Unit> = Result.success(Unit)
	suspend fun deleteConversation(conversationId: Long, forEveryone: Boolean): Result<Unit> = Result.success(Unit)
	suspend fun ensureSavedMessagesConversation(): Result<Unit> = Result.success(Unit)
}

private val EmptyChatsPagingState = kotlinx.coroutines.flow.MutableStateFlow(ChatsPagingState())

data class ChatsPagingState(
	val isLoadingMore: Boolean = false,
	val hasMore: Boolean = false,
	val loadMoreError: Boolean = false,
)
