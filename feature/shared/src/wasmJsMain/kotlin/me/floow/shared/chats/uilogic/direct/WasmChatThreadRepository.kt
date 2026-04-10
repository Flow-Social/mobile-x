package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.uilogic.WasmChatConversation
import me.floow.shared.chats.uilogic.WasmChatMessage
import me.floow.shared.chats.uilogic.WasmChatsApiSupport
import me.floow.shared.chats.uilogic.parseChatConversation
import me.floow.shared.chats.uilogic.parseChatMessage
import me.floow.shared.chats.uilogic.parseChatMessagesAround
import me.floow.shared.chats.uilogic.parseChatMessagesPage
import me.floow.shared.chats.uilogic.parsePresenceItems
import me.floow.shared.chats.uilogic.parseSentChatMessage
import me.floow.shared.chats.uilogic.encodeURIComponent
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache
import me.floow.shared.chats.uilogic.direct.hasValidConversationTarget
import me.floow.shared.chats.uilogic.direct.hasValidPeerCreateTarget

class WasmChatThreadRepository(
	private val sessionCache: SharedChatSessionCache,
) : ChatThreadRepository {

	override suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> = runCatching {
		sessionCache.cachedThreadSnapshot(request)
	}

	override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		val conversation = resolveConversation(request, authToken)
		sessionCache.rememberPeer(conversation.id, conversation.peer.id)
		coroutineScope {
			val subtitleDeferred = if (request.isSavedMessages) {
				null
			} else {
				async { loadPeerSubtitle(conversation.peer.id, authToken) }
			}
			val snapshotDeferred = async {
				if (request.anchorMessageId != null && request.openMode == me.floow.shared.chats.model.ChatOpenMode.FROM_MESSAGE_LINK) {
					val around = parseChatMessagesAround(
						WasmChatsApiSupport.apiRequestV2(
							method = "GET",
							path = "/chats/conversations/${conversation.id}/messages/around?anchor_id=${request.anchorMessageId}&older=50&newer=50",
							authToken = authToken,
						)
					)
					around to null
				} else {
					null to parseChatMessagesPage(
						WasmChatsApiSupport.apiRequestV2(
							method = "GET",
							path = "/chats/conversations/${conversation.id}/messages?limit=50",
							authToken = authToken,
						)
					)
				}
			}
			val header = ChatThreadHeaderModel(
				peerUserId = conversation.peer.id,
				selfUserId = if (request.isSavedMessages) conversation.peer.id else null,
				title = if (request.isSavedMessages) "Избранное" else (
					conversation.peer.name?.takeIf(String::isNotBlank)
						?: conversation.peer.username?.takeIf(String::isNotBlank)
						?: request.peerDisplayName
				),
				avatarUrl = WasmChatsApiSupport.toAbsoluteMediaUrl(conversation.peer.avatar),
				subtitle = subtitleDeferred?.await(),
				isSavedMessages = request.isSavedMessages,
			)
			val (around, page) = snapshotDeferred.await()
			(if (around != null) {
				ChatThreadSnapshot(
					conversationId = conversation.id,
					header = header,
					messages = around.items.sortedBy(WasmChatMessage::id).map {
						it.toItemModel(
							peerUserId = conversation.peer.id,
							selfUserId = if (request.isSavedMessages) conversation.peer.id else null,
							peerLastReadMessageId = around.peerLastReadMessageId,
						)
					},
					canLoadMore = around.hasOlder,
					nextBeforeMessageId = around.items.minOfOrNull(WasmChatMessage::id)?.takeIf { around.hasOlder },
					peerLastReadMessageId = around.peerLastReadMessageId,
					highlightedMessageId = around.anchorId,
				)
			} else {
				checkNotNull(page)
				ChatThreadSnapshot(
					conversationId = conversation.id,
					header = header,
					messages = page.items.sortedBy(WasmChatMessage::id).map {
						it.toItemModel(
							peerUserId = conversation.peer.id,
							selfUserId = if (request.isSavedMessages) conversation.peer.id else null,
							peerLastReadMessageId = page.peerLastReadMessageId,
						)
					},
					canLoadMore = page.nextBeforeId != null,
					nextBeforeMessageId = page.nextBeforeId,
					peerLastReadMessageId = page.peerLastReadMessageId,
					highlightedMessageId = request.anchorMessageId,
				)
			}).also(sessionCache::cacheThreadSnapshot)
		}
	}

	override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		val beforeQuery = beforeMessageId?.let { "&before_id=$it" }.orEmpty()
		val page = parseChatMessagesPage(
			WasmChatsApiSupport.apiRequestV2(
				method = "GET",
				path = "/chats/conversations/$conversationId/messages?limit=50$beforeQuery",
				authToken = authToken,
			)
		)
		val peerId = sessionCache.peerUserId(conversationId).orEmpty()
		val selfUserId = sessionCache.threadSnapshot(conversationId)?.header?.selfUserId
		ChatThreadPage(
			items = page.items
				.sortedBy(WasmChatMessage::id)
				.map {
					it.toItemModel(
						peerUserId = peerId,
						selfUserId = selfUserId,
						peerLastReadMessageId = page.peerLastReadMessageId,
					)
				},
			nextBeforeMessageId = page.nextBeforeId,
			canLoadMore = page.nextBeforeId != null,
			peerLastReadMessageId = page.peerLastReadMessageId,
		).also { chatPage ->
			sessionCache.updateThreadMessages(
				conversationId = conversationId,
				transform = { cachedMessages ->
					(chatPage.items + cachedMessages)
						.distinctBy(ChatMessageItemModel::id)
						.sortedBy(ChatMessageItemModel::id)
				},
				updateMetadata = { cached ->
					cached.copy(
						canLoadMore = chatPage.canLoadMore,
						nextBeforeMessageId = chatPage.nextBeforeMessageId,
						peerLastReadMessageId = chatPage.peerLastReadMessageId ?: cached.peerLastReadMessageId,
					)
				}
			)
		}
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		replyToMessageId: Long?,
	): Result<ChatMessageItemModel> = runCatching {
		sendMessage(conversationId, text, clientMessageId = null, replyToMessageId = replyToMessageId).getOrThrow()
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?,
	): Result<ChatMessageItemModel> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		val raw = WasmChatsApiSupport.apiRequestV2(
			method = "POST",
			path = "/chats/conversations/$conversationId/messages",
			authToken = authToken,
			contentType = "application/json",
			body = WasmChatsApiSupport.json.encodeToString(
				SendChatMessageRequest(
					text = text,
					idempotencyKey = clientMessageId,
					replyToMessageId = replyToMessageId?.toString(),
				)
			),
		)
		parseSentChatMessage(raw).toItemModel(
			peerUserId = sessionCache.peerUserId(conversationId).orEmpty(),
			selfUserId = sessionCache.cachedThreadSnapshot(
				DirectChatInitialRequest(
					peerUserId = sessionCache.peerUserId(conversationId).orEmpty(),
					peerDisplayName = "",
					conversationId = conversationId,
				)
			)?.header?.selfUserId,
			peerLastReadMessageId = null,
		).copy(
			clientMessageId = parseSentChatMessage(raw).clientMessageId ?: clientMessageId,
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENT,
			).also { message ->
				sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
					mergeMessagesByIdentity(cachedMessages, message)
				})
			}
	}

	override suspend fun editMessage(
		conversationId: Long,
		messageId: Long,
		newText: String,
	): Result<ChatMessageItemModel> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		val raw = WasmChatsApiSupport.apiRequestV2(
			method = "PATCH",
			path = "/chats/conversations/$conversationId/messages/$messageId",
			authToken = authToken,
			contentType = "application/json",
			body = WasmChatsApiSupport.json.encodeToString(UpdateChatMessageRequest(text = newText)),
		)
			parseChatMessage(raw).toItemModel(
				peerUserId = sessionCache.peerUserId(conversationId).orEmpty(),
				selfUserId = sessionCache.cachedThreadSnapshot(
					DirectChatInitialRequest(
						peerUserId = sessionCache.peerUserId(conversationId).orEmpty(),
						peerDisplayName = "",
						conversationId = conversationId,
					)
				)?.header?.selfUserId,
				peerLastReadMessageId = null,
			).also { updated ->
				sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
					cachedMessages.map { existing ->
						if (existing.id == updated.id) updated.copy(isOutgoing = existing.isOutgoing) else existing
					}
				})
			}
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		WasmChatsApiSupport.apiRequestV2(
			method = "DELETE",
			path = "/chats/conversations/$conversationId/messages/$messageId",
			authToken = authToken,
		)
			sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
				cachedMessages.filterNot { it.id == messageId }
			})
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
	): Result<Unit> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		WasmChatsApiSupport.apiRequestV2(
			method = if (isPinned) "POST" else "DELETE",
			path = "/chats/conversations/$conversationId/messages/$messageId/pin",
			authToken = authToken,
		)
			sessionCache.updateThreadMessages(conversationId, transform = { cachedMessages ->
				cachedMessages.map { message ->
					if (message.id == messageId) message.copy(isPinned = isPinned) else message
				}
			})
	}

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		WasmChatsApiSupport.apiRequestV2(
			method = "POST",
			path = "/chats/conversations/$conversationId/read-up-to",
			authToken = authToken,
			contentType = "application/json",
			body = WasmChatsApiSupport.json.encodeToString(MarkReadUpToRequest(messageId = messageId.toString())),
		)
	}

	override suspend fun setTyping(conversationId: Long, isTyping: Boolean): Result<Unit> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		WasmChatsApiSupport.apiRequestV2(
			method = "POST",
			path = "/chats/conversations/$conversationId/typing",
			authToken = authToken,
			contentType = "application/json",
			body = WasmChatsApiSupport.json.encodeToString(TypingRequest(typing = isTyping)),
		)
	}

	private suspend fun resolveConversation(
		request: DirectChatInitialRequest,
		authToken: String,
	): WasmChatConversation {
		if (request.isSavedMessages) {
			val raw = WasmChatsApiSupport.apiRequestV2(
				method = "POST",
				path = "/chats/saved",
				authToken = authToken,
			)
			return parseChatConversation(raw)
		}
		if (request.hasValidConversationTarget()) {
			request.conversationId?.takeIf { it > 0L }?.let { conversationId ->
				runCatching {
					return parseChatConversation(
						WasmChatsApiSupport.apiRequestV2(
							method = "GET",
							path = "/chats/conversations/$conversationId",
							authToken = authToken,
						)
					)
				}
			}
		}
		check(request.hasValidPeerCreateTarget()) {
			"conversation id is required for direct chat open"
		}
		val peerUserId = request.peerUserId.trim()
		check(peerUserId.isNotEmpty()) { "peer user id is missing" }
		val raw = WasmChatsApiSupport.apiRequestV2(
			method = "POST",
			path = "/chats/direct",
			authToken = authToken,
			contentType = "application/json",
			body = WasmChatsApiSupport.json.encodeToString(CreateDirectConversationRequest(userId = peerUserId)),
		)
		return parseChatConversation(raw)
	}

	private suspend fun loadPeerSubtitle(
		peerUserId: String,
		authToken: String,
	): String? {
		val presence = runCatching {
			parsePresenceItems(
				WasmChatsApiSupport.apiRequestV1(
					method = "GET",
					path = "/presence?user_ids=${encodeURIComponent(peerUserId)}",
					authToken = authToken,
				)
			).firstOrNull()
		}.getOrNull() ?: return null

		return when {
			presence.isOnline -> "online"
			presence.lastSeenAt != null && presence.lastSeenAt > 0L ->
				"last seen ${formatPresenceTime(presence.lastSeenAt)}"
			else -> "offline"
		}
	}
}

@Serializable
private data class CreateDirectConversationRequest(
	@SerialName("user_id")
	val userId: String,
)

@Serializable
private data class SendChatMessageRequest(
	val text: String,
	@SerialName("idempotency_key")
	val idempotencyKey: String? = null,
	@SerialName("reply_to_message_id")
	val replyToMessageId: String? = null,
)

@Serializable
private data class UpdateChatMessageRequest(
	val text: String,
)

@Serializable
private data class MarkReadUpToRequest(
	@SerialName("message_id")
	val messageId: String,
)

@Serializable
private data class TypingRequest(
	val typing: Boolean,
)

private fun WasmChatMessage.toItemModel(
	peerUserId: String,
	selfUserId: String?,
	peerLastReadMessageId: Long?,
): ChatMessageItemModel {
	val normalizedSelfUserId = selfUserId?.trim()?.takeIf(String::isNotEmpty)
	val isOutgoing = normalizedSelfUserId?.let { sender.id == it } ?: (peerUserId.isNotBlank() && sender.id != peerUserId)
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
		deliveryState = when {
			!isOutgoing -> null
			peerLastReadMessageId != null && peerLastReadMessageId >= id -> ChatDeliveryState.READ
			else -> ChatDeliveryState.SENT
		},
	)
}

private fun mergeMessagesByIdentity(
	currentMessages: List<ChatMessageItemModel>,
	message: ChatMessageItemModel,
): List<ChatMessageItemModel> {
	val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
	val optimistic = normalizedClientMessageId?.let { clientMessageId ->
		currentMessages.firstOrNull { it.clientMessageId == clientMessageId }
	}
	val normalized = if (optimistic != null) {
		message.copy(createdAtMillis = optimistic.createdAtMillis)
	} else {
		message
	}
	return (currentMessages.filterNot { existing ->
		existing.id == normalized.id ||
			(normalizedClientMessageId != null && existing.clientMessageId == normalizedClientMessageId)
	} + normalized).sortedBy(ChatMessageItemModel::createdAtMillis)
}

private fun formatPresenceTime(timestampMillis: Long): String {
	val totalMinutes = timestampMillis / 60_000L
	val hours = ((totalMinutes / 60L) % 24L).toInt().toString().padStart(2, '0')
	val minutes = (totalMinutes % 60L).toInt().toString().padStart(2, '0')
	return "$hours:$minutes"
}
