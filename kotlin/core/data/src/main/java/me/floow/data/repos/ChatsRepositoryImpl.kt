package me.floow.data.repos

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.ChatsRealtimeApi
import me.floow.domain.api.models.ChatConversationItem
import me.floow.domain.api.models.ChatMessageItem
import me.floow.domain.api.models.ChatReadStateItem
import me.floow.domain.api.models.ChatsGetConversationInitialResponse
import me.floow.domain.api.models.ChatsGetConversationResponse
import me.floow.domain.api.models.ChatsGetConversationsResponse
import me.floow.domain.api.models.ChatsGetMessagesResponse
import me.floow.domain.api.models.ChatsGetMessagesAroundResponse
import me.floow.domain.api.models.ChatsGetOrCreateDirectResponse
import me.floow.domain.api.models.ChatsDeleteConversationResponse
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
import me.floow.domain.models.DirectChatInitialPage
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessageMedia
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatOutboxCommand
import me.floow.domain.models.DirectChatOutboxState
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.domain.media.DownloadableMediaType
import me.floow.domain.media.MediaDownloadController
import me.floow.data.util.platformFileExistsAndNotEmpty
import me.floow.domain.utils.Logger
import me.floow.domain.utils.currentTimeMillis

class ChatsRepositoryImpl(
	private val logger: Logger,
	private val chatsApi: ChatsApi,
	private val chatsRealtimeApi: ChatsRealtimeApi,
	private val localStore: DirectChatsLocalStore,
	private val outgoingRetryScheduler: DirectMessagesOutgoingRetryScheduler,
	private val mediaDownloadManager: MediaDownloadController? = null,
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
	private val outgoingMutex = Mutex()
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

	private suspend fun triggerAutoDownloadForMessage(message: DirectChatMessage) {
		val manager = mediaDownloadManager ?: return
		val media = message.media ?: return
		val url = media.url
		if (url.isBlank()) return

		val mediaType = when (message.contentType) {
			"image" -> DownloadableMediaType.PHOTO
			"audio_voice" -> DownloadableMediaType.VOICE
			"video_circle" -> DownloadableMediaType.VIDEO_CIRCLE
			else -> return
		}

		val localPath = media.localPath?.trim()?.takeIf(String::isNotEmpty)
		if (localPath != null) {
			if (platformFileExistsAndNotEmpty(localPath)) {
				manager.registerLocalFile(url, localPath, mediaType)
				return
			}
		}

		if (manager.isDownloaded(url, mediaType)) {
			val downloadedPath = manager.getLocalPathIfDownloaded(url, mediaType)
			if (downloadedPath != null) {
				manager.registerLocalFile(url, downloadedPath, mediaType)
				if (localPath == null) {
					localStore.upsertMessage(message.copy(media = media.copy(localPath = downloadedPath)))
				}
				return
			}
		}

		if (message.deliveryStatus == MessageDeliveryStatus.SENDING) {
			return
		}

		manager.enqueueAutoDownload(
			url = url,
			mediaType = mediaType,
			sizeBytes = media.sizeBytes,
		)
	}

	override fun observeConversations(): Flow<List<DirectChatConversation>> {
		return localStore.observeConversations()
	}

	override fun observeLocalReadStates(): Flow<Map<Long, DirectChatReadState>> {
		return localStore.observeLocalReadStates()
	}

	override fun observeMessages(
		conversationId: Long,
		limit: Int
	): Flow<DirectChatMessagesPage> {
		return localStore.observeMessages(
			conversationId = conversationId,
			limit = limit.coerceAtLeast(1)
		).onEach { page ->
			page.items.forEach { triggerAutoDownloadForMessage(it) }
		}
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
		val nowMs = currentTimeMillis()

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
					fetchedAtMs = currentTimeMillis(),
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

	override suspend fun setConversationPinned(
		conversationId: Long,
		isPinned: Boolean
	): GetDataResponse<DirectChatConversation> {
		if (conversationId <= 0L) {
			return GetDataResponse.Error(error = GetDataError.Other)
		}
		val updated = localStore.setConversationPinned(
			conversationId = conversationId,
			isPinned = isPinned,
			pinnedAt = if (isPinned) currentTimeMillis() else null,
		)
		invalidateConversationsMemoryCache()
		return when (val response = chatsApi.setConversationPinned(conversationId, isPinned)) {
			is ChatsGetConversationResponse.Success -> {
				val conversation = response.conversation.toDomain()
				localStore.upsertConversation(conversation)
				invalidateConversationsMemoryCache()
				GetDataResponse.Success(conversation)
			}
			ChatsGetConversationResponse.NotFound,
			ChatsGetConversationResponse.Error -> {
				updated?.let { GetDataResponse.Success(it) }
					?: GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun getConversationInitial(
		conversationId: Long,
		messageLimit: Int,
		includePins: Boolean,
	): GetDataResponse<DirectChatInitialPage> {
		if (conversationId <= 0L) {
			return GetDataResponse.Error(error = GetDataError.Other)
		}
		return when (
			val response = chatsApi.getConversationInitial(
				conversationId = conversationId,
				messageLimit = messageLimit,
				includePins = includePins,
			)
		) {
			is ChatsGetConversationInitialResponse.Success -> {
				val conversation = response.conversation.toDomain()
				val items = response.items
					.map(ChatMessageItem::toDomain)
					.sortedBy(DirectChatMessage::id)
				val pinnedMessages = response.pinnedItems
					.map(ChatMessageItem::toDomain)
					.sortedByDescending { it.pinnedAt ?: 0L }
				localStore.upsertConversation(conversation)
				localStore.upsertMessages(conversationId = conversationId, items = items + pinnedMessages)
				items.forEach { triggerAutoDownloadForMessage(it) }
				pinnedMessages.forEach { triggerAutoDownloadForMessage(it) }
				GetDataResponse.Success(
					DirectChatInitialPage(
						conversation = conversation,
						messages = DirectChatMessagesPage(
							items = items,
							nextBeforeId = response.nextBeforeId,
							peerLastReadMessageId = response.peerLastReadMessageId,
						),
						readStateVersion = response.readStateVersion,
						pinnedMessages = pinnedMessages,
					)
				)
			}

			ChatsGetConversationInitialResponse.NotFound,
			ChatsGetConversationInitialResponse.Error -> {
				logger.d("ChatsRepositoryImpl.getConversationInitial", "Failure response")
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
				items.forEach { triggerAutoDownloadForMessage(it) }
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
				items.forEach { triggerAutoDownloadForMessage(it) }
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
			getMessagesAround(
				conversationId = conversationId,
				anchorId = anchorMessageId,
				olderLimit = olderLimit,
				newerLimit = newerLimit,
			)
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
		if (conversationId <= 0L || text.isBlank()) return GetDataResponse.Error(error = GetDataError.Other)
		val key = clientMessageId?.trim()?.takeIf(String::isNotEmpty)
			?: "direct-${conversationId}-${currentTimeMillis()}"
		val existing = localStore.getMessageByClientMessageId(conversationId, key)
		val nowMs = currentTimeMillis()
		val localMessage = existing ?: DirectChatMessage(
			id = -nowMs,
			conversationId = conversationId,
			sender = DirectChatPeer(
				id = "self",
				username = null,
				name = null,
				avatarUrl = null
			),
			text = text,
			contentType = null,
			media = null,
			clientMessageId = key,
			replyToMessageId = replyToMessageId?.takeIf { it > 0L },
			replyToMessageText = null,
			isPinned = false,
			pinnedAt = null,
			pinnedByUserId = null,
			deliveryStatus = MessageDeliveryStatus.SENDING,
			createdAt = nowMs,
			updatedAt = nowMs
		)
		val command = localStore.enqueueOutgoingText(
			localMessage.copy(
				text = text,
				clientMessageId = key,
				replyToMessageId = replyToMessageId?.takeIf { it > 0L },
				deliveryStatus = MessageDeliveryStatus.SENDING,
				updatedAt = nowMs
			)
		) ?: return GetDataResponse.Error(error = GetDataError.Other)
		invalidateConversationsMemoryCache()
		outgoingRetryScheduler.enqueue()
		val sent = flushOutgoingCommand(command, force = true)
		return sent?.let { GetDataResponse.Success(it) }
			?: localStore.getMessageByClientMessageId(conversationId, key)?.let { GetDataResponse.Success(it) }
			?: GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun retryPendingOutgoingMessages(
		conversationId: Long,
		limit: Int
	) {
		flushPendingOutgoingMessages(limit = limit.coerceAtLeast(1))
	}

	override suspend fun flushPendingOutgoingMessages(limit: Int) {
		val nowMs = currentTimeMillis()
		localStore.getDueOutgoingCommands(
			now = nowMs,
			limit = limit.coerceAtLeast(1)
		).forEach { command ->
			if (!command.contentType.isNullOrBlank() && command.contentType != "text") return@forEach
			flushOutgoingCommand(command, force = false)
		}
	}

	override suspend fun retryOutgoingMessage(
		conversationId: Long,
		clientMessageId: String
	) {
		if (conversationId <= 0L) return
		val key = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val command = localStore.getOutgoingCommand(key)
			?.takeIf { it.conversationId == conversationId }
			?: return
		flushOutgoingCommand(command, force = true)
	}

	private suspend fun flushOutgoingCommand(
		command: DirectChatOutboxCommand,
		force: Boolean
	): DirectChatMessage? {
		val key = command.clientMessageId.trim()
		if (key.isEmpty() || command.conversationId <= 0L) return null
		if (!command.contentType.isNullOrBlank() && command.contentType != "text") return null
		if (!tryMarkOutgoingInFlight(key)) return null
		try {
			val currentCommand = localStore.getOutgoingCommand(key) ?: return null
			if (currentCommand.state == DirectChatOutboxState.CANCELLED || currentCommand.state == DirectChatOutboxState.SENT) return null
			if (
				!force &&
				currentCommand.state != DirectChatOutboxState.QUEUED &&
				currentCommand.state != DirectChatOutboxState.FAILED &&
				currentCommand.state != DirectChatOutboxState.SENDING
			) return null
			if (!force && currentCommand.nextAttemptAt > currentTimeMillis()) return null
			val sendingCommand = localStore.markOutgoingSending(key, now = currentTimeMillis()) ?: return null
			if (sendingCommand.state == DirectChatOutboxState.CANCELLED || sendingCommand.state == DirectChatOutboxState.SENT) return null
			when (
				val response = chatsApi.sendMessage(
					conversationId = sendingCommand.conversationId,
					text = sendingCommand.text,
					clientMessageId = key,
					replyToMessageId = sendingCommand.replyToMessageId
				)
			) {
				is ChatsSendMessageResponse.Success -> {
					val message = response.message.toDomain().asSentWithClientMessageId(key)
					val result = localStore.markOutgoingSent(key, message, now = currentTimeMillis())
					invalidateConversationsMemoryCache()
					return result
				}

				is ChatsSendMessageResponse.Conflict -> {
					return resolveConflictByClientMessageId(
						conversationId = sendingCommand.conversationId,
						clientMessageId = key,
						existingMessage = response.existingMessage?.toDomain()
					).let { result ->
						(result as? GetDataResponse.Success)?.data
					}
				}

				ChatsSendMessageResponse.NotFound,
				ChatsSendMessageResponse.Error -> {
					logger.d("ChatsRepositoryImpl.retryPendingOutgoing", "Failure response")
					markOutgoingFailedWithBackoff(key, sendingCommand, "send failed")
				}
			}
			return null
		} catch (t: Throwable) {
			if (t is CancellationException) throw t
			logger.d("ChatsRepositoryImpl.retryPendingOutgoing", "Exception: ${t.message}")
			markOutgoingFailedWithBackoff(
				clientMessageId = key,
				command = localStore.getOutgoingCommand(key) ?: command,
				error = t.message
			)
			return null
		} finally {
			unmarkOutgoingInFlight(key)
		}
	}

	private suspend fun tryMarkOutgoingInFlight(clientMessageId: String): Boolean {
		if (clientMessageId.isBlank()) return true
		return outgoingMutex.withLock {
			if (clientMessageId in inFlightOutgoingClientMessageIds) {
				false
			} else {
				inFlightOutgoingClientMessageIds.add(clientMessageId)
				true
			}
		}
	}

	private suspend fun unmarkOutgoingInFlight(clientMessageId: String) {
		if (clientMessageId.isBlank()) return
		outgoingMutex.withLock {
			inFlightOutgoingClientMessageIds.remove(clientMessageId)
		}
	}

	private fun DirectChatMessage.asSentWithClientMessageId(clientMessageId: String): DirectChatMessage {
		return if (this.clientMessageId.isNullOrBlank() && clientMessageId.isNotBlank()) {
			copy(
				clientMessageId = clientMessageId,
				deliveryStatus = MessageDeliveryStatus.SENT
			)
		} else {
			copy(deliveryStatus = MessageDeliveryStatus.SENT)
		}
	}

	private suspend fun markOutgoingFailedWithBackoff(
		clientMessageId: String,
		command: DirectChatOutboxCommand,
		error: String?
	) {
		val attempt = command.attemptCount.coerceAtLeast(0) + 1
		val delayMs = minOf(5 * 60_000L, 1_000L * (1L shl attempt.coerceAtMost(8)))
		val nowMs = currentTimeMillis()
		localStore.markOutgoingFailed(
			clientMessageId = clientMessageId,
			error = error,
			nextAttemptAt = nowMs + delayMs,
			now = nowMs
		)
		outgoingRetryScheduler.enqueue()
	}

	private suspend fun resolveConflictByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		existingMessage: DirectChatMessage? = null
	): GetDataResponse<DirectChatMessage> {
		if (clientMessageId.isBlank()) return GetDataResponse.Error(error = GetDataError.Other)
		existingMessage
			?.takeIf { it.id > 0L }
			?.let { message ->
				val sent = message.asSentWithClientMessageId(clientMessageId)
				localStore.markOutgoingSent(clientMessageId, sent, now = currentTimeMillis())?.let {
					return GetDataResponse.Success(it)
				}
			}
		when (val lookup = chatsApi.getMessageByClientMessageId(conversationId, clientMessageId)) {
			is me.floow.domain.api.models.ChatsGetMessageByClientMessageIdResponse.Success -> {
				val sent = lookup.message.toDomain().asSentWithClientMessageId(clientMessageId)
				localStore.markOutgoingSent(clientMessageId, sent, now = currentTimeMillis())?.let {
					return GetDataResponse.Success(it)
				}
			}
			me.floow.domain.api.models.ChatsGetMessageByClientMessageIdResponse.NotFound,
			me.floow.domain.api.models.ChatsGetMessageByClientMessageIdResponse.Error -> Unit
		}
		logger.d("ChatsRepositoryImpl.sendMessage", "Conflict could not be resolved for clientMessageId=$clientMessageId")
		localStore.getOutgoingCommand(clientMessageId)?.let { command ->
			markOutgoingFailedWithBackoff(clientMessageId, command, "conflict unresolved")
		}
		return GetDataResponse.Error(error = GetDataError.Other)
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): UpdateDataResponse {
		if (conversationId <= 0L || messageId <= 0L) return UpdateDataResponse.Failure()
		localStore.deleteMessage(conversationId = conversationId, messageId = messageId)
		return when (val response = chatsApi.deleteMessage(conversationId = conversationId, messageId = messageId)) {
			is ChatsDeleteMessageResponse.Success -> {
				response.readState?.let { state ->
					localStore.applyReadState(state.toDomain())
				}
				UpdateDataResponse.Success()
			}

			ChatsDeleteMessageResponse.Forbidden,
			ChatsDeleteMessageResponse.NotFound,
			ChatsDeleteMessageResponse.Error -> {
				logger.d("ChatsRepositoryImpl.deleteMessage", "Failure response")
				UpdateDataResponse.Failure()
			}
		}
	}

	override suspend fun deleteMessagesBatch(conversationId: Long, messageIds: List<Long>): UpdateDataResponse {
		val validIds = messageIds.filter { it > 0L }
		if (conversationId <= 0L || validIds.isEmpty()) return UpdateDataResponse.Failure()
		localStore.deleteMessages(conversationId = conversationId, messageIds = validIds)
		return when (val response = chatsApi.deleteMessagesBatch(conversationId = conversationId, messageIds = validIds)) {
			is me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Success -> {
				response.readState?.let { state ->
					localStore.applyReadState(state.toDomain())
				}
				UpdateDataResponse.Success()
			}

			me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Forbidden,
			me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.NotFound,
			me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Error -> {
				logger.d("ChatsRepositoryImpl.deleteMessagesBatch", "Failure response")
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
		val normalizedContentType = message.contentType?.trim()?.takeIf(String::isNotEmpty)
		val media = message.media
		val localPath = media?.localPath?.trim()?.takeIf(String::isNotEmpty)
		if (!message.clientMessageId.isNullOrBlank() && localPath != null) {
			localStore.enqueueOutgoingMedia(
				message = message,
				localFilePath = localPath,
				mediaMimeType = media.mimeType.ifBlank { "application/octet-stream" },
				mediaSizeBytes = media.sizeBytes,
				mediaDurationMs = media.durationMs,
				mediaWidth = media.width,
				mediaHeight = media.height,
				mediaWaveform = media.waveform?.joinToString(","),
				groupId = media.groupId ?: message.groupId,
				mediaPosition = media.position
			)
		} else if (
			!message.clientMessageId.isNullOrBlank() &&
			(normalizedContentType == null || normalizedContentType == "text")
		) {
			localStore.enqueueOutgoingText(message)
		} else {
			localStore.upsertMessage(message)
		}
		if (message.conversationId > 0L && normalizedContentType == null) {
			outgoingRetryScheduler.enqueue()
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

	override suspend fun deleteConversation(
		conversationId: Long,
		forEveryone: Boolean
	): UpdateDataResponse {
		if (conversationId <= 0L) return UpdateDataResponse.Failure()
		localStore.deleteConversation(conversationId)
		return when (chatsApi.deleteConversation(conversationId = conversationId, forEveryone = forEveryone)) {
			ChatsDeleteConversationResponse.Success -> UpdateDataResponse.Success()
			ChatsDeleteConversationResponse.NotFound,
			ChatsDeleteConversationResponse.Error -> {
				logger.d("ChatsRepositoryImpl.deleteConversation", "Failure response from backend")
				UpdateDataResponse.Failure()
			}
		}
	}

	override suspend fun sendTyping(
		conversationId: Long,
		isTyping: Boolean,
		action: String
	): UpdateDataResponse {
		if (conversationId <= 0L) return UpdateDataResponse.Failure()
		return when (chatsApi.sendTyping(conversationId = conversationId, isTyping = isTyping, action = action)) {
			ChatsTypingResponse.Success -> UpdateDataResponse.Success()
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
				val domainMessage = event.message.toDomain()
				localStore.upsertMessage(domainMessage)
				triggerAutoDownloadForMessage(domainMessage)
				applyReadState(event)
				invalidateConversationsMemoryCache()
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
				event.actorLastReadMessageId?.let { messageId ->
					localStore.applyPeerLastReadUpTo(
						conversationId = event.conversationId,
						messageId = messageId
					)
				}
				invalidateConversationsMemoryCache()
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
				invalidateConversationsMemoryCache()
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
				invalidateConversationsMemoryCache()
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
				action = event.action,
				typingTtlMs = event.typingTtlMs,
				seq = event.seq,
				lastReadMessageId = event.lastReadMessageId,
				unreadCount = event.unreadCount,
				firstUnreadId = event.firstUnreadId,
				maxMessageId = event.maxMessageId,
				readStateVersion = event.readStateVersion
			)

			is ChatsRealtimeEvent.ConversationDeleted -> {
				localStore.deleteConversation(event.conversationId)
				DirectChatRealtimeEvent.ConversationDeleted(
					conversationId = event.conversationId,
					actorUserId = event.actorUserId
				)
			}

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
		isPinned = isPinned,
		pinnedAt = pinnedAt,
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
		contentType = contentType,
		media = media?.toDomain(),
		clientMessageId = clientMessageId,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		pinnedAt = pinnedAt,
		pinnedByUserId = pinnedByUserId,
		createdAt = createdAt,
		updatedAt = updatedAt,
		groupId = groupId ?: media?.groupId,
		position = if (position > 0) position else (media?.position ?: 0)
	)
}

private fun me.floow.domain.api.models.ChatMessageMediaItem.toDomain(): DirectChatMessageMedia {
	return DirectChatMessageMedia(
		url = url,
		objectKey = objectKey,
		mimeType = mimeType,
		sizeBytes = sizeBytes,
		durationMs = durationMs,
		width = width,
		height = height,
		thumbnailUrl = thumbnailUrl,
		waveform = waveform
	)
}

private fun me.floow.domain.api.models.ChatUserItem.toDomain(): DirectChatPeer {
	return DirectChatPeer(
		id = id,
		username = username,
		name = name,
		avatarUrl = avatar,
		avatarThumb = avatarThumb
	)
}
