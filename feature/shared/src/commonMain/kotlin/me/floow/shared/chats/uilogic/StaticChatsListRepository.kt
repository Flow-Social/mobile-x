package me.floow.shared.chats.uilogic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.model.ChatDeliveryStatus

class StaticChatsListRepository(
	private val chats: List<ChatListItemModel> = defaultChats(),
) : ChatsListRepository {
	private val cachedChats = MutableStateFlow<List<ChatListItemModel>?>(chats)

	override fun observeChats(): StateFlow<List<ChatListItemModel>?> = cachedChats

	override suspend fun refreshChats(): Result<List<ChatListItemModel>> = Result.success(chats).also {
		cachedChats.value = chats
	}

	private companion object {
		fun defaultChats(): List<ChatListItemModel> = listOf(
			ChatListItemModel(
				id = "replies",
				peerUserId = null,
				conversationId = null,
				title = "Replies",
				previewText = "New reply",
				timeLabel = "12:45",
				unreadCount = 3,
				isMuted = false,
				isOnline = false,
				avatarUrl = null,
				hasAttachmentPreview = false,
				isOutgoingPreview = false,
				deliveryStatus = null,
				visualType = ChatListItemVisualType.RepliesInbox,
			),
			ChatListItemModel(
				id = "saved",
				peerUserId = "saved",
				conversationId = 1L,
				title = "Избранное",
				previewText = "No messages",
				timeLabel = "09:12",
				unreadCount = 0,
				isMuted = false,
				isOnline = false,
				avatarUrl = null,
				hasAttachmentPreview = false,
				isOutgoingPreview = false,
				deliveryStatus = null,
				visualType = ChatListItemVisualType.SavedMessages,
			),
			ChatListItemModel(
				id = "user_bob",
				peerUserId = "user_bob",
				conversationId = 101L,
				title = "Bogdan",
				previewText = "Пошли в созвон вечером",
				timeLabel = "08:31",
				unreadCount = 2,
				isMuted = false,
				isOnline = true,
				avatarUrl = null,
				hasAttachmentPreview = false,
				isOutgoingPreview = false,
				deliveryStatus = null,
				visualType = ChatListItemVisualType.Regular,
			),
			ChatListItemModel(
				id = "user_ann",
				peerUserId = "user_ann",
				conversationId = 102L,
				title = "Anya",
				previewText = "Скинул макет",
				timeLabel = "Вчера",
				unreadCount = 0,
				isMuted = true,
				isOnline = false,
				avatarUrl = null,
				hasAttachmentPreview = true,
				isOutgoingPreview = true,
				deliveryStatus = ChatDeliveryStatus.Read,
				visualType = ChatListItemVisualType.Regular,
			),
		)
	}
}
