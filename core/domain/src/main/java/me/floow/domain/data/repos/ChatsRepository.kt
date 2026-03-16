package me.floow.domain.data.repos

import kotlinx.coroutines.flow.Flow
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatRealtimeEvent

interface ChatsRepository {
	fun observeConversations(): Flow<List<DirectChatConversation>>

	fun observeMessages(
		conversationId: Long,
		limit: Int = 50
	): Flow<DirectChatMessagesPage>

	fun observePinnedMessages(
		conversationId: Long,
		limit: Int = 20
	): Flow<List<DirectChatMessage>>

	suspend fun getOrCreateDirectConversation(peerUserId: String): GetDataResponse<DirectChatConversation>

	suspend fun getOrCreateSavedMessagesConversation(): GetDataResponse<DirectChatConversation>

	suspend fun getConversations(
		limit: Int = 50,
		cursor: String? = null
	): GetDataResponse<DirectChatConversationsPage>

	suspend fun getConversation(
		conversationId: Long
	): GetDataResponse<DirectChatConversation>

	suspend fun getMessages(
		conversationId: Long,
		limit: Int = 50,
		beforeId: Long? = null
	): GetDataResponse<DirectChatMessagesPage>

	suspend fun getMessagesAround(
		conversationId: Long,
		anchorId: Long,
		olderLimit: Int = 30,
		newerLimit: Int = 30
	): GetDataResponse<DirectChatAnchoredMessagesWindow>

	suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int = 30,
		newerLimit: Int = 30
	): GetDataResponse<DirectChatAnchoredMessagesWindow>

	suspend fun refreshLatestMessagesWindow(
		conversationId: Long,
		limit: Int = 50
	): GetDataResponse<DirectChatMessagesPage>

	suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String? = null,
		replyToMessageId: Long? = null
	): GetDataResponse<DirectChatMessage>

	suspend fun retryPendingOutgoingMessages(
		conversationId: Long,
		limit: Int = 50
	)

	suspend fun retryOutgoingMessage(
		conversationId: Long,
		clientMessageId: String
	)

	suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	): UpdateDataResponse

	suspend fun updateMessage(
		conversationId: Long,
		messageId: Long,
		text: String
	): GetDataResponse<DirectChatMessage>

	suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int = 20
	): GetDataResponse<List<DirectChatMessage>>

	suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean
	): GetDataResponse<DirectChatMessage>

	suspend fun storeOutgoingOptimisticMessage(message: DirectChatMessage)

	suspend fun deleteLocalMessage(
		conversationId: Long,
		messageId: Long
	)

	suspend fun deleteLocalMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	)

	suspend fun applyLocalPinnedState(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): DirectChatMessage?

	suspend fun updateLocalMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		status: me.floow.domain.models.MessageDeliveryStatus
	)

	suspend fun updateLocalMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		status: me.floow.domain.models.MessageDeliveryStatus
	)

	suspend fun markReadUpTo(
		conversationId: Long,
		messageId: Long
	): GetDataResponse<DirectChatReadState>

	suspend fun applyLocalReadUpTo(
		conversationId: Long,
		messageId: Long
	): DirectChatReadState?

	suspend fun applyPeerLastReadUpTo(
		conversationId: Long,
		messageId: Long
	)

	suspend fun sendTyping(
		conversationId: Long,
		isTyping: Boolean
	): UpdateDataResponse

	fun subscribeConversation(
		conversationId: Long,
		afterSeq: Long,
		replayLimit: Int = 200
	): Flow<DirectChatRealtimeEvent>

	fun subscribeAllConversations(
		afterSeq: Long,
		replayLimit: Int = 200
	): Flow<DirectChatRealtimeEvent>
}
