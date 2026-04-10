package me.floow.chats.uilogic.chats

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.DirectChatConversation
import me.floow.shared.chats.model.ChatDeliveryStatus
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.uilogic.ChatsListRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AndroidChatsListRepository(
	private val chatsRepository: ChatsRepository,
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository,
	private val presenceRepository: PresenceRepository,
	private val authenticationManager: AuthenticationManager,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ChatsListRepository {
	private val chats = MutableStateFlow<List<ChatListItemModel>?>(null)
	private var latestConversations: List<DirectChatConversation> = emptyList()
	private var latestRepliesPreview: ChatListItemModel = emptyRepliesItem()
	private var latestPresence: Map<String, me.floow.domain.models.UserPresence> = emptyMap()

	init {
		scope.launch {
			chatsRepository.observeConversations().collectLatest { conversations ->
				latestConversations = conversations
				syncPresenceTargets(conversations)
				publishChats()
			}
		}
		scope.launch {
			notificationsRealtimeRepository.repliesState.collectLatest { realtimeState ->
				latestRepliesPreview = realtimeState.page.toRepliesItem()
				publishChats()
			}
		}
		scope.launch {
			presenceRepository.presences.collectLatest { presences ->
				latestPresence = presences
				publishChats()
			}
		}
	}

	override fun observeChats(): StateFlow<List<ChatListItemModel>?> = chats

	override suspend fun refreshChats(): Result<List<ChatListItemModel>> = runCatching {
		ensureSavedMessagesConversation().getOrThrow()
		notificationsRealtimeRepository.start()
		notificationsRealtimeRepository.refresh()
		when (val response = chatsRepository.getConversations(limit = 50, cursor = null)) {
			is GetDataResponse.Success -> {
				latestConversations = response.data.items
				syncPresenceTargets(response.data.items)
				publishChats()
				chats.value.orEmpty()
			}
			is GetDataResponse.Error -> error("failed to refresh chats")
		}
	}

	override suspend fun ensureSavedMessagesConversation(): Result<Unit> = runCatching {
		when (chatsRepository.getOrCreateSavedMessagesConversation()) {
			is GetDataResponse.Success -> Unit
			is GetDataResponse.Error -> error("failed to ensure saved messages conversation")
		}
	}

	private fun syncPresenceTargets(conversations: List<DirectChatConversation>) {
		val targets = conversations
			.asSequence()
			.filterNot(DirectChatConversation::isSavedMessages)
			.map { it.peer.id.trim() }
			.filter(String::isNotEmpty)
			.distinct()
			.toList()
		presenceRepository.setTargets(owner = PRESENCE_OWNER, userIds = targets)
	}

	private fun publishChats() {
		chats.value = buildList {
			add(latestRepliesPreview)
			addAll(
				latestConversations
					.sortedWith(
						compareByDescending<DirectChatConversation> { it.isSavedMessages }
							.thenByDescending { it.lastMessage?.createdAt ?: it.updatedAt }
					)
					.map { conversation ->
						conversation.toChatListItem(
							selfUserId = authenticationManager.getSelfUserIdOrNull(),
							isOnline = latestPresence[conversation.peer.id]?.isOnline == true,
						)
					}
			)
		}
	}

	private companion object {
		const val PRESENCE_OWNER = "android_chats_list"
	}
}

private fun me.floow.domain.models.UserNotificationsPage.toRepliesItem(): ChatListItemModel {
	val latest = items.maxByOrNull { it.createdAt }
	val previewText = latest?.commentText?.takeIf(String::isNotBlank)
		?: latest?.replyToCommentText?.takeIf(String::isNotBlank)
		?: latest?.body?.takeIf(String::isNotBlank)
		?: latest?.title?.takeIf(String::isNotBlank)
		?: "Ответы и комментарии к вашим постам"
	return ChatListItemModel(
		id = "replies",
		peerUserId = null,
		conversationId = null,
		title = "Ответы",
		previewText = previewText,
		timeLabel = formatTimeLabel(latest?.createdAt),
		unreadCount = unreadCount.coerceAtLeast(0),
		isMuted = false,
		isOnline = false,
		avatarUrl = null,
		hasAttachmentPreview = false,
		isOutgoingPreview = false,
		deliveryStatus = null,
		visualType = ChatListItemVisualType.RepliesInbox,
	)
}

private fun emptyRepliesItem(): ChatListItemModel {
	return ChatListItemModel(
		id = "replies",
		title = "Ответы",
		previewText = "Ответы и комментарии к вашим постам",
		timeLabel = "",
		unreadCount = 0,
		isMuted = false,
		isOnline = false,
		avatarUrl = null,
		hasAttachmentPreview = false,
		isOutgoingPreview = false,
		deliveryStatus = null,
		visualType = ChatListItemVisualType.RepliesInbox,
	)
}

private fun DirectChatConversation.toChatListItem(
	selfUserId: String?,
	isOnline: Boolean,
): ChatListItemModel {
	val latestMessage = lastMessage
	val title = if (isSavedMessages) {
		"Избранное"
	} else {
		peer.name?.takeIf(String::isNotBlank)
			?: peer.username?.takeIf(String::isNotBlank)
			?: "User ${peer.id}"
	}
	val previewText = latestMessage?.text?.takeIf(String::isNotBlank)
		?: if (isSavedMessages) "Нет сообщений" else "Диалог создан"
	val isOutgoingPreview = latestMessage?.sender?.id?.takeIf(String::isNotBlank) == selfUserId && !isSavedMessages
	val deliveryStatus = if (!isOutgoingPreview || latestMessage == null) {
		null
	} else if ((peerLastReadMessageId ?: 0L) >= latestMessage.id) {
		ChatDeliveryStatus.Read
	} else {
		ChatDeliveryStatus.Sent
	}
	return ChatListItemModel(
		id = if (isSavedMessages) "saved" else peer.id,
		peerUserId = peer.id,
		conversationId = id,
		title = title,
		previewText = previewText,
		timeLabel = formatTimeLabel(latestMessage?.createdAt ?: updatedAt),
		unreadCount = unreadCount.coerceAtLeast(0),
		isMuted = false,
		isOnline = !isSavedMessages && isOnline,
		avatarUrl = peer.avatarUrl,
		hasAttachmentPreview = false,
		isOutgoingPreview = isOutgoingPreview,
		deliveryStatus = deliveryStatus,
		visualType = if (isSavedMessages) ChatListItemVisualType.SavedMessages else ChatListItemVisualType.Regular,
	)
}

private val DirectChatConversation.isSavedMessages: Boolean
	get() = kind.trim().lowercase() == SAVED_MESSAGES_CHAT_KIND || kind.trim().lowercase() == "saved"

private fun formatTimeLabel(epochMillis: Long?): String {
	if (epochMillis == null || epochMillis <= 0L) return ""
	return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))
}
