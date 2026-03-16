package me.floow.chats.uilogic.chat

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import me.floow.chats.uilogic.shared.UnifiedOpenMode
import me.floow.chats.uilogic.shared.mergePendingReadCursor
import me.floow.chats.uilogic.shared.resolveTypingDecision
import me.floow.chats.uilogic.shared.resolvePeerReadUpdate
import me.floow.chats.uilogic.shared.resolveAnchorMessageIdByCursor
import me.floow.chats.uilogic.shared.resolveOpenAnchorCursor
import me.floow.chats.uilogic.shared.shouldApplyVisibleReadCandidate
import me.floow.chats.uilogic.shared.shouldEnqueueReadCursor
import me.floow.chats.uilogic.shared.toSafeUriOrNull
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.DirectChatViewportSnapshot
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.UserPresence
import me.floow.domain.realtime.DirectChatRealtimeDecision
import me.floow.domain.realtime.DirectChatTimelineMutation
import me.floow.domain.realtime.DirectChatUpsertKind
import me.floow.domain.realtime.reduceDirectChatRealtimeEvent
import me.floow.domain.realtime.RealtimeViewCursorState
import me.floow.domain.realtime.TimelineRealtimeEventInput
import me.floow.domain.realtime.TimelineRealtimeEventKind
import me.floow.domain.realtime.reduceTimelineRealtimeEvent
import me.floow.domain.utils.toLocalDateTimeFromEpochMillis
import me.floow.domain.utils.Logger
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatAnchorRequest
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatHighlightRequest
import me.floow.uikit.chat.model.ChatInitialViewport
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.ChatScrollRequest
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.DEFAULT_CHAT_MESSAGE_MAX_LENGTH
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.MessageFieldReply
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.isWithinCodePointLimit
import me.floow.uikit.chat.model.resolveDefaultContextMenuActions
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class ChatScreenVmState(
	val messageFieldValue: String = "",
	val chatInterlocutorId: String = "",
	val chatInterlocutorName: String = "",
	val chatInterlocutorAvatarUrl: Uri? = null,
	val conversationId: Long? = null,
	val timelineSessionToken: Long = 0L,
	val initialViewport: ChatInitialViewport? = null,
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val isLoadingMore: Boolean = false,
	val messages: List<DatedChatMessages>? = null,
	val nextBeforeId: Long? = null,
	val messageFieldReply: MessageFieldReply? = null,
	val typingUserNames: List<String> = emptyList(),
	val pinnedMessages: List<ChatMessage> = emptyList(),
	val lastDeletedMessage: ChatMessage? = null,
	val messageToEditId: Long? = null,
	val priorEditDraftText: String = "",
	val priorEditReply: MessageFieldReply? = null,
	val scrollToBottomRequestToken: Long = 0L,
	val anchorRequest: ChatAnchorRequest? = null,
	val highlightRequest: ChatHighlightRequest? = null,
	val unreadMessageIds: Set<Long> = emptySet(),
	val unreadBoundaryMessageId: Long? = null,
	val peerLastReadMessageId: Long = 0L,
	val canLoadMore: Boolean = false,
	val selectedMessageIds: Set<Long> = emptySet(),
	val peerIsOnline: Boolean = false,
	val peerLastSeenAtMillis: Long? = null
) {
	fun toUiState(): ChatScreenUiState {
		val flattenedMessages = flattenMessages(messages)
		val existingMessageIds = flattenedMessages.mapTo(linkedSetOf(), ChatMessage::id)
		val effectiveSelectedMessageIds = selectedMessageIds.filterTo(linkedSetOf()) { it in existingMessageIds }
		val selectedMessages = flattenedMessages.filter { it.id in effectiveSelectedMessageIds }
		val canCopySelection = selectedMessages.isNotEmpty() &&
			selectedMessages.none { it.messageText.isBlank() || it is PostPreviewMessage }
		val canDeleteSelection = selectedMessages.isNotEmpty() &&
			selectedMessages.all { it is PrimaryOutMessage || it is ReplyOutMessage }
		val selectionCopyText = if (canCopySelection) {
			selectedMessages.joinToString(separator = "\n\n") { it.messageText.trim() }
		} else {
			""
		}
		val selectionState = ChatSelectionState(
			selectedMessageIds = effectiveSelectedMessageIds,
			selectedCount = effectiveSelectedMessageIds.size,
			canCopy = canCopySelection,
			canDelete = canDeleteSelection,
			copyText = selectionCopyText
		)
		return when {
			isLoading -> {
				ChatScreenUiState.Loading(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messageFieldReply = messageFieldReply,
					peerIsOnline = peerIsOnline,
					peerLastSeenAtMillis = peerLastSeenAtMillis,
				)
			}

			isError || messages == null -> {
				ChatScreenUiState.Error(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messageFieldReply = messageFieldReply,
					peerIsOnline = peerIsOnline,
					peerLastSeenAtMillis = peerLastSeenAtMillis,
				)
			}

			messages.isEmpty() -> {
				ChatScreenUiState.NoMessages(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messageFieldReply = messageFieldReply,
					peerIsOnline = peerIsOnline,
					peerLastSeenAtMillis = peerLastSeenAtMillis,
				)
			}

			else -> {
				ChatScreenUiState.HasData(
					chatInterlocutorId = chatInterlocutorId,
					chatInterlocutorName = chatInterlocutorName,
					chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messages = messages,
					messageFieldReply = messageFieldReply,
					peerIsOnline = peerIsOnline,
					peerLastSeenAtMillis = peerLastSeenAtMillis,
					timelineSessionToken = timelineSessionToken,
					initialViewport = initialViewport,
					anchorRequest = anchorRequest,
					highlightRequest = highlightRequest,
					scrollRequest = scrollToBottomRequestToken
						.takeIf { token -> token > 0L }
						?.let(::ChatScrollRequest),
					typingUserNames = typingUserNames,
					pinnedMessages = pinnedMessages,
					messageToEditId = messageToEditId,
					scrollToBottomRequestToken = scrollToBottomRequestToken,
					scrollToBottomBadgeCount = unreadMessageIds.size,
					peerLastReadMessageId = peerLastReadMessageId,
					unreadBoundaryMessageId = unreadBoundaryMessageId,
					canLoadMore = canLoadMore,
					isLoadingMore = isLoadingMore,
					selectionState = selectionState
				)
			}
		}
	}
}

private val ChatScreenVmState.currentHighlightMessageId: Long?
	get() = highlightRequest?.messageId

private val ChatScreenVmState.currentAnchorRequestToken: Long
	get() = anchorRequest?.requestToken ?: 0L

private val ChatScreenVmState.currentHighlightRequestToken: Long
	get() = highlightRequest?.requestToken ?: 0L

internal data class DirectChatReadModelInput(
	val peerUserId: String,
	val messages: List<ChatMessage>,
	val serverReadUpToMessageId: Long,
	val localReadUpToMessageId: Long,
	val firstUnreadMessageId: Long?,
	val openAnchorMessageId: Long?,
	val openMode: DirectChatOpenMode,
	val messageLinkAnchorMessageId: Long?
)

internal data class DirectChatReadModelProjection(
	val unreadMessageIds: Set<Long>,
	val unreadBoundaryMessageId: Long?,
	val openAnchorMessageId: Long?,
	val readUpToMessageId: Long
)

internal data class DirectChatRenderedMessagesSnapshot(
	val messages: List<ChatMessage>,
	val groupedMessages: List<DatedChatMessages>,
	val projection: DirectChatReadModelProjection
)

internal data class DirectChatObservedPageProjection(
	val renderedSnapshot: DirectChatRenderedMessagesSnapshot,
	val nextBeforeId: Long?,
	val canLoadMore: Boolean,
	val peerLastReadMessageId: Long,
	val preservePaginationCursor: Boolean
)



private data class InitialMessagesLoadResult(
	val latestMessageId: Long,
	val loadedCount: Int,
	val anchorFound: Boolean
)

class ChatScreenViewModel(
	private val chatsRepository: ChatsRepository,
	private val directMessagesReadCursorStore: DirectMessagesReadCursorStore,
	private val authenticationManager: AuthenticationManager,
	private val logger: Logger,
	private val presenceRepository: PresenceRepository
) : ViewModel() {
	fun resolveContextMenuActions(message: ChatMessage): List<ChatContextMenuAction> {
		val isOwnMessage = message is PrimaryOutMessage || message is ReplyOutMessage
		if (!isOwnMessage) return message.resolveDefaultContextMenuActions(allowEditAndDelete = false)
		return when (message.deliveryStatus) {
			me.floow.domain.models.MessageDeliveryStatus.SENDING -> listOf(ChatContextMenuAction.Delete)
			me.floow.domain.models.MessageDeliveryStatus.FAILED -> listOf(
				ChatContextMenuAction.Delete,
				ChatContextMenuAction.Retry
			)
			me.floow.domain.models.MessageDeliveryStatus.SENT -> message.resolveDefaultContextMenuActions()
		}
	}

	private var useMockData: Boolean = false
	private val _state: MutableStateFlow<ChatScreenVmState> = MutableStateFlow(ChatScreenVmState())

	private val readController = ChatReadController(
		scope = viewModelScope,
		state = _state,
		readCursorStore = directMessagesReadCursorStore,
		onFlushRead = { conversationId, messageId -> syncReadUpToNow(messageId, conversationId) },
		onApplyReadLocally = { messageId -> applyReadUpToLocally(messageId) },
	)

	private val anchorController = ChatScrollAnchorController(
		scope = viewModelScope,
		readCursorStore = directMessagesReadCursorStore,
	)

	private val outgoingController = ChatOutgoingController(
		scope = viewModelScope,
		chatsRepository = chatsRepository,
		onRestoreDeleted = { message ->
			val updated = mergeMessages(flattenMessages(_state.value.messages), listOf(message))
			_state.update { it.copy(messages = groupMessagesByDate(updated), lastDeletedMessage = null) }
		},
		onClearDeletedMessage = {
			_state.update { it.copy(lastDeletedMessage = null) }
		},
	)

	private val typingController = ChatTypingController(
		scope = viewModelScope,
		chatsRepository = chatsRepository,
		onIncomingTypingChanged = { isTyping, displayName ->
			_state.update { state ->
				state.copy(typingUserNames = if (isTyping) listOf(displayName) else emptyList())
			}
		},
	)

	init {
		viewModelScope.launch {
			presenceRepository.presences.collectLatest { presences ->
				applyPeerPresence(presences[_state.value.chatInterlocutorId])
			}
		}
	}

	// Convenience accessors delegating to controllers
	private var confirmedReadUpToMessageId: Long
		get() = readController.confirmedReadUpToMessageId
		set(value) = readController.applyConfirmedRead(value)
	private var localReadUpToMessageId: Long
		get() = readController.localReadUpToMessageId
		set(value) = readController.applyLocalRead(value)
	private var lastAppliedReadUpToMessageId: Long
		get() = readController.lastAppliedReadUpToMessageId
		set(_) {}
	private var maxVisibleUnreadMessageIdCandidate: Long
		get() = readController.maxVisibleUnreadMessageIdCandidate
		set(_) {}
	private var lastAppliedVisibleUnreadMessageId: Long
		get() = readController.lastAppliedVisibleUnreadMessageId
		set(_) {}
	private var currentOpenAnchorMessageId: Long
		get() = anchorController.currentAnchorMessageId
		set(_) {}
	private var currentOpenAnchorOffsetPx: Int
		get() = anchorController.currentAnchorOffsetPx
		set(_) {}
	private var currentOpenAnchorBottomPinned: Boolean
		get() = anchorController.currentAnchorBottomPinned
		set(_) {}
	private var lastViewportItemIndex: Int
		get() = anchorController.lastViewportItemIndex
		set(_) {}
	private var lastViewportItemScrollOffsetPx: Int
		get() = anchorController.lastViewportItemScrollOffsetPx
		set(_) {}
	private var hasLastViewportSnapshot: Boolean
		get() = anchorController.hasLastViewportSnapshot
		set(_) {}
	private val pendingOutgoingClientMessageIds get() = outgoingController.pendingOutgoingClientMessageIds
	private val pendingOutgoingSendJobs get() = outgoingController.pendingOutgoingSendJobs

	private var realtimeJob: Job? = null
	private var localMessagesJob: Job? = null
	private var pinnedMessagesJob: Job? = null
	private var pendingColdRestoreAnchorMessageId: Long? = null
	private var pendingColdRestoreOffsetPx: Int = 0
	private var bufferedObservedMessagesPage: DirectChatMessagesPage? = null
	private var pendingColdRestoreNeedsRefresh: Boolean = false
	private var coldRestoreUnlockJob: Job? = null
	private var openMode: DirectChatOpenMode = DirectChatOpenMode.FROM_LAST_SEEN
	private var isScreenActive: Boolean = false
	private var isScreenVisibleToUser: Boolean = false
	private var messageLinkAnchorId: Long? = null
	private var optimisticMessageIdSeed: Long = -1L
	private var highlightCleanupJob: Job? = null
	private var chatOpenedAtMs: Long = 0L
	private var hasReportedFirstFrameMetric: Boolean = false
	private var hasReportedAnchorMetric: Boolean = false
	private var lastHandledRealtimeResyncCursor: Long = 0L
	private var observedMessagesLimit: Int = PAGE_SIZE
	private var timelineSessionTokenSeed: Long = 0L
	private var isLoadMoreInFlight: Boolean = false
	private var activeObservedConversationId: Long? = null
	private var currentPresenceOwner: String? = null
	private val selfUserId: String?
		get() = authenticationManager.getSelfUserIdOrNull()
			?.trim()
			?.takeIf(String::isNotEmpty)

	private fun nextTimelineSessionToken(): Long {
		timelineSessionTokenSeed += 1L
		return timelineSessionTokenSeed
	}

	private fun createAnchorRestoreRequest(
		messageId: Long?,
		requestToken: Long,
		initialOffsetPx: Int = 0,
		keepAnchored: Boolean = true
	): ChatAnchorRequest? = messageId
		?.takeIf { it > 0L }
		?.let { anchorId ->
			ChatAnchorRequest(
				messageId = anchorId,
				requestToken = requestToken,
				initialOffsetPx = initialOffsetPx.coerceAtLeast(0),
				keepAnchored = keepAnchored
			)
		}

	private fun createHighlightRequest(
		messageId: Long?,
		requestToken: Long,
		keepAnchored: Boolean
	): ChatHighlightRequest? = messageId
		?.takeIf { it > 0L }
		?.let { highlightId ->
			ChatHighlightRequest(
				messageId = highlightId,
				requestToken = requestToken,
				keepAnchored = keepAnchored
			)
		}

	private fun resolveDurableOpenAnchor(explicitMessageId: Long? = null): PersistedAnchor? {
		return anchorController.resolveDurableAnchor(explicitMessageId)
	}

	private fun issueTimelineRestoreRequest(
		targetMessageId: Long?,
		initialOffsetPx: Int = 0,
		highlightAnchored: Boolean,
		resetSession: Boolean = false,
	) {
		val normalizedTarget = targetMessageId?.takeIf { it > 0L }
		logAnchor(
			"issue_restore target=$normalizedTarget offset=${initialOffsetPx.coerceAtLeast(0)} " +
				"highlight=$highlightAnchored openMode=$openMode messageLink=$messageLinkAnchorId " +
				"stored=$currentOpenAnchorMessageId storedOffset=$currentOpenAnchorOffsetPx resetSession=$resetSession"
		)
		val requestToken = System.currentTimeMillis()
		_state.update { state ->
			state.copy(
				timelineSessionToken = if (resetSession) nextTimelineSessionToken() else state.timelineSessionToken,
				initialViewport = if (resetSession) null else state.initialViewport,
				scrollToBottomRequestToken = 0L,
				anchorRequest = createAnchorRestoreRequest(
					messageId = normalizedTarget,
					requestToken = requestToken,
					initialOffsetPx = initialOffsetPx
				),
				highlightRequest = createHighlightRequest(
					messageId = normalizedTarget,
					requestToken = requestToken,
					keepAnchored = highlightAnchored && normalizedTarget != null
				)
			)
		}
	}

	private fun clearPendingAnchorRestore() {
		_state.update { state ->
			state.copy(
				anchorRequest = null,
				highlightRequest = state.highlightRequest?.takeUnless { it.keepAnchored }
			)
		}
	}

	private fun canProcessVisibleReadSignals(): Boolean = isScreenActive && isScreenVisibleToUser

	val state: StateFlow<ChatScreenUiState> = _state
		.map(ChatScreenVmState::toUiState)
		.stateIn(
			viewModelScope,
			SharingStarted.Eagerly,
			ChatScreenUiState.Loading(
				chatInterlocutorId = "",
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = "",
				messageFieldReply = null,
				peerIsOnline = false,
				peerLastSeenAtMillis = null
			)
		)

	fun setUseMockData(flag: Boolean) {
		useMockData = flag
	}

	fun setInitialData(
		chatInterlocutorId: String,
		chatInterlocutorName: String,
		chatInterlocutorAvatarUrl: Uri?,
		conversationId: Long? = null,
		messageAnchorId: Long? = null,
		openMode: DirectChatOpenMode = DirectChatOpenMode.FROM_LAST_SEEN
	): Boolean {
		val targetConversationId = conversationId?.takeIf { it > 0L }
		val targetPeerId = chatInterlocutorId.trim()
		val targetAnchorId = messageAnchorId?.takeIf { it > 0L }
		val current = _state.value
		this.openMode = openMode
		applyPeerPresence(presenceRepository.presences.value[targetPeerId])
		val isSameConversation = targetConversationId != null &&
			current.conversationId == targetConversationId &&
			current.chatInterlocutorId == targetPeerId
		if (isSameConversation) {
			messageLinkAnchorId = targetAnchorId
			val shouldReload = current.messages == null || current.isError || !isScreenActive
			if (!isScreenActive) {
				_state.update { state ->
					state.copy(
						isLoading = true,
						isError = false,
						messages = null,
						nextBeforeId = null,
						canLoadMore = false,
						initialViewport = null,
						anchorRequest = null,
						highlightRequest = null
					)
				}
				maxVisibleUnreadMessageIdCandidate = 0L
				lastAppliedVisibleUnreadMessageId = 0L
				val durableOpenAnchor = resolveDurableOpenAnchor(targetAnchorId)
				logAnchor(
					"reuse_same_conversation_restore target=${durableOpenAnchor?.messageId} " +
						"offset=${durableOpenAnchor?.offsetPx ?: 0} explicit=$targetAnchorId"
				)
			issueTimelineRestoreRequest(
				targetMessageId = durableOpenAnchor?.messageId,
				initialOffsetPx = durableOpenAnchor?.offsetPx ?: 0,
				highlightAnchored = targetAnchorId != null || openMode == DirectChatOpenMode.FROM_MESSAGE_LINK,
				resetSession = true,
			)
		}
		if (targetAnchorId != null) {
			issueTimelineRestoreRequest(
				targetMessageId = targetAnchorId,
				initialOffsetPx = 0,
				highlightAnchored = true,
				resetSession = true,
			)
			}
			isScreenActive = true
			isScreenVisibleToUser = true
			updateFocusPresenceTargets(isVisible = true)
			return shouldReload
		}

		val previousConversationId = _state.value.conversationId
		clearPresenceBinding()
		if (!useMockData && previousConversationId != null && typingController.isOutgoingTypingActive()) {
			stopOutgoingTyping(previousConversationId)
		}
		realtimeJob?.cancel()
		localMessagesJob?.cancel()
		pinnedMessagesJob?.cancel()
		coldRestoreUnlockJob?.cancel()
		highlightCleanupJob?.cancel()
		activeObservedConversationId = null
		pendingColdRestoreAnchorMessageId = null
		pendingColdRestoreOffsetPx = 0
		bufferedObservedMessagesPage = null
		pendingColdRestoreNeedsRefresh = false
		lastHandledRealtimeResyncCursor = 0L
		typingController.reset()
		outgoingController.reset()
		readController.reset()
		anchorController.reset()
		messageLinkAnchorId = messageAnchorId?.takeIf { it > 0L }
		observedMessagesLimit = PAGE_SIZE
		lastViewportItemIndex = 0
		lastViewportItemScrollOffsetPx = 0
		hasLastViewportSnapshot = false
		chatOpenedAtMs = System.currentTimeMillis()
		hasReportedFirstFrameMetric = false
		hasReportedAnchorMetric = false
		isScreenActive = true
		isScreenVisibleToUser = true
		_state.update {
			ChatScreenVmState(
				chatInterlocutorId = chatInterlocutorId,
				chatInterlocutorName = chatInterlocutorName,
				chatInterlocutorAvatarUrl = chatInterlocutorAvatarUrl,
				conversationId = conversationId?.takeIf { it > 0L },
				timelineSessionToken = nextTimelineSessionToken(),
				initialViewport = null,
				anchorRequest = createAnchorRestoreRequest(
					messageId = messageLinkAnchorId,
					requestToken = if (messageLinkAnchorId != null) 1L else 0L,
					initialOffsetPx = 0
				),
				highlightRequest = createHighlightRequest(messageLinkAnchorId, if (messageLinkAnchorId != null) 1L else 0L, keepAnchored = messageLinkAnchorId != null)
			)
		}
		updateFocusPresenceTargets(isVisible = true)
		applyPeerPresence(presenceRepository.presences.value[targetPeerId])
		return true
	}

	private fun resumeExistingConversationSession() {
		if (useMockData) return
		val conversation = currentConversationSnapshot(
			state = _state.value,
			confirmedReadUpToMessageId = confirmedReadUpToMessageId
		) ?: return
		logAnchor(
			"resume_existing_session conversation=${conversation.id} latestCached=${latestPersistableMessageId(_state.value.messages) ?: 0L}"
		)
		observeCachedMessages(conversation)
		subscribeRealtime(
			conversationId = conversation.id,
			afterSeq = latestPersistableMessageId(_state.value.messages) ?: 0L
		)
		syncChatMetadata(conversation.id)
		refreshPinnedMessages(conversation.id, conversation.peer.id)
	}

	fun loadData() {
		if (useMockData) {
			loadMockData()
			return
		}

		viewModelScope.launch {
			_state.update { current ->
				current.copy(
					isLoading = current.messages == null,
					isError = false,
					isLoadingMore = false,
					messages = current.messages,
					nextBeforeId = current.nextBeforeId,
					canLoadMore = current.canLoadMore,
					unreadMessageIds = current.unreadMessageIds,
					unreadBoundaryMessageId = current.unreadBoundaryMessageId,
					peerLastReadMessageId = current.peerLastReadMessageId
				)
			}

			val conversation = resolveConversationOrNull()
			if (conversation == null) {
				_state.update { current ->
					current.copy(
						isLoading = false,
						isError = true,
						messages = emptyList(),
						canLoadMore = false
					)
				}
				return@launch
			}

			localReadUpToMessageId = directMessagesReadCursorStore
				.getLocalLastReadMessageId(conversation.id)
				.coerceAtLeast(0L)
			val storedViewportSnapshot = directMessagesReadCursorStore
				.getOpenViewportSnapshot(conversation.id)
			currentOpenAnchorMessageId = storedViewportSnapshot.anchorMessageId
				?.coerceAtLeast(0L)
				?: 0L
			currentOpenAnchorOffsetPx = storedViewportSnapshot.anchorOffsetPx.coerceAtLeast(0)
			currentOpenAnchorBottomPinned = storedViewportSnapshot.isBottomPinned
			logAnchor(
				"load_data conversation=${conversation.id} localRead=$localReadUpToMessageId " +
					"storedAnchor=$currentOpenAnchorMessageId storedOffset=$currentOpenAnchorOffsetPx " +
					"storedBottomPinned=$currentOpenAnchorBottomPinned " +
					"messageLink=$messageLinkAnchorId openMode=$openMode"
			)
			val durableOpenAnchor = resolveDurableOpenAnchor(messageLinkAnchorId)
				?.takeUnless {
					it.isBottomPinned &&
						messageLinkAnchorId == null &&
						openMode == DirectChatOpenMode.FROM_LAST_SEEN
				}
			val localAnchorWindow = durableOpenAnchor
				?.takeIf { shouldWarmupAnchorBeforeShowing() }
				?.let { anchor ->
					getLocalAnchoredMessagesWindow(
						conversationId = conversation.id,
						targetAnchor = anchor
					)
				}
			if (durableOpenAnchor != null && localAnchorWindow != null) {
				val appliedLocalStart = applyAnchoredLocalStart(
					conversation = conversation,
					window = localAnchorWindow,
					offsetPx = durableOpenAnchor.offsetPx
				)
				if (appliedLocalStart) {
					observeCachedMessages(conversation)
					subscribeRealtime(
						conversationId = conversation.id,
						afterSeq = localAnchorWindow.latestCachedMessageId.coerceAtLeast(0L)
					)
					refreshPinnedMessages(conversation.id, conversation.peer.id)
					return@launch
				}
			}
			val warmupAnchor = durableOpenAnchor?.takeIf { shouldWarmupAnchorBeforeShowing() }
			val messagesResponse = preloadMessagesForOpen(
				conversationId = conversation.id,
				targetAnchor = warmupAnchor
			)
			val stateAfterPreload = _state.value
			// Prevent duplicate restore scheduling:
			// the first restore may already settle while preload is in flight.
			if (stateAfterPreload.anchorRequest == null && stateAfterPreload.messages == null) {
				issueTimelineRestoreRequest(
					targetMessageId = durableOpenAnchor?.messageId,
					initialOffsetPx = durableOpenAnchor?.offsetPx ?: 0,
					highlightAnchored = messageLinkAnchorId != null && openMode == DirectChatOpenMode.FROM_MESSAGE_LINK,
					resetSession = true,
				)
			}
			observeCachedMessages(conversation)

			when (messagesResponse) {
				is GetDataResponse.Success -> {
					subscribeRealtime(conversation.id, messagesResponse.data.latestMessageId)
					refreshPinnedMessages(conversation.id, conversation.peer.id)
				}

				is GetDataResponse.Error -> {
					_state.update { current ->
						current.copy(
							isLoading = false,
							isError = true,
							messages = emptyList(),
							canLoadMore = false
						)
					}
				}
			}
		}
	}

	private fun observeCachedMessages(conversation: DirectChatConversation) {
		val limit = observedMessagesLimit.coerceAtLeast(PAGE_SIZE)
		val alreadyObservingSameConversation = activeObservedConversationId == conversation.id &&
			localMessagesJob?.isActive == true
		if (alreadyObservingSameConversation) {
			return
		}
		localMessagesJob?.cancel()
		pinnedMessagesJob?.cancel()
		activeObservedConversationId = conversation.id
		localMessagesJob = viewModelScope.launch {
			chatsRepository.observeMessages(
				conversationId = conversation.id,
				limit = limit
			)
				.distinctUntilChanged()
				.collectLatest { page ->
					if (!isScreenActive) return@collectLatest
					applyObservedMessagesPage(
						conversation = conversation,
						page = page
					)
				}
		}
		pinnedMessagesJob = viewModelScope.launch {
			chatsRepository.observePinnedMessages(
				conversationId = conversation.id,
				limit = PINNED_MESSAGES_LIMIT
			)
				.distinctUntilChanged()
				.collectLatest { pinned ->
					if (!isScreenActive) return@collectLatest
					val uiMessages = pinned.map { message ->
						message.toUiMessage(
							peerUserId = conversation.peer.id,
							selfUserId = selfUserId
						)
					}
					_state.update { state ->
						if (state.conversationId != conversation.id) state
						else state.copy(pinnedMessages = uiMessages)
					}
				}
		}
	}

	private fun reobserveCachedMessagesIfLimitGrew(conversation: DirectChatConversation) {
		val newLimit = observedMessagesLimit.coerceAtLeast(PAGE_SIZE)
		val isObservingCurrentConversation = activeObservedConversationId == conversation.id &&
			localMessagesJob?.isActive == true
		if (!isObservingCurrentConversation) {
			observeCachedMessages(conversation)
			return
		}
		activeObservedConversationId = null
		observeCachedMessages(conversation)
	}

	private fun shouldWarmupAnchorBeforeShowing(): Boolean {
		return !useMockData &&
			openMode == DirectChatOpenMode.FROM_LAST_SEEN &&
			messageLinkAnchorId == null &&
			!currentOpenAnchorBottomPinned &&
			currentOpenAnchorMessageId > 0L &&
			_state.value.messages == null
	}

	private suspend fun preloadMessagesForOpen(
		conversationId: Long,
		targetAnchor: PersistedAnchor?
	): GetDataResponse<InitialMessagesLoadResult> {
		if (conversationId <= 0L) {
			return GetDataResponse.Error(error = me.floow.domain.data.GetDataError.Other)
		}
		if (targetAnchor != null) {
			return when (
				val response = chatsRepository.getMessagesAround(
					conversationId = conversationId,
					anchorId = targetAnchor.messageId,
					olderLimit = PAGE_SIZE,
					newerLimit = PAGE_SIZE
				)
			) {
				is GetDataResponse.Success -> {
					val loadedCount = response.data.items.size
					val latestMessageId = response.data.latestCachedMessageId
					val anchorFound = response.data.anchorIndex >= 0
					observedMessagesLimit = maxOf(
						observedMessagesLimit,
						loadedCount.coerceAtLeast(PAGE_SIZE)
					)
					logAnchor(
						"preload_open_around conversation=$conversationId loaded=$loadedCount " +
							"anchor=${targetAnchor.messageId} anchorFound=$anchorFound"
					)
					GetDataResponse.Success(
						InitialMessagesLoadResult(
							latestMessageId = latestMessageId,
							loadedCount = loadedCount,
							anchorFound = anchorFound
						)
					)
				}

				is GetDataResponse.Error -> GetDataResponse.Error(error = response.error)
			}
		}

		return when (val response = chatsRepository.getMessages(conversationId = conversationId, limit = PAGE_SIZE)) {
			is GetDataResponse.Success -> {
				val loadedCount = response.data.items.size
				val latestMessageId = response.data.items.maxOfOrNull(DirectChatMessage::id) ?: 0L
				observedMessagesLimit = maxOf(
					observedMessagesLimit,
					loadedCount.coerceAtLeast(PAGE_SIZE)
				)
				GetDataResponse.Success(
					InitialMessagesLoadResult(
						latestMessageId = latestMessageId,
						loadedCount = loadedCount,
						anchorFound = true
					)
				)
			}

			is GetDataResponse.Error -> GetDataResponse.Error(error = response.error)
		}
	}

	private suspend fun getLocalAnchoredMessagesWindow(
		conversationId: Long,
		targetAnchor: PersistedAnchor
	): DirectChatAnchoredMessagesWindow? {
		return when (
			val response = chatsRepository.getAnchoredMessagesWindow(
				conversationId = conversationId,
				anchorMessageId = targetAnchor.messageId,
				olderLimit = LOCAL_ANCHOR_WINDOW_OLDER_LIMIT,
				newerLimit = LOCAL_ANCHOR_WINDOW_NEWER_LIMIT
			)
		) {
			is GetDataResponse.Success -> {
				logAnchor(
					"local_anchor_window conversation=$conversationId anchor=${targetAnchor.messageId} " +
						"items=${response.data.items.size} anchorIndex=${response.data.anchorIndex} " +
						"hasOlder=${response.data.hasOlderMessages} newerCached=${response.data.newerCachedCount} " +
						"latestCached=${response.data.latestCachedMessageId}"
				)
				response.data
			}

			is GetDataResponse.Error -> {
				logAnchor(
					"local_anchor_window_miss conversation=$conversationId anchor=${targetAnchor.messageId}"
				)
				null
			}
		}
	}

	private suspend fun applyAnchoredLocalStart(
		conversation: DirectChatConversation,
		window: DirectChatAnchoredMessagesWindow,
		offsetPx: Int
	): Boolean {
		if (window.items.isEmpty()) return false
		val uiMessages = window.items.map { message ->
			message.toUiMessage(
				peerUserId = conversation.peer.id,
				selfUserId = selfUserId
			)
		}
		val renderedSnapshot = buildRenderedMessagesSnapshot(
			conversation = conversation,
			messages = uiMessages,
			serverReadUpToMessageId = maxOf(
				confirmedReadUpToMessageId,
				conversation.lastReadMessageId.coerceAtLeast(0L)
			),
			localReadUpToMessageId = localReadUpToMessageId,
			openAnchorMessageId = window.anchorMessageId,
			openMode = openMode,
			messageLinkAnchorMessageId = messageLinkAnchorId
		)
		val projection = renderedSnapshot.projection
		confirmedReadUpToMessageId = maxOf(
			confirmedReadUpToMessageId,
			conversation.lastReadMessageId.coerceAtLeast(0L)
		)
		localReadUpToMessageId = maxOf(localReadUpToMessageId, projection.readUpToMessageId)
		lastAppliedReadUpToMessageId = maxOf(lastAppliedReadUpToMessageId, projection.readUpToMessageId)
		lastAppliedVisibleUnreadMessageId = maxOf(lastAppliedVisibleUnreadMessageId, projection.readUpToMessageId)
		maxVisibleUnreadMessageIdCandidate = maxOf(maxVisibleUnreadMessageIdCandidate, projection.readUpToMessageId)
		observedMessagesLimit = maxOf(
			PAGE_SIZE,
			window.newerCachedCount + window.anchorIndex + 1
		)
		val exactInitialViewport = resolveTimelineInitialViewport(
			groupedMessages = renderedSnapshot.groupedMessages,
			targetMessageId = window.anchorMessageId,
			offsetPx = offsetPx
		)
		val roughInitialViewport = exactInitialViewport?.copy(itemScrollOffsetPx = 0)
		val nextAnchorRequestToken = _state.value.currentAnchorRequestToken + 1L
		_state.update { current ->
			current.copy(
				isLoading = false,
				isError = false,
				messages = renderedSnapshot.groupedMessages,
				pinnedMessages = current.pinnedMessages,
				nextBeforeId = window.items.firstOrNull()?.id?.takeIf { window.hasOlderMessages },
				canLoadMore = window.hasOlderMessages,
				conversationId = conversation.id,
				chatInterlocutorId = conversation.peer.id,
				chatInterlocutorName = conversation.displayPeerName(),
				chatInterlocutorAvatarUrl = conversation.peer.avatarUrl.toSafeUriOrNull(),
				initialViewport = roughInitialViewport,
				anchorRequest = createAnchorRestoreRequest(
					messageId = window.anchorMessageId,
					requestToken = nextAnchorRequestToken,
					initialOffsetPx = offsetPx,
					keepAnchored = true
				),
				highlightRequest = null,
				unreadMessageIds = projection.unreadMessageIds,
				unreadBoundaryMessageId = projection.unreadBoundaryMessageId,
				peerLastReadMessageId = maxOf(
					current.peerLastReadMessageId,
					conversation.peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L,
					window.peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L
				)
			)
		}
		logAnchor(
			"apply_local_anchor_start conversation=${conversation.id} anchor=${window.anchorMessageId} " +
				"roughViewportIndex=${roughInitialViewport?.itemIndex} roughOffset=${roughInitialViewport?.itemScrollOffsetPx} " +
				"finalOffset=$offsetPx anchorRequestToken=$nextAnchorRequestToken observedLimit=$observedMessagesLimit"
		)
		pendingColdRestoreAnchorMessageId = window.anchorMessageId
		pendingColdRestoreOffsetPx = offsetPx.coerceAtLeast(0)
		bufferedObservedMessagesPage = null
		pendingColdRestoreNeedsRefresh = false
		coldRestoreUnlockJob?.cancel()
		coldRestoreUnlockJob = null
		reportOpenMetricsIfNeeded(projection)
		return true
	}

	private suspend fun releasePendingColdRestoreLock(reason: String) {
		val anchorMessageId = pendingColdRestoreAnchorMessageId ?: return
		logAnchor(
			"cold_restore_unlock reason=$reason anchor=$anchorMessageId offset=$pendingColdRestoreOffsetPx"
		)
		pendingColdRestoreAnchorMessageId = null
		pendingColdRestoreOffsetPx = 0
		coldRestoreUnlockJob?.cancel()
		coldRestoreUnlockJob = null
		val shouldRefresh = pendingColdRestoreNeedsRefresh
		pendingColdRestoreNeedsRefresh = false
		val conversation = currentConversationSnapshot(
			state = _state.value,
			confirmedReadUpToMessageId = confirmedReadUpToMessageId
		) ?: return
		val bufferedPage = bufferedObservedMessagesPage
		bufferedObservedMessagesPage = null
		if (bufferedPage != null) {
			applyObservedMessagesPage(
				conversation = conversation,
				page = bufferedPage
			)
		}
		if (shouldRefresh) {
			refreshConversationWindow(
				conversationId = conversation.id,
				peerUserId = conversation.peer.id
			)
			refreshPinnedMessages(conversation.id, conversation.peer.id)
		}
	}

	private fun shouldBufferRealtimeTimelineMutation(
		reason: String,
		conversationId: Long
	): Boolean {
		val pendingAnchor = pendingColdRestoreAnchorMessageId ?: return false
		pendingColdRestoreNeedsRefresh = true
		logAnchor(
			"buffer_realtime_for_cold_restore conversation=$conversationId anchor=$pendingAnchor reason=$reason"
		)
		return true
	}

	private suspend fun applyObservedMessagesPage(
		conversation: DirectChatConversation,
		page: DirectChatMessagesPage
	) {
		syncPendingOutgoingFromMessages(page.items)
		resolveConfirmedOutgoingOverlays(page.items.filter { message -> message.id > 0L })
		val pendingRestoreAnchor = pendingColdRestoreAnchorMessageId
		if (pendingRestoreAnchor != null) {
			bufferedObservedMessagesPage = page
			logAnchor(
				"buffer_observed_page_for_cold_restore conversation=${conversation.id} " +
					"anchor=$pendingRestoreAnchor items=${page.items.size}"
			)
			return
		}
		if (!isScreenActive) return
		val currentState = _state.value
		val currentMessages = flattenMessages(currentState.messages)
		val pendingOutgoingOptimisticIdsSnapshot = pendingOutgoingClientMessageIds.keys.toSet()
		val pendingOutgoingClientMessageIdsSnapshot = pendingOutgoingClientMessageIds.toMap()
		val openAnchorMessageIdSnapshot = resolveDurableOpenAnchor(messageLinkAnchorId)?.messageId
			val observedProjection = withContext(Dispatchers.Default) {
				buildObservedPageProjection(
					conversation = conversation,
					page = page,
					currentMessages = currentMessages,
					pendingOutgoingOptimisticIds = pendingOutgoingOptimisticIdsSnapshot,
					pendingOutgoingClientMessageIds = pendingOutgoingClientMessageIdsSnapshot,
					confirmedReadUpToMessageId = confirmedReadUpToMessageId,
					localReadUpToMessageId = localReadUpToMessageId,
					selfUserId = selfUserId,
					openAnchorMessageId = openAnchorMessageIdSnapshot,
					openMode = openMode,
					messageLinkAnchorMessageId = messageLinkAnchorId,
					onResolvedOutgoing = { }
				)
			} ?: return
		val renderedSnapshot = observedProjection.renderedSnapshot
		val projection = renderedSnapshot.projection
		val shouldHideUnreadBoundaryInActiveChat = canProcessVisibleReadSignals() && currentState.messages != null
		confirmedReadUpToMessageId = maxOf(
			confirmedReadUpToMessageId,
			conversation.lastReadMessageId.coerceAtLeast(0L)
		)
		localReadUpToMessageId = maxOf(localReadUpToMessageId, projection.readUpToMessageId)
		lastAppliedReadUpToMessageId = maxOf(lastAppliedReadUpToMessageId, projection.readUpToMessageId)
		lastAppliedVisibleUnreadMessageId = maxOf(lastAppliedVisibleUnreadMessageId, projection.readUpToMessageId)
		maxVisibleUnreadMessageIdCandidate = maxOf(maxVisibleUnreadMessageIdCandidate, projection.readUpToMessageId)
		if (
			currentState.messages == null &&
			currentState.anchorRequest == null &&
			messageLinkAnchorId == null &&
			openMode == DirectChatOpenMode.FROM_UNREAD
		) {
			val unreadAnchorMessageId = projection.openAnchorMessageId?.takeIf { it > 0L }
			if (unreadAnchorMessageId != null) {
				issueTimelineRestoreRequest(
					targetMessageId = unreadAnchorMessageId,
					initialOffsetPx = 0,
					highlightAnchored = false,
					resetSession = false
				)
			}
		}

		_state.update { current ->
			val shouldPreservePaginationCursor = observedProjection.preservePaginationCursor ||
				(current.canLoadMore && current.nextBeforeId != null && observedProjection.nextBeforeId == null)
			current.copy(
				isLoading = false,
				isError = false,
				messages = renderedSnapshot.groupedMessages,
				pinnedMessages = current.pinnedMessages,
				nextBeforeId = if (shouldPreservePaginationCursor) current.nextBeforeId else observedProjection.nextBeforeId,
				canLoadMore = if (shouldPreservePaginationCursor) current.canLoadMore else observedProjection.canLoadMore,
					conversationId = conversation.id,
					chatInterlocutorId = conversation.peer.id,
					chatInterlocutorName = conversation.displayPeerName(),
					chatInterlocutorAvatarUrl = conversation.peer.avatarUrl.toSafeUriOrNull(),
					unreadMessageIds = projection.unreadMessageIds,
					unreadBoundaryMessageId = if (shouldHideUnreadBoundaryInActiveChat) null else projection.unreadBoundaryMessageId,
					peerLastReadMessageId = maxOf(
						current.peerLastReadMessageId,
						conversation.peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L,
					observedProjection.peerLastReadMessageId
				)
			)
		}
		if (!hasReportedAnchorMetric) reportOpenMetricsIfNeeded(projection)
	}

	fun loadMore() {
		if (useMockData) return
		val current = _state.value
		val conversationId = current.conversationId ?: return
		val nextBeforeId = current.nextBeforeId ?: return
		if (current.isLoading || current.isLoadingMore || isLoadMoreInFlight) {
			return
		}

		isLoadMoreInFlight = true
		_state.update { state -> state.copy(isLoadingMore = true) }
		viewModelScope.launch {
			try {
				loadMorePage(conversationId = conversationId, beforeId = nextBeforeId)
			} finally {
				isLoadMoreInFlight = false
			}
		}
	}

	private suspend fun loadMorePage(
		conversationId: Long,
		beforeId: Long
	): Boolean {
		if (conversationId <= 0L || beforeId <= 0L) return false
		val current = _state.value
		if (current.isLoading) return false
		return try {
			when (
				val response = chatsRepository.getMessages(
					conversationId = conversationId,
					limit = PAGE_SIZE,
					beforeId = beforeId
				)
			) {
			is GetDataResponse.Success -> {
				val nextBeforeId = response.data.nextBeforeId?.takeUnless { it == beforeId }
				observedMessagesLimit += response.data.items.size.coerceAtLeast(PAGE_SIZE)
				currentConversationSnapshot(
					state = _state.value,
					confirmedReadUpToMessageId = confirmedReadUpToMessageId
				)?.let(::reobserveCachedMessagesIfLimitGrew)
				_state.update { state ->
					state.copy(
						nextBeforeId = nextBeforeId,
						canLoadMore = nextBeforeId != null
					)
				}
				refreshPinnedMessages(conversationId, _state.value.chatInterlocutorId)
				response.data.items.isNotEmpty()
			}

				is GetDataResponse.Error -> {
					false
				}
			}
		} catch (t: Throwable) {
			false
		} finally {
			_state.update { state -> state.copy(isLoadingMore = false) }
		}
	}

	private fun subscribeRealtime(conversationId: Long, afterSeq: Long) {
		realtimeJob?.cancel()
		realtimeJob = viewModelScope.launch {
			chatsRepository.subscribeConversation(
				conversationId = conversationId,
				afterSeq = afterSeq,
				replayLimit = 200
			).collect { event ->
					val viewEventDecision = reduceDirectChatRealtimeEvent(
						state = RealtimeViewCursorState(
							readCursor = confirmedReadUpToMessageId,
							lastHandledResyncCursor = lastHandledRealtimeResyncCursor
						),
						event = event,
						isLoading = _state.value.isLoading || _state.value.isLoadingMore
					)
					applyDirectChatRealtimeDecision(viewEventDecision, event)
					when (event) {
						is DirectChatRealtimeEvent.Hello -> {
							// no-op: cursors already reduced by shared realtime reducer
						}

					is DirectChatRealtimeEvent.ReadUpToUpdated -> {
						localReadUpToMessageId = maxOf(localReadUpToMessageId, confirmedReadUpToMessageId)
					if (shouldBufferRealtimeTimelineMutation("read_updated", event.conversationId)) {
						return@collect
					}
					val firstUnreadOverride = if (canProcessVisibleReadSignals()) null else event.firstUnreadId
					val activeConversationId = _state.value.conversationId
					val peerDecision = resolvePeerReadUpdate(
						currentPeerLastReadMessageId = _state.value.peerLastReadMessageId,
							actorUserId = event.actorUserId,
							actorLastReadMessageId = event.actorLastReadMessageId,
							peerUserId = _state.value.chatInterlocutorId
						)
						if (!useMockData && activeConversationId != null && peerDecision.shouldPersistPeerRead) {
							val persistedReadId = peerDecision.persistedPeerReadMessageId ?: 0L
							if (persistedReadId > 0L) {
								viewModelScope.launch {
									chatsRepository.applyPeerLastReadUpTo(
										conversationId = activeConversationId,
										messageId = persistedReadId
									)
								}
							}
						}
					val shouldHideUnreadBoundaryInActiveChat = canProcessVisibleReadSignals()
					_state.update { state ->
						val snapshot = buildStateSnapshot(
							conversationId = state.conversationId,
							peerUserId = state.chatInterlocutorId,
								peerName = state.chatInterlocutorName,
								peerAvatarUrl = state.chatInterlocutorAvatarUrl,
								messages = flattenMessages(state.messages),
								serverReadUpToMessageId = confirmedReadUpToMessageId,
								localReadUpToMessageId = localReadUpToMessageId,
								openAnchorMessageId = resolveDurableOpenAnchor()?.messageId,
								openMode = openMode,
								messageLinkAnchorMessageId = messageLinkAnchorId,
								firstUnreadMessageIdOverride = firstUnreadOverride
							)
						state.copy(
							messages = snapshot?.groupedMessages ?: state.messages,
							pinnedMessages = state.pinnedMessages,
							unreadMessageIds = snapshot?.projection?.unreadMessageIds ?: state.unreadMessageIds,
							unreadBoundaryMessageId = if (shouldHideUnreadBoundaryInActiveChat) null else snapshot?.projection?.unreadBoundaryMessageId,
							peerLastReadMessageId = peerDecision.nextPeerLastReadMessageId
						)
					}
						}

				is DirectChatRealtimeEvent.Typing -> {
					val displayName = _state.value.chatInterlocutorName.trim().ifBlank { "User" }
					val decision = resolveTypingDecision(
						actorUserId = event.actorUserId,
						peerUserId = _state.value.chatInterlocutorId,
						isTyping = event.isTyping,
						typingTtlMs = event.typingTtlMs,
						peerDisplayName = displayName
					)
					if (!decision.shouldApply) return@collect
					typingController.applyIncomingTyping(
						isTyping = decision.isTyping,
						ttlMs = decision.typingTtlMs,
						displayName = decision.displayName,
					)
				}

					is DirectChatRealtimeEvent.ResyncRequired -> {
						if (shouldBufferRealtimeTimelineMutation("resync_required", event.conversationId)) {
							return@collect
						}
						if (!viewEventDecision.cursorDecision.shouldHandleResync) {
							return@collect
						}
						lastHandledRealtimeResyncCursor = viewEventDecision.cursorDecision.nextCursorState.lastHandledResyncCursor
						refreshConversationWindow(
							conversationId = event.conversationId,
							peerUserId = _state.value.chatInterlocutorId
						)
					}
					else -> Unit
				}
			}
		}
	}

	fun closeCurrentReply() {
		_state.update { it.copy(messageFieldReply = null) }
	}

	fun updateMessageInputField(newValue: String) {
		if (_state.value.selectedMessageIds.isNotEmpty()) return
		_state.update { it.copy(messageFieldValue = newValue) }
		processTypingInput(newValue)
	}

	fun enterSelectionMode(messageId: Long) {
		if (messageId <= 0L) return
		_state.update { state ->
			state.copy(
				selectedMessageIds = state.selectedMessageIds + messageId,
				messageFieldReply = null,
				messageToEditId = null
			)
		}
	}

	fun toggleMessageSelection(messageId: Long) {
		if (messageId <= 0L) return
		_state.update { state ->
			val nextSelected = if (messageId in state.selectedMessageIds) {
				state.selectedMessageIds - messageId
			} else {
				state.selectedMessageIds + messageId
			}
			state.copy(selectedMessageIds = nextSelected)
		}
	}

	fun clearSelection() {
		_state.update { state -> state.copy(selectedMessageIds = emptySet()) }
	}

	fun deleteSelectedMessages() {
		val selectedIds = _state.value.selectedMessageIds
			.ifEmpty { return }
			.sorted()
		val currentMessages = flattenMessages(_state.value.messages)
		val selectedMessages = selectedIds.mapNotNull { selectedId ->
			currentMessages.firstOrNull { it.id == selectedId }
		}
		if (selectedMessages.isEmpty()) return
		clearSelection()
		selectedMessages.forEach { message ->
			deleteMessage(message)
			outgoingController.flushPendingDeleteNow()
		}
	}

	fun sendMessage() {
		if (useMockData) {
			sendMockMessage()
			return
		}

		viewModelScope.launch {
			val currentState = _state.value
			val text = currentState.messageFieldValue.trim()
			if (text.isBlank()) return@launch
			if (!text.isWithinCodePointLimit(DEFAULT_CHAT_MESSAGE_MAX_LENGTH)) return@launch

			val conversation = ensureConversationReadyForSend() ?: run {
				_state.update { state ->
					state.copy(
						isLoading = false,
						isError = state.messages == null
					)
				}
				return@launch
			}
			val conversationId = conversation.id
			val idempotencyKey = UUID.randomUUID().toString()
			val capturedReply = _state.value.messageFieldReply
			val replyToMessageId = capturedReply?.replyId
			val replyToMessageText = capturedReply?.replyMessageText
			val optimisticMessageId = nextOptimisticMessageId()
			val optimisticDomainMessage = buildOptimisticOutgoingDomainMessage(
				conversationId = conversationId,
				id = optimisticMessageId,
				text = text,
				replyToMessageId = replyToMessageId,
				replyToMessageText = replyToMessageText,
				idempotencyKey = idempotencyKey
			)
			pendingOutgoingClientMessageIds[optimisticMessageId] = idempotencyKey
			chatsRepository.storeOutgoingOptimisticMessage(optimisticDomainMessage)

			_state.update { state ->
				state.copy(
					messageFieldValue = "",
					messageFieldReply = null,
					isLoading = false,
					isError = false,
					scrollToBottomRequestToken = System.currentTimeMillis(),
					highlightRequest = null
				)
			}
			latestPersistableMessageId(_state.value.messages)?.let { anchorMessageId ->
				currentOpenAnchorMessageId = anchorMessageId
				currentOpenAnchorOffsetPx = 0
			}

			stopOutgoingTyping(conversationId)
			val sendJob = launch {
				try {
					val response = withTimeout(OUTGOING_SEND_TIMEOUT_MS) {
						chatsRepository.sendMessage(
							conversationId = conversationId,
							text = text,
							clientMessageId = idempotencyKey,
							replyToMessageId = replyToMessageId
						)
					}
					when (response) {
						is GetDataResponse.Success -> {
							if (outgoingController.isCancelled(idempotencyKey)) {
								chatsRepository.deleteMessage(
									conversationId = conversationId,
									messageId = response.data.id
								)
								chatsRepository.deleteLocalMessageByClientMessageId(
									conversationId = conversationId,
									clientMessageId = idempotencyKey
								)
								outgoingController.removeCancelledClientMessageId(idempotencyKey)
								removePendingOutgoing(conversationId, optimisticMessageId)
								return@launch
							}
							val matchedOptimisticId = outgoingController.resolvePendingOptimisticId(response.data.clientMessageId)
								?: optimisticMessageId
							removePendingOutgoing(conversationId, matchedOptimisticId)
							latestPersistableMessageId(_state.value.messages)?.let { messageId ->
								rememberOpenAnchorMessage(messageId, 0, isBottomPinned = false)
							}
						}

					is GetDataResponse.Error -> {
						removePendingOutgoing(conversationId, optimisticMessageId)
						_state.update { state ->
							state.copy(
								messageFieldValue = state.messageFieldValue.ifBlank { text },
								messageFieldReply = state.messageFieldReply ?: capturedReply,
								isError = false
							)
						}
					}
					}
				} catch (timeout: TimeoutCancellationException) {
					chatsRepository.updateLocalMessageDeliveryStatusByClientMessageId(
						conversationId = conversationId,
						clientMessageId = idempotencyKey,
						status = me.floow.domain.models.MessageDeliveryStatus.FAILED
					)
				} catch (cancel: CancellationException) {
					throw cancel
				} catch (t: Throwable) {
					chatsRepository.updateLocalMessageDeliveryStatusByClientMessageId(
						conversationId = conversationId,
						clientMessageId = idempotencyKey,
						status = me.floow.domain.models.MessageDeliveryStatus.FAILED
					)
				} finally {
					pendingOutgoingSendJobs.remove(optimisticMessageId)
				}
			}
			pendingOutgoingSendJobs[optimisticMessageId] = sendJob
		}
	}

	private fun sendMockMessage() {
		val currentState = _state.value
		val text = currentState.messageFieldValue
		if (text.isBlank()) return

		val reply = currentState.messageFieldReply
		val newMessage: ChatMessage = if (reply != null) {
			ReplyOutMessage(
				id = System.currentTimeMillis(),
				messageText = text,
				dateTime = LocalDateTime.now(),
				replyMessageId = reply.replyId,
				replyMessageText = reply.replyMessageText
			)
		} else {
			PrimaryOutMessage(
				id = System.currentTimeMillis(),
				messageText = text,
				dateTime = LocalDateTime.now()
			)
		}

		_state.update { state ->
			val oldMessages = flattenMessages(state.messages)
			val combined = mergeMessages(oldMessages, listOf(newMessage))
			val groupedMessages = groupMessagesByDate(combined)
			state.copy(
				messageFieldValue = "",
				messageFieldReply = null,
				messages = groupedMessages,
				pinnedMessages = state.pinnedMessages,
				scrollToBottomRequestToken = System.currentTimeMillis()
			)
		}
	}

	fun addCurrentReply(chatMessage: ChatMessage) {
		if (_state.value.selectedMessageIds.isNotEmpty()) return
		val replyAuthorName = when (chatMessage) {
			is PrimaryInMessage, is ReplyInMessage -> _state.value.chatInterlocutorName
			else -> "You"
		}

		_state.update {
			it.copy(
				messageFieldReply = MessageFieldReply(
					replyId = chatMessage.id,
					replyAuthorName = replyAuthorName,
					replyMessageText = chatMessage.messageText
				)
			)
		}
	}

	fun jumpToMessage(messageId: Long) = navigateToMessage(messageId)

	fun onPinnedMessageClick(messageId: Long) = navigateToMessage(messageId)

	private fun navigateToMessage(messageId: Long) {
		val targetMessageId = messageId.takeIf { it > 0L } ?: return
		val currentMessages = flattenMessages(_state.value.messages)
		if (currentMessages.any { it.id == targetMessageId }) {
			issueJumpRequest(targetMessageId)
			return
		}
		if (useMockData) {
			issueJumpRequest(targetMessageId)
			return
		}
		val conversationId = _state.value.conversationId ?: run {
			issueJumpRequest(targetMessageId)
			return
		}
		viewModelScope.launch {
			when (
				val response = chatsRepository.getMessagesAround(
					conversationId = conversationId,
					anchorId = targetMessageId,
					olderLimit = PAGE_SIZE,
					newerLimit = PAGE_SIZE
				)
			) {
				is GetDataResponse.Success -> {
					observedMessagesLimit = maxOf(
						observedMessagesLimit,
						response.data.items.size.coerceAtLeast(PAGE_SIZE)
					)
					currentConversationSnapshot(
						state = _state.value,
						confirmedReadUpToMessageId = confirmedReadUpToMessageId
					)?.let(::reobserveCachedMessagesIfLimitGrew)
					issueTimelineRestoreRequest(
						targetMessageId = targetMessageId,
						initialOffsetPx = 0,
						highlightAnchored = true
					)
				}
				is GetDataResponse.Error -> issueJumpRequest(targetMessageId)
			}
		}
	}

	private fun issueJumpRequest(targetMessageId: Long) {
		val requestToken = System.currentTimeMillis()
		_state.update { state ->
			state.copy(
				scrollToBottomRequestToken = 0L,
				anchorRequest = null,
				highlightRequest = createHighlightRequest(
					messageId = targetMessageId,
					requestToken = requestToken,
					keepAnchored = false
				)
			)
		}
		highlightCleanupJob?.cancel()
		highlightCleanupJob = viewModelScope.launch {
			delay(HIGHLIGHT_CLEAR_DELAY_MS + HIGHLIGHT_JUMP_SETTLE_MARGIN_MS)
			_state.update { state ->
				if (state.currentHighlightMessageId == targetMessageId) {
					state.copy(highlightRequest = null)
				} else {
					state
				}
			}
		}
	}

	private fun scheduleHighlightCleanup(messageId: Long) {
		highlightCleanupJob?.cancel()
		highlightCleanupJob = viewModelScope.launch {
			delay(HIGHLIGHT_CLEAR_DELAY_MS)
			_state.update { state ->
				if (state.currentHighlightMessageId == messageId) {
					state.copy(
						highlightRequest = null
					)
				} else {
					state
				}
			}
		}
	}

	fun togglePinMessage(messageId: Long) {
		val currentMessages = flattenMessages(_state.value.messages)
		val target = currentMessages.firstOrNull { it.id == messageId } ?: return
		if (target.isPinned) {
			unpinMessage(messageId)
		} else {
			pinMessage(messageId)
		}
	}

	fun pinMessage(messageId: Long) {
		val conversationId = _state.value.conversationId ?: return
		val currentMessages = flattenMessages(_state.value.messages)
		val target = currentMessages.firstOrNull { it.id == messageId } ?: return
		if (target.isPinned) return

		if (useMockData) {
			_state.update { state -> applyPinnedFlagToState(state, messageId, isPinned = true) }
			return
		}

		_state.update { state -> applyPinnedFlagToState(state, messageId, isPinned = true) }

		viewModelScope.launch {
			chatsRepository.applyLocalPinnedState(
				conversationId = conversationId,
				messageId = messageId,
				isPinned = true,
				pinnedAt = System.currentTimeMillis(),
				pinnedByUserId = null
			)
			when (
				val response = chatsRepository.setMessagePinned(
					conversationId = conversationId,
					messageId = messageId,
					isPinned = true
				)
			) {
				is GetDataResponse.Success -> {
					refreshPinnedMessages(conversationId, _state.value.chatInterlocutorId)
				}
				is GetDataResponse.Error -> {
					_state.update { state -> applyPinnedFlagToState(state, messageId, isPinned = false) }
					chatsRepository.applyLocalPinnedState(
						conversationId = conversationId,
						messageId = messageId,
						isPinned = false,
						pinnedAt = null,
						pinnedByUserId = null
					)
				}
			}
		}
	}

	fun unpinMessage(messageId: Long) {
		val conversationId = _state.value.conversationId ?: return
		val currentPinned = _state.value.pinnedMessages
		val target = currentPinned.firstOrNull { it.id == messageId }
			?: flattenMessages(_state.value.messages).firstOrNull { it.id == messageId }
			?: return
		if (!target.isPinned && currentPinned.none { it.id == messageId }) return

		if (useMockData) {
			_state.update { state -> applyPinnedFlagToState(state, messageId, isPinned = false) }
			return
		}

		_state.update { state -> applyPinnedFlagToState(state, messageId, isPinned = false) }

		viewModelScope.launch {
			chatsRepository.applyLocalPinnedState(
				conversationId = conversationId,
				messageId = messageId,
				isPinned = false,
				pinnedAt = null,
				pinnedByUserId = null
			)
			when (
				val response = chatsRepository.setMessagePinned(
					conversationId = conversationId,
					messageId = messageId,
					isPinned = false
				)
			) {
				is GetDataResponse.Success -> {
					refreshPinnedMessages(conversationId, _state.value.chatInterlocutorId)
				}
				is GetDataResponse.Error -> {
					_state.update { state ->
						applyPinnedFlagToState(state, messageId, isPinned = true)
					}
					chatsRepository.applyLocalPinnedState(
						conversationId = conversationId,
						messageId = messageId,
						isPinned = true,
						pinnedAt = null,
						pinnedByUserId = null
					)
				}
			}
		}
	}

	fun deleteMessage(message: ChatMessage) {
		if (useMockData) {
			val currentMessages = flattenMessages(_state.value.messages)
			val messageToDelete = currentMessages.find { it.id == message.id } ?: return
			val updatedMessages = currentMessages.filterNot { it.id == message.id }
			_state.update {
				it.copy(
					messages = groupMessagesByDate(updatedMessages),
					pinnedMessages = it.pinnedMessages,
					lastDeletedMessage = messageToDelete
				)
			}
			schedulePendingDeleteCommit(message.id, conversationId = null)
			return
		}

		val conversationId = _state.value.conversationId ?: return
		val clientMessageId = message.clientMessageId?.trim().orEmpty()
		if (message.id <= 0L || message.deliveryStatus != me.floow.domain.models.MessageDeliveryStatus.SENT) {
			if (clientMessageId.isNotEmpty()) {
				outgoingController.addCancelledClientMessageId(clientMessageId)
				val pendingEntry = pendingOutgoingClientMessageIds
					.entries
					.firstOrNull { it.value == clientMessageId }
				pendingEntry?.let { entry ->
					pendingOutgoingSendJobs.remove(entry.key)?.cancel()
					pendingOutgoingClientMessageIds.remove(entry.key)
				}
				viewModelScope.launch {
					chatsRepository.deleteLocalMessageByClientMessageId(
						conversationId = conversationId,
						clientMessageId = clientMessageId
					)
				}
			}
			return
		}

		val currentMessages = flattenMessages(_state.value.messages)
		val messageToDelete = currentMessages.find { it.id == message.id } ?: return
		val updatedMessages = currentMessages.filterNot { it.id == message.id }
		_state.update {
			it.copy(
				messages = groupMessagesByDate(updatedMessages),
				pinnedMessages = it.pinnedMessages,
				lastDeletedMessage = messageToDelete
			)
		}
		schedulePendingDeleteCommit(message.id, conversationId)
	}

	private fun schedulePendingDeleteCommit(messageId: Long, conversationId: Long?) {
		val message = _state.value.lastDeletedMessage ?: return
		outgoingController.schedulePendingDeleteCommit(message, conversationId)
	}

	fun undoDeleteMessage() {
		outgoingController.cancelPendingDelete()
		val restored = _state.value.lastDeletedMessage ?: return
		val updatedMessages = mergeMessages(
			base = flattenMessages(_state.value.messages),
			incoming = listOf(restored)
		)
		_state.update {
			it.copy(
				messages = groupMessagesByDate(updatedMessages),
				pinnedMessages = it.pinnedMessages,
				lastDeletedMessage = null
			)
		}
	}

	fun startEditingMessage(messageId: Long, messageText: String) {
		if (_state.value.selectedMessageIds.isNotEmpty()) return
		_state.update { state ->
			state.copy(
				messageToEditId = messageId,
				messageFieldValue = messageText,
				messageFieldReply = null,
				priorEditDraftText = state.messageFieldValue,
				priorEditReply = state.messageFieldReply,
			)
		}
	}

	fun editMessage(messageId: Long, newText: String) {
		if (newText.isBlank()) return
		val trimmedText = newText.trim()
		if (!trimmedText.isWithinCodePointLimit(DEFAULT_CHAT_MESSAGE_MAX_LENGTH)) return
		val currentState = _state.value
		val previousText = flattenMessages(currentState.messages)
			.firstOrNull { it.id == messageId }
			?.messageText
			?: return

		_state.update { state -> applyLocalEdit(state, messageId, trimmedText) }

		if (useMockData) return

		val conversationId = currentState.conversationId ?: return
		viewModelScope.launch {
			when (
				val response = chatsRepository.updateMessage(
					conversationId = conversationId,
					messageId = messageId,
					text = trimmedText
				)
			) {
				is GetDataResponse.Success -> Unit
				is GetDataResponse.Error -> {
					_state.update { state ->
						applyLocalEdit(state, messageId, previousText).copy(
							messageToEditId = messageId,
							messageFieldValue = trimmedText,
						)
					}
				}
			}
		}
	}

	private fun applyLocalEdit(
		state: ChatScreenVmState,
		messageId: Long,
		text: String,
	): ChatScreenVmState {
		val updatedMessages = flattenMessages(state.messages).map { message ->
			if (message.id != messageId) message else when (message) {
				is PrimaryOutMessage -> message.copy(messageText = text)
				is ReplyOutMessage -> message.copy(messageText = text)
				is PrimaryInMessage -> message.copy(messageText = text)
				is ReplyInMessage -> message.copy(messageText = text)
				is PostPreviewMessage -> message
			}
		}
		val updatedPinned = state.pinnedMessages.map { pinned ->
			if (pinned.id != messageId) pinned else when (pinned) {
				is PrimaryOutMessage -> pinned.copy(messageText = text)
				is ReplyOutMessage -> pinned.copy(messageText = text)
				is PrimaryInMessage -> pinned.copy(messageText = text)
				is ReplyInMessage -> pinned.copy(messageText = text)
				is PostPreviewMessage -> pinned
			}
		}
		return state.copy(
			messages = groupMessagesByDate(updatedMessages),
			pinnedMessages = updatedPinned,
			messageToEditId = null,
			messageFieldValue = "",
			priorEditDraftText = "",
			priorEditReply = null,
		)
	}

	fun cancelEditing() {
		_state.update { state ->
			state.copy(
				messageToEditId = null,
				messageFieldValue = state.priorEditDraftText,
				messageFieldReply = state.priorEditReply,
				priorEditDraftText = "",
				priorEditReply = null,
			)
		}
	}

	fun retryFailedMessage(message: ChatMessage) {
		val conversationId = _state.value.conversationId ?: return
		if (useMockData) return
		if (message.deliveryStatus != me.floow.domain.models.MessageDeliveryStatus.FAILED) return
		val clientMessageId = message.clientMessageId?.trim().orEmpty()
		if (clientMessageId.isEmpty()) return

		viewModelScope.launch {
			chatsRepository.updateLocalMessageDeliveryStatusByClientMessageId(
				conversationId = conversationId,
				clientMessageId = clientMessageId,
				status = me.floow.domain.models.MessageDeliveryStatus.SENDING
			)
			chatsRepository.retryOutgoingMessage(conversationId, clientMessageId)
		}
	}

	fun requestScrollToBottom() {
		val currentState = _state.value
		val latestMessageId = latestPersistableMessageId(currentState.messages)
		val unreadMaxMessageId = currentState.unreadMessageIds.maxOrNull()
		if (unreadMaxMessageId != null && unreadMaxMessageId > 0L) {
			applyReadUpToLocally(unreadMaxMessageId)
		}
		if (latestMessageId != null && latestMessageId > 0L) {
			rememberOpenAnchorMessage(latestMessageId, 0, isBottomPinned = true)
		}
		clearPendingAnchorRestore()
		_state.update { state ->
			state.copy(
				scrollToBottomRequestToken = System.currentTimeMillis(),
				anchorRequest = null,
				highlightRequest = null
			)
		}
	}

	fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
		if (!canProcessVisibleReadSignals()) return

		val state = _state.value
		val hasPendingTimelineRestore = state.anchorRequest != null ||
			(state.highlightRequest?.keepAnchored == true)
		val pendingRestoreMessageId = state.anchorRequest?.messageId
			?: state.highlightRequest
				?.takeIf { it.keepAnchored }
				?.messageId
		val isViewportStillOnPendingRestoreTarget = pendingRestoreMessageId != null &&
			snapshot.firstVisibleMessageId == pendingRestoreMessageId
		logAnchor(
			"viewport atBottom=${snapshot.isAtBottom} firstVisible=${snapshot.firstVisibleMessageId} " +
				"offset=${snapshot.firstVisibleOffsetPx} index=${snapshot.firstVisibleItemIndex} " +
				"itemOffset=${snapshot.firstVisibleItemScrollOffsetPx} pendingRestore=$hasPendingTimelineRestore " +
				"pendingTarget=$pendingRestoreMessageId onPendingTarget=$isViewportStillOnPendingRestoreTarget " +
				"visibleCount=${snapshot.visibleMessageIds.size}"
		)
		lastViewportItemIndex = snapshot.firstVisibleItemIndex.coerceAtLeast(0)
		lastViewportItemScrollOffsetPx = snapshot.firstVisibleItemScrollOffsetPx.coerceAtLeast(0)
		hasLastViewportSnapshot = snapshot.visibleMessageIds.isNotEmpty()
		val pendingColdRestoreAnchor = pendingColdRestoreAnchorMessageId
		if (
			pendingColdRestoreAnchor != null &&
			snapshot.firstVisibleMessageId == pendingColdRestoreAnchor &&
			kotlin.math.abs(snapshot.firstVisibleOffsetPx - pendingColdRestoreOffsetPx) <= COLD_RESTORE_OFFSET_TOLERANCE_PX
		) {
			viewModelScope.launch {
				releasePendingColdRestoreLock(reason = "viewport_settled")
			}
		}

		if (!useMockData && snapshot.isAtBottom) {
			if (!hasPendingTimelineRestore && !isViewportStillOnPendingRestoreTarget) {
				val latestMessageId = latestPersistableMessageId(state.messages)
				if (latestMessageId != null && latestMessageId > 0L) {
					rememberOpenAnchorMessage(latestMessageId, 0, isBottomPinned = true)
				}
			}
		} else if (!useMockData) {
			val firstVisibleMessageId = snapshot.firstVisibleMessageId?.takeIf { it > 0L }
			if (!hasPendingTimelineRestore && !isViewportStillOnPendingRestoreTarget && firstVisibleMessageId != null) {
				rememberOpenAnchorMessage(
					anchorMessageId = firstVisibleMessageId,
					offsetPx = snapshot.firstVisibleOffsetPx,
					isBottomPinned = false
				)
			}
		}

		if (useMockData) return
		snapshot.visibleReadCandidateId?.let { candidateId ->
			applyVisibleReadCandidate(candidateId)
		}
	}

	fun onAnchorRestoreSettled(messageId: Long, offsetPx: Int) {
		if (messageId <= 0L) return
		logAnchor("restore_settled message=$messageId offset=$offsetPx")
		rememberOpenAnchorMessage(messageId, offsetPx, isBottomPinned = false)
		if (pendingColdRestoreAnchorMessageId == messageId) {
			viewModelScope.launch {
				releasePendingColdRestoreLock(reason = "anchor_settled")
			}
		}
		var shouldScheduleHighlightCleanup = false
		_state.update { state ->
			val clearAnchorRequest = state.anchorRequest?.messageId == messageId
			val highlightRequest = state.highlightRequest
			val transformHighlightRequest = highlightRequest?.messageId == messageId && highlightRequest.keepAnchored
			val notifyHighlightSettled = highlightRequest?.messageId == messageId && !highlightRequest.keepAnchored
			if (!clearAnchorRequest && !transformHighlightRequest && !notifyHighlightSettled) {
				state
			} else {
				if (transformHighlightRequest || notifyHighlightSettled) {
					shouldScheduleHighlightCleanup = true
				}
				state.copy(
					anchorRequest = if (clearAnchorRequest) null else state.anchorRequest,
					highlightRequest = if (transformHighlightRequest) {
						highlightRequest?.copy(keepAnchored = false)
					} else {
						highlightRequest
					}
				)
			}
		}
		if (shouldScheduleHighlightCleanup) {
			scheduleHighlightCleanup(messageId)
		}
	}

	fun onAnchorRestoreTimedOut(messageId: Long) {
		if (messageId <= 0L) return
		logAnchor("restore_timed_out message=$messageId")
		if (pendingColdRestoreAnchorMessageId == messageId) {
			viewModelScope.launch {
				releasePendingColdRestoreLock(reason = "anchor_timeout")
			}
		}
		_state.update { state ->
			val clearAnchorRequest = state.anchorRequest?.messageId == messageId
			val clearHighlightRequest = state.highlightRequest?.messageId == messageId
			if (!clearAnchorRequest && !clearHighlightRequest) {
				state
			} else {
				state.copy(
					anchorRequest = if (clearAnchorRequest) null else state.anchorRequest,
					highlightRequest = if (clearHighlightRequest) null else state.highlightRequest
				)
			}
		}
	}

	fun onScreenClosed() {
		if (!isScreenActive) return
		isScreenActive = false
		updateFocusPresenceTargets(isVisible = false)
		applyVisibleReadCandidate(maxVisibleUnreadMessageIdCandidate)
		isScreenVisibleToUser = false
		outgoingController.flushPendingDeleteNow()
		realtimeJob?.cancel()
		realtimeJob = null
		localMessagesJob?.cancel()
		localMessagesJob = null
		pinnedMessagesJob?.cancel()
		pinnedMessagesJob = null
		highlightCleanupJob?.cancel()
		highlightCleanupJob = null
		val conversationId = _state.value.conversationId
		if (!useMockData && conversationId != null && typingController.isOutgoingTypingActive()) {
			stopOutgoingTyping(conversationId)
		}
		typingController.reset()
		clearPendingAnchorRestore()
		_state.update { state ->
			state.copy(
				scrollToBottomRequestToken = 0L,
				initialViewport = hasLastViewportSnapshot.takeIf { it }?.let {
					ChatInitialViewport(
						itemIndex = lastViewportItemIndex,
						itemScrollOffsetPx = lastViewportItemScrollOffsetPx
					)
				},
				highlightRequest = null,
				anchorRequest = null
			)
		}
		if (!useMockData) {
			val closingAnchor = anchorController.resolveDurableAnchor()
				?: latestPersistableMessageId(_state.value.messages)?.let { messageId ->
					PersistedAnchor(messageId = messageId, offsetPx = 0, isBottomPinned = true)
				}
			if (closingAnchor != null && closingAnchor.messageId > 0L) {
				logAnchor(
					"screen_closed persist_anchor message=${closingAnchor.messageId} " +
						"offset=${closingAnchor.offsetPx} bottomPinned=${closingAnchor.isBottomPinned}"
				)
				anchorController.enqueuePersist(
					closingAnchor.messageId,
					closingAnchor.offsetPx,
					closingAnchor.isBottomPinned,
				)
			}
		}

		val pendingReadMessageId = readController.cancelPendingFlush()
		val (pendingAnchorMessageId, pendingAnchorOffsetPx, pendingAnchorBottomPinned) = anchorController.cancelPersist()

		if (pendingReadMessageId == null && pendingAnchorMessageId == null && conversationId == null) return

		viewModelScope.launch(Dispatchers.IO + NonCancellable) {
			val readMessageIdToFlush = pendingReadMessageId
				?: conversationId?.let { activeConversationId ->
					directMessagesReadCursorStore
						.getPendingReadUpTo(activeConversationId)
						.takeIf { it > 0L }
				}
			if (conversationId != null && readMessageIdToFlush != null) {
				syncReadUpToNow(
					readUpToMessageId = readMessageIdToFlush,
					explicitConversationId = conversationId
				)
			}
			pendingAnchorMessageId?.let { messageId ->
				persistOpenAnchorNow(
					anchorMessageId = messageId,
					offsetPx = pendingAnchorOffsetPx ?: anchorController.currentAnchorOffsetPx,
					isBottomPinned = pendingAnchorBottomPinned ?: anchorController.currentAnchorBottomPinned
				)
			}
		}
	}

	fun onChatScreenVisible() {
		if (!isScreenActive) return
		isScreenVisibleToUser = true
		updateFocusPresenceTargets(isVisible = true)
		retryPendingReadFlushIfNeeded()
	}

	private fun retryPendingReadFlushIfNeeded() {
		val candidateId = maxVisibleUnreadMessageIdCandidate.takeIf { it > 0L } ?: return
		val conversationId = _state.value.conversationId ?: return
		val baseline = maxOf(localReadUpToMessageId, confirmedReadUpToMessageId)
		if (candidateId <= baseline) return
		readController.forceEnqueueReadUpTo(conversationId, candidateId)
	}

	private fun applyPeerPresence(presence: UserPresence?) {
		if (presence == null) return
		val isOnline = presence.isOnline
		val incomingLastSeen = presence.lastSeenAtMillis
		val lastSeen = when {
			isOnline -> incomingLastSeen ?: _state.value.peerLastSeenAtMillis
			incomingLastSeen != null && incomingLastSeen > 0L -> incomingLastSeen
			else -> _state.value.peerLastSeenAtMillis
		}
		_state.update { state ->
			if (state.peerIsOnline == isOnline && state.peerLastSeenAtMillis == lastSeen) return@update state
			state.copy(
				peerIsOnline = isOnline,
				peerLastSeenAtMillis = lastSeen
			)
		}
	}

	private fun updateFocusPresenceTargets(isVisible: Boolean) {
		val peerId = _state.value.chatInterlocutorId.trim()
		if (peerId.isBlank() || peerId == selfUserId) {
			clearPresenceBinding()
			return
		}
		val owner = buildPresenceOwnerKey(peerId)
		if (isVisible) {
			if (currentPresenceOwner != owner) {
				currentPresenceOwner?.let(presenceRepository::clearTargets)
				currentPresenceOwner = owner
			}
			presenceRepository.setTargets(owner = owner, userIds = listOf(peerId))
		} else {
			clearPresenceBinding()
		}
	}

	fun onChatScreenHidden() {
		if (!isScreenActive) return
		isScreenVisibleToUser = false
		updateFocusPresenceTargets(isVisible = false)
	}

	private fun clearPresenceBinding() {
		currentPresenceOwner?.let(presenceRepository::clearTargets)
		currentPresenceOwner = null
	}

	private fun buildPresenceOwnerKey(peerId: String): String {
		val conversationId = _state.value.conversationId
		return if (conversationId != null && conversationId > 0L) {
			"chat:$conversationId"
		} else {
			"chat:$peerId"
		}
	}

	private fun applyVisibleReadCandidate(readUpToMessageId: Long) {
		val baselineCursor = maxOf(localReadUpToMessageId, confirmedReadUpToMessageId)
		val shouldApply = shouldApplyVisibleReadCandidate(readUpToMessageId, lastAppliedVisibleUnreadMessageId)
		logger.d(
			"READ_DEBUG",
			"candidate=$readUpToMessageId baseline=$baselineCursor visible=$isScreenVisibleToUser apply=$shouldApply"
		)
		readController.applyVisibleReadCandidate(
			readUpToMessageId = readUpToMessageId,
			isVisible = isScreenVisibleToUser
		)
	}

	private fun applyReadUpToLocally(readUpToMessageId: Long) {
		if (readUpToMessageId <= 0L) return
		val effectiveLocalReadUpToMessageId = maxOf(localReadUpToMessageId, readUpToMessageId)
		localReadUpToMessageId = effectiveLocalReadUpToMessageId
			_state.update { state ->
				val unreadProjection = projectDirectChatReadModel(
					input = DirectChatReadModelInput(
					peerUserId = state.chatInterlocutorId,
					messages = flattenMessages(state.messages),
					serverReadUpToMessageId = confirmedReadUpToMessageId,
					localReadUpToMessageId = effectiveLocalReadUpToMessageId,
					firstUnreadMessageId = null,
					openAnchorMessageId = resolveDurableOpenAnchor()?.messageId,
					openMode = openMode,
					messageLinkAnchorMessageId = messageLinkAnchorId
				)
				)
				state.copy(
					unreadMessageIds = unreadProjection.unreadMessageIds,
					unreadBoundaryMessageId = if (canProcessVisibleReadSignals()) null else unreadProjection.unreadBoundaryMessageId
				)
			}
			enqueueReadUpToMessageId(effectiveLocalReadUpToMessageId)
		}

	private fun enqueueReadUpToMessageId(readUpToMessageId: Long) {
		val conversationId = _state.value.conversationId ?: return
		readController.enqueueReadUpTo(conversationId, readUpToMessageId)
	}

	private fun rememberOpenAnchorMessage(
		anchorMessageId: Long,
		offsetPx: Int = 0,
		isBottomPinned: Boolean = false,
	) {
		if (anchorMessageId <= 0L) return
		logAnchor(
			"remember_anchor message=$anchorMessageId offset=${offsetPx.coerceAtLeast(0)} " +
				"bottomPinned=$isBottomPinned"
		)
		anchorController.remember(anchorMessageId, offsetPx, isBottomPinned)
	}

	private suspend fun syncReadUpToNow(readUpToMessageId: Long, explicitConversationId: Long? = null): Boolean {
		val conversationId = explicitConversationId ?: _state.value.conversationId ?: return false
		if (conversationId <= 0L || readUpToMessageId <= 0L) return false
		directMessagesReadCursorStore.enqueueReadUpTo(
			conversationId = conversationId,
			messageId = readUpToMessageId
		)
		directMessagesReadCursorStore.setLocalLastReadMessageId(
			conversationId = conversationId,
			messageId = readUpToMessageId
		)
		chatsRepository.applyLocalReadUpTo(
			conversationId = conversationId,
			messageId = readUpToMessageId
		)
		return when (
			val response = chatsRepository.markReadUpTo(
				conversationId = conversationId,
				messageId = readUpToMessageId
			)
		) {
			is GetDataResponse.Success -> {
				val appliedMessageId = maxOf(
					readUpToMessageId,
					response.data.lastReadMessageId.coerceAtLeast(0L)
				)
				confirmedReadUpToMessageId = maxOf(confirmedReadUpToMessageId, appliedMessageId)
				localReadUpToMessageId = maxOf(localReadUpToMessageId, appliedMessageId)
				directMessagesReadCursorStore.markPendingReadUpToApplied(
					conversationId = conversationId,
					appliedMessageId = appliedMessageId
				)
				directMessagesReadCursorStore.resetPendingRetryCount(conversationId)
				if (appliedMessageId > readUpToMessageId) {
					chatsRepository.applyLocalReadUpTo(
						conversationId = conversationId,
						messageId = appliedMessageId
					)
					directMessagesReadCursorStore.setLocalLastReadMessageId(
						conversationId = conversationId,
						messageId = appliedMessageId
					)
				}
				true
			}

			is GetDataResponse.Error -> false
		}
	}

	private suspend fun persistOpenAnchorNow(anchorMessageId: Long, offsetPx: Int, isBottomPinned: Boolean) {
		val conversationId = _state.value.conversationId ?: return
		if (conversationId <= 0L || anchorMessageId <= 0L) return
		currentOpenAnchorMessageId = anchorMessageId
		currentOpenAnchorOffsetPx = offsetPx.coerceAtLeast(0)
		currentOpenAnchorBottomPinned = isBottomPinned
		logAnchor(
			"persist_anchor conversation=$conversationId message=$anchorMessageId " +
				"offset=$currentOpenAnchorOffsetPx bottomPinned=$currentOpenAnchorBottomPinned"
		)
		directMessagesReadCursorStore.setOpenViewportSnapshot(
			conversationId = conversationId,
			snapshot = DirectChatViewportSnapshot(
				anchorMessageId = anchorMessageId,
				anchorOffsetPx = currentOpenAnchorOffsetPx,
				isBottomPinned = currentOpenAnchorBottomPinned
			)
		)
	}

	private fun refreshConversationWindow(
		conversationId: Long,
		peerUserId: String
	) {
		if (useMockData || conversationId <= 0L) return
		viewModelScope.launch {
			when (
				val response = chatsRepository.refreshLatestMessagesWindow(
					conversationId = conversationId,
					limit = observedMessagesLimit.coerceAtLeast(PAGE_SIZE)
				)
			) {
				is GetDataResponse.Success -> {
					response.data.peerLastReadMessageId
						?.coerceAtLeast(0L)
						?.takeIf { it > 0L }
						?.let { peerLastReadMessageId ->
							_state.update { state ->
								if (state.conversationId != conversationId) {
									state
								} else {
									state.copy(
										peerLastReadMessageId = maxOf(
											state.peerLastReadMessageId,
											peerLastReadMessageId
										)
									)
								}
							}
						}
				}

				is GetDataResponse.Error -> {
					// keep current snapshot and rely on the next resync attempt
				}
			}
			refreshPinnedMessages(conversationId, peerUserId)
		}
	}

	private fun refreshPinnedMessages(
		conversationId: Long,
		peerUserId: String
	) {
		if (useMockData || conversationId <= 0L) return
		viewModelScope.launch {
			when (
				val response = chatsRepository.getPinnedMessages(
					conversationId = conversationId,
					limit = PINNED_MESSAGES_LIMIT
				)
			) {
				is GetDataResponse.Success -> Unit

				is GetDataResponse.Error -> {
					// no-op: keep current pinned snapshot
				}
			}
		}
	}

	private fun processTypingInput(input: String) {
		if (useMockData) return
		val conversationId = _state.value.conversationId ?: return
		typingController.onInput(input, conversationId)
	}

	private fun syncChatMetadata(conversationId: Long) {
		if (conversationId <= 0L || useMockData) return
		viewModelScope.launch {
			when (val response = chatsRepository.getConversation(conversationId)) {
				is GetDataResponse.Success -> {
					val peerLastRead = response.data.peerLastReadMessageId ?: 0L
					_state.update { state ->
						state.copy(
							peerLastReadMessageId = maxOf(state.peerLastReadMessageId, peerLastRead)
						)
					}
				}

				is GetDataResponse.Error -> {
					// no-op: keep current metadata snapshot
				}
			}
		}
	}

	private fun stopOutgoingTyping(conversationId: Long) {
		typingController.stopOutgoing(conversationId)
	}

	private fun resolveConfirmedOutgoingOverlays(
		observedMessages: List<DirectChatMessage>
	) {
		if (observedMessages.isEmpty() || pendingOutgoingClientMessageIds.isEmpty()) return
		val conversationId = _state.value.conversationId ?: return
		val confirmedClientMessageIds = observedMessages
			.asSequence()
			.mapNotNull(DirectChatMessage::clientMessageId)
			.map(String::trim)
			.filter(String::isNotEmpty)
			.toSet()
		if (confirmedClientMessageIds.isEmpty()) return
		val resolvedOptimisticIds = pendingOutgoingClientMessageIds
			.filterValues { clientMessageId -> confirmedClientMessageIds.contains(clientMessageId) }
			.keys
			.toList()
		if (resolvedOptimisticIds.isEmpty()) return
		resolvedOptimisticIds.forEach { optimisticId ->
			removePendingOutgoing(conversationId, optimisticId)
		}
	}

	private fun syncPendingOutgoingFromMessages(
		observedMessages: List<DirectChatMessage>
	) {
		if (observedMessages.isEmpty()) {
			pendingOutgoingClientMessageIds.clear()
			return
		}
		val pending = observedMessages
			.asSequence()
			.filter { message ->
				message.id <= 0L && !message.clientMessageId.isNullOrBlank()
			}
			.associate { message -> message.id to message.clientMessageId!!.trim() }
		if (pending.isEmpty()) {
			pendingOutgoingClientMessageIds.clear()
			return
		}
		pendingOutgoingClientMessageIds.keys.retainAll(pending.keys)
		pending.forEach { (id, key) ->
			if (!pendingOutgoingClientMessageIds.containsKey(id)) {
				pendingOutgoingClientMessageIds[id] = key
			}
		}
	}


	private fun resolveOutgoingConfirmation(
		baseMessages: List<ChatMessage>,
		confirmedMessage: DirectChatMessage,
		peerUserId: String,
		conversationId: Long,
		optimisticMessageIdFallback: Long? = null,
	): List<ChatMessage> {
		val matchedOptimisticId = outgoingController.resolvePendingOptimisticId(confirmedMessage.clientMessageId)
			?: optimisticMessageIdFallback?.takeIf(outgoingController::isPendingOptimisticId)
		val optimisticMessage = matchedOptimisticId?.let { id -> baseMessages.firstOrNull { it.id == id } }
		val confirmedUiMessage = confirmedMessage.toUiMessage(
			peerUserId = peerUserId,
			selfUserId = selfUserId,
			uiKey = optimisticMessage?.uiKey,
		)
		if (matchedOptimisticId != null) {
			removePendingOutgoing(conversationId, matchedOptimisticId)
			return replaceOrMergeMessage(
				base = baseMessages,
				replaceId = matchedOptimisticId,
				confirmed = confirmedUiMessage,
			)
		}
		return mergeMessages(baseMessages, listOf(confirmedUiMessage))
	}

	private fun removePendingOutgoing(conversationId: Long, optimisticMessageId: Long) {
		pendingOutgoingClientMessageIds.remove(optimisticMessageId)
		if (conversationId <= 0L) return
		viewModelScope.launch {
			chatsRepository.deleteLocalMessage(
				conversationId = conversationId,
				messageId = optimisticMessageId,
			)
		}
	}

	override fun onCleared() {
		val conversationId = _state.value.conversationId
		if (!useMockData && conversationId != null && typingController.isOutgoingTypingActive()) {
			stopOutgoingTyping(conversationId)
		}
		outgoingController.cancelAllPendingDeletes()
		onScreenClosed()
		super.onCleared()
	}

	private suspend fun resolveConversationOrNull(): DirectChatConversation? {
		val state = _state.value
		val explicitConversationId = state.conversationId
		if (explicitConversationId != null && explicitConversationId > 0L) {
			val existingPeerId = state.chatInterlocutorId.trim()
			if (existingPeerId.isNotEmpty()) {
				return DirectChatConversation(
					id = explicitConversationId,
					kind = "direct",
					peer = me.floow.domain.models.DirectChatPeer(
						id = existingPeerId,
						username = null,
						name = state.chatInterlocutorName.takeIf { it.isNotBlank() },
						avatarUrl = state.chatInterlocutorAvatarUrl?.toString()
					),
					lastMessage = null,
					unreadCount = 0,
					lastReadMessageId = 0L,
					createdAt = 0L,
					updatedAt = 0L
				)
			}
			val resolvedConversation = when (val conversations = chatsRepository.getConversations(limit = 100, cursor = null)) {
				is GetDataResponse.Success -> conversations.data.items.firstOrNull { conversation ->
					conversation.id == explicitConversationId
				}

				is GetDataResponse.Error -> null
			}
			if (resolvedConversation != null) {
				return resolvedConversation
			}
			return DirectChatConversation(
				id = explicitConversationId,
				kind = "direct",
				peer = me.floow.domain.models.DirectChatPeer(
					id = "unknown",
					username = null,
					name = state.chatInterlocutorName.takeIf { it.isNotBlank() } ?: "Чат",
					avatarUrl = state.chatInterlocutorAvatarUrl?.toString()
				),
				lastMessage = null,
				unreadCount = 0,
				lastReadMessageId = 0L,
				createdAt = 0L,
				updatedAt = 0L
			)
		}

		val peerId = state.chatInterlocutorId.trim().takeIf(String::isNotEmpty) ?: return null
		return when (val response = chatsRepository.getOrCreateDirectConversation(peerUserId = peerId)) {
			is GetDataResponse.Success -> response.data
			is GetDataResponse.Error -> null
		}
	}

	private suspend fun ensureConversationReadyForSend(): DirectChatConversation? {
		val current = _state.value
		val explicitConversationId = current.conversationId
		if (explicitConversationId != null && explicitConversationId > 0L) {
			return currentConversationSnapshot(
				state = current,
				confirmedReadUpToMessageId = confirmedReadUpToMessageId
			) ?: resolveConversationOrNull()
		}

		val resolvedConversation = withTimeoutOrNull(CONVERSATION_RESOLVE_TIMEOUT_MS) {
			resolveConversationOrNull()
		} ?: return null

		localReadUpToMessageId = directMessagesReadCursorStore
			.getLocalLastReadMessageId(resolvedConversation.id)
			.coerceAtLeast(0L)
		val storedViewportSnapshot = directMessagesReadCursorStore
			.getOpenViewportSnapshot(resolvedConversation.id)
		currentOpenAnchorMessageId = storedViewportSnapshot.anchorMessageId
			?.coerceAtLeast(0L)
			?: 0L
		currentOpenAnchorOffsetPx = storedViewportSnapshot.anchorOffsetPx.coerceAtLeast(0)
		currentOpenAnchorBottomPinned = storedViewportSnapshot.isBottomPinned

		_state.update { state ->
			state.copy(
				conversationId = resolvedConversation.id,
				chatInterlocutorId = resolvedConversation.peer.id,
				chatInterlocutorName = resolvedConversation.displayPeerName(),
				chatInterlocutorAvatarUrl = resolvedConversation.peer.avatarUrl.toSafeUriOrNull(),
				isLoading = false,
				isError = false,
				messages = state.messages ?: emptyList()
			)
		}

		observeCachedMessages(resolvedConversation)
		val latestCachedId = latestPersistableMessageId(_state.value.messages) ?: 0L
		subscribeRealtime(resolvedConversation.id, latestCachedId)

		withTimeoutOrNull(INITIAL_CONVERSATION_SYNC_TIMEOUT_MS) {
			when (chatsRepository.getMessages(conversationId = resolvedConversation.id, limit = PAGE_SIZE)) {
				is GetDataResponse.Success -> {
					refreshPinnedMessages(resolvedConversation.id, resolvedConversation.peer.id)
				}

				is GetDataResponse.Error -> Unit
			}
		}

		return resolvedConversation
	}

		private fun loadMockData() {
			viewModelScope.launch {
				_state.update { it.copy(isLoading = true) }
				delay(300L)
				val messages = generateChatMessages()
				val groupedMessages = groupMessagesByDate(messages)
				_state.update { state ->
					state.copy(
						isLoading = false,
						isError = false,
						messages = groupedMessages,
						pinnedMessages = state.pinnedMessages,
						canLoadMore = false,
						nextBeforeId = null
					)
				}
			}
		}

		private fun nextOptimisticMessageId(): Long {
			val id = optimisticMessageIdSeed
			optimisticMessageIdSeed -= 1L
			return id
		}

	private fun buildOptimisticOutgoingMessage(
		id: Long,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String?
	): ChatMessage {
		val now = LocalDateTime.now()
		return if (replyToMessageId != null) {
			ReplyOutMessage(
				id = id,
				uiKey = "tmp_$id",
				replyMessageId = replyToMessageId,
					replyMessageText = replyToMessageText.orEmpty(),
					messageText = text,
					dateTime = now
				)
		} else {
			PrimaryOutMessage(
				id = id,
				uiKey = "tmp_$id",
				messageText = text,
					dateTime = now
				)
			}
		}

	private fun ChatMessage.copyWithDateTime(dateTime: LocalDateTime): ChatMessage {
		return when (this) {
			is PrimaryOutMessage -> copy(dateTime = dateTime)
			is ReplyOutMessage -> copy(dateTime = dateTime)
			is PrimaryInMessage -> copy(dateTime = dateTime)
			is ReplyInMessage -> copy(dateTime = dateTime)
			is PostPreviewMessage -> copy(dateTime = dateTime)
		}
	}

	private fun reportOpenMetricsIfNeeded(
		projection: DirectChatReadModelProjection
	) {
		val now = System.currentTimeMillis()
		val startedAt = chatOpenedAtMs.takeIf { it > 0L } ?: now
		val openLatencyMs = (now - startedAt).coerceAtLeast(0L)
		if (!hasReportedFirstFrameMetric && _state.value.messages.orEmpty().isNotEmpty()) {
			hasReportedFirstFrameMetric = true
			logger.d(DM_METRIC_OVERLAY_FIRST_FRAME, openLatencyMs.toString())
		}
		if (hasReportedAnchorMetric) return
		when {
			projection.openAnchorMessageId != null -> {
				hasReportedAnchorMetric = true
				logger.d(DM_METRIC_OPEN_TO_ANCHOR, openLatencyMs.toString())
			}
			projection.unreadMessageIds.isNotEmpty() -> {
				hasReportedAnchorMetric = true
				logger.d(DM_METRIC_ANCHOR_MISS_RATE, "1")
			}
		}
	}

	private fun buildOptimisticOutgoingDomainMessage(
		conversationId: Long,
		id: Long,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String?,
		idempotencyKey: String
	): DirectChatMessage {
		val nowMillis = System.currentTimeMillis()
		val optimisticSenderId = selfUserId ?: "self"
		return DirectChatMessage(
			id = id,
			conversationId = conversationId,
			sender = DirectChatPeer(
				id = optimisticSenderId,
				username = null,
				name = "You",
				avatarUrl = null
			),
			text = text,
			clientMessageId = idempotencyKey,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			isPinned = false,
			pinnedAt = null,
			pinnedByUserId = null,
			deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENDING,
			createdAt = nowMillis,
			updatedAt = nowMillis
		)
	}

	private suspend fun applyDirectChatRealtimeDecision(
		decision: DirectChatRealtimeDecision,
		event: DirectChatRealtimeEvent
	) {
		confirmedReadUpToMessageId = decision.cursorDecision.nextCursorState.readCursor
		lastHandledRealtimeResyncCursor = decision.cursorDecision.nextCursorState.lastHandledResyncCursor

		when (val mutation = decision.mutation) {
			is DirectChatTimelineMutation.Upsert -> {
				val conversationId = event.realtimeConversationId()
				if (shouldBufferRealtimeTimelineMutation("message_upsert", conversationId)) {
					return
				}
				val peerId = _state.value.chatInterlocutorId
					val mergedMessages = when (mutation.kind) {
						DirectChatUpsertKind.CREATED -> {
							val isOutgoing = selfUserId?.let { mutation.message.sender.id == it }
								?: (mutation.message.sender.id != peerId)
							val oldMessages = flattenMessages(_state.value.messages)
							if (isOutgoing) {
								resolveOutgoingConfirmation(
									baseMessages = oldMessages,
									confirmedMessage = mutation.message,
									peerUserId = peerId,
									conversationId = conversationId
								)
							} else {
								mergeMessages(
									oldMessages,
									listOf(
										mutation.message.toUiMessage(
											peerUserId = peerId,
											selfUserId = selfUserId
										)
									)
								)
							}
						}
						DirectChatUpsertKind.UPDATED,
						DirectChatUpsertKind.PINNED_UPDATED -> {
							mergeMessages(
								flattenMessages(_state.value.messages),
								listOf(
									mutation.message.toUiMessage(
										peerUserId = peerId,
										selfUserId = selfUserId
									)
								)
							)
						}
					}

				val firstUnreadOverride = if (canProcessVisibleReadSignals()) null else event.realtimeFirstUnreadId()
				val shouldHideUnreadBoundaryInActiveChat = canProcessVisibleReadSignals()
				_state.update { state ->
					val snapshot = buildStateSnapshot(
						conversationId = state.conversationId,
						peerUserId = peerId,
						peerName = state.chatInterlocutorName,
						peerAvatarUrl = state.chatInterlocutorAvatarUrl,
						messages = mergedMessages,
						serverReadUpToMessageId = confirmedReadUpToMessageId,
						localReadUpToMessageId = localReadUpToMessageId,
						openAnchorMessageId = resolveDurableOpenAnchor()?.messageId,
						openMode = openMode,
						messageLinkAnchorMessageId = messageLinkAnchorId,
						firstUnreadMessageIdOverride = firstUnreadOverride
					)
					state.copy(
						messages = snapshot?.groupedMessages ?: state.messages,
						pinnedMessages = state.pinnedMessages,
						unreadMessageIds = snapshot?.projection?.unreadMessageIds ?: state.unreadMessageIds,
						unreadBoundaryMessageId = if (shouldHideUnreadBoundaryInActiveChat) null else snapshot?.projection?.unreadBoundaryMessageId,
						highlightRequest = state.highlightRequest
					)
				}

				if (mutation.kind == DirectChatUpsertKind.PINNED_UPDATED) {
					refreshPinnedMessages(conversationId, _state.value.chatInterlocutorId)
				}

			}

			is DirectChatTimelineMutation.Delete -> {
				val conversationId = event.realtimeConversationId()
				if (shouldBufferRealtimeTimelineMutation("message_deleted", conversationId)) {
					return
				}
				val firstUnreadOverride = if (canProcessVisibleReadSignals()) null else event.realtimeFirstUnreadId()
				val shouldHideUnreadBoundaryInActiveChat = canProcessVisibleReadSignals()
				_state.update { state ->
					val removedState = removeMessageFromState(
						state = state,
						messageId = mutation.messageId,
						readUpToMessageId = maxOf(
							confirmedReadUpToMessageId,
							localReadUpToMessageId
						)
					)
					val snapshot = buildStateSnapshot(
						conversationId = removedState.conversationId,
						peerUserId = removedState.chatInterlocutorId,
						peerName = removedState.chatInterlocutorName,
						peerAvatarUrl = removedState.chatInterlocutorAvatarUrl,
						messages = flattenMessages(removedState.messages),
						serverReadUpToMessageId = confirmedReadUpToMessageId,
						localReadUpToMessageId = localReadUpToMessageId,
						openAnchorMessageId = resolveDurableOpenAnchor()?.messageId,
						openMode = openMode,
						messageLinkAnchorMessageId = messageLinkAnchorId,
						firstUnreadMessageIdOverride = firstUnreadOverride
					)
					removedState.copy(
						messages = snapshot?.groupedMessages ?: removedState.messages,
						pinnedMessages = removedState.pinnedMessages,
						unreadMessageIds = snapshot?.projection?.unreadMessageIds ?: removedState.unreadMessageIds,
						unreadBoundaryMessageId = if (shouldHideUnreadBoundaryInActiveChat) null else snapshot?.projection?.unreadBoundaryMessageId
					)
				}
			}

			DirectChatTimelineMutation.None -> Unit
		}
	}

	private fun DirectChatRealtimeEvent.realtimeConversationId(): Long {
		return when (this) {
			is DirectChatRealtimeEvent.Hello -> conversationId
			is DirectChatRealtimeEvent.MessageCreated -> conversationId
			is DirectChatRealtimeEvent.ReadUpToUpdated -> conversationId
			is DirectChatRealtimeEvent.MessageDeleted -> conversationId
			is DirectChatRealtimeEvent.MessageUpdated -> conversationId
			is DirectChatRealtimeEvent.MessagePinnedUpdated -> conversationId
			is DirectChatRealtimeEvent.Typing -> conversationId
			is DirectChatRealtimeEvent.ResyncRequired -> conversationId
		}
	}

	private fun DirectChatRealtimeEvent.realtimeFirstUnreadId(): Long? {
		return when (this) {
			is DirectChatRealtimeEvent.Hello -> firstUnreadId
			is DirectChatRealtimeEvent.MessageCreated -> firstUnreadId
			is DirectChatRealtimeEvent.ReadUpToUpdated -> firstUnreadId
			is DirectChatRealtimeEvent.MessageDeleted -> firstUnreadId
			is DirectChatRealtimeEvent.MessageUpdated -> firstUnreadId
			is DirectChatRealtimeEvent.MessagePinnedUpdated -> firstUnreadId
			is DirectChatRealtimeEvent.Typing -> firstUnreadId
			is DirectChatRealtimeEvent.ResyncRequired -> firstUnreadId
		}
	}


	private companion object {
		const val CHAT_ANCHOR_DEBUG_TAG = "FlowChatAnchorVM"
		const val PAGE_SIZE = 50
		const val MAX_INITIAL_ANCHOR_WARMUP_PAGES = 6
		const val LOCAL_ANCHOR_WINDOW_OLDER_LIMIT = 30
		const val LOCAL_ANCHOR_WINDOW_NEWER_LIMIT = 30
		const val PINNED_MESSAGES_LIMIT = 20
		const val HIGHLIGHT_CLEAR_DELAY_MS = 2500L
		const val HIGHLIGHT_JUMP_SETTLE_MARGIN_MS = 500L
		const val COLD_RESTORE_OFFSET_TOLERANCE_PX = 48
		const val CONVERSATION_RESOLVE_TIMEOUT_MS = 10_000L
		const val INITIAL_CONVERSATION_SYNC_TIMEOUT_MS = 12_000L
		const val OUTGOING_SEND_TIMEOUT_MS = 12_000L
		const val DM_METRIC_OPEN_TO_ANCHOR = "DirectMessagesMetrics.open_to_anchor_ms"
		const val DM_METRIC_ANCHOR_MISS_RATE = "DirectMessagesMetrics.anchor_miss_rate"
		const val DM_METRIC_OVERLAY_FIRST_FRAME = "DirectMessagesMetrics.overlay_first_frame_ms"
	}

	private fun logAnchor(message: String) {
		Log.d(CHAT_ANCHOR_DEBUG_TAG, message)
	}
}
