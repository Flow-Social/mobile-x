package me.floow.shared.chats.uilogic

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatDeliveryStatus
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache
import kotlin.math.min

class WasmChatsListRepository(
	private val sessionCache: SharedChatSessionCache,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ChatsListRepository {
	init {
		if (sessionCache.markSavedMessagesEnsureStarted()) {
			scope.launch {
				val success = ensureSavedMessagesConversation().isSuccess
				sessionCache.markSavedMessagesEnsureFinished(success)
				if (success) {
					refreshChats()
				}
			}
		}
	}

	override fun observeChats(): StateFlow<List<ChatListItemModel>?> = sessionCache.chats

	override suspend fun refreshChats(): Result<List<ChatListItemModel>> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		val conversations = parseChatConversations(
			WasmChatsApiSupport.apiRequestV2(
				method = "GET",
				path = "/chats/conversations?limit=50",
				authToken = authToken,
			)
		)
		val (presenceByUserId, repliesItem) = coroutineScope {
			val presenceDeferred = async { loadPresence(conversations, authToken) }
			val repliesDeferred = async { loadRepliesInbox(authToken) }
			presenceDeferred.await() to repliesDeferred.await()
		}

		buildList {
			add(repliesItem)
			addAll(
				conversations
					.sortedWith(
						compareByDescending<WasmChatConversation> { it.isSavedMessages() }
							.thenByDescending { it.lastMessage?.createdAt ?: it.updatedAt }
					)
					.map { conversation -> conversation.toChatListItem(presenceByUserId[conversation.peer.id]) }
			)
		}.also(sessionCache::updateChats)
	}

	override suspend fun ensureSavedMessagesConversation(): Result<Unit> = runCatching {
		val authToken = WasmChatsApiSupport.requireAuthToken()
		WasmChatsApiSupport.apiRequestV2(
			method = "POST",
			path = "/chats/saved",
			authToken = authToken,
		)
	}

	private suspend fun loadPresence(
		conversations: List<WasmChatConversation>,
		authToken: String,
	): Map<String, WasmPresenceItem> {
		val userIds = conversations
			.asSequence()
			.filterNot(WasmChatConversation::isSavedMessages)
			.map { it.peer.id.trim() }
			.filter(String::isNotEmpty)
			.distinct()
			.toList()
		if (userIds.isEmpty()) return emptyMap()

		val query = userIds.joinToString(separator = ",", limit = min(userIds.size, 100)) { encodeURIComponent(it) }
		return parsePresenceItems(
			WasmChatsApiSupport.apiRequestV1(
				method = "GET",
				path = "/presence?user_ids=$query",
				authToken = authToken,
			)
		).associateBy(WasmPresenceItem::userId)
	}

	private suspend fun loadRepliesInbox(authToken: String): ChatListItemModel {
		val page = parseNotificationsPage(
			WasmChatsApiSupport.apiRequestV1(
				method = "GET",
				path = "/notifications?limit=20&channel=replies&types=comment_on_post,reply_to_comment,comment_reply",
				authToken = authToken,
			)
		)
		val latest = page.items.maxByOrNull(WasmNotificationItem::createdAt)
		val previewText = latest?.commentText?.takeIf(String::isNotBlank)
			?: latest?.body?.takeIf(String::isNotBlank)
			?: latest?.title?.takeIf(String::isNotBlank)
			?: "Ответы и комментарии к вашим постам"

		return ChatListItemModel(
			id = "replies",
			peerUserId = null,
			conversationId = null,
			title = "Ответы",
			previewText = previewText,
			timeLabel = WasmChatsApiSupport.formatTimeLabel(latest?.createdAt),
			unreadCount = page.unreadCount.coerceAtLeast(0),
			isMuted = false,
			isOnline = false,
			avatarUrl = null,
			hasAttachmentPreview = false,
			isOutgoingPreview = false,
			deliveryStatus = null,
			visualType = ChatListItemVisualType.RepliesInbox,
		)
	}
}

private fun WasmChatConversation.toChatListItem(presence: WasmPresenceItem?): ChatListItemModel {
	val savedMessages = isSavedMessages()
	val title = if (savedMessages) {
		"Избранное"
	} else {
		peer.name?.takeIf(String::isNotBlank)
			?: peer.username?.takeIf(String::isNotBlank)
			?: "User ${peer.id}"
	}
	val previewText = lastMessage?.text?.takeIf(String::isNotBlank) ?: if (savedMessages) "Нет сообщений" else "Диалог создан"
	val peerLastReadMessageId = peerLastReadMessageId ?: 0L
	val lastMessageId = lastMessage?.id
	val isOutgoingPreview = lastMessage?.sender?.id != null &&
		lastMessage.sender.id != peer.id &&
		previewText != "Диалог создан"
	val deliveryStatus = if (!isOutgoingPreview || lastMessageId == null) {
		null
	} else if (peerLastReadMessageId >= lastMessageId) {
		ChatDeliveryStatus.Read
	} else {
		ChatDeliveryStatus.Sent
	}

	return ChatListItemModel(
		id = if (savedMessages) "saved" else peer.id,
		peerUserId = peer.id,
		conversationId = id,
		title = title,
		previewText = previewText,
		timeLabel = WasmChatsApiSupport.formatTimeLabel(lastMessage?.createdAt ?: updatedAt),
		unreadCount = unreadCount.coerceAtLeast(0),
		isMuted = false,
		isOnline = !savedMessages && presence?.isOnline == true,
		avatarUrl = WasmChatsApiSupport.toAbsoluteMediaUrl(peer.avatar),
		hasAttachmentPreview = false,
		isOutgoingPreview = isOutgoingPreview,
		deliveryStatus = deliveryStatus,
		visualType = if (savedMessages) ChatListItemVisualType.SavedMessages else ChatListItemVisualType.Regular,
	)
}

private fun WasmChatConversation.isSavedMessages(): Boolean {
	return kind.trim().lowercase() == "saved_messages" || kind.trim().lowercase() == "saved"
}
