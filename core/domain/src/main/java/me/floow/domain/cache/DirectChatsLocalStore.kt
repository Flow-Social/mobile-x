package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatReadState

interface DirectChatsLocalStore {
	fun observeConversations(): Flow<List<DirectChatConversation>>

	suspend fun getConversations(
		limit: Int,
		offset: Int
	): List<DirectChatConversation>

	suspend fun upsertConversations(items: List<DirectChatConversation>)

	suspend fun upsertConversation(item: DirectChatConversation)

	suspend fun getConversationById(conversationId: Long): DirectChatConversation?

	fun observeMessages(
		conversationId: Long,
		limit: Int
	): Flow<DirectChatMessagesPage>

	suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): DirectChatMessagesPage

	suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int,
		newerLimit: Int
	): DirectChatAnchoredMessagesWindow?

	suspend fun upsertMessages(
		conversationId: Long,
		items: List<DirectChatMessage>
	)

	suspend fun replaceLatestMessagesWindow(
		conversationId: Long,
		limit: Int,
		items: List<DirectChatMessage>
	)

	suspend fun upsertMessage(message: DirectChatMessage)

	suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): List<DirectChatMessage>

	fun observePinnedMessages(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessage>>

	suspend fun getMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	): DirectChatMessage?

	suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): DirectChatMessage?

	suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	)

	suspend fun deleteMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	)

	suspend fun applyReadState(state: DirectChatReadState)

	suspend fun applyLocalReadUpTo(
		conversationId: Long,
		messageId: Long
	): DirectChatReadState?

	suspend fun applyPeerLastReadUpTo(
		conversationId: Long,
		messageId: Long
	)

	suspend fun updateMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		deliveryStatus: me.floow.domain.models.MessageDeliveryStatus
	)

	suspend fun updateMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		deliveryStatus: me.floow.domain.models.MessageDeliveryStatus
	)

	suspend fun clear()
}
