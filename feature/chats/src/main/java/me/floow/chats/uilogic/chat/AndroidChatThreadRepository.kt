package me.floow.chats.uilogic.chat

import kotlinx.coroutines.flow.first
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.uilogic.direct.ChatThreadPage
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest

class AndroidChatThreadRepository(
	private val chatsRepository: ChatsRepository,
	private val authenticationManager: AuthenticationManager,
	private val directMessagesReadCursorStore: me.floow.domain.data.repos.DirectMessagesReadCursorStore,
) : ChatThreadRepository {
	override suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> = runCatching {
		val conversation = resolveCachedConversation(request) ?: return@runCatching null
		val selfUserId = resolveSelfUserId(request, conversation)
		val header = conversation.toHeader(request, selfUserId)
		val anchorMessageId = request.anchorMessageId
		if (anchorMessageId != null && request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) {
			when (val response = chatsRepository.getAnchoredMessagesWindow(conversation.id, anchorMessageId)) {
				is GetDataResponse.Success -> response.data.toSnapshot(header, selfUserId)
				is GetDataResponse.Error -> null
			}
		} else {
			val cachedPage = chatsRepository.observeMessages(conversation.id, limit = 50).first()
			if (cachedPage.items.isEmpty()) {
				null
			} else {
				ChatThreadSnapshot(
					conversationId = conversation.id,
					header = header,
					messages = cachedPage.items.sortedBy(DirectChatMessage::id).map { it.toItemModel(selfUserId) },
					canLoadMore = cachedPage.nextBeforeId != null,
					nextBeforeMessageId = cachedPage.nextBeforeId,
					peerLastReadMessageId = cachedPage.peerLastReadMessageId,
					highlightedMessageId = request.anchorMessageId,
				)
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

	override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> = runCatching {
		val conversation = resolveConversation(request)
		val selfUserId = resolveSelfUserId(request, conversation)
		val header = conversation.toHeader(request, selfUserId)
		val anchorMessageId = request.anchorMessageId
		val snapshot = if (anchorMessageId != null && request.openMode == ChatOpenMode.FROM_MESSAGE_LINK) {
			when (val response = chatsRepository.getAnchoredMessagesWindow(conversation.id, anchorMessageId)) {
				is GetDataResponse.Success -> response.data.toSnapshot(header, selfUserId)
				is GetDataResponse.Error -> error("failed to load anchored chat")
			}
		} else {
			when (val response = chatsRepository.getMessages(conversation.id, limit = 50, beforeId = null)) {
				is GetDataResponse.Success -> ChatThreadSnapshot(
					conversationId = conversation.id,
					header = header,
					messages = response.data.items.sortedBy(DirectChatMessage::id).map { it.toItemModel(selfUserId) },
					canLoadMore = response.data.nextBeforeId != null,
					nextBeforeMessageId = response.data.nextBeforeId,
					peerLastReadMessageId = response.data.peerLastReadMessageId,
					highlightedMessageId = request.anchorMessageId,
				)
				is GetDataResponse.Error -> error("failed to load chat")
			}
		}
		snapshot
	}

	override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> = runCatching {
		when (val response = chatsRepository.getMessages(conversationId, limit = 50, beforeId = beforeMessageId)) {
			is GetDataResponse.Success -> {
				val selfUserId = authenticationManager.getSelfUserIdOrNull()
				ChatThreadPage(
					items = response.data.items.sortedBy(DirectChatMessage::id).map { it.toItemModel(selfUserId) },
					nextBeforeMessageId = response.data.nextBeforeId,
					canLoadMore = response.data.nextBeforeId != null,
					peerLastReadMessageId = response.data.peerLastReadMessageId,
				)
			}
			is GetDataResponse.Error -> error("failed to load more chat messages")
		}
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?,
	): Result<ChatMessageItemModel> = runCatching {
		when (val response = chatsRepository.sendMessage(conversationId, text, clientMessageId, replyToMessageId)) {
			is GetDataResponse.Success -> response.data.toItemModel(authenticationManager.getSelfUserIdOrNull())
			is GetDataResponse.Error -> error("failed to send message")
		}
	}

	override suspend fun editMessage(
		conversationId: Long,
		messageId: Long,
		newText: String,
	): Result<ChatMessageItemModel> = runCatching {
		when (val response = chatsRepository.updateMessage(conversationId, messageId, newText)) {
			is GetDataResponse.Success -> response.data.toItemModel(authenticationManager.getSelfUserIdOrNull())
			is GetDataResponse.Error -> error("failed to edit message")
		}
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		when (chatsRepository.deleteMessage(conversationId, messageId)) {
			UpdateDataResponse.Success -> Unit
			is UpdateDataResponse.Failure -> error("failed to delete message")
		}
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
	): Result<Unit> = runCatching {
		when (chatsRepository.setMessagePinned(conversationId, messageId, isPinned)) {
			is GetDataResponse.Success -> Unit
			is GetDataResponse.Error -> error("failed to pin message")
		}
	}

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		when (chatsRepository.markReadUpTo(conversationId, messageId)) {
			is GetDataResponse.Success -> {
				directMessagesReadCursorStore.setLocalLastReadMessageId(conversationId, messageId)
				Unit
			}
			is GetDataResponse.Error -> error("failed to mark read up to")
		}
	}

	override suspend fun setTyping(conversationId: Long, isTyping: Boolean): Result<Unit> = runCatching {
		when (chatsRepository.sendTyping(conversationId, isTyping)) {
			UpdateDataResponse.Success -> Unit
			is UpdateDataResponse.Failure -> error("failed to send typing")
		}
	}

	private suspend fun resolveConversation(request: DirectChatInitialRequest): DirectChatConversation {
		val explicitConversationId = request.conversationId?.takeIf { it > 0L }
		if (explicitConversationId != null) {
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
		return null
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
	return ChatThreadHeaderModel(
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
		subtitle = null,
		isSavedMessages = request.isSavedMessages,
	)
}

private fun DirectChatAnchoredMessagesWindow.toSnapshot(
	header: ChatThreadHeaderModel,
	selfUserId: String?,
): ChatThreadSnapshot {
	return ChatThreadSnapshot(
		conversationId = items.firstOrNull()?.conversationId,
		header = header,
		messages = items.sortedBy(DirectChatMessage::id).map { it.toItemModel(selfUserId) },
		canLoadMore = hasOlderMessages,
		nextBeforeMessageId = items.minOfOrNull(DirectChatMessage::id)?.takeIf { hasOlderMessages },
		peerLastReadMessageId = peerLastReadMessageId,
		highlightedMessageId = anchorMessageId,
	)
}

private fun DirectChatMessage.toItemModel(selfUserId: String?): ChatMessageItemModel {
	val normalizedSelfUserId = selfUserId?.trim()?.takeIf(String::isNotEmpty)
	val isOutgoing = normalizedSelfUserId != null && sender.id == normalizedSelfUserId
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
	val content = if (contentType == "video_circle" && messageMedia != null) {
		ChatMessageContent.VideoCircle(
			localPath = null,
			remoteUrl = messageMedia.url,
			durationMs = messageMedia.durationMs,
			width = messageMedia.width ?: 0,
			height = messageMedia.height ?: 0,
			uploadState = VideoUploadState.Uploaded
		)
	} else {
		ChatMessageContent.Text
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
		isDeleted = false,
		deliveryState = deliveryState,
		content = content,
	)
}

private val DirectChatConversation.isSavedMessages: Boolean
	get() = kind.trim().lowercase() == SAVED_MESSAGES_CHAT_KIND || kind.trim().lowercase() == "saved"

private const val SAVED_MESSAGES_CHAT_KIND = "saved_messages"
