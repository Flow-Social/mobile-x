package me.floow.shared.chats.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.database.dao.DirectChatsDao
import me.floow.domain.data.GetDataError
import me.floow.domain.network.NetworkMonitor
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
import me.floow.shared.chats.uilogic.direct.ChatMessageUnavailableException
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
    private val progressDao: DirectChatsDao,
    private val networkMonitor: NetworkMonitor,
	private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
	private val logger: Logger? = null,
) : ChatThreadRepository {
    override val networkOnline get() = networkMonitor.isOnline

    private val progress = DirectChatProgress(progressDao, chatsRepository, authenticationManager, networkMonitor, backgroundScope)

    override suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> = runCatching {
        val conversation = resolveCachedConversation(request) ?: return@runCatching null
        val selfUserId = resolveSelfUserId(request, conversation)
        val header = conversation.toHeader(request, selfUserId)
        val viewport = openViewport(conversation.id)
        val readState = progressDao.getReadState(conversation.id)
        val target = resolveInitialAnchorMessageId(request, viewport, readState?.firstUnreadId ?: conversation.firstUnreadId)
        val cachedSession = sessionCache.cachedThreadSnapshot(request)
        val savedRange = viewport.history?.takeIf {
            if (target != null) target in it.oldestId..it.newestId else !it.hasNewer
        }
        val savedRows = savedRange?.let { chatsRepository.getVerifiedWindow(conversation.id, it.oldestId, it.newestId) }.orEmpty()
        val verified = savedRange != null && savedRows.isNotEmpty() && (target == null || savedRows.any { it.id == target })
        val snapshot = when {
            verified && savedRange != null -> ChatThreadSnapshot(
                conversationId = conversation.id,
                header = header,
                messages = savedRows.map { it.toItemModel(selfUserId) },
                canLoadMore = savedRange.hasOlder,
                nextBeforeMessageId = savedRows.first().id.takeIf { savedRange.hasOlder },
                hasNewerMessages = savedRange.hasNewer,
                peerLastReadMessageId = conversation.peerLastReadMessageId,
                openAnchorMessageId = target,
                openAnchorOffsetPx = if (target == viewport.anchorMessageId && request.anchorMessageId == null) viewport.anchorOffsetPx else 0,
                keepOpenAnchorLocked = target != null,
                highlightedMessageId = request.anchorMessageId,
            )
            target != null -> {
                // A single cached target is usable offline. Neighbours in Room can be from disjoint pages.
                chatsRepository.getCachedMessageAnchor(conversation.id, target)
                    ?.copy(hasOlderMessages = true, hasNewerMessages = true)
                    ?.toSnapshot(header, selfUserId, request.anchorMessageId, target,
                        if (target == viewport.anchorMessageId && request.anchorMessageId == null) viewport.anchorOffsetPx else 0, true)
            }
            request.openMode == ChatOpenMode.FROM_MESSAGE_LINK -> null
            cachedSession != null && !cachedSession.hasNewerMessages && !cachedSession.provisionalHistory ->
                cachedSession.copy(openAnchorMessageId = null, openAnchorOffsetPx = 0,
                    keepOpenAnchorLocked = false, highlightedMessageId = null)
            else -> loadLatestCachedSnapshot(conversation.id, header, selfUserId, conversation.peerLastReadMessageId, null)
        }
        snapshot?.copy(
            provisionalHistory = !verified,
            firstUnreadMessageId = readState?.firstUnreadId ?: conversation.firstUnreadId,
            lastReadMessageId = maxOf(conversation.lastReadMessageId, readState?.lastReadMessageId ?: 0L),
        )
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

    override fun observeReadState(conversationId: Long): Flow<me.floow.domain.models.DirectChatReadState?> =
        chatsRepository.observeLocalReadStates().map { it[conversationId] }.distinctUntilChanged()

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
        val conversation = resolveConversation(request)
        val selfUserId = resolveSelfUserId(request, conversation)
        val header = conversation.toHeader(request, selfUserId)
        val explicitTarget = request.anchorMessageId?.takeIf { request.openMode == ChatOpenMode.FROM_MESSAGE_LINK }
        val viewport = openViewport(conversation.id)
        val localRead = progressDao.getReadState(conversation.id)
        val target = resolveInitialAnchorMessageId(request, viewport, localRead?.firstUnreadId ?: conversation.firstUnreadId)
        var snapshot: ChatThreadSnapshot? = null
        if (target != null) {
            when (val response = chatsRepository.getMessagesAround(conversation.id, target)) {
                is GetDataResponse.Success -> snapshot = response.data.toSnapshot(
                    header, selfUserId, explicitTarget, target,
                    if (explicitTarget == null && target == viewport.anchorMessageId) viewport.anchorOffsetPx else 0, true,
                )
                is GetDataResponse.Error -> {
                    if (response.error != GetDataError.NoData) error("chat history unavailable")
                    if (explicitTarget != null) throw ChatMessageUnavailableException()
                }
            }
        }
        if (snapshot == null) {
            snapshot = loadLatestSnapshot(conversation.id, header, selfUserId, highlightedMessageId = null)
            val unread = snapshot.firstUnreadMessageId
            if (request.openMode != ChatOpenMode.LATEST && target == null && unread != null) {
                snapshot = if (snapshot.messages.any { it.id == unread }) {
                    snapshot.copy(openAnchorMessageId = unread, keepOpenAnchorLocked = true)
                } else {
                    when (val result = chatsRepository.getMessagesAround(conversation.id, unread)) {
                        is GetDataResponse.Success -> result.data.toSnapshot(header, selfUserId, null, unread, 0, true)
                        is GetDataResponse.Error -> snapshot
                    }
                }
            }
        }
        val confirmed = progressDao.getReadState(conversation.id)
        snapshot.copy(
            firstUnreadMessageId = if (confirmed != null) confirmed.firstUnreadId else snapshot.firstUnreadMessageId ?: conversation.firstUnreadId,
            lastReadMessageId = maxOf(conversation.lastReadMessageId, confirmed?.lastReadMessageId ?: 0L, snapshot.lastReadMessageId),
        ).also {
            sessionCache.cacheThreadSnapshot(it.copy(
                openAnchorMessageId = null, openAnchorOffsetPx = 0, keepOpenAnchorLocked = false, highlightedMessageId = null,
            ))
            launchPendingOutgoingRetry(conversation.id)
        }
    }

    override suspend fun getLocalLastReadMessageId(conversationId: Long): Long =
        maxOf(progressDao.getReadState(conversationId)?.lastReadMessageId ?: 0L, progress.pendingRead(conversationId))

    private suspend fun openViewport(conversationId: Long) =
        progress.viewport(conversationId)?.takeIf { it.isBottomPinned || it.anchorMessageId != null }
            ?: directMessagesReadCursorStore.getOpenViewportSnapshot(conversationId)

    override suspend fun getOpenViewportSnapshot(conversationId: Long): DirectChatOpenViewportSnapshot {
        val snapshot = openViewport(conversationId)
        return DirectChatOpenViewportSnapshot(snapshot.anchorMessageId, snapshot.anchorOffsetPx, snapshot.isBottomPinned, snapshot.history)
    }

    override suspend fun setOpenViewportSnapshot(conversationId: Long, snapshot: DirectChatOpenViewportSnapshot): Result<Unit> =
        runCatching {
            val accountId = authenticationManager.getSelfUserIdOrNull() ?: return@runCatching
            progress.saveViewport(accountId, conversationId, me.floow.domain.data.repos.DirectChatViewportSnapshot(
                snapshot.anchorMessageId, snapshot.anchorOffsetPx, snapshot.isBottomPinned, snapshot.history,
            ))
        }

    override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> = runCatching {
        val response = chatsRepository.getMessages(conversationId, limit = 50, beforeId = beforeMessageId)
        val page = (response as? GetDataResponse.Success)?.data
            ?.takeUnless { it.isCached } ?: error("Не удалось загрузить историю")
        val selfUserId = authenticationManager.getSelfUserIdOrNull()
        ChatThreadPage(
            items = page.items.map { it.toItemModel(selfUserId) },
            nextBeforeMessageId = page.nextBeforeId,
            canLoadMore = page.nextBeforeId != null,
            peerLastReadMessageId = page.peerLastReadMessageId,
        )
    }

    override suspend fun loadAroundMessage(conversationId: Long, messageId: Long): Result<ChatThreadPage> = runCatching {
        require(conversationId > 0L && messageId > 0L)
        when (val response = chatsRepository.getMessagesAround(conversationId, messageId)) {
            is GetDataResponse.Success -> response.data.toThreadPage()
            is GetDataResponse.Error -> error(if (response.error == GetDataError.NoData) "Сообщение недоступно" else "Не удалось загрузить сообщение")
        }
    }

    override suspend fun loadNewer(conversationId: Long, afterMessageId: Long): Result<ChatThreadPage> = runCatching {
        when (val response = chatsRepository.getMessagesAround(conversationId, afterMessageId, olderLimit = 0, newerLimit = 50)) {
            is GetDataResponse.Success -> response.data.toThreadPage().let { page ->
                page.copy(items = page.items.filter { it.id > afterMessageId })
            }
            is GetDataResponse.Error -> error("Не удалось загрузить новые сообщения")
        }
    }

    private fun DirectChatAnchoredMessagesWindow.toThreadPage(): ChatThreadPage {
        val selfUserId = authenticationManager.getSelfUserIdOrNull()
        return ChatThreadPage(
            items = items.map { it.toItemModel(selfUserId) },
            nextBeforeMessageId = items.minOfOrNull { it.id }?.takeIf { hasOlderMessages },
            canLoadMore = hasOlderMessages,
            peerLastReadMessageId = peerLastReadMessageId,
            hasNewerMessages = hasNewerMessages,
        )
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
		when (val response = chatsRepository.setMessagePinned(conversationId, messageId, isPinned)) {
			is GetDataResponse.Success -> {
				sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
					cachedMessages.map { message ->
						if (message.id == messageId) message.copy(isPinned = isPinned) else message
					}
				})
				Unit
			}
            is GetDataResponse.Error -> error(if (response.error == GetDataError.LimitExceeded) {
                "В чате можно закрепить до 20 сообщений"
            } else "Не удалось обновить закреп")
		}
	}

	private fun upsertCachedMessage(
		cachedMessages: List<ChatMessageItemModel>,
		message: ChatMessageItemModel,
	): List<ChatMessageItemModel> {
		val clientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		val existingByClientMessageId = clientMessageId?.let { normalizedClientMessageId ->
			cachedMessages.firstOrNull { existing -> existing.clientMessageId?.trim() == normalizedClientMessageId }
		}
        if (existingByClientMessageId == null && cachedMessages.none { it.id == message.id }) return cachedMessages
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

    override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
        if (conversationId <= 0L || messageId <= 0L) return@runCatching
        val accountId = authenticationManager.getSelfUserIdOrNull() ?: return@runCatching
        progress.enqueue(accountId, conversationId, messageId)
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

    private fun resolveInitialAnchorMessageId(
        request: DirectChatInitialRequest,
        viewport: me.floow.domain.data.repos.DirectChatViewportSnapshot,
        firstUnread: Long?,
    ): Long? {
        if (request.openMode == ChatOpenMode.LATEST) return null
        if (request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) return request.anchorMessageId?.takeIf { it > 0L }
        if (!viewport.isBottomPinned) viewport.anchorMessageId?.takeIf { it > 0L }?.let { return it }
        return firstUnread?.takeIf { it > 0L }
    }

	private suspend fun loadLatestCachedSnapshot(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		selfUserId: String?,
		peerLastReadFallback: Long?,
		highlightedMessageId: Long?,
	): ChatThreadSnapshot? {
		val page = chatsRepository.observeMessages(conversationId, limit = INITIAL_MESSAGES_LIMIT).first()
        // Without a saved bound, Room can contain isolated rows cached by pin and quote requests.
        val newest = page.items.lastOrNull { it.id > 0L }
        val cachedPage = page.copy(items = listOfNotNull(newest) + page.items.filter { it.id <= 0L })
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
            ).copy(
                firstUnreadMessageId = initialResponse.data.conversation.firstUnreadId,
                lastReadMessageId = initialResponse.data.conversation.lastReadMessageId,
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
					page = response.data.takeUnless { it.isCached } ?: error("Не удалось загрузить последние сообщения"),
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
        hasNewerMessages = hasNewerMessages,
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
