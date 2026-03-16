package me.floow.data.repos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.ChatsRealtimeApi
import me.floow.domain.api.models.ChatConversationItem
import me.floow.domain.api.models.ChatMessageItem
import me.floow.domain.api.models.ChatReadStateItem
import me.floow.domain.api.models.ChatsGetConversationResponse
import me.floow.domain.api.models.ChatsGetConversationsResponse
import me.floow.domain.api.models.ChatsGetMessagesResponse
import me.floow.domain.api.models.ChatsGetMessagesAroundResponse
import me.floow.domain.api.models.ChatsGetOrCreateDirectResponse
import me.floow.domain.api.models.ChatsDeleteMessageResponse
import me.floow.domain.api.models.ChatsMarkReadUpToResponse
import me.floow.domain.api.models.ChatsRealtimeEvent
import me.floow.domain.api.models.ChatsGetPinnedMessagesResponse
import me.floow.domain.api.models.ChatsPinMessageResponse
import me.floow.domain.api.models.ChatsSendMessageResponse
import me.floow.domain.api.models.ChatsTypingResponse
import me.floow.domain.api.models.ChatsUnpinMessageResponse
import me.floow.domain.api.models.ChatsUpdateMessageResponse
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.DirectMessagesOutgoingRetryScheduler
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.utils.Logger

class ChatsRepositoryImpl(
	private val logger: Logger,
	private val chatsApi: ChatsApi,
	private val chatsRealtimeApi: ChatsRealtimeApi,
	private val localStore: DirectChatsLocalStore,
	private val outgoingRetryScheduler: DirectMessagesOutgoingRetryScheduler,
) : ChatsRepository {
	private companion object {
		private const val CONVERSATIONS_CACHE_TTL_MS = 12_000L
	}

	private data class ConversationsRequestKey(
		val limit: Int,
		val cursor: String?
	)

	private data class ConversationsCacheEntry(
		val fetchedAtMs: Long,
		val data: GetDataResponse<DirectChatConversationsPage>
	)

	private val inFlightOutgoingClientMessageIds = mutableSetOf<String>()
	private val retryAttemptedClientMessageIds = mutableSetOf<String>()
	private val conversationsRequestMutex = Mutex()
	private val inFlightConversationsRequests = mutableMapOf<
		ConversationsRequestKey,
		CompletableDeferred<GetDataResponse<DirectChatConversationsPage>>
	>()
	private val conversationsMemoryCache = mutableMapOf<ConversationsRequestKey, ConversationsCacheEntry>()
	private val savedMessagesRequestMutex = Mutex()
	private var inFlightSavedMessagesRequest: CompletableDeferred<GetDataResponse<DirectChatConversation>>? = null

	private suspend fun invalidateConversationsMemoryCache() {
		conversationsRequestMutex.withLock {
			conversationsMemoryCache.clear()
		}
	}

	override fun observeConversations(): Flow<List<DirectChatConversation>> {
		return localStore.observeConversations()
	}

	override fun observeMessages(
		conversationId: Long,
		limit: Int
	): Flow<DirectChatMessagesPage> {
		return localStore.observeMessages(
			conversationId = conversationId,
			limit = limit.coerceAtLeast(1)
		)
	}

	override fun observePinnedMessages(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessage>> {
		return localStore.observePinnedMessages(
			conversationId = conversationId,
			limit = limit.coerceAtLeast(1)
		)
	}

	override suspend fun getOrCreateDirectConversation(peerUserId: String): GetDataResponse<DirectChatConversation> {
		return when (val response = chatsApi.getOrCreateDirectConversation(peerUserId = peerUserId)) {
			is ChatsGetOrCreateDirectResponse.Success -> {
				val conversation = response.conversation.toDomain()
				localStore.upsertConversation(conversation)
				invalidateConversationsMemoryCache()
				GetDataResponse.Success(conversation)
			}

			ChatsGetOrCreateDirectResponse.Error -> {
				logger.d("ChatsRepositoryImpl.getOrCreateDirectConversation", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getOrCreateSavedMessagesConversation(): GetDataResponse<DirectChatConversation> {
		var inFlightDeferred: CompletableDeferred<GetDataResponse<DirectChatConversation>>? = null
		var ownsRequest = false
		savedMessagesRequestMutex.withLock {
			val existing = inFlightSavedMessagesRequest
			if (existing != null) {
				inFlightDeferred = existing
			} else {
				val created = CompletableDeferred<GetDataResponse<DirectChatConversation>>()
				inFlightSavedMessagesRequest = created
				inFlightDeferred = created
				ownsRequest = true
			}
		}
		val request = inFlightDeferred ?: return GetDataResponse.Error(error = GetDataError.Other)
		if (!ownsRequest) return request.await()

		try {
			val result = try {
				when (val response = chatsApi.getOrCreateSavedMessagesConversation()) {
					is ChatsGetOrCreateDirectResponse.Success -> {
						val conversation = response.conversation.toDomain()
						localStore.upsertConversation(conversation)
						invalidateConversationsMemoryCache()
						GetDataResponse.Success(conversation)
					}

					ChatsGetOrCreateDirectResponse.Error -> {
						logger.d("ChatsRepositoryImpl.getOrCreateSavedMessagesConversation", "Failure response")
						GetDataResponse.Error(error = GetDataError.Other)
					}
				}
			} catch (throwable: Throwable) {
				if (throwable is CancellationException) throw throwable
				logger.d(
					"ChatsRepositoryImpl.getOrCreateSavedMessagesConversation",
					"Exception: ${throwable.message}"
				)
				GetDataResponse.Error(error = GetDataError.Other)
			}
			request.complete(result)
			return result
		} catch (ce: CancellationException) {
			request.cancel(ce)
			throw ce
		} finally {
			savedMessagesRequestMutex.withLock {
				if (inFlightSavedMessagesRequest === request) {
					inFlightSavedMessagesRequest = null
				}
			}
		}
	}

	override suspend fun getConversations(
		limit: Int,
		cursor: String?
	): GetDataResponse<DirectChatConversationsPage> {
		val requestKey = ConversationsRequestKey(
			limit = limit.coerceAtLeast(1),
			cursor = cursor?.trim()?.takeIf(String::isNotEmpty)
		)
		val nowMs = System.currentTimeMillis()

		var cachedResult: GetDataResponse<DirectChatConversationsPage>? = null
		var inFlightDeferred: CompletableDeferred<GetDataResponse<DirectChatConversationsPage>>? = null
		var ownsRequest = false
		conversationsRequestMutex.withLock {
			val cached = conversationsMemoryCache[requestKey]
			if (cached != null && nowMs - cached.fetchedAtMs <= CONVERSATIONS_CACHE_TTL_MS) {
				cachedResult = cached.data
				return@withLock
			}
			val existing = inFlightConversationsRequests[requestKey]
			if (existing != null) {
				inFlightDeferred = existing
			} else {
				val created = CompletableDeferred<GetDataResponse<DirectChatConversationsPage>>()
				inFlightConversationsRequests[requestKey] = created
				inFlightDeferred = created
				ownsRequest = true
			}
		}
		cachedResult?.let { return it }
		val request = inFlightDeferred ?: return GetDataResponse.Error(error = GetDataError.Other)
		if (!ownsRequest) return request.await()

		try {
			val result = fetchConversationsWithFallback(
				limit = requestKey.limit,
				cursor = requestKey.cursor
			)
			request.complete(result)
			conversationsRequestMutex.withLock {
				conversationsMemoryCache[requestKey] = ConversationsCacheEntry(
					fetchedAtMs = System.currentTimeMillis(),
					data = result
				)
			}
			return result
		} catch (ce: CancellationException) {
			request.cancel(ce)
			throw ce
		} finally {
			conversationsRequestMutex.withLock {
				if (inFlightConversationsRequests[requestKey] === request) {
					inFlightConversationsRequests.remove(requestKey)
				}
			}
		}
	}

	private suspend fun fetchConversationsWithFallback(
		limit: Int,
		cursor: String?
	): GetDataResponse<DirectChatConversationsPage> {
		return try {
			when (val response = chatsApi.getConversations(limit = limit, cursor = cursor)) {
				is ChatsGetConversationsResponse.Success -> {
					val items = response.items.map(ChatConversationItem::toDomain)
					localStore.upsertConversations(items)
					GetDataResponse.Success(
						DirectChatConversationsPage(
							items = items,
							nextCursor = response.nextCursor,
							hasMore = response.hasMore
						)
					)
				}

				ChatsGetConversationsResponse.Error -> {
					logger.d("ChatsRepositoryImpl.getConversations", "Failure response")
					val cached = localStore.getConversations(limit = limit.coerceAtLeast(1), offset = 0)
					if (cached.isNotEmpty()) {
						GetDataResponse.Success(
							DirectChatConversationsPage(
								items = cached,
								nextCursor = null,
								hasMore = false
							)
						)
					} else {
						GetDataResponse.Error(error = GetDataError.Other)
					}
				}
			}
		} catch (throwable: Throwable) {
			if (throwable is CancellationException) throw throwable
			logger.d(
				"ChatsRepositoryImpl.getConversations",
				"Exception: ${throwable.message}"
			)
			val cached = localStore.getConversations(limit = limit.coerceAtLeast(1), offset = 0)
			if (cached.isNotEmpty()) {
				GetDataResponse.Success(
					DirectChatConversationsPage(
						items = cached,
						nextCursor = null,
						hasMore = false
					)
				)
			} else {
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getConversation(conversationId: Long): GetDataResponse<DirectChatConversation> {
		return when (val response = chatsApi.getConversation(conversationId = conversationId)) {
			is ChatsGetConversationResponse.Success -> {
				val conversation = response.conversation.toDomain()
				localStore.upsertConversation(conversation)
				invalidateConversationsMemoryCache()
				GetDataResponse.Success(conversation)
			}

			ChatsGetConversationResponse.NotFound,
			ChatsGetConversationResponse.Error -> {
				logger.d("ChatsRepositoryImpl.getConversation", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): GetDataResponse<DirectChatMessagesPage> {
		return when (
			val response = chatsApi.getMessages(
				conversationId = conversationId,
				limit = limit,
				beforeId = beforeId
			)
		) {
			is ChatsGetMessagesResponse.Success -> {
				val items = response.items
					.map(ChatMessageItem::toDomain)
					.sortedBy(DirectChatMessage::id)
				localStore.upsertMessages(
					conversationId = conversationId,
					items = items
				)
				GetDataResponse.Success(
					DirectChatMessagesPage(
						items = items,
						nextBeforeId = response.nextBeforeId,
						peerLastReadMessageId = response.peerLastReadMessageId
					)
				)
			}

			ChatsGetMessagesResponse.NotFound,
			ChatsGetMessagesResponse.Error -> {
				logger.d("ChatsRepositoryImpl.getMessages", "Failure response")
				val cached = localStore.getMessages(
					conversationId = conversationId,
					limit = limit.coerceAtLeast(1),
					beforeId = beforeId
				)
				if (cached.items.isNotEmpty()) {
					GetDataResponse.Success(cached)
				} else {
					GetDataResponse.Error(error = GetDataError.Other)
				}
			}
		}
	}

	override suspend fun getMessagesAround(
		conversationId: Long,
		anchorId: Long,
		olderLimit: Int,
		newerLimit: Int
	): GetDataResponse<DirectChatAnchoredMessagesWindow> {
		return when (
			val response = chatsApi.getMessagesAround(
				conversationId = conversationId,
				anchorId = anchorId,
				olderLimit = olderLimit,
				newerLimit = newerLimit
			)
		) {
			is ChatsGetMessagesAroundResponse.Success -> {
				val items = response.items
					.map(ChatMessageItem::toDomain)
					.sortedBy(DirectChatMessage::id)
				localStore.upsertMessages(
					conversationId = conversationId,
					items = items
				)
				val anchorIndex = response.anchorIndex
					.takeIf { it in items.indices }
					?: items.indexOfFirst { it.id == response.anchorId }
				val resolvedAnchorIndex = anchorIndex.takeIf { it >= 0 } ?: 0
				GetDataResponse.Success(
					DirectChatAnchoredMessagesWindow(
						items = items,
						anchorMessageId = response.anchorId,
						anchorIndex = resolvedAnchorIndex,
						hasOlderMessages = response.hasOlder,
						newerCachedCount = if (resolvedAnchorIndex in items.indices) {
							items.size - resolvedAnchorIndex - 1
						} else {
							0
						},
						latestCachedMessageId = items.maxOfOrNull(DirectChatMessage::id) ?: 0L,
						peerLastReadMessageId = response.peerLastReadMessageId
					)
				)
			}

			ChatsGetMessagesAroundResponse.NotFound,
			ChatsGetMessagesAroundResponse.Error -> {
				logger.d("ChatsRepositoryImpl.getMessagesAround", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int,
		newerLimit: Int
	): GetDataResponse<DirectChatAnchoredMessagesWindow> {
		val cached = localStore.getAnchoredMessagesWindow(
			conversationId = conversationId,
			anchorMessageId = anchorMessageId,
			olderLimit = olderLimit.coerceAtLeast(0),
			newerLimit = newerLimit.coerceAtLeast(0)
		)
		return if (cached != null && cached.items.isNotEmpty()) {
			GetDataResponse.Success(cached)
		} else {
			GetDataResponse.Error(error = GetDataError.Other)
		}
	}

	override suspend fun refreshLatestMessagesWindow(
		conversationId: Long,
		limit: Int
	): GetDataResponse<DirectChatMessagesPage> {
		return when (
			val response = chatsApi.getMessages(
				conversationId = conversationId,
				limit = limit,
				beforeId = null
			)
		) {
			is ChatsGetMessagesResponse.Success -> {
				val items = response.items
					.map(ChatMessageItem::toDomain)
					.sortedBy(DirectChatMessage::id)
				localStore.replaceLatestMessagesWindow(
					conversationId = conversationId,
					limit = limit.coerceAtLeast(1),
					items = items
				)
				GetDataResponse.Success(
					DirectChatMessagesPage(
						items = items,
						nextBeforeId = response.nextBeforeId,
						peerLastReadMessageId = response.peerLastReadMessageId
					)
				)
			}

			ChatsGetMessagesResponse.NotFound,
			ChatsGetMessagesResponse.Error -> {
				logger.d("ChatsRepositoryImpl.refreshLatestMessagesWindow", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?
	): GetDataResponse<DirectChatMessage> {
		return when (
			val response = chatsApi.sendMessage(
				conversationId = conversationId,
				text = text,
				clientMessageId = clientMessageId,
				replyToMessageId = replyToMessageId
			)
		) {
			is ChatsSendMessageResponse.Success -> {
				val serverMessage = response.message.toDomain()
				val normalizedClientMessageId = clientMessageId?.trim().orEmpty()
				val message = if (serverMessage.clientMessageId.isNullOrBlank() && normalizedClientMessageId.isNotEmpty()) {
					serverMessage.copy(
						clientMessageId = normalizedClientMessageId,
						deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT
					)
				} else {
					serverMessage.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT)
				}
				localStore.upsertMessage(message)
				GetDataResponse.Success(message)
			}
			
			ChatsSendMessageResponse.Conflict -> {
				val key = clientMessageId?.trim().orEmpty()
				val existing = if (key.isNotEmpty()) {
					localStore.getMessageByClientMessageId(conversationId, key)
				} else {
					null
				}
				if (existing != null) {
					localStore.upsertMessage(existing.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT))
					GetDataResponse.Success(existing)
				} else {
					logger.d("ChatsRepositoryImpl.sendMessage", "Conflict but no local message found")
					GetDataResponse.Error(error = GetDataError.Other)
				}
			}

			ChatsSendMessageResponse.NotFound,
			ChatsSendMessageResponse.Error -> {
				logger.d("ChatsRepositoryImpl.sendMessage", "Failure response")
				val key = clientMessageId?.trim().orEmpty()
				if (key.isNotEmpty()) {
					localStore.getMessageByClientMessageId(conversationId, key)?.let { message ->
						localStore.upsertMessage(message.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.FAILED))
					}
				}
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun retryPendingOutgoingMessages(
		conversationId: Long,
		limit: Int
	) {
		if (conversationId <= 0L) return
		val cached = localStore.getMessages(
			conversationId = conversationId,
			limit = limit.coerceAtLeast(1),
			beforeId = null
		)
		if (cached.items.isEmpty()) return
		val pendingOutgoing = cached.items.filter { message ->
			message.id <= 0L &&
				!message.clientMessageId.isNullOrBlank() &&
				(message.deliveryStatus == me.floow.domain.models.MessageDeliveryStatus.SENDING || message.deliveryStatus == me.floow.domain.models.MessageDeliveryStatus.FAILED)
		}
		if (pendingOutgoing.isEmpty()) return

		pendingOutgoing.forEach { pending ->
			val key = pending.clientMessageId?.trim().orEmpty()
			if (key.isEmpty()) return@forEach
			retryOutgoingInternal(conversationId, pending, allowRepeat = false)
		}
	}

	override suspend fun retryOutgoingMessage(
		conversationId: Long,
		clientMessageId: String
	) {
		if (conversationId <= 0L) return
		val key = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val pending = localStore.getMessageByClientMessageId(conversationId, key) ?: return
		retryOutgoingInternal(conversationId, pending, allowRepeat = true)
	}

	private suspend fun retryOutgoingInternal(
		conversationId: Long,
		pending: DirectChatMessage,
		allowRepeat: Boolean
	) {
		val key = pending.clientMessageId?.trim().orEmpty()
		if (key.isEmpty()) return
		if (key in inFlightOutgoingClientMessageIds) return
		if (!allowRepeat && key in retryAttemptedClientMessageIds) return
		if (allowRepeat) {
			retryAttemptedClientMessageIds.remove(key)
		}
		retryAttemptedClientMessageIds.add(key)
		inFlightOutgoingClientMessageIds.add(key)
		try {
			when (
				val response = chatsApi.sendMessage(
					conversationId = conversationId,
					text = pending.text,
					clientMessageId = key,
					replyToMessageId = pending.replyToMessageId
				)
			) {
				is ChatsSendMessageResponse.Success -> {
					val serverMessage = response.message.toDomain()
					val message = if (serverMessage.clientMessageId.isNullOrBlank()) {
						serverMessage.copy(
							clientMessageId = key,
							deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT
						)
					} else {
						serverMessage.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT)
					}
					localStore.upsertMessage(message)
				}

				ChatsSendMessageResponse.Conflict -> {
					localStore.upsertMessage(pending.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT))
				}

				ChatsSendMessageResponse.NotFound,
				ChatsSendMessageResponse.Error -> {
					logger.d("ChatsRepositoryImpl.retryPendingOutgoing", "Failure response")
					localStore.upsertMessage(pending.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.FAILED))
				}
			}
		} catch (t: Throwable) {
			logger.d("ChatsRepositoryImpl.retryPendingOutgoing", "Exception: ${t.message}")
			localStore.upsertMessage(pending.copy(deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.FAILED))
		} finally {
			inFlightOutgoingClientMessageIds.remove(key)
		}
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): UpdateDataResponse {
		if (conversationId <= 0L || messageId <= 0L) return UpdateDataResponse.Failure()
		return when (val response = chatsApi.deleteMessage(conversationId = conversationId, messageId = messageId)) {
			is ChatsDeleteMessageResponse.Success -> {
				localStore.deleteMessage(conversationId = conversationId, messageId = response.deletedMessageId)
				response.readState?.let { state ->
					localStore.applyReadState(state.toDomain())
				}
				UpdateDataResponse.Success
			}

			ChatsDeleteMessageResponse.Forbidden,
			ChatsDeleteMessageResponse.NotFound,
			ChatsDeleteMessageResponse.Error -> {
				logger.d("ChatsRepositoryImpl.deleteMessage", "Failure response")
				UpdateDataResponse.Failure()
			}
		}
	}

	override suspend fun updateMessage(
		conversationId: Long,
		messageId: Long,
		text: String
	): GetDataResponse<DirectChatMessage> {
		if (conversationId <= 0L || messageId <= 0L || text.isBlank()) {
			return GetDataResponse.Error(error = GetDataError.Other)
		}
		return when (
			val response = chatsApi.updateMessage(
				conversationId = conversationId,
				messageId = messageId,
				text = text
			)
		) {
			is ChatsUpdateMessageResponse.Success -> {
				val message = response.message.toDomain()
				localStore.upsertMessage(message)
				GetDataResponse.Success(message)
			}

			ChatsUpdateMessageResponse.Forbidden,
			ChatsUpdateMessageResponse.NotFound,
			ChatsUpdateMessageResponse.Error -> {
				logger.d("ChatsRepositoryImpl.updateMessage", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): GetDataResponse<List<DirectChatMessage>> {
		if (conversationId <= 0L) {
			return GetDataResponse.Error(error = GetDataError.Other)
		}
		return when (val response = chatsApi.getPinnedMessages(conversationId = conversationId, limit = limit)) {
			is ChatsGetPinnedMessagesResponse.Success -> {
				val messages = response.items.map(ChatMessageItem::toDomain)
				localStore.upsertMessages(conversationId = conversationId, items = messages)
				GetDataResponse.Success(messages)
			}

			ChatsGetPinnedMessagesResponse.NotFound,
			ChatsGetPinnedMessagesResponse.Error -> {
				logger.d("ChatsRepositoryImpl.getPinnedMessages", "Failure response")
				val cached = localStore.getPinnedMessages(
					conversationId = conversationId,
					limit = limit.coerceAtLeast(1)
				)
				if (cached.isNotEmpty()) {
					GetDataResponse.Success(cached)
				} else {
					GetDataResponse.Error(error = GetDataError.Other)
				}
			}
		}
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean
	): GetDataResponse<DirectChatMessage> {
		if (conversationId <= 0L || messageId <= 0L) {
			return GetDataResponse.Error(error = GetDataError.Other)
		}

		return if (isPinned) {
			when (val response = chatsApi.pinMessage(conversationId = conversationId, messageId = messageId)) {
				is ChatsPinMessageResponse.Success -> {
					val message = response.message.toDomain()
					localStore.upsertMessage(message)
					GetDataResponse.Success(message)
				}

				ChatsPinMessageResponse.NotFound,
				ChatsPinMessageResponse.Conflict,
				ChatsPinMessageResponse.Error -> {
					logger.d("ChatsRepositoryImpl.setMessagePinned", "Failure response pinned=true")
					GetDataResponse.Error(error = GetDataError.Other)
				}
			}
		} else {
			when (val response = chatsApi.unpinMessage(conversationId = conversationId, messageId = messageId)) {
				is ChatsUnpinMessageResponse.Success -> {
					val message = response.message.toDomain()
					localStore.upsertMessage(message)
					GetDataResponse.Success(message)
				}

				ChatsUnpinMessageResponse.NotFound,
				ChatsUnpinMessageResponse.Error -> {
					logger.d("ChatsRepositoryImpl.setMessagePinned", "Failure response pinned=false")
					GetDataResponse.Error(error = GetDataError.Other)
				}
			}
		}
	}

	override suspend fun storeOutgoingOptimisticMessage(message: DirectChatMessage) {
		localStore.upsertMessage(message)
		if (message.conversationId > 0L) {
			outgoingRetryScheduler.enqueue(message.conversationId)
		}
	}

	override suspend fun deleteLocalMessage(conversationId: Long, messageId: Long) {
		localStore.deleteMessage(conversationId = conversationId, messageId = messageId)
	}

	override suspend fun deleteLocalMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	) {
		localStore.deleteMessageByClientMessageId(
			conversationId = conversationId,
			clientMessageId = clientMessageId
		)
	}

	override suspend fun applyLocalPinnedState(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): DirectChatMessage? {
		return localStore.setMessagePinned(
			conversationId = conversationId,
			messageId = messageId,
			isPinned = isPinned,
			pinnedAt = pinnedAt,
			pinnedByUserId = pinnedByUserId
		)
	}

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long): GetDataResponse<DirectChatReadState> {
		return when (
			val response = chatsApi.markReadUpTo(
				conversationId = conversationId,
				messageId = messageId
			)
		) {
			is ChatsMarkReadUpToResponse.Success -> {
				val readState = response.readState.toDomain()
				localStore.applyReadState(readState)
				GetDataResponse.Success(readState)
			}

			ChatsMarkReadUpToResponse.NotFound,
			ChatsMarkReadUpToResponse.Error -> {
				logger.d("ChatsRepositoryImpl.markReadUpTo", "Failure response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun applyLocalReadUpTo(
		conversationId: Long,
		messageId: Long
	): DirectChatReadState? {
		if (conversationId <= 0L || messageId <= 0L) return null
		return localStore.applyLocalReadUpTo(
			conversationId = conversationId,
			messageId = messageId
		)
	}

	override suspend fun applyPeerLastReadUpTo(
		conversationId: Long,
		messageId: Long
	) {
		if (conversationId <= 0L || messageId <= 0L) return
		localStore.applyPeerLastReadUpTo(
			conversationId = conversationId,
			messageId = messageId
		)
	}

	override suspend fun updateLocalMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		status: me.floow.domain.models.MessageDeliveryStatus
	) {
		if (conversationId <= 0L || messageId <= 0L) return
		localStore.updateMessageDeliveryStatus(
			conversationId = conversationId,
			messageId = messageId,
			deliveryStatus = status
		)
	}

	override suspend fun updateLocalMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		status: me.floow.domain.models.MessageDeliveryStatus
	) {
		if (conversationId <= 0L) return
		val normalizedClientMessageId = clientMessageId.trim()
		if (normalizedClientMessageId.isEmpty()) return
		localStore.updateMessageDeliveryStatusByClientMessageId(
			conversationId = conversationId,
			clientMessageId = normalizedClientMessageId,
			deliveryStatus = status
		)
	}

	override suspend fun sendTyping(conversationId: Long, isTyping: Boolean): UpdateDataResponse {
		if (conversationId <= 0L) return UpdateDataResponse.Failure()
		return when (chatsApi.sendTyping(conversationId = conversationId, isTyping = isTyping)) {
			ChatsTypingResponse.Success -> UpdateDataResponse.Success
			ChatsTypingResponse.NotFound,
			ChatsTypingResponse.Error -> {
				logger.d("ChatsRepositoryImpl.sendTyping", "Failure response")
				UpdateDataResponse.Failure()
			}
		}
	}

	override fun subscribeConversation(
		conversationId: Long,
		afterSeq: Long,
		replayLimit: Int
	): Flow<DirectChatRealtimeEvent> {
		return chatsRealtimeApi.subscribeConversation(
			conversationId = conversationId,
			afterSeq = afterSeq,
			replayLimit = replayLimit
		).map { event -> mapRealtimeEvent(event) }
	}

	override fun subscribeAllConversations(
		afterSeq: Long,
		replayLimit: Int
	): Flow<DirectChatRealtimeEvent> {
		return chatsRealtimeApi.subscribeAllConversations(
			afterSeq = afterSeq,
			replayLimit = replayLimit
		).map { event -> mapRealtimeEvent(event) }
	}

	private suspend fun mapRealtimeEvent(event: ChatsRealtimeEvent): DirectChatRealtimeEvent {
		return when (event) {
			is ChatsRealtimeEvent.Hello -> DirectChatRealtimeEvent.Hello(
				conversationId = event.conversationId,
				eventId = event.eventId,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			).also {
				applyReadState(event)
			}

			is ChatsRealtimeEvent.MessageCreated -> DirectChatRealtimeEvent.MessageCreated(
				conversationId = event.conversationId,
				eventId = event.eventId,
				message = event.message.toDomain(),
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion,
				isReplay = event.isReplay
			).also {
				localStore.upsertMessage(event.message.toDomain())
				applyReadState(event)
			}

			is ChatsRealtimeEvent.ReadUpToUpdated -> DirectChatRealtimeEvent.ReadUpToUpdated(
				conversationId = event.conversationId,
				eventId = event.eventId,
				actorUserId = event.actorUserId,
				actorLastReadMessageId = event.actorLastReadMessageId,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			).also {
				applyReadState(event)
			}

			is ChatsRealtimeEvent.MessageDeleted -> DirectChatRealtimeEvent.MessageDeleted(
				conversationId = event.conversationId,
				eventId = event.eventId,
				deletedMessageId = event.deletedMessageId,
				actorUserId = event.actorUserId,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			).also {
				localStore.deleteMessage(
					conversationId = event.conversationId,
					messageId = event.deletedMessageId
				)
				applyReadState(event)
			}

			is ChatsRealtimeEvent.MessageUpdated -> DirectChatRealtimeEvent.MessageUpdated(
				conversationId = event.conversationId,
				eventId = event.eventId,
				message = event.message.toDomain(),
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			).also {
				localStore.upsertMessage(event.message.toDomain())
				applyReadState(event)
			}

			is ChatsRealtimeEvent.MessagePinnedUpdated -> DirectChatRealtimeEvent.MessagePinnedUpdated(
				conversationId = event.conversationId,
				eventId = event.eventId,
				message = event.message.toDomain(),
				pinnedMessageId = event.pinnedMessageId,
				isPinned = event.isPinned,
				pinnedAt = event.pinnedAt,
				pinnedByUserId = event.pinnedByUserId,
				actorUserId = event.actorUserId,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			).also {
				localStore.setMessagePinned(
					conversationId = event.conversationId,
					messageId = event.pinnedMessageId,
					isPinned = event.isPinned,
					pinnedAt = event.pinnedAt,
					pinnedByUserId = event.pinnedByUserId
				)
				applyReadState(event)
			}

			is ChatsRealtimeEvent.Typing -> DirectChatRealtimeEvent.Typing(
				conversationId = event.conversationId,
				eventId = event.eventId,
				actorUserId = event.actorUserId,
				isTyping = event.isTyping,
				typingTtlMs = event.typingTtlMs,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			)

			is ChatsRealtimeEvent.ResyncRequired -> DirectChatRealtimeEvent.ResyncRequired(
				conversationId = event.conversationId,
				eventId = event.eventId,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			)
		}
	}

	private suspend fun applyReadState(event: ChatsRealtimeEvent) {
		localStore.applyReadState(
			DirectChatReadState(
				conversationId = event.conversationId,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			)
		)
	}
}

private fun ChatConversationItem.toDomain(): DirectChatConversation {
	return DirectChatConversation(
		id = id,
		kind = kind,
		peer = peer.toDomain(),
		lastMessage = lastMessage?.toDomain(),
		unreadCount = unreadCount,
		lastReadMessageId = lastReadMessageId,
		peerLastReadMessageId = peerLastReadMessageId,
		createdAt = createdAt,
		updatedAt = updatedAt
	)
}

private fun ChatReadStateItem.toDomain(): DirectChatReadState {
	return DirectChatReadState(
		conversationId = conversationId,
		lastReadMessageId = lastReadMessageId,
		unreadCount = unreadCount,
		firstUnreadId = firstUnreadId,
		maxMessageId = maxMessageId,
		readStateVersion = readStateVersion
	)
}

private fun ChatMessageItem.toDomain(): DirectChatMessage {
	return DirectChatMessage(
		id = id,
		conversationId = conversationId,
		sender = sender.toDomain(),
		text = text,
		clientMessageId = clientMessageId,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		pinnedAt = pinnedAt,
		pinnedByUserId = pinnedByUserId,
		createdAt = createdAt,
		updatedAt = updatedAt
	)
}

private fun me.floow.domain.api.models.ChatUserItem.toDomain(): DirectChatPeer {
	return DirectChatPeer(
		id = id,
		username = username,
		name = name,
		avatarUrl = avatar
	)
}
