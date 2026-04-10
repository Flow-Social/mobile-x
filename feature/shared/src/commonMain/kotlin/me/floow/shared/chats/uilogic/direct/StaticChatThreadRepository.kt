package me.floow.shared.chats.uilogic.direct
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache

class StaticChatThreadRepository(
	private val sessionCache: SharedChatSessionCache = SharedChatSessionCache(),
) : ChatThreadRepository {
	private val messagesByConversationId = mutableMapOf<Long, MutableList<ChatMessageItemModel>>()
	private var nextMessageId = 10_000L
	private var nextTimestampMillis = 1_741_000_200_000L

	override suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> = runCatching {
		sessionCache.cachedThreadSnapshot(request)
	}

	override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> = runCatching {
		val conversationId = request.conversationId ?: stableConversationId(request.peerUserId)
		val messages = messagesByConversationId.getOrPut(conversationId) {
			defaultMessages(conversationId, request)
		}.toList()
		sessionCache.rememberPeer(conversationId, request.peerUserId)
		ChatThreadSnapshot(
			conversationId = conversationId,
			header = ChatThreadHeaderModel(
				peerUserId = request.peerUserId,
				selfUserId = if (request.isSavedMessages) "self" else null,
				title = request.peerDisplayName,
				avatarUrl = request.peerAvatarUrl,
				subtitle = if (request.isSavedMessages) null else "online",
				isSavedMessages = request.isSavedMessages,
			),
			messages = messages,
			canLoadMore = false,
			nextBeforeMessageId = null,
			highlightedMessageId = request.anchorMessageId,
		).also(sessionCache::cacheThreadSnapshot)
	}

	override suspend fun loadMore(
		conversationId: Long,
		beforeMessageId: Long?,
	): Result<ChatThreadPage> = Result.success(ChatThreadPage(items = emptyList()))

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		replyToMessageId: Long?,
	): Result<ChatMessageItemModel> = runCatching {
		val replyToMessageText = replyToMessageId?.let { replyId ->
			messagesByConversationId[conversationId]
				.orEmpty()
				.firstOrNull { it.id == replyId }
				?.text
		}
		val message = ChatMessageItemModel(
			id = nextMessageId++,
			senderUserId = "self",
			senderDisplayName = "You",
			text = text,
			createdAtMillis = nextTimestamp(),
			isOutgoing = true,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			deliveryState = ChatDeliveryState.READ,
		)
		messagesByConversationId.getOrPut(conversationId) { mutableListOf() }.add(message)
			sessionCache.updateThreadMessages(conversationId, transform = {
				messagesByConversationId[conversationId].orEmpty().toList()
			})
		message
	}

	override suspend fun editMessage(
		conversationId: Long,
		messageId: Long,
		newText: String,
	): Result<ChatMessageItemModel> = runCatching {
		val messages = messagesByConversationId.getOrPut(conversationId) { mutableListOf() }
		val index = messages.indexOfFirst { it.id == messageId }
		check(index >= 0) { "message not found" }
		val updated = messages[index].copy(text = newText)
		messages[index] = updated
			sessionCache.updateThreadMessages(conversationId, transform = { messages.toList() })
		updated
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> = runCatching {
		messagesByConversationId[conversationId]?.removeAll { it.id == messageId }
			sessionCache.updateThreadMessages(conversationId, transform = {
				messagesByConversationId[conversationId].orEmpty().toList()
			})
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
	): Result<Unit> = runCatching {
		val messages = messagesByConversationId.getOrPut(conversationId) { mutableListOf() }
		val index = messages.indexOfFirst { it.id == messageId }
		check(index >= 0) { "message not found" }
		messages[index] = messages[index].copy(isPinned = isPinned)
			sessionCache.updateThreadMessages(conversationId, transform = { messages.toList() })
	}

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> = Result.success(Unit)

	private fun defaultMessages(
		conversationId: Long,
		request: DirectChatInitialRequest,
	): MutableList<ChatMessageItemModel> = mutableListOf(
		ChatMessageItemModel(
			id = conversationId * 100 + 1,
			senderUserId = if (request.isSavedMessages) "self" else request.peerUserId,
			senderDisplayName = if (request.isSavedMessages) "You" else request.peerDisplayName,
			text = if (request.isSavedMessages) "Сохранил это сюда" else "Привет. Это локальный shared chat.",
			createdAtMillis = nextTimestamp() - 120_000,
			isOutgoing = request.isSavedMessages,
		),
		ChatMessageItemModel(
			id = conversationId * 100 + 2,
			senderUserId = "self",
			senderDisplayName = "You",
			text = "Теперь этот экран работает локально в common/wasm.",
			createdAtMillis = nextTimestamp() - 60_000,
			isOutgoing = true,
			deliveryState = ChatDeliveryState.READ,
		),
	)

	private fun stableConversationId(peerUserId: String): Long {
		return peerUserId.fold(7L) { acc, char -> acc * 31 + char.code }.let(::absoluteValue)
	}

	private fun nextTimestamp(): Long {
		nextTimestampMillis += 1_000L
		return nextTimestampMillis
	}
}

private fun absoluteValue(value: Long): Long = if (value < 0L) -value else value
