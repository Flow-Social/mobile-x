package me.floow.mock.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.GetDataError
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatRealtimeEvent

class MockChatsRepository : ChatsRepository {
	override fun observeConversations(): Flow<List<DirectChatConversation>> = emptyFlow()

	override fun observeMessages(
		conversationId: Long,
		limit: Int
	): Flow<DirectChatMessagesPage> = emptyFlow()

	override fun observePinnedMessages(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessage>> = emptyFlow()

	override suspend fun getOrCreateSavedMessagesConversation(): GetDataResponse<DirectChatConversation> {
		return getOrCreateDirectConversation(peerUserId = "self")
	}

	override suspend fun getOrCreateDirectConversation(peerUserId: String): GetDataResponse<DirectChatConversation> {
		val peer = DirectChatPeer(
			id = peerUserId,
			username = "user$peerUserId",
			name = "User $peerUserId",
			avatarUrl = null
		)
		return GetDataResponse.Success(
			DirectChatConversation(
				id = peerUserId.toLongOrNull() ?: 1L,
				kind = "direct",
				peer = peer,
				lastMessage = null,
				unreadCount = 0,
				lastReadMessageId = 0L,
				createdAt = 0L,
				updatedAt = 0L
			)
		)
	}

	override suspend fun getConversations(limit: Int, cursor: String?): GetDataResponse<DirectChatConversationsPage> {
		return GetDataResponse.Success(
			DirectChatConversationsPage(
				items = emptyList(),
				nextCursor = null,
				hasMore = false
			)
		)
	}

	override suspend fun getConversation(conversationId: Long): GetDataResponse<DirectChatConversation> {
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): GetDataResponse<DirectChatMessagesPage> {
		return GetDataResponse.Success(
			DirectChatMessagesPage(
				items = emptyList(),
				nextBeforeId = null,
				peerLastReadMessageId = null
			)
		)
	}

	override suspend fun getMessagesAround(
		conversationId: Long,
		anchorId: Long,
		olderLimit: Int,
		newerLimit: Int
	): GetDataResponse<DirectChatAnchoredMessagesWindow> {
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int,
		newerLimit: Int
	): GetDataResponse<DirectChatAnchoredMessagesWindow> {
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun refreshLatestMessagesWindow(
		conversationId: Long,
		limit: Int
	): GetDataResponse<DirectChatMessagesPage> {
		return getMessages(conversationId = conversationId, limit = limit, beforeId = null)
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?
	): GetDataResponse<DirectChatMessage> {
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun retryPendingOutgoingMessages(
		conversationId: Long,
		limit: Int
	) = Unit

	override suspend fun retryOutgoingMessage(
		conversationId: Long,
		clientMessageId: String
	) = Unit

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): UpdateDataResponse {
		return UpdateDataResponse.Success
	}

	override suspend fun deleteLocalMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	) = Unit

	override suspend fun updateMessage(
		conversationId: Long,
		messageId: Long,
		text: String
	): GetDataResponse<DirectChatMessage> {
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): GetDataResponse<List<DirectChatMessage>> {
		return GetDataResponse.Success(emptyList())
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean
	): GetDataResponse<DirectChatMessage> {
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun storeOutgoingOptimisticMessage(message: DirectChatMessage) = Unit

	override suspend fun deleteLocalMessage(conversationId: Long, messageId: Long) = Unit

	override suspend fun applyLocalPinnedState(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): DirectChatMessage? {
		return null
	}

	override suspend fun markReadUpTo(
		conversationId: Long,
		messageId: Long
	): GetDataResponse<DirectChatReadState> {
		return GetDataResponse.Success(
			DirectChatReadState(
				conversationId = conversationId,
				lastReadMessageId = messageId,
				unreadCount = 0,
				firstUnreadId = null,
				maxMessageId = messageId
			)
		)
	}

	override suspend fun applyLocalReadUpTo(
		conversationId: Long,
		messageId: Long
	): DirectChatReadState? {
		if (conversationId <= 0L || messageId <= 0L) return null
		return DirectChatReadState(
			conversationId = conversationId,
			lastReadMessageId = messageId,
			unreadCount = 0,
			firstUnreadId = null,
			maxMessageId = messageId
		)
	}

	override suspend fun applyPeerLastReadUpTo(
		conversationId: Long,
		messageId: Long
	) = Unit

	override suspend fun updateLocalMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		status: me.floow.domain.models.MessageDeliveryStatus
	) = Unit

	override suspend fun updateLocalMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		status: me.floow.domain.models.MessageDeliveryStatus
	) = Unit

	override suspend fun sendTyping(
		conversationId: Long,
		isTyping: Boolean
	): UpdateDataResponse = UpdateDataResponse.Success

	override fun subscribeConversation(
		conversationId: Long,
		afterSeq: Long,
		replayLimit: Int
	): Flow<DirectChatRealtimeEvent> = emptyFlow()

	override fun subscribeAllConversations(
		afterSeq: Long,
		replayLimit: Int
	): Flow<DirectChatRealtimeEvent> = emptyFlow()
}
