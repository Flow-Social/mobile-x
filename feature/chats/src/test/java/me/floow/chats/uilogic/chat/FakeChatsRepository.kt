package me.floow.chats.uilogic.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.MessageDeliveryStatus

class FakeChatsRepository : ChatsRepository {

	var deleteMessageResult: UpdateDataResponse = UpdateDataResponse.Success
	val deletedMessageIds = mutableListOf<Long>()
	val typingEvents = mutableListOf<Pair<Long, Boolean>>()
	val observeMessagesCalls = mutableListOf<Pair<Long, Int>>()
	val refreshLatestMessagesWindowCalls = mutableListOf<Pair<Long, Int>>()
	val getMessagesAroundCalls = mutableListOf<Triple<Long, Long, Pair<Int, Int>>>()
	val getAnchoredMessagesWindowCalls = mutableListOf<Triple<Long, Long, Pair<Int, Int>>>()
	var observedMessagesPage = DirectChatMessagesPage(
		items = emptyList(),
		nextBeforeId = null,
		peerLastReadMessageId = null
	)
	val observedMessagesFlow = MutableStateFlow(observedMessagesPage)
	var getConversationResult: GetDataResponse<DirectChatConversation> =
		GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
	var refreshLatestMessagesWindowResult: GetDataResponse<DirectChatMessagesPage> =
		GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
	var getMessagesAroundResult: GetDataResponse<DirectChatAnchoredMessagesWindow> =
		GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
	var getAnchoredMessagesWindowResult: GetDataResponse<DirectChatAnchoredMessagesWindow> =
		GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)
	val observedConversationsFlow = MutableStateFlow<List<DirectChatConversation>>(emptyList())
	var getConversationsResult: GetDataResponse<DirectChatConversationsPage> =
		GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)

	override fun observeConversations(): Flow<List<DirectChatConversation>> = observedConversationsFlow

	override fun observeMessages(conversationId: Long, limit: Int): Flow<DirectChatMessagesPage> {
		observeMessagesCalls += conversationId to limit
		return observedMessagesFlow
	}

	override fun observePinnedMessages(conversationId: Long, limit: Int): Flow<List<DirectChatMessage>> = emptyFlow()

	override suspend fun getOrCreateDirectConversation(peerUserId: String) =
		GetDataResponse.Error<DirectChatConversation>(me.floow.domain.data.GetDataError.Other)

	override suspend fun getOrCreateSavedMessagesConversation() =
		GetDataResponse.Error<DirectChatConversation>(me.floow.domain.data.GetDataError.Other)

	override suspend fun getConversations(limit: Int, cursor: String?) = getConversationsResult

	override suspend fun getConversation(conversationId: Long) = getConversationResult

	override suspend fun getMessages(conversationId: Long, limit: Int, beforeId: Long?) =
		GetDataResponse.Error<DirectChatMessagesPage>(me.floow.domain.data.GetDataError.Other)

	override suspend fun getMessagesAround(
		conversationId: Long,
		anchorId: Long,
		olderLimit: Int,
		newerLimit: Int,
	): GetDataResponse<DirectChatAnchoredMessagesWindow> {
		getMessagesAroundCalls += Triple(conversationId, anchorId, olderLimit to newerLimit)
		return getMessagesAroundResult
	}

	override suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int,
		newerLimit: Int,
	): GetDataResponse<DirectChatAnchoredMessagesWindow> {
		getAnchoredMessagesWindowCalls += Triple(conversationId, anchorMessageId, olderLimit to newerLimit)
		return getAnchoredMessagesWindowResult
	}

	override suspend fun refreshLatestMessagesWindow(conversationId: Long, limit: Int): GetDataResponse<DirectChatMessagesPage> {
		refreshLatestMessagesWindowCalls += conversationId to limit
		return refreshLatestMessagesWindowResult
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?,
	) = GetDataResponse.Error<DirectChatMessage>(me.floow.domain.data.GetDataError.Other)

	override suspend fun retryPendingOutgoingMessages(conversationId: Long, limit: Int) = Unit

	override suspend fun retryOutgoingMessage(conversationId: Long, clientMessageId: String) = Unit

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): UpdateDataResponse {
		deletedMessageIds.add(messageId)
		return deleteMessageResult
	}

	override suspend fun updateMessage(conversationId: Long, messageId: Long, text: String) =
		GetDataResponse.Error<DirectChatMessage>(me.floow.domain.data.GetDataError.Other)

	override suspend fun getPinnedMessages(conversationId: Long, limit: Int) =
		GetDataResponse.Error<List<DirectChatMessage>>(me.floow.domain.data.GetDataError.Other)

	override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean) =
		GetDataResponse.Error<DirectChatMessage>(me.floow.domain.data.GetDataError.Other)

	override suspend fun storeOutgoingOptimisticMessage(message: DirectChatMessage) = Unit

	override suspend fun deleteLocalMessage(conversationId: Long, messageId: Long) = Unit

	override suspend fun deleteLocalMessageByClientMessageId(conversationId: Long, clientMessageId: String) = Unit

	override suspend fun applyLocalPinnedState(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?,
	): DirectChatMessage? = null

	override suspend fun updateLocalMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		status: MessageDeliveryStatus,
	) = Unit

	override suspend fun updateLocalMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		status: MessageDeliveryStatus,
	) = Unit

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long) =
		GetDataResponse.Error<DirectChatReadState>(me.floow.domain.data.GetDataError.Other)

	override suspend fun applyLocalReadUpTo(conversationId: Long, messageId: Long): DirectChatReadState? = null

	override suspend fun applyPeerLastReadUpTo(conversationId: Long, messageId: Long) = Unit

	override suspend fun sendTyping(conversationId: Long, isTyping: Boolean): UpdateDataResponse {
		typingEvents.add(conversationId to isTyping)
		return UpdateDataResponse.Success
	}

	override fun subscribeConversation(
		conversationId: Long,
		afterSeq: Long,
		replayLimit: Int,
	): Flow<DirectChatRealtimeEvent> = emptyFlow()

	override fun subscribeAllConversations(afterSeq: Long, replayLimit: Int): Flow<DirectChatRealtimeEvent> =
		emptyFlow()
}
