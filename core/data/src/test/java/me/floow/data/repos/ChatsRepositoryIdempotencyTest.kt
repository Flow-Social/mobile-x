package me.floow.data.repos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.models.*
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.models.*
import me.floow.domain.realtime.DirectChatRealtimeDecision
import me.floow.domain.utils.Logger
import me.floow.domain.api.ChatsRealtimeApi
import me.floow.domain.data.repos.DirectMessagesOutgoingRetryScheduler
import me.floow.domain.data.GetDataResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatsRepositoryIdempotencyTest {

	private class FakeChatsApi : ChatsApi {
		var conflictResponse = false
		override suspend fun sendMessage(
			conversationId: Long,
			text: String,
			clientMessageId: String?,
			replyToMessageId: Long?
		): ChatsSendMessageResponse {
			if (conflictResponse) return ChatsSendMessageResponse.Conflict
			throw NotImplementedError()
		}

		override suspend fun getOrCreateDirectConversation(peerUserId: String): ChatsGetOrCreateDirectResponse = throw NotImplementedError()
		override suspend fun getConversations(limit: Int, cursor: String?): ChatsGetConversationsResponse = throw NotImplementedError()
		override suspend fun getConversation(conversationId: Long): ChatsGetConversationResponse = throw NotImplementedError()
		override suspend fun getMessages(conversationId: Long, limit: Int, beforeId: Long?): ChatsGetMessagesResponse = throw NotImplementedError()
		override suspend fun getMessagesAround(conversationId: Long, anchorId: Long, olderLimit: Int, newerLimit: Int): ChatsGetMessagesAroundResponse = throw NotImplementedError()
		override suspend fun deleteMessage(conversationId: Long, messageId: Long): ChatsDeleteMessageResponse = throw NotImplementedError()
		override suspend fun updateMessage(conversationId: Long, messageId: Long, text: String): ChatsUpdateMessageResponse = throw NotImplementedError()
		override suspend fun getPinnedMessages(conversationId: Long, limit: Int): ChatsGetPinnedMessagesResponse = throw NotImplementedError()
		override suspend fun pinMessage(conversationId: Long, messageId: Long): ChatsPinMessageResponse = throw NotImplementedError()
		override suspend fun unpinMessage(conversationId: Long, messageId: Long): ChatsUnpinMessageResponse = throw NotImplementedError()
		override suspend fun markReadUpTo(conversationId: Long, messageId: Long): ChatsMarkReadUpToResponse = throw NotImplementedError()
		override suspend fun sendTyping(conversationId: Long, isTyping: Boolean): ChatsTypingResponse = throw NotImplementedError()
		override suspend fun getOrCreateSavedMessagesConversation(): ChatsGetOrCreateDirectResponse = throw NotImplementedError()
	}

	private class FakeDirectChatsLocalStore : DirectChatsLocalStore {
		var upsertedMessage: DirectChatMessage? = null
		var stubbedExistingMessage: DirectChatMessage? = null

		override suspend fun upsertMessage(message: DirectChatMessage) {
			upsertedMessage = message
		}

		override fun observeConversations(): Flow<List<DirectChatConversation>> = emptyFlow()
		override suspend fun getConversations(limit: Int, offset: Int): List<DirectChatConversation> = throw NotImplementedError()
		override suspend fun upsertConversations(items: List<DirectChatConversation>) = throw NotImplementedError()
		override suspend fun upsertConversation(item: DirectChatConversation) = throw NotImplementedError()
		override suspend fun getConversationById(conversationId: Long): DirectChatConversation? = throw NotImplementedError()
		override fun observeMessages(conversationId: Long, limit: Int): Flow<DirectChatMessagesPage> = emptyFlow()
		override fun observePinnedMessages(conversationId: Long, limit: Int): Flow<List<DirectChatMessage>> = emptyFlow()
		
		override suspend fun getMessages(conversationId: Long, limit: Int, beforeId: Long?): DirectChatMessagesPage {
			return DirectChatMessagesPage(
				items = listOfNotNull(stubbedExistingMessage),
				nextBeforeId = null,
				peerLastReadMessageId = null
			)
		}
		override suspend fun getMessageByClientMessageId(conversationId: Long, clientMessageId: String): DirectChatMessage? {
			return stubbedExistingMessage?.takeIf { it.clientMessageId == clientMessageId }
		}
		override suspend fun getAnchoredMessagesWindow(conversationId: Long, anchorMessageId: Long, olderLimit: Int, newerLimit: Int): DirectChatAnchoredMessagesWindow? = throw NotImplementedError()
		override suspend fun upsertMessages(conversationId: Long, items: List<DirectChatMessage>) = throw NotImplementedError()
		override suspend fun replaceLatestMessagesWindow(conversationId: Long, limit: Int, items: List<DirectChatMessage>) = throw NotImplementedError()
		override suspend fun getPinnedMessages(conversationId: Long, limit: Int): List<DirectChatMessage> = throw NotImplementedError()
		override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean, pinnedAt: Long?, pinnedByUserId: String?): DirectChatMessage? = throw NotImplementedError()
		override suspend fun deleteMessage(conversationId: Long, messageId: Long) = throw NotImplementedError()
		override suspend fun deleteMessageByClientMessageId(conversationId: Long, clientMessageId: String) = throw NotImplementedError()
		override suspend fun applyReadState(state: DirectChatReadState) = throw NotImplementedError()
		override suspend fun applyLocalReadUpTo(conversationId: Long, messageId: Long): DirectChatReadState? = throw NotImplementedError()
		override suspend fun applyPeerLastReadUpTo(conversationId: Long, messageId: Long) = throw NotImplementedError()
		override suspend fun updateMessageDeliveryStatus(conversationId: Long, messageId: Long, deliveryStatus: me.floow.domain.models.MessageDeliveryStatus) = throw NotImplementedError()
		override suspend fun updateMessageDeliveryStatusByClientMessageId(conversationId: Long, clientMessageId: String, deliveryStatus: me.floow.domain.models.MessageDeliveryStatus) = throw NotImplementedError()
		override suspend fun clear() = throw NotImplementedError()
	}

	private val mockChatsApi = FakeChatsApi()
	private val mockLocalStore = FakeDirectChatsLocalStore()
	private val stubRealtimeApi = object : ChatsRealtimeApi {
		override fun subscribeConversation(conversationId: Long, afterSeq: Long, replayLimit: Int): Flow<ChatsRealtimeEvent> = emptyFlow()
		override fun subscribeAllConversations(afterSeq: Long, replayLimit: Int): Flow<ChatsRealtimeEvent> = emptyFlow()
		override fun sendPushAck(request: me.floow.domain.api.models.PushAckRequest): Boolean = false
	}
	private val stubLogger = object : Logger {
		override fun d(tag: String?, message: String) {}
	}
	private val stubRetryScheduler = object : DirectMessagesOutgoingRetryScheduler {
		override fun enqueue(conversationId: Long) {}
	}

	private val repository = ChatsRepositoryImpl(
		logger = stubLogger,
		chatsApi = mockChatsApi,
		chatsRealtimeApi = stubRealtimeApi,
		localStore = mockLocalStore,
		outgoingRetryScheduler = stubRetryScheduler
	)

	@Test
	fun `sendMessage updates status to SENT on HTTP 409 Conflict`() = runBlocking {
		// Given
		val conversationId = 1L
		val text = "test message"
		val clientMessageId = "client-uuid"
		
		mockChatsApi.conflictResponse = true
		mockLocalStore.stubbedExistingMessage = DirectChatMessage(
			id = 0L,
			conversationId = conversationId,
			sender = DirectChatPeer("peer1", "username", "name", null),
			text = text,
			replyToMessageId = null,
			replyToMessageText = null,
			isPinned = false,
			pinnedAt = null,
			pinnedByUserId = null,
			deliveryStatus = MessageDeliveryStatus.SENDING,
			clientMessageId = clientMessageId,
			createdAt = 0L,
			updatedAt = 0L
		)

		// When
		val response = repository.sendMessage(conversationId, text, clientMessageId, null)

		// Then
		// The repository should still return Success based on idempotency rules
		// meaning the payload is considered implicitly handled.
		assertTrue(response is GetDataResponse.Success)
		
		// And verify the local store upsert was called with SENT status
		val lastValue = mockLocalStore.upsertedMessage!!
		assertEquals(MessageDeliveryStatus.SENT, lastValue.deliveryStatus)
		assertEquals(clientMessageId, lastValue.clientMessageId)
	}
}
