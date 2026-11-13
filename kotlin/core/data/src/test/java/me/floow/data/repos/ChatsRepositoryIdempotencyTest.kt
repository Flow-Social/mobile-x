package me.floow.data.repos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
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
		var latestMessages: List<ChatMessageItem> = emptyList()
		override suspend fun sendMessage(
			conversationId: Long,
			text: String,
			clientMessageId: String?,
			replyToMessageId: Long?
		): ChatsSendMessageResponse {
			if (conflictResponse) return ChatsSendMessageResponse.Conflict(latestMessages.firstOrNull())
			throw NotImplementedError()
		}

		override suspend fun getMessageByClientMessageId(
			conversationId: Long,
			clientMessageId: String
		): ChatsGetMessageByClientMessageIdResponse {
			return latestMessages.firstOrNull { it.conversationId == conversationId && it.clientMessageId == clientMessageId }
				?.let { ChatsGetMessageByClientMessageIdResponse.Success(it) }
				?: ChatsGetMessageByClientMessageIdResponse.NotFound
		}

		override suspend fun sendVideoCircleMessage(
			conversationId: Long,
			clientMessageId: String?,
			mediaUrl: String,
			mediaObjectKey: String,
			mediaSizeBytes: Long,
			mediaDurationMs: Long,
			mediaWidth: Int,
			mediaHeight: Int,
			thumbnailUrl: String?,
			replyToMessageId: Long?
		): ChatsSendMessageResponse = throw NotImplementedError()

		override suspend fun sendVoiceMessage(
			conversationId: Long,
			clientMessageId: String?,
			mediaUrl: String,
			mediaObjectKey: String,
			mediaSizeBytes: Long,
			mediaDurationMs: Long,
			waveform: List<Int>,
			replyToMessageId: Long?
		): ChatsSendMessageResponse = throw NotImplementedError()

		override suspend fun sendImageMessage(
			conversationId: Long,
			clientMessageId: String?,
			mediaUrl: String,
			mediaObjectKey: String,
			mediaSizeBytes: Long,
			mediaWidth: Int,
			mediaHeight: Int,
			caption: String?,
			groupId: String?,
			position: Int,
			replyToMessageId: Long?,
			thumbnailUrl: String?
		): ChatsSendMessageResponse = throw NotImplementedError()

		override suspend fun sendAlbumMessage(
			conversationId: Long,
			items: List<me.floow.domain.api.models.ChatAlbumItemPayload>,
			caption: String?,
			replyToMessageId: Long?,
			idempotencyKey: String,
		): me.floow.domain.api.models.ChatsSendAlbumMessageResponse = throw NotImplementedError()

		override suspend fun getOrCreateDirectConversation(peerUserId: String): ChatsGetOrCreateDirectResponse = throw NotImplementedError()
		override suspend fun getConversations(limit: Int, cursor: String?): ChatsGetConversationsResponse = throw NotImplementedError()
		override suspend fun getConversation(conversationId: Long): ChatsGetConversationResponse = throw NotImplementedError()
		override suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean): ChatsGetConversationResponse = throw NotImplementedError()
		override suspend fun getMessages(conversationId: Long, limit: Int, beforeId: Long?): ChatsGetMessagesResponse =
			ChatsGetMessagesResponse.Success(latestMessages, nextBeforeId = null, peerLastReadMessageId = null)
		override suspend fun getMessagesAround(conversationId: Long, anchorId: Long, olderLimit: Int, newerLimit: Int): ChatsGetMessagesAroundResponse = throw NotImplementedError()
		override suspend fun deleteMessage(conversationId: Long, messageId: Long): ChatsDeleteMessageResponse = throw NotImplementedError()
		override suspend fun deleteMessagesBatch(conversationId: Long, messageIds: List<Long>): me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse = throw NotImplementedError()
		override suspend fun updateMessage(conversationId: Long, messageId: Long, text: String): ChatsUpdateMessageResponse = throw NotImplementedError()
		override suspend fun getPinnedMessages(conversationId: Long, limit: Int): ChatsGetPinnedMessagesResponse = throw NotImplementedError()
		override suspend fun pinMessage(conversationId: Long, messageId: Long): ChatsPinMessageResponse = throw NotImplementedError()
		override suspend fun unpinMessage(conversationId: Long, messageId: Long): ChatsUnpinMessageResponse = throw NotImplementedError()
		override suspend fun markReadUpTo(conversationId: Long, messageId: Long): ChatsMarkReadUpToResponse = throw NotImplementedError()
		override suspend fun sendTyping(conversationId: Long, isTyping: Boolean, action: String): ChatsTypingResponse = throw NotImplementedError()
		override suspend fun deleteConversation(conversationId: Long, forEveryone: Boolean): me.floow.domain.api.models.ChatsDeleteConversationResponse = throw NotImplementedError()
		override suspend fun getOrCreateSavedMessagesConversation(): ChatsGetOrCreateDirectResponse = throw NotImplementedError()
	}

	private class FakeDirectChatsLocalStore : DirectChatsLocalStore {
		var upsertedMessage: DirectChatMessage? = null
		var stubbedExistingMessage: DirectChatMessage? = null
		var outboxCommand: DirectChatOutboxCommand? = null

		override suspend fun upsertMessage(message: DirectChatMessage) {
			upsertedMessage = message
			stubbedExistingMessage = message
		}

		override suspend fun deleteConversation(conversationId: Long) {}

		override fun observeConversations(): Flow<List<DirectChatConversation>> = emptyFlow()
		override fun observeLocalReadStates(): Flow<Map<Long, DirectChatReadState>> = emptyFlow()
		override suspend fun getConversations(limit: Int, offset: Int): List<DirectChatConversation> = throw NotImplementedError()
		override suspend fun upsertConversations(items: List<DirectChatConversation>) = throw NotImplementedError()
		override suspend fun upsertConversation(item: DirectChatConversation) = throw NotImplementedError()
		override suspend fun getConversationById(conversationId: Long): DirectChatConversation? = throw NotImplementedError()
		override suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean, pinnedAt: Long?): DirectChatConversation? = throw NotImplementedError()
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
		override suspend fun enqueueOutgoingText(message: DirectChatMessage): DirectChatOutboxCommand? {
			stubbedExistingMessage = message
			return DirectChatOutboxCommand(
				clientMessageId = message.clientMessageId ?: return null,
				conversationId = message.conversationId,
				text = message.text,
				contentType = message.contentType,
				replyToMessageId = message.replyToMessageId,
				state = DirectChatOutboxState.QUEUED,
				attemptCount = 0,
				nextAttemptAt = 0L,
				lastError = null,
				createdAt = message.createdAt,
				updatedAt = message.updatedAt
			).also { outboxCommand = it }
		}
		var enqueuedMediaMessage: DirectChatMessage? = null
		var enqueuedLocalFilePath: String? = null
		override suspend fun enqueueOutgoingMedia(
			message: DirectChatMessage,
			localFilePath: String,
			mediaMimeType: String,
			mediaSizeBytes: Long,
			mediaDurationMs: Long?,
			mediaWidth: Int?,
			mediaHeight: Int?,
			mediaWaveform: String?,
			groupId: String?,
			mediaPosition: Int
		): DirectChatOutboxCommand? {
			enqueuedMediaMessage = message
			enqueuedLocalFilePath = localFilePath
			return null
		}
		override suspend fun getActiveMediaCommands(): List<DirectChatOutboxCommand> = emptyList()
		override fun observeActiveMediaCommands(): Flow<List<DirectChatOutboxCommand>> = emptyFlow()
		override suspend fun updateOutboxMediaStage(
			clientMessageId: String,
			uploadStage: me.floow.domain.models.DirectChatUploadStage,
			mediaUrl: String?,
			mediaThumbnailUrl: String?,
			error: String?
		) {}
		override fun observePendingOutboxMessages(conversationId: Long): Flow<List<DirectChatMessage>> = emptyFlow()
		override suspend fun getOutgoingCommand(clientMessageId: String): DirectChatOutboxCommand? {
			return outboxCommand?.takeIf { it.clientMessageId == clientMessageId }
		}
		override suspend fun getDueOutgoingCommands(now: Long, limit: Int): List<DirectChatOutboxCommand> =
			listOfNotNull(outboxCommand)
		override suspend fun markOutgoingSending(clientMessageId: String, now: Long): DirectChatOutboxCommand? {
			outboxCommand = outboxCommand?.copy(state = DirectChatOutboxState.SENDING, updatedAt = now)
			stubbedExistingMessage = stubbedExistingMessage?.copy(deliveryStatus = MessageDeliveryStatus.SENDING)
			return outboxCommand
		}
		override suspend fun markOutgoingFailed(clientMessageId: String, error: String?, nextAttemptAt: Long, now: Long): DirectChatOutboxCommand? {
			outboxCommand = outboxCommand?.copy(
				state = DirectChatOutboxState.FAILED,
				attemptCount = outboxCommand?.attemptCount?.plus(1) ?: 1,
				nextAttemptAt = nextAttemptAt,
				lastError = error,
				updatedAt = now
			)
			stubbedExistingMessage = stubbedExistingMessage?.copy(deliveryStatus = MessageDeliveryStatus.FAILED)
			return outboxCommand
		}
		override suspend fun markOutgoingSent(clientMessageId: String, serverMessage: DirectChatMessage, now: Long): DirectChatMessage? {
			outboxCommand = outboxCommand?.copy(state = DirectChatOutboxState.SENT, updatedAt = now)
			return serverMessage.copy(deliveryStatus = MessageDeliveryStatus.SENT).also {
				upsertedMessage = it
				stubbedExistingMessage = it
			}
		}
		override suspend fun cancelOutgoing(conversationId: Long, clientMessageId: String) {
			outboxCommand = outboxCommand?.copy(state = DirectChatOutboxState.CANCELLED)
			stubbedExistingMessage = null
		}
		override suspend fun getPendingOutgoingMessages(limit: Int): List<DirectChatMessage> =
			listOfNotNull(stubbedExistingMessage)
		override suspend fun getAnchoredMessagesWindow(conversationId: Long, anchorMessageId: Long, olderLimit: Int, newerLimit: Int): DirectChatAnchoredMessagesWindow? = throw NotImplementedError()
		override suspend fun upsertMessages(conversationId: Long, items: List<DirectChatMessage>) = throw NotImplementedError()
		override suspend fun replaceLatestMessagesWindow(conversationId: Long, limit: Int, items: List<DirectChatMessage>) {
			items.firstOrNull { it.clientMessageId == stubbedExistingMessage?.clientMessageId }?.let { stubbedExistingMessage = it }
		}
		override suspend fun getPinnedMessages(conversationId: Long, limit: Int): List<DirectChatMessage> = throw NotImplementedError()
		override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean, pinnedAt: Long?, pinnedByUserId: String?): DirectChatMessage? = throw NotImplementedError()
		override suspend fun deleteMessageByClientMessageId(conversationId: Long, clientMessageId: String) = throw NotImplementedError()
		var appliedPeerLastRead: Pair<Long, Long>? = null
		override suspend fun applyReadState(state: DirectChatReadState) {}
		override suspend fun applyLocalReadUpTo(conversationId: Long, messageId: Long): DirectChatReadState? = throw NotImplementedError()
		override suspend fun applyPeerLastReadUpTo(conversationId: Long, messageId: Long) {
			appliedPeerLastRead = conversationId to messageId
		}
		override suspend fun updateMessageDeliveryStatus(conversationId: Long, messageId: Long, deliveryStatus: me.floow.domain.models.MessageDeliveryStatus) = throw NotImplementedError()
		override suspend fun deleteMessage(conversationId: Long, messageId: Long) {}
		override suspend fun deleteMessages(conversationId: Long, messageIds: List<Long>) {}
		override suspend fun updateMessageDeliveryStatusByClientMessageId(conversationId: Long, clientMessageId: String, deliveryStatus: me.floow.domain.models.MessageDeliveryStatus) = throw NotImplementedError()
		override suspend fun clear() = throw NotImplementedError()
	}

	private val mockChatsApi = FakeChatsApi()
	private val mockLocalStore = FakeDirectChatsLocalStore()
	private val realtimeEventsFlow = kotlinx.coroutines.flow.MutableSharedFlow<ChatsRealtimeEvent>(extraBufferCapacity = 16)
	private val stubRealtimeApi = object : ChatsRealtimeApi {
		override fun subscribeConversation(conversationId: Long, afterSeq: Long, replayLimit: Int): Flow<ChatsRealtimeEvent> = realtimeEventsFlow
		override fun subscribeAllConversations(afterSeq: Long, replayLimit: Int): Flow<ChatsRealtimeEvent> = realtimeEventsFlow
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
	fun `sendMessage resolves HTTP 409 Conflict through server message with same client id`() = runBlocking {
		// Given
		val conversationId = 1L
		val text = "test message"
		val clientMessageId = "client-uuid"
		
		mockChatsApi.conflictResponse = true
		mockChatsApi.latestMessages = listOf(
			ChatMessageItem(
				id = 42L,
				conversationId = conversationId,
				sender = ChatUserItem("peer1", "username", "name", null),
				text = text,
				clientMessageId = clientMessageId,
				replyToMessageId = null,
				replyToMessageText = null,
				isPinned = false,
				pinnedAt = null,
				pinnedByUserId = null,
				createdAt = 10L,
				updatedAt = 10L
			)
		)
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
		assertTrue(response is GetDataResponse.Success)
		
		val lastValue = mockLocalStore.upsertedMessage!!
		assertEquals(42L, lastValue.id)
		assertEquals(MessageDeliveryStatus.SENT, lastValue.deliveryStatus)
		assertEquals(clientMessageId, lastValue.clientMessageId)
	}

	@Test
	fun `storeOutgoingOptimisticMessage with media enqueues media command in local store`() = runBlocking {
		// Given
		val conversationId = 5L
		val clientMessageId = "media-client-123"
		val localPath = "/cache/media/circle.mp4"
		val mediaMessage = DirectChatMessage(
			id = 0L,
			conversationId = conversationId,
			sender = DirectChatPeer("me", "my_user", "My Name", null),
			text = "",
			contentType = "video_circle",
			media = DirectChatMessageMedia(
				url = "",
				objectKey = "",
				mimeType = "video/mp4",
				sizeBytes = 1048576L,
				durationMs = 5000L,
				localPath = localPath,
				width = 720,
				height = 720
			),
			replyToMessageId = null,
			replyToMessageText = null,
			isPinned = false,
			pinnedAt = null,
			pinnedByUserId = null,
			deliveryStatus = MessageDeliveryStatus.SENDING,
			clientMessageId = clientMessageId,
			createdAt = 1000L,
			updatedAt = 1000L
		)

		// When
		repository.storeOutgoingOptimisticMessage(mediaMessage)

		// Then
		assertEquals(mediaMessage, mockLocalStore.enqueuedMediaMessage)
		assertEquals(localPath, mockLocalStore.enqueuedLocalFilePath)
	}

	@Test
	fun `readUpToUpdated realtime event applies peer last read to local store`() = runBlocking<Unit> {
		// Given
		val conversationId = 1L
		val peerMessageId = 88L
		var collectedEvent: DirectChatRealtimeEvent? = null

		val job = launch(Dispatchers.Unconfined) {
			repository.subscribeConversation(conversationId = conversationId, afterSeq = 0L, replayLimit = 0)
				.collect { event ->
					collectedEvent = event
				}
		}

		// When
		realtimeEventsFlow.emit(
			ChatsRealtimeEvent.ReadUpToUpdated(
				conversationId = conversationId,
				eventId = "evt-1",
				actorUserId = "peer-user",
				actorLastReadMessageId = peerMessageId,
				seq = 1L,
				lastReadMessageId = 50L,
				unreadCount = 0,
				firstUnreadId = null,
				maxMessageId = 100L,
				readStateVersion = 1L
			)
		)

		// Then
		assertTrue(collectedEvent is DirectChatRealtimeEvent.ReadUpToUpdated)
		assertEquals(conversationId to peerMessageId, mockLocalStore.appliedPeerLastRead)

		job.cancel()
	}
}
