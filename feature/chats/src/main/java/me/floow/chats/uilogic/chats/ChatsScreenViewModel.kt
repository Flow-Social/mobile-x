package me.floow.chats.uilogic.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.RepliesRealtimeState
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.UserNotification
import me.floow.domain.values.ProfileName
import me.floow.domain.utils.Logger

private data class ChatsScreenVmState(
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val chats: List<Chat>? = null
) {
	fun toUiState(): ChatsScreenUiState {
		if (isLoading) return ChatsScreenUiState.Loading

		return if (chats != null && !isError) {
			if (chats.isEmpty()) {
				ChatsScreenUiState.NoChats
			} else {
				ChatsScreenUiState.HasData(chats)
			}
		} else {
			ChatsScreenUiState.Error
		}
	}
}

private val NAME_CLEANER = Regex("[<>&\"']")

class ChatsScreenViewModel(
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository,
	private val chatsRepository: ChatsRepository,
	private val presenceRepository: PresenceRepository,
	private val authenticationManager: me.floow.domain.auth.AuthenticationManager,
	private val logger: Logger,
) : ViewModel() {
	private companion object {
		private const val REMOTE_REVALIDATE_MIN_INTERVAL_MS = 15_000L
		private const val PRESENCE_OWNER_CHAT_LIST = "chat_list"
	}

	private val _state = MutableStateFlow(ChatsScreenVmState())
	private var useMockData: Boolean = false
	private var directChatsBase: List<Chat> = emptyList()
	private var directChats: List<Chat> = emptyList()
	private var directChatIndexByUserId: Map<String, Int> = emptyMap()
	private var latestRepliesState: RepliesRealtimeState = RepliesRealtimeState()
	private var directChatsLoading: Boolean = false
	private var directChatsLoadFailed: Boolean = false
	private var loadJob: Job? = null
	private var lastSuccessfulRemoteSyncAtMs: Long = 0L
	private var isSavedMessagesEnsureInFlight: Boolean = false
	private var savedMessagesEnsured: Boolean = false
	private var presenceByUserId: Map<String, me.floow.domain.models.UserPresence> = emptyMap()

	val state: StateFlow<ChatsScreenUiState> = _state
		.map(ChatsScreenVmState::toUiState)
		.stateIn(viewModelScope, SharingStarted.Eagerly, ChatsScreenUiState.Loading)

	init {
		viewModelScope.launch {
			notificationsRealtimeRepository.repliesState.collectLatest { repliesState ->
				latestRepliesState = repliesState
				publishState()
			}
		}
		viewModelScope.launch {
			presenceRepository.presences.collectLatest { presences ->
				presenceByUserId = presences
				if (directChatsBase.isNotEmpty()) {
					directChats = applyPresenceToChats(directChatsBase)
					publishState()
				}
			}
		}
		viewModelScope.launch {
			chatsRepository.observeConversations().collectLatest { conversations ->
				if (useMockData) return@collectLatest
				val selfUserId = authenticationManager.getSelfUserIdOrNull()
				directChatsBase = conversations
					.map { conversation -> conversation.toChatListItem(logger, selfUserId) }
					.sortedWith(
						compareByDescending<Chat> { it.isSavedMessages() }
							.thenByDescending { it.lastMessageTimeMillis }
					)
				directChatIndexByUserId = directChatsBase
					.mapIndexedNotNull { index, chat ->
						chat.id.takeIf { chat.type == ChatType.DIRECT }?.let { userId -> userId to index }
					}
					.toMap()
					presenceRepository.setTargets(
						owner = PRESENCE_OWNER_CHAT_LIST,
						userIds = conversations
							.map { it.peer.id }
							.filterNot { it == selfUserId }
							.distinct()
					)
				directChats = applyPresenceToChats(directChatsBase)
				publishState()
			}
		}
	}

	fun setUseMockData(flag: Boolean) {
		useMockData = flag
	}

	fun load() {
		notificationsRealtimeRepository.start()
		if (useMockData) {
			directChats = generateRandomChats(50)
			directChatsLoading = false
			directChatsLoadFailed = false
			publishState()
			return
		}
		if (loadJob?.isActive == true) return

		ensureSavedMessagesConversationExistsAsync()

		val nowMs = System.currentTimeMillis()
		val hasDirectChatsData = directChats.isNotEmpty()
		val shouldSkipRemoteRevalidate = hasDirectChatsData &&
			nowMs - lastSuccessfulRemoteSyncAtMs < REMOTE_REVALIDATE_MIN_INTERVAL_MS
		if (shouldSkipRemoteRevalidate) {
			directChatsLoading = false
			publishState()
			return
		}

		loadJob = viewModelScope.launch {
			directChatsLoading = !hasDirectChatsData
			directChatsLoadFailed = false
			publishState()

			val selfUserId = authenticationManager.getSelfUserIdOrNull()
			when (val response = chatsRepository.getConversations(limit = 50, cursor = null)) {
				is GetDataResponse.Success -> {
					directChatsBase = response.data.items
					.map { conversation -> conversation.toChatListItem(logger, selfUserId) }
					.sortedWith(
						compareByDescending<Chat> { it.isSavedMessages() }
							.thenByDescending { it.lastMessageTimeMillis }
					)
				directChatIndexByUserId = directChatsBase
					.mapIndexedNotNull { index, chat ->
						chat.id.takeIf { chat.type == ChatType.DIRECT }?.let { userId -> userId to index }
					}
					.toMap()
				presenceRepository.setTargets(
					owner = PRESENCE_OWNER_CHAT_LIST,
					userIds = response.data.items
						.map { it.peer.id }
						.filterNot { it == selfUserId }
						.distinct()
				)
				directChats = applyPresenceToChats(directChatsBase)
				directChatsLoadFailed = false
				lastSuccessfulRemoteSyncAtMs = System.currentTimeMillis()
				}

				is GetDataResponse.Error -> {
					directChatsLoadFailed = true
				}
			}
			directChatsLoading = false
			publishState()
		}.also { job ->
			job.invokeOnCompletion {
				loadJob = null
			}
		}
	}

	private fun ensureSavedMessagesConversationExistsAsync() {
		if (useMockData) return
		if (savedMessagesEnsured || isSavedMessagesEnsureInFlight) return
		isSavedMessagesEnsureInFlight = true
		viewModelScope.launch {
			try {
				when (chatsRepository.getOrCreateSavedMessagesConversation()) {
					is GetDataResponse.Success -> {
						savedMessagesEnsured = true
					}

					is GetDataResponse.Error -> {
						// no-op: we'll retry on next load call
					}
				}
			} finally {
				isSavedMessagesEnsureInFlight = false
			}
		}
	}

	private fun hasAnyRenderableData(repliesState: RepliesRealtimeState): Boolean {
		return directChats.isNotEmpty() || repliesState.page.items.isNotEmpty()
	}

	private fun shouldShowBlockingLoading(repliesState: RepliesRealtimeState): Boolean {
		val repliesLoadingWithoutData = repliesState.isBootstrapping && repliesState.page.items.isEmpty()
		return !hasAnyRenderableData(repliesState) && (repliesLoadingWithoutData || directChatsLoading)
	}

	private fun shouldShowBlockingError(repliesState: RepliesRealtimeState): Boolean {
		val repliesErrorWithoutData = repliesState.hasError && repliesState.page.items.isEmpty()
		val chatsErrorWithoutData = directChatsLoadFailed && directChats.isEmpty()
		return !hasAnyRenderableData(repliesState) && (repliesErrorWithoutData || chatsErrorWithoutData)
	}

	private fun applyPresenceToChats(chats: List<Chat>): List<Chat> {
		if (chats.isEmpty() || presenceByUserId.isEmpty()) return chats
		var updatedChats: MutableList<Chat>? = null
		directChatIndexByUserId.forEach { (userId, index) ->
			val currentChat = chats.getOrNull(index) ?: return@forEach
			val nextIsOnline = presenceByUserId[userId]?.isOnline ?: false
			if (currentChat.isOnline == nextIsOnline) return@forEach
			val target = updatedChats ?: chats.toMutableList().also { updatedChats = it }
			target[index] = currentChat.copy(isOnline = nextIsOnline)
		}
		return updatedChats ?: chats
	}

	private fun publishState() {
		val repliesState = latestRepliesState
		val repliesInboxChat = buildRepliesInboxChatFromRealtime(repliesState)

		_state.update {
			it.copy(
				isLoading = shouldShowBlockingLoading(repliesState),
				isError = shouldShowBlockingError(repliesState),
				chats = buildList {
					add(repliesInboxChat)
					addAll(directChats)
				}
			)
		}
	}

	private fun buildRepliesInboxChatFromRealtime(repliesState: RepliesRealtimeState): Chat {
		val replyNotifications = repliesState.page.items
			.filter(::isReplyNotification)
		val latestReplyNotification = replyNotifications.maxByOrNull(UserNotification::createdAt)
		val unreadRepliesCount = repliesState.page.unreadCount
		val latestReplyCommentText = latestReplyNotification
			?.commentText
			?.trim()
			?.takeIf(String::isNotEmpty)

		val previewText = latestReplyCommentText
			?: latestReplyNotification
				?.messagePreviewTextOrNull()
			?: "Ответы и комментарии к вашим постам"
		val previewTimeMillis = latestReplyNotification?.createdAt
			?.coerceAtLeast(0L)
			?: System.currentTimeMillis()

		return buildRepliesInboxChat(
			lastMessageText = previewText,
			lastMessageTimeMillis = previewTimeMillis,
			unreadCount = unreadRepliesCount
		)
	}

	private fun isReplyNotification(notification: UserNotification): Boolean {
		return notification.type == "comment_on_post" ||
			notification.type == "reply_to_comment" ||
			notification.type == "comment_reply"
	}

	private fun UserNotification.messagePreviewTextOrNull(): String? {
		return body.trim().takeIf(String::isNotEmpty)
			?: title.trim().takeIf(String::isNotEmpty)
	}

	override fun onCleared() {
		presenceRepository.clearTargets(PRESENCE_OWNER_CHAT_LIST)
		super.onCleared()
	}

}

private fun DirectChatConversation.isSavedMessagesConversation(selfUserId: String?): Boolean {
	val kindLower = kind.trim().lowercase()
	if (kindLower == "saved_messages" || kindLower == "saved") return true
	return selfUserId != null && peer.id == selfUserId
}

private fun DirectChatConversation.toChatListItem(logger: Logger, selfUserId: String?): Chat {
	if (isSavedMessagesConversation(selfUserId)) {
		val lastMessageText = lastMessage?.text?.trim()?.takeIf(String::isNotEmpty) ?: "Нет сообщений"
		val lastMessageTimeMillis = (lastMessage?.createdAt ?: updatedAt).coerceAtLeast(0L)
		return buildSavedMessagesChat(
			peerId = peer.id,
			conversationId = id,
			lastMessageText = lastMessageText,
			lastMessageTimeMillis = lastMessageTimeMillis,
		)
	}

	val peerName = peer.name?.trim().takeIf { !it.isNullOrEmpty() }
		?: peer.username?.trim()?.takeIf(String::isNotEmpty)
		?: "User ${peer.id}"
	val normalizedName = peerName
		.replace(NAME_CLEANER, "")
		.trim()
		.ifBlank { "User" }
		.take(32)
	val profileName = runCatching { ProfileName.create(normalizedName) }
		.getOrElse { ProfileName.create("User") }
	val lastMessageItem = lastMessage
	val lastMessageText = lastMessageItem?.text?.trim().takeIf { !it.isNullOrEmpty() } ?: "Диалог создан"
	val lastMessageTimeMillis = (lastMessageItem?.createdAt ?: updatedAt)
		.coerceAtLeast(0L)
	val isLastMessageOutgoing = lastMessageItem?.sender?.id?.let { senderId ->
		senderId != peer.id
	} == true
	val normalizedPeerLastReadMessageId = peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L
	val lastSentState = when {
		lastMessageItem == null || !isLastMessageOutgoing -> null
		normalizedPeerLastReadMessageId >= lastMessageItem.id -> LastSentMessageState.Read
		else -> LastSentMessageState.Sent
	}

	return Chat(
		id = peer.id,
		conversationId = id,
		type = resolveChatType(kind, logger),
		name = profileName,
		lastMessageText = lastMessageText,
		lastMessageTimeMillis = lastMessageTimeMillis,
		isOnline = false,
		unreadCount = unreadCount.coerceAtLeast(0),
		chatMuted = false,
		avatarUrl = peer.avatarUrl?.trim()?.takeIf(String::isNotEmpty),
		attachedMediaUrl = null,
		lastSentMessageState = lastSentState
	)
}

private fun resolveChatType(kind: String, logger: Logger): ChatType {
	return when (kind.trim().lowercase()) {
		"system", "replies", "replies_inbox" -> ChatType.SYSTEM
		"group" -> ChatType.GROUP
		"saved_messages", "saved" -> ChatType.SAVED
		"direct", "" -> ChatType.DIRECT
		else -> {
			logger.d("ChatsScreenViewModel.resolveChatType", "Unknown chat kind=$kind")
			ChatType.DIRECT
		}
	}
}
