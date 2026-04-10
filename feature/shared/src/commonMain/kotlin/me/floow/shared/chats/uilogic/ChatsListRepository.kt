package me.floow.shared.chats.uilogic

import kotlinx.coroutines.flow.StateFlow
import me.floow.shared.chats.model.ChatListItemModel

interface ChatsListRepository {
	fun observeChats(): StateFlow<List<ChatListItemModel>?>
	suspend fun refreshChats(): Result<List<ChatListItemModel>>
	suspend fun ensureSavedMessagesConversation(): Result<Unit> = Result.success(Unit)
}
