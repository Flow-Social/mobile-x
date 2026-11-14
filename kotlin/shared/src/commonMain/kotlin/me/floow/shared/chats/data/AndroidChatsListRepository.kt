package me.floow.shared.chats.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.domain.models.ChatMessageContentType
import me.floow.shared.chats.model.ChatDeliveryStatus
import me.floow.shared.chats.model.ConversationActivityState
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListMediaPreview
import me.floow.shared.chats.model.ChatListItemVisualType
import me.floow.shared.chats.uilogic.ChatsListRepository
import me.floow.shared.chats.uilogic.ChatsPagingState
import me.floow.shared.chats.uilogic.ConversationActivityStore
import me.floow.domain.utils.currentTimeMillis
import me.floow.shared.chats.ui.formatChatClockTime

class AndroidChatsListRepository(
	private val chatsRepository: ChatsRepository,
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository,
	private val presenceRepository: PresenceRepository,
	private val authenticationManager: AuthenticationManager,
	private val activityStore: ConversationActivityStore = ConversationActivityStore(),
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ChatsListRepository {
	private val conversationsFlow: StateFlow<List<DirectChatConversation>?> =
		chatsRepository.observeConversations()
			.stateIn(
				scope = scope,
				started = SharingStarted.Eagerly,
				initialValue = null,
			)

	private val chats = MutableStateFlow<List<ChatListItemModel>?>(null)
	private val pagingState = MutableStateFlow(ChatsPagingState())
	private var latestConversations: List<DirectChatConversation> = emptyList()
	private var latestRepliesPreview: ChatListItemModel = emptyRepliesItem()
	private var latestPresence: Map<String, me.floow.domain.models.UserPresence> = emptyMap()
	private var latestLocalReadStates: Map<Long, me.floow.domain.models.DirectChatReadState> = emptyMap()
	private var latestTypingActivities: Map<Long, ConversationActivityState> = emptyMap()
	private var latestLocalActivities: Map<Long, ConversationActivityState> = emptyMap()
	private var activeTypingConversationIds: Set<Long> = emptySet()
	private var typingSubscriptionJob: Job? = null
	private val typingClearJobs = mutableMapOf<Long, Job>()
	private var nextCursor: String? = null
	private var hasMore = false

	init {
		conversationsFlow.value?.let { initialConversations ->
			latestConversations = initialConversations
			syncPresenceTargets(initialConversations)
			syncTypingSubscriptions(initialConversations)
			publishChats()
		}
		scope.launch {
			conversationsFlow.collectLatest { conversations ->
				conversations ?: return@collectLatest
				latestConversations = conversations
				syncPresenceTargets(conversations)
				syncTypingSubscriptions(conversations)
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
		scope.launch {
			chatsRepository.observeLocalReadStates().collectLatest { readStates ->
				latestLocalReadStates = readStates
				publishChats()
			}
		}
		scope.launch {
			activityStore.localActivities.collectLatest { activities ->
				latestLocalActivities = activities
				publishChats()
			}
		}
	}

	override fun observeChats(): StateFlow<List<ChatListItemModel>?> = chats
	override fun observePagingState(): StateFlow<ChatsPagingState> = pagingState

	override suspend fun refreshChats(): Result<List<ChatListItemModel>> = runCatching {
		ensureSavedMessagesConversation()
		notificationsRealtimeRepository.start()
		notificationsRealtimeRepository.refresh()
		when (val response = chatsRepository.getConversations(limit = 50, cursor = null)) {
			is GetDataResponse.Success -> {
				val savedConv = latestConversations.firstOrNull { it.isSavedMessages }
				val merged = if (savedConv != null && response.data.items.none { it.isSavedMessages }) {
					listOf(savedConv) + response.data.items
				} else {
					response.data.items
				}
				latestConversations = merged
				nextCursor = response.data.nextCursor
				hasMore = response.data.hasMore
				pagingState.value = ChatsPagingState(hasMore = hasMore)
				syncPresenceTargets(merged)
				syncTypingSubscriptions(merged)
				publishChats()
				chats.value.orEmpty()
			}
			is GetDataResponse.Error -> error("failed to refresh chats")
		}
	}

	override suspend fun ensureSavedMessagesConversation(): Result<Unit> = runCatching {
		when (val res = chatsRepository.getOrCreateSavedMessagesConversation()) {
			is GetDataResponse.Success -> {
				val savedConv = res.data
				if (latestConversations.none { it.id == savedConv.id || it.isSavedMessages }) {
					latestConversations = listOf(savedConv) + latestConversations
					publishChats()
				}
			}
			is GetDataResponse.Error -> error("failed to ensure saved messages conversation")
		}
	}

	override suspend fun loadMoreChats(): Result<List<ChatListItemModel>> = runCatching {
		if (pagingState.value.isLoadingMore || !hasMore) return@runCatching chats.value.orEmpty()
		val cursor = nextCursor?.trim()?.takeIf(String::isNotEmpty) ?: return@runCatching chats.value.orEmpty()
		pagingState.value = pagingState.value.copy(
			isLoadingMore = true,
			loadMoreError = false,
		)
		try {
			when (val response = chatsRepository.getConversations(limit = PAGE_SIZE, cursor = cursor)) {
				is GetDataResponse.Success -> {
					val savedConv = latestConversations.firstOrNull { it.isSavedMessages }
					var merged = mergeConversations(latestConversations, response.data.items)
					if (savedConv != null && merged.none { it.isSavedMessages }) {
						merged = listOf(savedConv) + merged
					}
					latestConversations = merged
					nextCursor = response.data.nextCursor
					hasMore = response.data.hasMore
					syncPresenceTargets(latestConversations)
					syncTypingSubscriptions(latestConversations)
					publishChats()
					chats.value.orEmpty()
				}
				is GetDataResponse.Error -> {
					pagingState.value = pagingState.value.copy(
						isLoadingMore = false,
						hasMore = hasMore,
						loadMoreError = true,
					)
					error("failed to load more chats")
				}
			}
		} finally {
			if (pagingState.value.isLoadingMore) {
				pagingState.value = ChatsPagingState(
					isLoadingMore = false,
					hasMore = hasMore,
					loadMoreError = false,
				)
			}
		}
	}

	override suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean): Result<Unit> = runCatching {
		when (chatsRepository.setConversationPinned(conversationId, isPinned)) {
			is GetDataResponse.Success -> {
				latestConversations = latestConversations.map { conversation ->
					if (conversation.id == conversationId) {
						conversation.copy(
							isPinned = isPinned,
							pinnedAt = if (isPinned) currentTimeMillis() else null,
						)
					} else {
						conversation
					}
				}
				publishChats()
			}
			is GetDataResponse.Error -> error("failed to update conversation pin")
		}
	}

	override suspend fun deleteConversation(conversationId: Long, forEveryone: Boolean): Result<Unit> = runCatching {
		when (chatsRepository.deleteConversation(conversationId, forEveryone)) {
			is UpdateDataResponse.Success -> {
				latestConversations = latestConversations.filterNot { it.id == conversationId }
				publishChats()
			}
			is UpdateDataResponse.Failure -> error("failed to delete conversation")
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

	private fun syncTypingSubscriptions(conversations: List<DirectChatConversation>) {
		val activeConversationIds = conversations
			.asSequence()
			.filterNot(DirectChatConversation::isSavedMessages)
			.map(DirectChatConversation::id)
			.filter { it > 0L }
			.toSet()
		activeTypingConversationIds = activeConversationIds
		(latestTypingActivities.keys - activeConversationIds).forEach { conversationId ->
			typingClearJobs.remove(conversationId)?.cancel()
			latestTypingActivities = latestTypingActivities - conversationId
		}
		if (typingSubscriptionJob != null) return
		typingSubscriptionJob = scope.launch {
			chatsRepository.subscribeAllConversations(afterSeq = 0L).collect { event ->
				when (event) {
					is DirectChatRealtimeEvent.Typing -> {
						if (event.conversationId in activeTypingConversationIds) {
							applyTypingEvent(event)
						}
					}
					is DirectChatRealtimeEvent.MessageCreated,
					is DirectChatRealtimeEvent.MessageUpdated,
					is DirectChatRealtimeEvent.MessageDeleted,
					is DirectChatRealtimeEvent.ReadUpToUpdated -> {
						chatsRepository.getConversations(limit = 50, cursor = null)
					}
					else -> Unit
				}
			}
		}
	}

	private fun applyTypingEvent(event: DirectChatRealtimeEvent.Typing) {
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		if (event.actorUserId.isBlank() || event.actorUserId == selfUserId) return
		val conversation = latestConversations.firstOrNull { it.id == event.conversationId } ?: return
		typingClearJobs.remove(event.conversationId)?.cancel()
		if (!event.isTyping) {
			latestTypingActivities = latestTypingActivities - event.conversationId
			publishChats()
			return
		}
		val ttlMs = event.typingTtlMs.takeIf { it > 0L } ?: DEFAULT_TYPING_TTL_MS
		val activity = if (event.action == "recording_video") {
			ConversationActivityState.RecordingVideo(startedAtMillis = currentTimeMillis())
		} else {
			ConversationActivityState.Typing(
				displayName = conversation.resolveTitle(),
				expiresAtMillis = currentTimeMillis() + ttlMs,
			)
		}
		latestTypingActivities = latestTypingActivities + (event.conversationId to activity)
		publishChats()
		typingClearJobs[event.conversationId] = scope.launch {
			delay(ttlMs)
			latestTypingActivities = latestTypingActivities - event.conversationId
			publishChats()
		}
	}

	private fun publishChats() {
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		val list = buildList {
			add(latestRepliesPreview)
			addAll(
				latestConversations
					.sortedWith(
						compareByDescending<DirectChatConversation> { it.isSavedMessages || (selfUserId != null && it.peer.id == selfUserId) }
							.thenByDescending { it.isPinned }
							.thenByDescending { it.pinnedAt ?: 0L }
							.thenByDescending { it.lastMessage?.createdAt ?: it.updatedAt }
					)
					.map { conversation ->
						conversation.toChatListItem(
							selfUserId = selfUserId,
							isOnline = latestPresence[conversation.peer.id]?.isOnline == true,
							localReadState = latestLocalReadStates[conversation.id],
							activityState = resolveConversationActivity(conversation),
						)
					}
			)
		}.distinctBy { it.id }
		chats.value = list
	}

	private fun resolveConversationActivity(conversation: DirectChatConversation): ConversationActivityState {
		latestTypingActivities[conversation.id]?.let { return it }
		latestLocalActivities[conversation.id]?.let { return it }
		if (!conversation.isSavedMessages) {
			val draftText = activityStore.getDraftText(conversation.id).ifBlank {
				activityStore.getDraftTextByKey(conversation.id.toString()).ifBlank {
					activityStore.getDraftTextByKey(conversation.peer.id)
				}
			}
			if (draftText.isNotBlank()) {
				return ConversationActivityState.Draft(
					updatedAtMillis = 0L,
					text = draftText.trim(),
				)
			}
		}
		val latestMessage = conversation.lastMessage ?: return ConversationActivityState.None
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		val isOutgoing = latestMessage.sender.id.takeIf(String::isNotBlank) == selfUserId && !conversation.isSavedMessages
		return if (isOutgoing && (latestMessage.deliveryStatus == MessageDeliveryStatus.SENDING || latestMessage.id <= 0L)) {
			ConversationActivityState.Sending(startedAtMillis = latestMessage.createdAt)
		} else {
			ConversationActivityState.None
		}
	}

	private companion object {
		const val PRESENCE_OWNER = "android_chats_list"
		const val DEFAULT_TYPING_TTL_MS = 3_000L
		const val PAGE_SIZE = 50
	}
}

private fun mergeConversations(
	current: List<DirectChatConversation>,
	incoming: List<DirectChatConversation>
): List<DirectChatConversation> {
	if (incoming.isEmpty()) return current
	val result = ArrayList<DirectChatConversation>(current.size + incoming.size)
	val seen = HashSet<Long>(current.size + incoming.size)
	incoming.forEach { conversation ->
		if (seen.add(conversation.id)) {
			result += conversation
		}
	}
	current.forEach { conversation ->
		if (seen.add(conversation.id)) {
			result += conversation
		}
	}
	return result
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
		isPinned = false,
		isOnline = false,
		avatarUrl = null,
		mediaPreview = null,
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
		isPinned = false,
		isOnline = false,
		avatarUrl = null,
		mediaPreview = null,
		isOutgoingPreview = false,
		deliveryStatus = null,
		visualType = ChatListItemVisualType.RepliesInbox,
	)
}

private fun DirectChatConversation.toChatListItem(
	selfUserId: String?,
	isOnline: Boolean,
	localReadState: me.floow.domain.models.DirectChatReadState?,
	activityState: ConversationActivityState,
): ChatListItemModel {
	val latestMessage = lastMessage
	val title = resolveTitle()
	val isActuallySaved = isSavedMessages || (selfUserId != null && peer.id == selfUserId)
	val mediaType = ChatMessageContentType.fromString(
		raw = latestMessage?.contentType,
		mimeType = latestMessage?.media?.mimeType
	)
	val previewText = latestMessage?.text?.takeIf(String::isNotBlank)
		?: when {
			latestMessage?.groupId != null || latestMessage?.media?.groupId != null -> "Альбом"
			mediaType == ChatMessageContentType.VIDEO_NOTE -> "Видеосообщение"
			mediaType == ChatMessageContentType.VIDEO -> "Видео"
			mediaType == ChatMessageContentType.VOICE -> "Голосовое сообщение"
			mediaType == ChatMessageContentType.IMAGE -> "Фотография"
			isActuallySaved -> "Нет сообщений"
			else -> "Диалог создан"
		}
	val mediaPreview: ChatListMediaPreview? = when (mediaType) {
		ChatMessageContentType.TEXT, ChatMessageContentType.VOICE -> null
		ChatMessageContentType.IMAGE, ChatMessageContentType.VIDEO -> {
			val url = latestMessage?.media?.thumbnailUrl?.takeIf(String::isNotBlank)
				?: latestMessage?.media?.url?.takeIf(String::isNotBlank)
			url?.let { ChatListMediaPreview(thumbnailUrl = it, isCircle = false) }
		}
		ChatMessageContentType.VIDEO_NOTE -> {
			val url = latestMessage?.media?.thumbnailUrl?.takeIf(String::isNotBlank)
				?: latestMessage?.media?.url?.takeIf(String::isNotBlank)
			url?.let { ChatListMediaPreview(thumbnailUrl = it, isCircle = true) }
		}
	}
	val isOutgoingPreview = latestMessage?.sender?.id?.takeIf(String::isNotBlank) == selfUserId && !isActuallySaved
	val deliveryStatus = if (!isOutgoingPreview || latestMessage == null) {
		null
	} else if (latestMessage.deliveryStatus == MessageDeliveryStatus.SENDING) {
		ChatDeliveryStatus.Sending
	} else if (latestMessage.deliveryStatus == MessageDeliveryStatus.FAILED) {
		ChatDeliveryStatus.Failed
	} else if (latestMessage.id > 0L && (peerLastReadMessageId ?: 0L) >= latestMessage.id) {
		ChatDeliveryStatus.Read
	} else if (latestMessage.id <= 0L) {
		ChatDeliveryStatus.Sending
	} else {
		ChatDeliveryStatus.Sent
	}
	val resolvedUnreadCount = localReadState?.unreadCount?.coerceAtLeast(0) ?: this.unreadCount.coerceAtLeast(0)
	return ChatListItemModel(
		id = if (isActuallySaved) "saved" else (peer.id.takeIf(String::isNotBlank) ?: "conv_$id"),
		peerUserId = peer.id,
		conversationId = id,
		title = if (isActuallySaved) "Избранное" else title,
		previewText = previewText,
		timeLabel = formatTimeLabel(latestMessage?.createdAt ?: updatedAt),
		unreadCount = resolvedUnreadCount,
		isMuted = false,
		isPinned = isPinned,
		isOnline = !isActuallySaved && isOnline,
		avatarUrl = if (isActuallySaved) null else peer.avatarUrl,
		avatarThumb = if (isActuallySaved) null else peer.avatarThumb,
		mediaPreview = mediaPreview,
		isOutgoingPreview = isOutgoingPreview,
		deliveryStatus = deliveryStatus,
		visualType = if (isSavedMessages) ChatListItemVisualType.SavedMessages else ChatListItemVisualType.Regular,
		activityState = activityState,
	)
}

private fun DirectChatConversation.resolveTitle(): String {
	return if (isSavedMessages) {
		"Избранное"
	} else {
		peer.name?.takeIf(String::isNotBlank)
			?: peer.username?.takeIf(String::isNotBlank)
			?: "User ${peer.id}"
	}
}

private const val SAVED_MESSAGES_CHAT_KIND = "saved_messages"

private val DirectChatConversation.isSavedMessages: Boolean
	get() = kind.trim().lowercase() == SAVED_MESSAGES_CHAT_KIND || kind.trim().lowercase() == "saved"


private fun formatTimeLabel(epochMillis: Long?): String {
	if (epochMillis == null || epochMillis <= 0L) return ""
	return formatChatClockTime(epochMillis)
}
