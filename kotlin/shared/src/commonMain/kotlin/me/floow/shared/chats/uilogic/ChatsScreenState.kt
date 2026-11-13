package me.floow.shared.chats.uilogic

import me.floow.shared.chats.model.ChatListItemModel

sealed interface ChatsScreenState {
	data object Loading : ChatsScreenState
	data object NoChats : ChatsScreenState
	data object Error : ChatsScreenState
	data class HasData(
		val chats: List<ChatListItemModel>,
		val isLoadingMore: Boolean = false,
		val hasMore: Boolean = false,
		val loadMoreError: Boolean = false,
	) : ChatsScreenState
}
