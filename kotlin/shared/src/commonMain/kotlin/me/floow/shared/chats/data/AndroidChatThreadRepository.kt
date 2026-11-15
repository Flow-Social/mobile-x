package me.floow.shared.chats.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessageMedia
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.domain.utils.Logger
import me.floow.domain.utils.currentTimeMillis
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.model.withSavedMessagesSemantics
import me.floow.shared.chats.uilogic.direct.ChatThreadPage
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.DirectChatOpenViewportSnapshot
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache

class AndroidChatThreadRepository(
	private val chatsRepository: ChatsRepository,
	private val authenticationManager: AuthenticationManager,
	private val directMessagesReadCursorStore: me.floow.domain.data.repos.DirectMessagesReadCursorStore,
	private val sessionCache: SharedChatSessionCache,
	private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
	private val logger: Logger? = null,
) : ChatThreadRepository {
	override suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> = runCatching {
		val startedAtMillis = currentTimeMillis()
		sessionCache.cachedThreadSnapshot(request)
			?.takeIf { cached -> canUseSessionSnapshotForInitialOpen(request, cached) }
			?.let { cached ->
				logLoadStage("cachedInitial", request, startedAtMillis, "source=session")
				launchPendingOutgoingRetry(cached.conversationId)
				return@runCatching cached.withOpenViewportFor(request)
			}

		val conversation = resolveCachedConversation(request) ?: return@runCatching null
		val selfUserId = resolveSelfUserId(request, conversation)
		val header = conversation.toHeader(request, selfUserId)
		loadLatestCachedSnapshot(
			conversationId = conversation.id,
			header = header,
			selfUserId = selfUserId,
			peerLastReadFallback = conversation.peerLastReadMessageId,
			highlightedMessageId = request.anchorMessageId,
		)?.let { snapshot ->
			logLoadStage("cachedInitial", request, startedAtMillis, "source=messages conversationId=${conversation.id}")
			launchPendingOutgoingRetry(conversation.id)
			return@runCatching snapshot.withOpenViewportFor(request)
		}

		val openViewport = directMessagesReadCursorStore.getOpenViewportSnapshot(conversation.id)
		val anchorMessageId = resolveInitialAnchorMessageId(request, conversation.id, conversation, openViewport)
		val shouldLoadAnchored = anchorMessageId != null && (
			request.openMode == ChatOpenMode.FROM_MESSAGE_LINK || !openViewport.isBottomPinned
		)
		if (shouldLoadAnchored) {
			when (val response = chatsRepository.getAnchoredMessagesWindow(conversation.id, checkNotNull(anchorMessageId))) {
				is GetDataResponse.Success -> response.data.toSnapshot(
					header = header,
					selfUserId = selfUserId,
					highlightedMessageId = request.anchorMessageId,
					openAnchorMessageId = anchorMessageId,
					openAnchorOffsetPx = if (request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) 0 else openViewport.anchorOffsetPx,
					keepOpenAnchorLocked = true,
				).also { snapshot ->
					logLoadStage("cachedInitial", request, startedAtMillis, "source=anchored conversationId=${conversation.id}")
					sessionCache.cacheThreadSnapshot(snapshot)
					launchPendingOutgoingRetry(conversation.id)
				}
				is GetDataResponse.Error -> loadLatestCachedSnapshot(
					conversationId = conversation.id,
					header = header,
					selfUserId = selfUserId,
					peerLastReadFallback = conversation.peerLastReadMessageId,
					highlightedMessageId = request.anchorMessageId,
				)?.also {
					logLoadStage("cachedInitial", request, startedAtMillis, "source=fallback conversationId=${conversation.id}")
					launchPendingOutgoingRetry(conversation.id)
				}
			}
		} else {
			loadLatestCachedSnapshot(
				conversationId = conversation.id,
				header = header,
				selfUserId = selfUserId,
				peerLastReadFallback = conversation.peerLastReadMessageId,
				highlightedMessageId = request.anchorMessageId,
			)?.also {
				logLoadStage("cachedInitial", request, startedAtMillis, "source=latest conversationId=${conversation.id}")
				launchPendingOutgoingRetry(conversation.id)
			}
		}
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		replyToMessageId: Long?,
	): Result<ChatMessageItemModel> {
		return sendMessage(conversationId, text, clientMessageId = null, replyToMessageId = replyToMessageId)
	}

	override fun observeMessages(conversationId: Long): Flow<ChatThreadPage> {
		return chatsRepository.observeMessages(conversationId, limit = 80).map { page ->
			val selfUserId = authenticationManager.getSelfUserIdOrNull()
			ChatThreadPage(
				items = page.items.map { message -> message.toItemModel(selfUserId) },
				nextBeforeMessageId = page.nextBeforeId,
				canLoadMore = page.nextBeforeId != null,
				peerLastReadMessageId = page.peerLastReadMessageId,
			)
		}
	}

	override fun observePinnedMessages(conversationId: Long): Flow<List<ChatMessageItemModel>> {
		return chatsRepository.observePinnedMessages(conversationId, limit = PINNED_MESSAGES_LIMIT).map { messages ->
			val selfUserId = authenticationManager.getSelfUserIdOrNull()
			messages.map { message -> message.toItemModel(selfUserId) }
				.sortedByPinnedAtDescending()
		}
	}

	override suspend fun loadPinnedMessages(conversationId: Long): Result<List<ChatMessageItemModel>> = runCatching {
		loadPinnedItemModels(
			conversationId = conversationId,
			selfUserId = authenticationManager.getSelfUserIdOrNull(),
		)
	}

	override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> = runCatching {
		val totalStartedAtMillis = currentTimeMillis()
		val resolveStartedAtMillis = totalStartedAtMillis
		val conversation = resolveConversation(request)
		logLoadStage("resolveConversation", request, resolveStartedAtMillis, "conversationId=${conversation.id}")
		val selfUserId = resolveSelfUserId(request, conversation)
		val header = conversation.toHeader(request, selfUserId)
		val openViewport = directMessagesReadCursorStore.getOpenViewportSnapshot(conversation.id)
		val anchorMessageId = resolveInitialAnchorMessageId(request, conversation.id, conversation, openViewport)
		val shouldLoadAnchored = anchorMessageId != null && (
			request.openMode == ChatOpenMode.FROM_MESSAGE_LINK || !openViewport.isBottomPinned
		)
		val messagesStartedAtMillis = currentTimeMillis()
		val snapshot = if (shouldLoadAnchored) {
			when (val response = chatsRepository.getAnchoredMessagesWindow(conversation.id, checkNotNull(anchorMessageId))) {
				is GetDataResponse.Success -> response.data.toSnapshot(
					header = header,
					selfUserId = selfUserId,
					highlightedMessageId = request.anchorMessageId,
					openAnchorMessageId = anchorMessageId,
					openAnchorOffsetPx = if (request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) 0 else openViewport.anchorOffsetPx,
					keepOpenAnchorLocked = true,
				).also(sessionCache::cacheThreadSnapshot)
				is GetDataResponse.Error -> loadLatestSnapshot(
					conversationId = conversation.id,
					header = header,
					selfUserId = selfUserId,
					highlightedMessageId = request.anchorMessageId,
				)
			}
		} else {
			loadLatestSnapshot(
				conversationId = conversation.id,
				header = header,
				selfUserId = selfUserId,
				highlightedMessageId = request.anchorMessageId,
			)
		}
		logLoadStage("loadMessages", request, messagesStartedAtMillis, "conversationId=${conversation.id} anchored=$shouldLoadAnchored")
		snapshot.also {
			sessionCache.cacheThreadSnapshot(it)
			launchPendingOutgoingRetry(conversation.id)
			logLoadStage("total", request, totalStartedAtMillis, "conversationId=${conversation.id}")
		}
	}

	override suspend fun getLocalLastReadMessageId(conversationId: Long): Long {
		return directMessagesReadCursorStore.getLocalLastReadMessageId(conversationId)
	}

	override suspend fun getOpenViewportSnapshot(conversationId: Long): DirectChatOpenViewportSnapshot {
		val snapshot = directMessagesReadCursorStore.getOpenViewportSnapshot(conversationId)
		return DirectChatOpenViewportSnapshot(
			anchorMessageId = snapshot.anchorMessageId,
			anchorOffsetPx = snapshot.anchorOffsetPx,
			isBottomPinned = snapshot.isBottomPinned,
		)
	}

	override suspend fun setOpenViewportSnapshot(
		conversationId: Long,
		snapshot: DirectChatOpenViewportSnapshot,
	): Result<Unit> = runCatching {
		directMessagesReadCursorStore.setOpenViewportSnapshot(
			conversationId = conversationId,
			snapshot = me.floow.domain.data.repos.DirectChatViewportSnapshot(
				anchorMessageId = snapshot.anchorMessageId,
				anchorOffsetPx = snapshot.anchorOffsetPx,
				isBottomPinned = snapshot.isBottomPinned,
			),
		)
	}

	override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> = runCatching {
		when (val response = chatsRepository.getMessages(conversationId, limit = 50, beforeId = beforeMessageId)) {
			is GetDataResponse.Success -> {
				val selfUserId = authenticationManager.getSelfUserIdOrNull()
				ChatThreadPage(
					items = response.data.items.map { it.toItemModel(selfUserId) },
					nextBeforeMessageId = response.data.nextBeforeId,
					canLoadMore = response.data.nextBeforeId != null,
					peerLastReadMessageId = response.data.peerLastReadMessageId,
				).also { page ->
					sessionCache.updateThreadMessages(
						conversationId = conversationId,
						transform = { cachedMessages ->
							mergeCachedMessages(page.items + cachedMessages)
						},
						updateMetadata = { cached ->
							cached.copy(
								canLoadMore = page.canLoadMore,
								nextBeforeMessageId = page.nextBeforeMessageId,
								peerLastReadMessageId = page.peerLastReadMessageId ?: cached.peerLastReadMessageId,
							)
						},
					)
				}
			}
			is GetDataResponse.Error -> error("failed to load more chat messages")
		}
	}

	override suspend fun loadAroundMessage(conversationId: Long, messageId: Long): Result<ChatThreadPage> = runCatching {
		require(conversationId > 0L && messageId > 0L) { "invalid message target" }
		when (val response = chatsRepository.getAnchoredMessagesWindow(conversationId, messageId)) {
			is GetDataResponse.Success -> {
				val selfUserId = authenticationManager.getSelfUserIdOrNull()
				ChatThreadPage(
					items = response.data.items.map { it.toItemModel(selfUserId) },
					nextBeforeMessageId = response.data.items
						.minOfOrNull(DirectChatMessage::id)
						?.takeIf { response.data.hasOlderMessages },
					canLoadMore = response.data.hasOlderMessages,
					peerLastReadMessageId = response.data.peerLastReadMessageId,
				).also { page ->
					sessionCache.updateThreadMessages(
						conversationId = conversationId,
						transform = { cachedMessages ->
							mergeCachedMessages(page.items + cachedMessages)
						},
						updateMetadata = { cached ->
							cached.copy(
								canLoadMore = page.canLoadMore,
								nextBeforeMessageId = page.nextBeforeMessageId,
								peerLastReadMessageId = page.peerLastReadMessageId ?: cached.peerLastReadMessageId,
							)
						},
					)
				}
			}
			is GetDataResponse.Error -> error("failed to load pinned message")
		}
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?,
	): Result<ChatMessageItemModel> = runCatching {
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		when (val response = chatsRepository.sendMessage(conversationId, text, clientMessageId, replyToMessageId)) {
			is GetDataResponse.Success -> response.data.toItemModel(selfUserId)
				.also { sent ->
					sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
						upsertCachedMessage(cachedMessages, sent)
					})
				}
			is GetDataResponse.Error -> error("failed to send message")
		}
	}

	override suspend fun storeOptimisticOutgoingMessage(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		message: ChatMessageItemModel,
	): Result<Unit> = runCatching {
		chatsRepository.storeOutgoingOptimisticMessage(
			buildOutgoingLocalMessage(
				conversationId = conversationId,
				selfUserId = header.selfUserId,
				text = message.text,
				clientMessageId = message.clientMessageId,
				replyToMessageId = message.replyToMessageId,
				replyToMessageText = message.replyToMessageText,
				content = message.content,
				createdAtMillis = message.createdAtMillis,
				optimisticId = message.id,
			)
		)
		sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
			upsertCachedMessage(cachedMessages, message)
		})
	}

	override suspend fun deleteLocalMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
	): Result<Unit> = runCatching {
		chatsRepository.deleteLocalMessageByClientMessageId(conversationId, clientMessageId)
		sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
			cachedMessages.filterNot { it.clientMessageId == clientMessageId }
		})
	}

	override suspend fun editMessage(
		conversationId: Long,
		messageId: Long,
		newText: String,
	): Result<ChatMessageItemModel> = runCatching {
		when (val response = chatsRepository.updateMessage(conversationId, messageId, newText)) {
			is GetDataResponse.Success -> response.data.toItemModel(authenticationManager.getSelfUserIdOrNull())
				.also { updated ->
					sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
						cachedMessages.map { existing ->
							if (existing.id == updated.id) updated.copy(isOutgoing = existing.isOutgoing) else existing
						}
					})
				}
			is GetDataResponse.Error -> error("failed to edit message")
		}
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		when (chatsRepository.deleteMessage(conversationId, messageId)) {
			is UpdateDataResponse.Success -> {
				sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
					cachedMessages.filterNot { it.id == messageId }
				})
				Unit
			}
			is UpdateDataResponse.Failure -> error("failed to delete message")
		}
	}

	override suspend fun deleteMessagesBatch(conversationId: Long, messageIds: List<Long>): Result<Unit> = runCatching {
		val idsSet = messageIds.toSet()
		when (chatsRepository.deleteMessagesBatch(conversationId, messageIds)) {
			is UpdateDataResponse.Success -> {
				sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
					cachedMessages.filterNot { it.id in idsSet }
				})
				Unit
			}
			is UpdateDataResponse.Failure -> error("failed to delete messages batch")
		}
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
	): Result<Unit> = runCatching {
		when (chatsRepository.setMessagePinned(conversationId, messageId, isPinned)) {
			is GetDataResponse.Success -> {
				sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
					cachedMessages.map { message ->
						if (message.id == messageId) message.copy(isPinned = isPinned) else message
					}
				})
				Unit
			}
			is GetDataResponse.Error -> error("failed to pin message")
		}
	}

	private suspend fun ChatThreadSnapshot.withOpenViewportFor(
		request: DirectChatInitialRequest,
	): ChatThreadSnapshot {
		val conversationId = conversationId?.takeIf { it > 0L } ?: return this
		val openViewport = directMessagesReadCursorStore.getOpenViewportSnapshot(conversationId)
		val anchorMessageId = resolveInitialAnchorMessageId(
			request = request,
			conversationId = conversationId,
			conversation = toCachedConversation(conversationId),
			openViewport = openViewport,
		)
		if (anchorMessageId == null || openViewport.isBottomPinned) {
			return copy(
				openAnchorMessageId = null,
				openAnchorOffsetPx = 0,
				keepOpenAnchorLocked = false,
				highlightedMessageId = request.anchorMessageId,
			)
		}
		return copy(
			openAnchorMessageId = anchorMessageId,
			openAnchorOffsetPx = if (request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) 0 else openViewport.anchorOffsetPx,
			keepOpenAnchorLocked = true,
			highlightedMessageId = request.anchorMessageId,
		)
	}

	private fun canUseSessionSnapshotForInitialOpen(
		request: DirectChatInitialRequest,
		cached: ChatThreadSnapshot,
	): Boolean {
		if (request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) {
			val requestedAnchor = request.anchorMessageId?.takeIf { it > 0L } ?: return false
			return cached.messages.any { it.id == requestedAnchor }
		}
		return cached.messages.isNotEmpty()
	}

	private fun ChatThreadSnapshot.toCachedConversation(conversationId: Long): DirectChatConversation {
		return DirectChatConversation(
			id = conversationId,
			kind = if (header.isSavedMessages) SAVED_MESSAGES_CHAT_KIND else "direct",
			peer = DirectChatPeer(
				id = header.peerUserId,
				username = null,
				name = header.title,
				avatarUrl = header.avatarUrl,
			),
			lastMessage = null,
			unreadCount = 0,
			lastReadMessageId = 0L,
			peerLastReadMessageId = peerLastReadMessageId,
			createdAt = 0L,
			updatedAt = 0L,
		)
	}

	private fun upsertCachedMessage(
		cachedMessages: List<ChatMessageItemModel>,
		message: ChatMessageItemModel,
	): List<ChatMessageItemModel> {
		val clientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		val existingByClientMessageId = clientMessageId?.let { normalizedClientMessageId ->
			cachedMessages.firstOrNull { existing -> existing.clientMessageId?.trim() == normalizedClientMessageId }
		}
		val normalizedMessage = if (existingByClientMessageId != null && message.id > 0L) {
			message.copy(createdAtMillis = existingByClientMessageId.createdAtMillis)
		} else {
			message
		}
		return (cachedMessages.filterNot { existing ->
			existing.id == normalizedMessage.id ||
				(clientMessageId != null && existing.clientMessageId?.trim() == clientMessageId)
		} + normalizedMessage).sortedWith(compareBy<ChatMessageItemModel> { it.createdAtMillis }.thenBy { it.id })
	}

	private fun mergeCachedMessages(messages: List<ChatMessageItemModel>): List<ChatMessageItemModel> {
		return messages.fold(emptyList()) { acc, message ->
			upsertCachedMessage(acc, message)
		}
	}

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		chatsRepository.applyLocalReadUpTo(conversationId, messageId)
		directMessagesReadCursorStore.setLocalLastReadMessageId(conversationId, messageId)
		directMessagesReadCursorStore.enqueueReadUpTo(conversationId, messageId)
	}

	override suspend fun setTyping(conversationId: Long, isTyping: Boolean, action: String): Result<Unit> = runCatching {
		when (chatsRepository.sendTyping(conversationId, isTyping, action)) {
			is UpdateDataResponse.Success -> Unit
			is UpdateDataResponse.Failure -> error("failed to send typing")
		}
	}

	private suspend fun resolveConversation(request: DirectChatInitialRequest): DirectChatConversation {
		val explicitConversationId = request.conversationId?.takeIf { it > 0L }
		if (explicitConversationId != null) {
			if (!request.isSavedMessages && request.peerUserId.isNotBlank()) {
				return request.toCachedConversation(explicitConversationId)
			}
			return when (val response = chatsRepository.getConversation(explicitConversationId)) {
				is GetDataResponse.Success -> response.data
				is GetDataResponse.Error -> error("failed to resolve conversation")
			}
		}
		if (request.isSavedMessages) {
			return when (val response = chatsRepository.getOrCreateSavedMessagesConversation()) {
				is GetDataResponse.Success -> response.data
				is GetDataResponse.Error -> error("failed to resolve saved messages conversation")
			}
		}
		if (request.allowCreateFromPeerUserId && request.peerUserId.isNotBlank()) {
			return when (val response = chatsRepository.getOrCreateDirectConversation(request.peerUserId)) {
				is GetDataResponse.Success -> response.data
				is GetDataResponse.Error -> error("failed to resolve direct conversation")
			}
		}
		error("chat conversation target is missing")
	}

	private suspend fun resolveCachedConversation(request: DirectChatInitialRequest): DirectChatConversation? {
		val cachedConversations = chatsRepository.observeConversations().first()
		val explicitConversationId = request.conversationId?.takeIf { it > 0L }
		if (explicitConversationId != null) {
			return cachedConversations.firstOrNull { it.id == explicitConversationId }
				?: request.toCachedConversation(explicitConversationId)
		}
		if (request.isSavedMessages) {
			return cachedConversations.firstOrNull(DirectChatConversation::isSavedMessages)
		}
		val peerUserId = request.peerUserId.trim().takeIf(String::isNotEmpty) ?: return null
		return cachedConversations
			.asSequence()
			.filterNot(DirectChatConversation::isSavedMessages)
			.filter { conversation -> conversation.peer.id == peerUserId }
			.maxByOrNull(DirectChatConversation::updatedAt)
	}

	private fun launchPendingOutgoingRetry(conversationId: Long?) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L } ?: return
		backgroundScope.launch {
			val startedAtMillis = currentTimeMillis()
			logger?.d(
				tag = "AndroidChatThreadRepository",
				message = "retryPending start conversationId=$resolvedConversationId",
			)
			runCatching {
				chatsRepository.retryPendingOutgoingMessages(resolvedConversationId, limit = 80)
			}.onFailure { throwable ->
				logger?.d(
					tag = "AndroidChatThreadRepository",
					message = "retryPending failed conversationId=$resolvedConversationId reason=${throwable.message ?: "unknown"}",
				)
			}.onSuccess {
				logger?.d(
					tag = "AndroidChatThreadRepository",
					message = "retryPending done conversationId=$resolvedConversationId durationMs=${currentTimeMillis() - startedAtMillis}",
				)
			}
		}
	}

	private fun logLoadStage(
		stage: String,
		request: DirectChatInitialRequest,
		startedAtMillis: Long,
		extra: String,
	) {
		logger?.d(
			tag = "AndroidChatThreadRepository",
			message = "load stage=$stage durationMs=${currentTimeMillis() - startedAtMillis} peer=${request.peerUserId} requestConversationId=${request.conversationId} $extra",
		)
	}

	private fun resolveSelfUserId(
		request: DirectChatInitialRequest,
		conversation: DirectChatConversation,
	): String? {
		return if (request.isSavedMessages) {
			conversation.peer.id
		} else {
			authenticationManager.getSelfUserIdOrNull()
		}
	}

	private suspend fun resolveInitialAnchorMessageId(
		request: DirectChatInitialRequest,
		conversationId: Long,
		conversation: DirectChatConversation,
		openViewport: me.floow.domain.data.repos.DirectChatViewportSnapshot,
	): Long? {
		if (request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) {
			return request.anchorMessageId?.takeIf { it > 0L }
		}
		if (request.openMode == ChatOpenMode.FROM_LAST_SEEN) {
			return openViewport.anchorMessageId?.takeIf { it > 0L && !openViewport.isBottomPinned }
		}
		val localRead = directMessagesReadCursorStore.getLocalLastReadMessageId(conversationId)
			.takeIf { it > 0L }
		return openViewport.anchorMessageId
			?.takeIf { it > 0L && !openViewport.isBottomPinned }
			?: localRead
			?: conversation.lastReadMessageId.takeIf { it > 0L }
	}

	private suspend fun loadLatestCachedSnapshot(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		selfUserId: String?,
		peerLastReadFallback: Long?,
		highlightedMessageId: Long?,
	): ChatThreadSnapshot? {
		val cachedPage = chatsRepository.observeMessages(conversationId, limit = INITIAL_MESSAGES_LIMIT).first()
		if (cachedPage.items.isEmpty()) return null
		return buildSnapshotFromMessagesPage(
			conversationId = conversationId,
			header = header,
			selfUserId = selfUserId,
			page = cachedPage,
			peerLastReadFallback = peerLastReadFallback,
			highlightedMessageId = highlightedMessageId,
		)
	}

	private suspend fun loadLatestSnapshot(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		selfUserId: String?,
		highlightedMessageId: Long?,
	): ChatThreadSnapshot {
		val initialResponse = chatsRepository.getConversationInitial(
			conversationId = conversationId,
			messageLimit = INITIAL_MESSAGES_LIMIT,
			includePins = false,
		)
		return when (initialResponse) {
			is GetDataResponse.Success -> buildSnapshotFromMessagesPage(
				conversationId = conversationId,
				header = initialResponse.data.conversation.toHeaderFromConversation(header, selfUserId),
				selfUserId = selfUserId,
				page = initialResponse.data.messages,
				highlightedMessageId = highlightedMessageId,
			)
			is GetDataResponse.Error -> when (
				val response = chatsRepository.getMessages(
					conversationId = conversationId,
					limit = INITIAL_MESSAGES_LIMIT,
					beforeId = null,
				)
			) {
				is GetDataResponse.Success -> buildSnapshotFromMessagesPage(
					conversationId = conversationId,
					header = header,
					selfUserId = selfUserId,
					page = response.data,
					highlightedMessageId = highlightedMessageId,
				)
				is GetDataResponse.Error -> error("failed to load chat")
			}
		}
	}

	private suspend fun buildSnapshotFromMessagesPage(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		selfUserId: String?,
		page: me.floow.domain.models.DirectChatMessagesPage,
		peerLastReadFallback: Long? = null,
		highlightedMessageId: Long?,
	): ChatThreadSnapshot {
		return ChatThreadSnapshot(
			conversationId = conversationId,
			header = header,
			messages = page.items.map { it.toItemModel(selfUserId) },
			canLoadMore = page.nextBeforeId != null,
			nextBeforeMessageId = page.nextBeforeId,
			peerLastReadMessageId = page.peerLastReadMessageId ?: peerLastReadFallback,
			highlightedMessageId = highlightedMessageId,
			openAnchorMessageId = highlightedMessageId,
			keepOpenAnchorLocked = highlightedMessageId != null,
		)
	}

	private suspend fun loadPinnedItemModels(
		conversationId: Long,
		selfUserId: String?,
	): List<ChatMessageItemModel> {
		return when (val response = chatsRepository.getPinnedMessages(conversationId, PINNED_MESSAGES_LIMIT)) {
			is GetDataResponse.Success -> response.data.map { it.toItemModel(selfUserId) }.sortedByPinnedAtDescending()
			is GetDataResponse.Error -> chatsRepository.observePinnedMessages(conversationId, PINNED_MESSAGES_LIMIT)
				.first()
				.map { it.toItemModel(selfUserId) }
				.sortedByPinnedAtDescending()
		}
	}
}

private fun DirectChatConversation.toHeaderFromConversation(
	fallback: ChatThreadHeaderModel,
	selfUserId: String?,
): ChatThreadHeaderModel {
	return if (isSavedMessages) {
		fallback.withSavedMessagesSemantics()
	} else {
		fallback.copy(
			peerUserId = peer.id,
			selfUserId = selfUserId,
			title = peer.name?.takeIf(String::isNotBlank)
				?: peer.username?.takeIf(String::isNotBlank)
				?: fallback.title,
			avatarUrl = peer.avatarUrl ?: fallback.avatarUrl,
		)
	}
}

private fun DirectChatInitialRequest.toCachedConversation(conversationId: Long): DirectChatConversation {
	return DirectChatConversation(
		id = conversationId,
		kind = if (isSavedMessages) SAVED_MESSAGES_CHAT_KIND else "direct",
		peer = DirectChatPeer(
			id = peerUserId,
			username = null,
			name = peerDisplayName,
			avatarUrl = peerAvatarUrl,
			avatarThumb = peerAvatarThumb,
		),
		lastMessage = null,
		unreadCount = 0,
		lastReadMessageId = 0L,
		peerLastReadMessageId = null,
		createdAt = 0L,
		updatedAt = 0L,
	)
}

private fun DirectChatConversation.toHeader(
	request: DirectChatInitialRequest,
	selfUserId: String?,
): ChatThreadHeaderModel {
	val baseHeader = ChatThreadHeaderModel(
		peerUserId = peer.id,
		selfUserId = selfUserId,
		title = if (request.isSavedMessages) {
			"Избранное"
		} else {
			peer.name?.takeIf(String::isNotBlank)
				?: peer.username?.takeIf(String::isNotBlank)
				?: request.peerDisplayName
		},
		avatarUrl = peer.avatarUrl,
		avatarThumb = peer.avatarThumb,
		subtitle = null,
		isSavedMessages = request.isSavedMessages,
	)
	return if (request.isSavedMessages) {
		baseHeader.withSavedMessagesSemantics()
	} else {
		baseHeader
	}
}

private fun DirectChatAnchoredMessagesWindow.toSnapshot(
	header: ChatThreadHeaderModel,
	selfUserId: String?,
	highlightedMessageId: Long?,
	openAnchorMessageId: Long?,
	openAnchorOffsetPx: Int,
	keepOpenAnchorLocked: Boolean,
): ChatThreadSnapshot {
	return ChatThreadSnapshot(
		conversationId = items.firstOrNull()?.conversationId,
		header = header,
		messages = items.map { it.toItemModel(selfUserId) },
		canLoadMore = hasOlderMessages,
		nextBeforeMessageId = items.minOfOrNull(DirectChatMessage::id)?.takeIf { hasOlderMessages },
		peerLastReadMessageId = peerLastReadMessageId,
		highlightedMessageId = highlightedMessageId,
		openAnchorMessageId = openAnchorMessageId,
		openAnchorOffsetPx = openAnchorOffsetPx,
		keepOpenAnchorLocked = keepOpenAnchorLocked,
	)
}

private fun List<ChatMessageItemModel>.sortedByPinnedAtDescending(): List<ChatMessageItemModel> {
	return filter(ChatMessageItemModel::isPinned)
		.sortedWith(
			compareByDescending<ChatMessageItemModel> { it.pinnedAtMillis ?: 0L }
				.thenByDescending { it.createdAtMillis }
				.thenByDescending { it.id }
		)
}

internal fun DirectChatMessage.toItemModel(selfUserId: String?): ChatMessageItemModel {
	val normalizedSelfUserId = selfUserId?.trim()?.takeIf(String::isNotEmpty)
	val isOutgoing = normalizedSelfUserId != null && sender.id.trim() == normalizedSelfUserId
	val deliveryState = if (!isOutgoing) {
		null
	} else {
		when (deliveryStatus) {
			MessageDeliveryStatus.SENDING -> ChatDeliveryState.SENDING
			MessageDeliveryStatus.SENT -> ChatDeliveryState.SENT
			MessageDeliveryStatus.FAILED -> ChatDeliveryState.FAILED
		}
	}
	val messageMedia = media
	val normalizedContentType = contentType?.trim()?.lowercase()
	val normalizedText = text.trim().lowercase()
	val normalizedMimeType = messageMedia?.mimeType?.trim()?.lowercase().orEmpty()
	val hasMediaSource = messageMedia != null &&
		(!messageMedia.url.isNullOrBlank() || !messageMedia.localPath.isNullOrBlank() || messageMedia.sizeBytes != null)
	val isVideoCircleType = normalizedContentType == "video_circle" ||
		normalizedContentType == "video" ||
		normalizedContentType?.contains("video") == true ||
		(normalizedText == "видеосообщение" && hasMediaSource) ||
		normalizedMimeType.startsWith("video/")
	val isVoiceType = normalizedContentType == "voice" ||
		normalizedContentType == "audio" ||
		normalizedContentType?.contains("voice") == true ||
		normalizedContentType?.contains("audio") == true ||
		(normalizedText == "голосовое сообщение" && (hasMediaSource || !messageMedia?.waveform.isNullOrEmpty())) ||
		normalizedMimeType.startsWith("audio/") ||
		!messageMedia?.waveform.isNullOrEmpty()
	val isImageType = normalizedContentType == "image" ||
		normalizedContentType == "photo" ||
		normalizedContentType?.contains("image") == true ||
		(normalizedText == "фотография" && hasMediaSource) ||
		normalizedMimeType.startsWith("image/") ||
		(messageMedia?.width ?: 0) > 0

	val content = when {
		isVideoCircleType -> ChatMessageContent.VideoCircle(
			localPath = messageMedia?.localPath,
			remoteUrl = messageMedia?.url,
			durationMs = messageMedia?.durationMs ?: 0L,
			thumbnailPath = messageMedia?.thumbnailUrl?.takeIf { !it.startsWith("http://") && !it.startsWith("https://") },
			remoteThumbnailUrl = messageMedia?.thumbnailUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") },
			width = messageMedia?.width ?: 0,
			height = messageMedia?.height ?: 0,
			uploadState = if (deliveryState == ChatDeliveryState.FAILED) VideoUploadState.Failed
			             else if (deliveryState == ChatDeliveryState.SENDING) VideoUploadState.Uploading
			             else VideoUploadState.Uploaded
		)
		isVoiceType -> ChatMessageContent.Voice(
			localPath = messageMedia?.localPath,
			remoteUrl = messageMedia?.url,
			durationMs = messageMedia?.durationMs ?: 0L,
			waveform = messageMedia?.waveform ?: emptyList(),
			uploadState = if (deliveryState == ChatDeliveryState.FAILED) VideoUploadState.Failed
			             else if (deliveryState == ChatDeliveryState.SENDING) VideoUploadState.Uploading
			             else VideoUploadState.Uploaded
		)
		isImageType -> ChatMessageContent.Image(
			localPath = messageMedia?.localPath,
			remoteUrl = messageMedia?.url,
			width = messageMedia?.width ?: 0,
			height = messageMedia?.height ?: 0,
			groupId = groupId ?: messageMedia?.groupId,
			position = if (position > 0) position else (messageMedia?.position ?: 0),
			uploadState = if (deliveryState == ChatDeliveryState.FAILED) VideoUploadState.Failed
			             else if (deliveryState == ChatDeliveryState.SENDING) VideoUploadState.Uploading
			             else VideoUploadState.Uploaded,
			thumbnailUrl = messageMedia?.thumbnailUrl,
			sizeBytes = messageMedia?.sizeBytes ?: 0L,
		)
		else -> ChatMessageContent.Text
	}
	return ChatMessageItemModel(
		id = id,
		clientMessageId = clientMessageId,
		senderUserId = sender.id,
		senderDisplayName = sender.name ?: sender.username,
		text = text,
		createdAtMillis = createdAt,
		isOutgoing = isOutgoing,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		pinnedAtMillis = pinnedAt,
		isDeleted = false,
		deliveryState = deliveryState,
		content = content,
		authorAvatarUrl = sender.avatarUrl,
		authorAvatarThumb = sender.avatarThumb,
	)
}

private val DirectChatConversation.isSavedMessages: Boolean
	get() = kind.trim().lowercase() == SAVED_MESSAGES_CHAT_KIND || kind.trim().lowercase() == "saved"

private const val PINNED_MESSAGES_LIMIT = 20
private const val INITIAL_MESSAGES_LIMIT = 30
private const val SAVED_MESSAGES_CHAT_KIND = "saved_messages"

private fun buildOutgoingLocalMessage(
	conversationId: Long,
	selfUserId: String?,
	text: String,
	clientMessageId: String?,
	replyToMessageId: Long?,
	replyToMessageText: String?,
	content: ChatMessageContent,
	createdAtMillis: Long = currentTimeMillis(),
	optimisticId: Long? = null,
): DirectChatMessage {
	val normalizedClientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		?: error("client message id is missing")
	val senderId = selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self"
	val media = when (content) {
		is ChatMessageContent.VideoCircle -> DirectChatMessageMedia(
			url = content.remoteUrl.orEmpty(),
			objectKey = "",
			mimeType = "video/mp4",
			sizeBytes = 0L,
			durationMs = content.durationMs,
			localPath = content.localPath,
			thumbnailUrl = content.thumbnailPath ?: content.remoteThumbnailUrl,
			width = content.width.takeIf { it > 0 },
			height = content.height.takeIf { it > 0 },
		)
		is ChatMessageContent.Voice -> DirectChatMessageMedia(
			url = content.remoteUrl.orEmpty(),
			objectKey = "",
			mimeType = "audio/ogg",
			sizeBytes = 0L,
			durationMs = content.durationMs,
			localPath = content.localPath,
			waveform = content.waveform,
		)
		is ChatMessageContent.Image -> DirectChatMessageMedia(
			url = content.remoteUrl.orEmpty(),
			objectKey = "",
			mimeType = "image/jpeg",
			sizeBytes = 0L,
			durationMs = 0L,
			localPath = content.localPath,
			width = content.width.takeIf { it > 0 },
			height = content.height.takeIf { it > 0 },
			groupId = content.groupId,
			position = content.position,
		)
		is ChatMessageContent.PostPreview,
		ChatMessageContent.Text -> null
	}
	return DirectChatMessage(
		id = optimisticId ?: -1L,
		conversationId = conversationId,
		sender = DirectChatPeer(
			id = senderId,
			username = null,
			name = null,
			avatarUrl = null
		),
		text = text,
		contentType = when (content) {
			is ChatMessageContent.VideoCircle -> "video_circle"
			is ChatMessageContent.Voice -> "voice"
			is ChatMessageContent.Image -> "image"
			else -> null
		},
		media = media,
		clientMessageId = normalizedClientMessageId,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = false,
		pinnedAt = null,
		pinnedByUserId = null,
		deliveryStatus = MessageDeliveryStatus.SENDING,
		createdAt = createdAtMillis,
		updatedAt = createdAtMillis,
		groupId = if (content is ChatMessageContent.Image) content.groupId else null,
		position = if (content is ChatMessageContent.Image) content.position else 0,
	)
}
