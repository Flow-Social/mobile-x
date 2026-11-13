package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatTypingState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ConversationActivityState
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.model.withSavedMessagesSemantics
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.shared.chats.uilogic.ConversationActivityStore
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.util.formatPresenceStatusLabel
import me.floow.domain.utils.Logger
import me.floow.shared.media.outbox.MediaOutboxEngine
import me.floow.shared.media.outbox.MediaOutboxStatus
import me.floow.shared.media.outbox.MediaOutboxTask
import kotlin.time.TimeMark
import kotlin.time.TimeSource

sealed interface DirectChatScreenState {
	data object Loading : DirectChatScreenState
	data class Error(val message: String) : DirectChatScreenState
	data class NoMessages(
		val conversationId: Long?,
		val header: ChatThreadHeaderModel,
		val input: String = "",
		val scrollToBottomRequestToken: Long = 0L,
		val peerIsOnline: Boolean = false,
		val peerLastSeenAtMillis: Long? = null,
		val conversationActivityState: ConversationActivityState = ConversationActivityState.None,
	) : DirectChatScreenState
	data class HasData(
		val conversationId: Long,
		val header: ChatThreadHeaderModel,
		val messages: List<ChatMessageItemModel>,
		val input: String = "",
		val canLoadMore: Boolean = false,
		val nextBeforeMessageId: Long? = null,
		val isLoadingMore: Boolean = false,
		val sending: Boolean = false,
		val currentReplyMessageId: Long? = null,
		val editingMessageId: Long? = null,
			val highlightedMessageId: Long? = null,
			val anchorRequestMessageId: Long? = null,
			val anchorRequestOffsetPx: Int = 0,
			val anchorRequestToken: Long = 0L,
			val keepAnchorRequest: Boolean = false,
			val scrollToBottomRequestToken: Long = 0L,
			val scrollToBottomBadgeCount: Int = 0,
		val peerLastReadMessageId: Long = 0L,
		val showUnreadIndicators: Boolean = true,
		val peerIsOnline: Boolean = false,
		val peerLastSeenAtMillis: Long? = null,
		val pinnedMessages: List<ChatMessageItemModel> = emptyList(),
		val typingUserNames: List<String> = emptyList(),
		val conversationActivityState: ConversationActivityState = ConversationActivityState.None,
	) : DirectChatScreenState
}

private fun ChatPresenceState.resolveIsOnline(): Boolean = isOnline

private fun ChatPresenceState.resolveLastSeenAtMillis(fallback: Long? = null): Long? {
	return lastSeenAtMillis ?: fallback
}

enum class DeleteMessageResult {
	IGNORED,
	DELETED_IMMEDIATELY,
	DELETED_WITH_UNDO,
}

class DirectChatStateHolder(
	private val repository: ChatThreadRepository,
	private val realtimeContract: ChatRealtimeContract? = null,
	private val presenceContract: ChatPresenceContract? = null,
	private val activityStore: ConversationActivityStore? = null,
	private val sessionCache: SharedChatSessionCache? = null,
	private val logger: Logger? = null,
	private val mediaOutboxEngine: MediaOutboxEngine? = null,
	private val mediaDownloadController: me.floow.domain.media.MediaDownloadController? = null,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
	sealed interface Event {
		data class ShowMessage(val message: String) : Event
		data class JumpToMessage(val messageId: Long) : Event
	}

	private val _state = MutableStateFlow<DirectChatScreenState>(DirectChatScreenState.Loading)
	val state: StateFlow<DirectChatScreenState> = _state.asStateFlow()

	private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
	val events: SharedFlow<Event> = _events.asSharedFlow()

	val mediaOutboxTasks: StateFlow<Map<String, MediaOutboxTask>> = mediaOutboxEngine?.tasks ?: MutableStateFlow(emptyMap())
	val mediaDownloadStates: StateFlow<Map<String, me.floow.domain.media.MediaDownloadState>> =
		mediaDownloadController?.states ?: MutableStateFlow(emptyMap())

	init {
		mediaOutboxEngine?.tasks?.let { tasksFlow ->
			scope.launch {
				tasksFlow.collect { tasks ->
					onMediaOutboxTasksChanged(tasks)
				}
			}
		}
	}

	fun downloadImage(url: String) {
		mediaDownloadController?.enqueue(url, me.floow.domain.media.DownloadableMediaType.PHOTO, me.floow.domain.media.MediaDownloadPriority.VISIBLE)
	}

	fun cancelImageDownload(url: String) {
		mediaDownloadController?.cancel(url)
	}

	private fun seedMediaDownloadStates(messages: List<ChatMessageItemModel>) {
		val controller = mediaDownloadController ?: return
		for (msg in messages) {
			val img = msg.content as? ChatMessageContent.Image ?: continue
			val url = img.remoteUrl?.trim()?.takeIf(String::isNotEmpty) ?: continue
			if (img.localPath != null) {
				controller.registerLocalFile(url, img.localPath, me.floow.domain.media.DownloadableMediaType.PHOTO)
			} else {
				val state = controller.getDownloadState(url)
				if (state is me.floow.domain.media.MediaDownloadState.Idle) {
					controller.enqueueAutoDownload(
						url = url,
						mediaType = me.floow.domain.media.DownloadableMediaType.PHOTO,
						sizeBytes = img.sizeBytes ?: 0L,
					)
				}
			}
		}
	}

	private fun onMediaOutboxTasksChanged(tasks: Map<String, MediaOutboxTask>) {
		if (tasks.isEmpty()) return
		_state.update { state ->
			val current = state as? DirectChatScreenState.HasData ?: return@update state
			var hasChanges = false
			val updatedMessages = current.messages.map { msg ->
				val cmid = msg.clientMessageId ?: return@map msg
				val task = tasks[cmid] ?: return@map msg
				when (task.status) {
					MediaOutboxStatus.Sent -> {
						if (msg.deliveryState != ChatDeliveryState.SENT) {
							hasChanges = true
							val updatedContent = when (val c = msg.content) {
								is ChatMessageContent.Image -> c.copy(
									uploadState = VideoUploadState.Uploaded,
									remoteUrl = task.remoteMediaUrl ?: c.remoteUrl,
								)
								is ChatMessageContent.Voice -> c.copy(
									uploadState = VideoUploadState.Uploaded,
									remoteUrl = task.remoteMediaUrl ?: c.remoteUrl,
								)
								is ChatMessageContent.VideoCircle -> c.copy(
									uploadState = VideoUploadState.Uploaded,
									remoteUrl = task.remoteMediaUrl ?: c.remoteUrl,
								)
								else -> c
							}
							msg.copy(
								deliveryState = ChatDeliveryState.SENT,
								content = updatedContent,
							)
						} else msg
					}
					MediaOutboxStatus.Failed -> {
						if (msg.deliveryState != ChatDeliveryState.FAILED) {
							hasChanges = true
							val updatedContent = when (val c = msg.content) {
								is ChatMessageContent.Image -> c.copy(uploadState = VideoUploadState.Failed)
								is ChatMessageContent.Voice -> c.copy(uploadState = VideoUploadState.Failed)
								is ChatMessageContent.VideoCircle -> c.copy(uploadState = VideoUploadState.Failed)
								else -> c
							}
							msg.copy(
								deliveryState = ChatDeliveryState.FAILED,
								content = updatedContent,
							)
						} else msg
					}
					else -> msg
				}
			}
			if (hasChanges) {
				current.copy(messages = updatedMessages)
			} else {
				state
			}
		}
	}

	private companion object {
		const val LOAD_TIMEOUT_MS = 15_000L
		const val TYPING_THROTTLE_MS = 3_000L
		const val TYPING_IDLE_STOP_MS = 1_200L
		const val MIN_INCOMING_TYPING_TTL_MS = 800L
		const val UNDO_DELETE_TIMEOUT_MS = 4_000L
		const val MAX_CANCELLED_CLIENT_MESSAGE_IDS = 64
		const val LOCAL_ONLY_CONVERSATION_ID = -1L
		const val READ_DEBOUNCE_DELAY_MS = 3_000L
	}

	private fun isOutgoingMessage(senderUserId: String?, header: ChatThreadHeaderModel): Boolean {
		val normalizedSenderUserId = senderUserId?.trim()?.takeIf(String::isNotEmpty) ?: return false
		if (normalizedSenderUserId == "self") return true
		val normalizedSelfUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty)
		return normalizedSelfUserId?.let { normalizedSenderUserId == it }
			?: (normalizedSenderUserId != header.peerUserId)
	}

	private fun normalizeRealtimeMessage(
		message: ChatMessageItemModel,
		header: ChatThreadHeaderModel,
		peerLastReadMessageId: Long?,
	): ChatMessageItemModel {
		val isOutgoing = isOutgoingMessage(message.senderUserId, header)
		val deliveryState = when {
			!isOutgoing -> null
			peerLastReadMessageId != null && peerLastReadMessageId >= message.id -> ChatDeliveryState.READ
			else -> ChatDeliveryState.SENT
		}
		return message.copy(
			isOutgoing = isOutgoing,
			deliveryState = deliveryState,
		)
	}

	private fun applyPeerReadState(
		messages: List<ChatMessageItemModel>,
		peerLastReadMessageId: Long?,
	): List<ChatMessageItemModel> {
		val normalizedPeerLastReadMessageId = peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L
		return messages.map { message ->
			if (!message.isOutgoing || message.id <= 0L) {
				message
			} else {
				val deliveryState = when (message.deliveryState) {
					ChatDeliveryState.SENDING,
					ChatDeliveryState.FAILED -> message.deliveryState
					ChatDeliveryState.READ,
					ChatDeliveryState.SENT,
					null -> if (normalizedPeerLastReadMessageId >= message.id) {
						ChatDeliveryState.READ
					} else {
						ChatDeliveryState.SENT
					}
				}
				message.copy(deliveryState = deliveryState)
			}
		}
	}

	private fun normalizePinnedMessages(
		messages: List<ChatMessageItemModel>,
		peerLastReadMessageId: Long?,
	): List<ChatMessageItemModel> {
		return applyPeerReadState(messages, peerLastReadMessageId)
			.filter(ChatMessageItemModel::isPinned)
			.distinctBy { message -> message.id.takeIf { it > 0L } ?: message.clientMessageId.orEmpty() }
			.sortedWith(
				compareByDescending<ChatMessageItemModel> { it.pinnedAtMillis ?: 0L }
					.thenByDescending { it.createdAtMillis }
					.thenByDescending { it.id }
			)
	}

	private fun mergePinnedFlagsIntoTimeline(
		timeline: List<ChatMessageItemModel>,
		pinnedMessages: List<ChatMessageItemModel>,
	): List<ChatMessageItemModel> {
		val pinnedById = pinnedMessages
			.filter { it.id > 0L }
			.associateBy(ChatMessageItemModel::id)
		return timeline.map { message ->
			val pinned = pinnedById[message.id]
			when {
				pinned != null -> message.copy(isPinned = true, pinnedAtMillis = pinned.pinnedAtMillis)
				message.isPinned -> message.copy(isPinned = false, pinnedAtMillis = null)
				else -> message
			}
		}
	}




	private var loadJob: Job? = null
	private var remoteRefreshRetryJob: Job? = null
	private val typingCoordinator = me.floow.shared.chats.uilogic.direct.coordinator.ChatTypingCoordinator(repository, scope)
	private val readTracker = me.floow.shared.chats.uilogic.direct.coordinator.ChatReadTracker(repository, scope)
	private val chatActions = me.floow.shared.chats.uilogic.direct.coordinator.ChatActions(repository, scope) { message ->
		_events.tryEmit(Event.ShowMessage(message))
	}
	private val chatLoader = me.floow.shared.chats.uilogic.direct.coordinator.ChatLoader(repository, sessionCache, logger)
	private val realtimeCoordinator = me.floow.shared.chats.uilogic.direct.coordinator.ChatRealtimeCoordinator(
		repository = repository,
		realtimeContract = realtimeContract,
		presenceContract = presenceContract,
		scope = scope,
	)
	private var outgoingRecordingJob: Job? = null
	private var latestPresenceState: ChatPresenceState = ChatPresenceState()
	private var hasObservedPresenceState: Boolean = false
	private var latestTypingState: ChatTypingState = ChatTypingState()
	private var localRecordingStartedAtMillis: Long? = null
		private var latestViewportSnapshot: ChatViewportSnapshot? = null
		private var anchorRequestTokenSeed: Long = 0L
	private var optimisticMessageIdSeed: Long = -1L
	private val pendingOutgoingClientMessageIds = mutableMapOf<Long, String>()
	private val inFlightEdits = mutableMapOf<Long, String>()
    private val pendingOutgoingSendJobs = mutableMapOf<Long, Job>()
    private val cancelledClientMessageIds = LinkedHashSet<String>()
    private val localVideoCircleMessages = LinkedHashMap<String, ChatMessageItemModel>()
    private val localVoiceMessages = LinkedHashMap<String, ChatMessageItemModel>()
    private val localImageMessages = LinkedHashMap<String, ChatMessageItemModel>()
	private var pendingAnchorDiagnostics: AnchorDiagnostics? = null

	private data class AnchorDiagnostics(
		val conversationId: Long,
		val anchorMessageId: Long,
		val requestedOffsetPx: Int,
	)

		private fun nextScrollRequestToken(): Long = currentChatEpochMillis()
		private fun nextAnchorRequestToken(): Long = ++anchorRequestTokenSeed
	private fun nextOptimisticMessageId(): Long = optimisticMessageIdSeed--
	private fun nextClientMessageId(optimisticMessageId: Long): String =
		"web-${currentChatEpochMillis()}-${kotlin.math.abs(optimisticMessageId)}"

	private fun addCancelledClientMessageId(clientMessageId: String) {
		if (cancelledClientMessageIds.size >= MAX_CANCELLED_CLIENT_MESSAGE_IDS) {
			val oldest = cancelledClientMessageIds.iterator().next()
			cancelledClientMessageIds.remove(oldest)
		}
		cancelledClientMessageIds.add(clientMessageId)
	}

	private fun removeCancelledClientMessageId(clientMessageId: String) {
		cancelledClientMessageIds.remove(clientMessageId)
	}

	private fun isCancelledClientMessageId(clientMessageId: String?): Boolean {
		val normalized = clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return false
		return normalized in cancelledClientMessageIds
	}

	fun dispose() {
		disposeActiveWork(clearLocalVideoCircles = true)
	}

	private fun disposeActiveWork(clearLocalVideoCircles: Boolean) {
			flushReadAndOpenAnchorOnClose()
		loadJob?.cancel()
		loadJob = null
		remoteRefreshRetryJob?.cancel()
		remoteRefreshRetryJob = null
		realtimeCoordinator.stopAll()
		outgoingRecordingJob?.cancel()
		outgoingRecordingJob = null
		typingCoordinator.reset()
		latestPresenceState = ChatPresenceState()
		hasObservedPresenceState = false
		latestTypingState = ChatTypingState()
		clearRecordingActivityForCurrentConversation()
		localRecordingStartedAtMillis = null
		latestViewportSnapshot = null
		readTracker.reset()
		if (clearLocalVideoCircles) {
			localVideoCircleMessages.clear()
			localVoiceMessages.clear()
			localImageMessages.clear()
		}
		inFlightEdits.clear()
		flushPendingDeleteNow()
	}

	var activeRequest: DirectChatInitialRequest? = null
		private set

	fun load(request: DirectChatInitialRequest) {
		activeRequest = request
		disposeActiveWork(clearLocalVideoCircles = false)
		val instantCached = chatLoader.getInstantCached(request)
		if (instantCached != null) {
			_state.value = snapshotToScreenState(
				snapshot = instantCached,
				showUnreadIndicators = true,
			)
			publishLocalActivityFromCurrentState()
			observeRealtime(instantCached.conversationId)
			observeLocalMessages(instantCached.conversationId)
			observePinnedMessages(instantCached.conversationId)
			observePresence(instantCached.header)
		} else {
			_state.value = DirectChatScreenState.Loading
		}
		loadJob = scope.launch {
			val cachedSnapshot = instantCached ?: chatLoader.loadCachedInitial(request)
			if (cachedSnapshot != null) {
				if (instantCached == null) {
					_state.value = snapshotToScreenState(
						snapshot = cachedSnapshot,
						showUnreadIndicators = true,
					)
					publishLocalActivityFromCurrentState()
					observeRealtime(cachedSnapshot.conversationId)
					observeLocalMessages(cachedSnapshot.conversationId)
					observePinnedMessages(cachedSnapshot.conversationId)
					observePresence(cachedSnapshot.header)
				}
			} else if (instantCached == null) {
				_state.value = DirectChatScreenState.Loading
			}

			chatLoader.loadInitialRemote(request)
				.onSuccess { snapshot ->
					_state.update { current ->
						val loadedState = snapshotToScreenState(
							snapshot = snapshot,
							showUnreadIndicators = true,
						)
						if (cachedSnapshot != null) {
							mergeFreshSnapshotIntoCurrent(current, loadedState)
						} else {
							loadedState
						}
					}
					publishLocalActivityFromCurrentState()
					snapshot.conversationId?.takeIf { it > 0L }?.let { conversationId ->
						readTracker.initializeLastMarkedRead(repository.getLocalLastReadMessageId(conversationId))
						flushPendingLocalVideoOutbox(conversationId, snapshot.header)
					}
					markLatestVisibleMessagesReadIfAppropriate()
					observeRealtime(snapshot.conversationId)
					observeLocalMessages(snapshot.conversationId)
					observePinnedMessages(snapshot.conversationId)
					observePresence(snapshot.header)
				}
				.onFailure {
					val message = if (it is kotlinx.coroutines.TimeoutCancellationException) {
						"chat load timed out"
					} else {
						it.message ?: "chat load failed"
					}
					logger?.d(
						tag = "DirectChatStateHolder",
						message = "load failed conversationId=${request.conversationId} peer=${request.peerUserId} cached=${cachedSnapshot != null} reason=$message"
					)
					if (cachedSnapshot == null) {
						_state.value = DirectChatScreenState.Error(message)
					} else {
						scheduleSilentRemoteRefreshRetry(request)
					}
				}
		}
	}

	private fun scheduleSilentRemoteRefreshRetry(
		request: DirectChatInitialRequest,
		delayMs: Long = 2_000L,
	) {
		remoteRefreshRetryJob?.cancel()
		remoteRefreshRetryJob = scope.launch {
			delay(delayMs)
			val snapshot = chatLoader.loadInitialRemote(request).getOrNull() ?: return@launch
			_state.update { current ->
				val loadedState = snapshotToScreenState(
					snapshot = snapshot,
					showUnreadIndicators = true,
				)
				mergeFreshSnapshotIntoCurrent(current, loadedState)
			}
			snapshot.conversationId?.takeIf { it > 0L }?.let { conversationId ->
				readTracker.initializeLastMarkedRead(repository.getLocalLastReadMessageId(conversationId))
			}
			markLatestVisibleMessagesReadIfAppropriate()
			observeRealtime(snapshot.conversationId)
			observeLocalMessages(snapshot.conversationId)
			observePinnedMessages(snapshot.conversationId)
			observePresence(snapshot.header)
		}
	}

	private fun markLatestVisibleMessagesReadIfAppropriate() {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		if (current.keepAnchorRequest) return
		current.messages
			.lastOrNull { message ->
				!message.isOutgoing &&
					!message.isDeleted &&
					message.id > 0L
			}
			?.id
			?.let { messageId ->
				markReadUpToIfNeeded(current.conversationId, messageId)
			}
	}

	private fun mergeFreshSnapshotIntoCurrent(
		current: DirectChatScreenState,
		loadedState: DirectChatScreenState,
	): DirectChatScreenState {
		if (current !is DirectChatScreenState.HasData || loadedState !is DirectChatScreenState.HasData) {
			return loadedState
		}
		return loadedState.copy(
			messages = current.messages
				.filter(::isLocalOutgoingPending)
				.fold(loadedState.messages) { messages, pending -> upsertMessage(messages, pending) },
			pinnedMessages = loadedState.pinnedMessages.ifEmpty { current.pinnedMessages },
			input = current.input,
			currentReplyMessageId = current.currentReplyMessageId,
			editingMessageId = current.editingMessageId,
			highlightedMessageId = current.highlightedMessageId ?: loadedState.highlightedMessageId,
			anchorRequestMessageId = current.anchorRequestMessageId,
			anchorRequestOffsetPx = current.anchorRequestOffsetPx,
			anchorRequestToken = current.anchorRequestToken,
			keepAnchorRequest = current.keepAnchorRequest,
			scrollToBottomRequestToken = current.scrollToBottomRequestToken,
			scrollToBottomBadgeCount = current.scrollToBottomBadgeCount,
			typingUserNames = current.typingUserNames,
		)
	}

	private fun isLocalOutgoingPending(message: ChatMessageItemModel): Boolean {
		return message.isOutgoing &&
			!message.clientMessageId.isNullOrBlank() &&
			message.id <= 0L &&
			(message.deliveryState == ChatDeliveryState.SENDING || message.deliveryState == ChatDeliveryState.FAILED)
	}

	private fun snapshotToScreenState(
		snapshot: me.floow.shared.chats.model.ChatThreadSnapshot,
		showUnreadIndicators: Boolean,
	): DirectChatScreenState {
		val isSavedMessages = snapshot.header.isSavedMessages
		val latestServerMessageId = snapshot.messages.maxOfOrNull { it.id.takeIf { id -> id > 0L } ?: 0L } ?: 0L
		val peerLastReadMessageId = if (isSavedMessages) {
			maxOf(snapshot.peerLastReadMessageId ?: 0L, latestServerMessageId)
		} else {
			snapshot.peerLastReadMessageId ?: 0L
		}
		val resolvedShowUnreadIndicators = showUnreadIndicators && !isSavedMessages
		val normalizedMessages = applyPeerReadState(
			mergeLocalVideoCircleMessages(snapshot.messages),
			peerLastReadMessageId,
		)
		seedMediaDownloadStates(normalizedMessages)
		val normalizedPinnedMessages = normalizePinnedMessages(
			messages = snapshot.pinnedMessages,
			peerLastReadMessageId = peerLastReadMessageId,
		)
		val resolvedPeerIsOnline = latestPresenceState.resolveIsOnline()
		val resolvedPeerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis()
		val restoredInput = restoredDraftInput(snapshot.conversationId, snapshot.header)
		pendingAnchorDiagnostics = snapshot.conversationId
			?.takeIf { it > 0L }
			?.let { conversationId ->
				snapshot.openAnchorMessageId
					?.takeIf { it > 0L }
					?.let { anchorMessageId ->
						AnchorDiagnostics(
							conversationId = conversationId,
							anchorMessageId = anchorMessageId,
							requestedOffsetPx = snapshot.openAnchorOffsetPx,
						)
					}
			}
		return if (normalizedMessages.isEmpty()) {
			DirectChatScreenState.NoMessages(
				conversationId = snapshot.conversationId,
				header = mergeLiveHeader(snapshot.header),
				input = restoredInput,
				scrollToBottomRequestToken = 0L,
				peerIsOnline = resolvedPeerIsOnline,
				peerLastSeenAtMillis = resolvedPeerLastSeenAtMillis,
			)
		} else if (snapshot.conversationId == null) {
			if (snapshot.messages.isEmpty()) {
				DirectChatScreenState.HasData(
					conversationId = LOCAL_ONLY_CONVERSATION_ID,
					header = mergeLiveHeader(snapshot.header),
					messages = normalizedMessages,
					input = restoredInput,
					pinnedMessages = normalizedPinnedMessages,
					canLoadMore = snapshot.canLoadMore,
					nextBeforeMessageId = snapshot.nextBeforeMessageId,
					highlightedMessageId = snapshot.highlightedMessageId,
					anchorRequestMessageId = snapshot.openAnchorMessageId,
					anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
					anchorRequestToken = if (snapshot.openAnchorMessageId != null) nextAnchorRequestToken() else 0L,
					keepAnchorRequest = snapshot.keepOpenAnchorLocked,
					scrollToBottomBadgeCount = 0,
					peerLastReadMessageId = peerLastReadMessageId,
					showUnreadIndicators = resolvedShowUnreadIndicators,
					peerIsOnline = resolvedPeerIsOnline,
					peerLastSeenAtMillis = resolvedPeerLastSeenAtMillis,
				)
			} else {
				DirectChatScreenState.Error("chat conversation id is missing")
			}
		} else {
			DirectChatScreenState.HasData(
				conversationId = snapshot.conversationId,
				header = mergeLiveHeader(snapshot.header),
				messages = normalizedMessages,
				input = restoredInput,
				pinnedMessages = normalizedPinnedMessages,
				canLoadMore = snapshot.canLoadMore,
				nextBeforeMessageId = snapshot.nextBeforeMessageId,
				highlightedMessageId = snapshot.highlightedMessageId,
				anchorRequestMessageId = snapshot.openAnchorMessageId,
				anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
				anchorRequestToken = if (snapshot.openAnchorMessageId != null) nextAnchorRequestToken() else 0L,
				keepAnchorRequest = snapshot.keepOpenAnchorLocked,
				scrollToBottomBadgeCount = 0,
				peerLastReadMessageId = peerLastReadMessageId,
				showUnreadIndicators = resolvedShowUnreadIndicators,
				peerIsOnline = resolvedPeerIsOnline,
				peerLastSeenAtMillis = resolvedPeerLastSeenAtMillis,
			)
		}
	}

	fun updateInput(text: String) {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					input = text,
					conversationActivityState = resolveConversationActivityState(
						header = current.header,
						messages = current.messages,
						input = text,
					),
				)
				is DirectChatScreenState.NoMessages -> current.copy(
					input = text,
					conversationActivityState = resolveConversationActivityState(
						header = current.header,
						messages = emptyList(),
						input = text,
					),
				)
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
		scheduleOutgoingTyping(text)
	}

	fun setLocalRecordingActive(isActive: Boolean) {
		val nextStartedAt = if (isActive) {
			localRecordingStartedAtMillis ?: currentChatEpochMillis()
		} else {
			null
		}
		if (localRecordingStartedAtMillis == nextStartedAt) return
		localRecordingStartedAtMillis = nextStartedAt
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					conversationActivityState = resolveConversationActivityState(
						header = current.header,
						messages = current.messages,
						input = current.input,
					),
				)
				is DirectChatScreenState.NoMessages -> current.copy(
					conversationActivityState = resolveConversationActivityState(
						header = current.header,
						messages = emptyList(),
						input = current.input,
					),
				)
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
		val conversationId = (_state.value as? DirectChatScreenState.HasData)?.conversationId
			?: (_state.value as? DirectChatScreenState.NoMessages)?.conversationId
		if (conversationId != null && conversationId > 0L) {
			outgoingRecordingJob?.cancel()
			if (isActive) {
				outgoingRecordingJob = scope.launch {
					while (isActive) {
						repository.setTyping(conversationId, true, "recording_video")
						delay(2000L)
					}
				}
			} else {
				outgoingRecordingJob = null
				scope.launch {
					repository.setTyping(conversationId, false, "recording_video")
				}
			}
		}
	}

	fun addReply(messageId: Long) {
		if (messageId <= 0L) return
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(currentReplyMessageId = messageId)
				else -> current
			}
		}
	}

	fun clearReply() {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(currentReplyMessageId = null)
				else -> current
			}
		}
	}

	fun startEditing(messageId: Long) {
		if (messageId <= 0L) return
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> {
					val message = current.messages.firstOrNull { it.id == messageId }
					current.copy(editingMessageId = messageId, input = message?.text ?: current.input)
				}
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
	}

	fun cancelEditing() {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(editingMessageId = null, input = "")
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
	}

	fun requestScrollToBottom() {
		val requestToken = nextScrollRequestToken()
		readTracker.onBottomReached { badgeCount -> updateScrollBadgeCount(badgeCount) }
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					scrollToBottomRequestToken = requestToken,
					scrollToBottomBadgeCount = 0,
				)
				is DirectChatScreenState.NoMessages -> current.copy(scrollToBottomRequestToken = requestToken)
				else -> current
			}
		}
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		current.messages.lastOrNull()?.id
			?.takeIf { it > 0L }
			?.let { messageId -> markReadUpToIfNeeded(current.conversationId, messageId) }
	}

	fun onUserStartedScroll() {
		latestViewportSnapshot = latestViewportSnapshot?.copy(isAtBottom = false)
	}

	fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
		latestViewportSnapshot = snapshot
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		val anchorRestoreActive = current.keepAnchorRequest &&
			current.anchorRequestMessageId?.takeIf { it > 0L } != null
		pendingAnchorDiagnostics
			?.takeIf { diagnostics ->
				diagnostics.conversationId == current.conversationId &&
					current.keepAnchorRequest &&
					current.anchorRequestMessageId == diagnostics.anchorMessageId
			}
			?.let { diagnostics ->
				logger?.d(
					"DirectChatStateHolder.anchor",
					"anchor_first_frame conversation_id=${diagnostics.conversationId} requested_message_id=${diagnostics.anchorMessageId} requested_offset_px=${diagnostics.requestedOffsetPx} first_visible_message_id=${snapshot.firstVisibleMessageId} first_visible_offset_px=${snapshot.firstVisibleOffsetPx}"
				)
				pendingAnchorDiagnostics = null
			}
		readTracker.onViewportSnapshotChanged(
			snapshot = snapshot,
			conversationId = current.conversationId,
			keepAnchorRequest = current.keepAnchorRequest,
			onBadgeCountChanged = { badgeCount ->
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> state.copy(scrollToBottomBadgeCount = badgeCount)
						else -> state
					}
				}
			}
		)
	}

	fun onScrollSettled() {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		readTracker.onScrollSettled(current.conversationId)
	}

	fun onAnchorRestoreSettled(messageId: Long, offsetPx: Int) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		if (current.conversationId <= 0L || messageId <= 0L) return
		val settledSnapshot = DirectChatOpenViewportSnapshot(
			anchorMessageId = messageId,
			anchorOffsetPx = offsetPx,
			isBottomPinned = false,
		)
		readTracker.pendingOpenViewportSnapshot = settledSnapshot
		scope.launch {
			repository.setOpenViewportSnapshot(current.conversationId, settledSnapshot)
		}
		logger?.d(
			"DirectChatStateHolder.anchor",
			"anchor_settled conversation_id=${current.conversationId} message_id=$messageId settled_offset_px=$offsetPx"
		)
		clearAnchorRestoreLock(messageId)
	}

	fun onAnchorRestoreTimedOut(messageId: Long) {
		clearAnchorRestoreLock(messageId)
	}

	fun retryMediaMessage(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		val current = _state.value as? DirectChatScreenState.HasData ?: return false
		val targetMessage = current.messages.firstOrNull { it.clientMessageId == normalizedClientMessageId }
			?: localImageMessages[normalizedClientMessageId]
			?: localVoiceMessages[normalizedClientMessageId]
			?: localVideoCircleMessages[normalizedClientMessageId]
			?: return false

		val activeConversationId = current.conversationId.takeIf { it > 0L } ?: return false

		when (val content = targetMessage.content) {
			is ChatMessageContent.Image -> {
				markLocalImageUploading(normalizedClientMessageId)
				val outboxTask = mediaOutboxEngine?.tasks?.value?.get(normalizedClientMessageId)
				if (outboxTask != null) {
					mediaOutboxEngine.retry(normalizedClientMessageId)
				} else {
					val localPath = content.localPath ?: return false
					mediaOutboxEngine?.enqueue(
						clientMessageId = normalizedClientMessageId,
						conversationId = activeConversationId,
						filePath = localPath,
						mimeType = "image/jpeg",
						width = content.width,
						height = content.height,
						caption = targetMessage.text.takeIf(String::isNotBlank),
						groupId = content.groupId,
						position = content.position,
						replyToMessageId = targetMessage.replyToMessageId,
					)
				}
				return true
			}
			is ChatMessageContent.Voice -> {
				markLocalVoiceUploading(normalizedClientMessageId)
				val outboxTask = mediaOutboxEngine?.tasks?.value?.get(normalizedClientMessageId)
				if (outboxTask != null) {
					mediaOutboxEngine.retry(normalizedClientMessageId)
				} else {
					val localPath = content.localPath ?: return false
					mediaOutboxEngine?.enqueue(
						clientMessageId = normalizedClientMessageId,
						conversationId = activeConversationId,
						filePath = localPath,
						mimeType = "audio/m4a",
						durationMs = content.durationMs,
						waveform = content.waveform ?: emptyList(),
						replyToMessageId = targetMessage.replyToMessageId,
					)
				}
				return true
			}
			is ChatMessageContent.VideoCircle -> {
				markLocalVideoCircleUploading(normalizedClientMessageId)
				val outboxTask = mediaOutboxEngine?.tasks?.value?.get(normalizedClientMessageId)
				if (outboxTask != null) {
					mediaOutboxEngine.retry(normalizedClientMessageId)
				} else {
					val localPath = content.localPath ?: return false
					mediaOutboxEngine?.enqueue(
						clientMessageId = normalizedClientMessageId,
						conversationId = activeConversationId,
						filePath = localPath,
						mimeType = "video/mp4",
						durationMs = content.durationMs,
						width = content.width,
						height = content.height,
						replyToMessageId = targetMessage.replyToMessageId,
					)
				}
				return true
			}
			else -> return false
		}
	}

	fun retryMessage(messageId: Long) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		val message = current.messages.firstOrNull { it.id == messageId } ?: return
		if (message.deliveryState != ChatDeliveryState.FAILED || !message.isOutgoing) return
		val clientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		if (clientMessageId != null && (message.content is ChatMessageContent.Image || message.content is ChatMessageContent.Voice || message.content is ChatMessageContent.VideoCircle)) {
			retryMediaMessage(clientMessageId)
			return
		}
		sendOptimisticMessage(
			conversationId = current.conversationId,
			header = current.header,
			text = message.text,
			replyToMessageId = message.replyToMessageId,
			replyToMessageText = message.replyToMessageText,
			existingMessageId = message.id,
			clientMessageId = message.clientMessageId,
		)
	}

	fun sendOrEdit() {
		val current = _state.value
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
			else -> return
		} ?: run {
			_events.tryEmit(Event.ShowMessage("chat conversation id is missing"))
			return
		}
		val text = when (current) {
			is DirectChatScreenState.HasData -> current.input.trim()
			is DirectChatScreenState.NoMessages -> current.input.trim()
		}
		val currentHasData = current as? DirectChatScreenState.HasData
		val editingMessageId = currentHasData?.editingMessageId
		val originalEditingMessage = currentHasData
			?.messages
			?.firstOrNull { it.id == editingMessageId }
		val currentReplyMessageId = currentHasData?.currentReplyMessageId
		val currentReplyMessageText = currentHasData
			?.messages
			?.firstOrNull { it.id == currentReplyMessageId }
			?.let { msg ->
				when {
					msg.text.isNotBlank() -> msg.text
					msg.content is ChatMessageContent.Image -> if (msg.content.groupId != null) "Альбом" else "Фотография"
					msg.content is ChatMessageContent.Voice -> "Голосовое сообщение"
					msg.content is ChatMessageContent.VideoCircle -> "Видеосообщение"
					msg.content is ChatMessageContent.PostPreview -> "Запись на стене"
					else -> "Сообщение"
				}
			}
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
		}
		if (text.isEmpty()) return

		if (editingMessageId == null) {
			sendOptimisticMessage(
				conversationId = conversationId,
				header = header,
				text = text,
				replyToMessageId = currentReplyMessageId,
				replyToMessageText = currentReplyMessageText,
			)
			return
		}
		if (originalEditingMessage == null) {
			_events.tryEmit(Event.ShowMessage("message action failed"))
			return
		}

		stopOutgoingTyping(conversationId)
		inFlightEdits[editingMessageId] = text
		val optimisticEditedMessage = originalEditingMessage.copy(text = text)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = state.messages.map { existing ->
						if (existing.id == editingMessageId) optimisticEditedMessage else existing
					}
					state.copy(
						messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
						input = "",
						sending = true,
						currentReplyMessageId = null,
						editingMessageId = null,
					)
				}
				else -> state
			}
		}
		publishLocalActivityFromCurrentState()
		scope.launch {
			val result = repository.editMessage(conversationId, editingMessageId, text)

			result.onSuccess { message ->
				inFlightEdits.remove(editingMessageId)
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val updatedMessages = state.messages.map { existing -> if (existing.id == message.id) message else existing }
							state.copy(
								messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
								sending = false,
								scrollToBottomRequestToken = nextScrollRequestToken(),
							)
						}
						is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
							conversationId = conversationId,
							header = header,
							messages = listOf(message),
							scrollToBottomRequestToken = nextScrollRequestToken(),
							peerIsOnline = state.peerIsOnline,
							peerLastSeenAtMillis = state.peerLastSeenAtMillis,
						)
						else -> state
					}
				}
				publishLocalActivityFromCurrentState()
			}.onFailure {
				inFlightEdits.remove(editingMessageId)
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val rolledBackMessages = state.messages.map { existing ->
								if (existing.id == originalEditingMessage.id) originalEditingMessage else existing
							}
							state.copy(
								messages = applyPeerReadState(rolledBackMessages, state.peerLastReadMessageId),
								input = originalEditingMessage.text,
								sending = false,
								editingMessageId = originalEditingMessage.id,
							)
						}
						else -> state
					}
				}
				publishLocalActivityFromCurrentState()
				_events.tryEmit(Event.ShowMessage(it.message ?: "message action failed"))
			}
		}
	}

	private fun sendOptimisticMessage(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String? = null,
		existingMessageId: Long? = null,
		clientMessageId: String? = null,
	) {
		val optimisticId = existingMessageId ?: nextOptimisticMessageId()
		val normalizedClientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: nextClientMessageId(optimisticId)
		val currentMessages = when (val current = _state.value) {
			is DirectChatScreenState.HasData -> current.messages
			else -> emptyList()
		}
		val existingMessage = existingMessageId?.let { messageId ->
			currentMessages.firstOrNull { it.id == messageId }
		} ?: currentMessages.firstOrNull { it.clientMessageId?.trim() == normalizedClientMessageId }
		val createdAtMillis = existingMessage?.createdAtMillis
			?: nextOutgoingCreatedAtMillis(currentMessages)
		val optimisticMessage = buildOptimisticOutgoingMessage(
			header = header,
			id = optimisticId,
			clientMessageId = normalizedClientMessageId,
			text = text,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			createdAtMillis = createdAtMillis,
			deliveryState = ChatDeliveryState.SENDING,
		)
		stopOutgoingTyping(conversationId)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val messages = upsertMessage(state.messages, optimisticMessage)
					state.copy(
						messages = messages,
						input = "",
						currentReplyMessageId = null,
						editingMessageId = null,
						scrollToBottomRequestToken = nextScrollRequestToken(),
						conversationActivityState = resolveConversationActivityState(state.header, messages, state.input),
					)
				}
				is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
					conversationId = conversationId,
					header = header,
					messages = listOf(optimisticMessage),
					input = "",
					currentReplyMessageId = null,
					editingMessageId = null,
					scrollToBottomRequestToken = nextScrollRequestToken(),
					peerIsOnline = state.peerIsOnline,
					peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					conversationActivityState = resolveConversationActivityState(header, listOf(optimisticMessage), ""),
				)
				else -> state
			}
		}
		publishLocalActivityFromCurrentState()
		if (optimisticId <= 0L) {
			pendingOutgoingClientMessageIds[optimisticId] = normalizedClientMessageId
		}
		val sendJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
			withContext(NonCancellable) {
				repository.storeOptimisticOutgoingMessage(conversationId, header, optimisticMessage)
			}
			repository.sendMessage(
				conversationId = conversationId,
				text = text,
				clientMessageId = normalizedClientMessageId,
				replyToMessageId = replyToMessageId,
			).onSuccess { message ->
				pendingOutgoingClientMessageIds.remove(optimisticId)
				if (isCancelledClientMessageId(normalizedClientMessageId)) {
					removeCancelledClientMessageId(normalizedClientMessageId)
					return@onSuccess
				}
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val messages = upsertMessage(
								state.messages,
								message.copy(
									clientMessageId = message.clientMessageId ?: normalizedClientMessageId,
									deliveryState = ChatDeliveryState.SENT,
									isOutgoing = true,
								)
							)
							state.copy(
								messages = messages,
								peerLastReadMessageId = state.peerLastReadMessageId,
							).let { updated ->
								val readMessages = applyPeerReadState(updated.messages, updated.peerLastReadMessageId)
								updated.copy(
									messages = readMessages,
									conversationActivityState = resolveConversationActivityState(updated.header, readMessages, updated.input),
								)
							}
						}
						else -> state
					}
				}
			}.onFailure { error ->
				pendingOutgoingClientMessageIds.remove(optimisticId)
				if (isCancelledClientMessageId(normalizedClientMessageId)) {
					removeCancelledClientMessageId(normalizedClientMessageId)
					return@onFailure
				}
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val messages = upsertMessage(
								state.messages,
								optimisticMessage.copy(deliveryState = ChatDeliveryState.FAILED)
							)
							state.copy(
								messages = messages,
							).let { updated ->
								val readMessages = applyPeerReadState(updated.messages, updated.peerLastReadMessageId)
								updated.copy(
									messages = readMessages,
									conversationActivityState = resolveConversationActivityState(updated.header, readMessages, updated.input),
								)
							}
						}
						else -> state
					}
				}
				_events.tryEmit(Event.ShowMessage(error.message ?: "message action failed"))
			}
		}
		if (optimisticId <= 0L) {
			pendingOutgoingSendJobs[optimisticId] = sendJob
			sendJob.invokeOnCompletion {
				pendingOutgoingSendJobs.remove(optimisticId)
				pendingOutgoingClientMessageIds.remove(optimisticId)
			}
		}
	}

	fun addLocalVideoCircle(clip: RecordedClip, enqueueOutbox: Boolean = true): String {
		val videoPath = clip.path
		val current = _state.value
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> return ""
		}
			val conversationId = when (current) {
				is DirectChatScreenState.HasData -> current.conversationId
				is DirectChatScreenState.NoMessages -> current.conversationId
			}
		val optimisticId = nextOptimisticMessageId()
			val clientMessageId = nextClientMessageId(optimisticId)
			val currentMessages = when (current) {
				is DirectChatScreenState.HasData -> current.messages
				is DirectChatScreenState.NoMessages -> emptyList()
			}
			val message = ChatMessageItemModel(
				id = optimisticId,
				clientMessageId = clientMessageId,
				senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self",
				text = "",
				createdAtMillis = nextOutgoingCreatedAtMillis(currentMessages),
				isOutgoing = true,
				deliveryState = ChatDeliveryState.SENDING,
				content = me.floow.shared.chats.model.ChatMessageContent.VideoCircle(
					localPath = videoPath,
					durationMs = clip.durationMs.coerceAtLeast(500L),
					thumbnailPath = clip.thumbnailPath,
					width = if (clip.width > 0) clip.width else 480,
					height = if (clip.height > 0) clip.height else 480,
					uploadState = me.floow.shared.chats.model.VideoUploadState.Pending,
				),
			)
			localVideoCircleMessages[clientMessageId] = message
			if (enqueueOutbox) {
				conversationId?.takeIf { it > 0L }?.let { activeConversationId ->
					scope.launch {
						repository.storeOptimisticOutgoingMessage(activeConversationId, header, message)
					}
					val fileReader = me.floow.api.util.PlatformFileReader(videoPath)
					mediaOutboxEngine?.enqueue(
						clientMessageId = clientMessageId,
						conversationId = activeConversationId,
						filePath = videoPath,
						mimeType = "video/mp4",
						sizeBytes = fileReader.length,
						durationMs = clip.durationMs.coerceAtLeast(500L),
						width = if (clip.width > 0) clip.width else 480,
						height = if (clip.height > 0) clip.height else 480,
					)
				}
			}
			_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> state.copy(
					messages = upsertMessage(state.messages, message),
					scrollToBottomRequestToken = nextScrollRequestToken(),
				)
				is DirectChatScreenState.NoMessages -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = listOf(message),
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				}
				else -> state
			}
		}
        return "cmid_$clientMessageId"
	}

	fun activeConversationIdOrNull(): Long? {
		return when (val current = _state.value) {
			is DirectChatScreenState.HasData -> current.conversationId.takeIf { it > 0L }
			is DirectChatScreenState.NoMessages -> current.conversationId?.takeIf { it > 0L }
			else -> null
		}
	}

	fun updateLocalVideoCircleClip(clientMessageId: String, clip: RecordedClip) {
		updateLocalVideoCircleMessage(clientMessageId) { message ->
			val content = message.content as? ChatMessageContent.VideoCircle
			if (content != null) {
				message.copy(
					content = content.copy(
						localPath = clip.path,
						durationMs = clip.durationMs.coerceAtLeast(500L),
						thumbnailPath = clip.thumbnailPath ?: content.thumbnailPath,
						width = if (clip.width > 0) clip.width else 480,
						height = if (clip.height > 0) clip.height else 480,
					),
				)
			} else {
				message
			}
		}

		activeConversationIdOrNull()?.let { activeConversationId ->
			val header = when (val current = _state.value) {
				is DirectChatScreenState.HasData -> current.header
				is DirectChatScreenState.NoMessages -> current.header
				else -> null
			}
			val updatedMsg = localVideoCircleMessages[clientMessageId]
			if (header != null && updatedMsg != null) {
				scope.launch {
					repository.storeOptimisticOutgoingMessage(activeConversationId, header, updatedMsg)
				}
			}
			scope.launch {
				var fileReader = me.floow.api.util.PlatformFileReader(clip.path)
				var attempts = 0
				while ((!fileReader.exists || fileReader.length <= 0L) && attempts < 10) {
					kotlinx.coroutines.delay(30)
					fileReader = me.floow.api.util.PlatformFileReader(clip.path)
					attempts++
				}
				val finalLength = fileReader.length.coerceAtLeast(1L)
				mediaOutboxEngine?.enqueue(
					clientMessageId = clientMessageId,
					conversationId = activeConversationId,
					filePath = clip.path,
					mimeType = "video/mp4",
					sizeBytes = finalLength,
					durationMs = clip.durationMs.coerceAtLeast(500L),
					width = if (clip.width > 0) clip.width else 480,
					height = if (clip.height > 0) clip.height else 480,
				)
			}
		}
	}

	fun markLocalVideoCircleUploading(clientMessageId: String) {
		updateLocalVideoCircleMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.SENDING,
				content = (message.content as? ChatMessageContent.VideoCircle)?.copy(
					uploadState = VideoUploadState.Uploading,
				) ?: message.content,
			)
		}
	}

	fun markLocalVideoCircleFailed(clientMessageId: String) {
		updateLocalVideoCircleMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.FAILED,
				content = (message.content as? ChatMessageContent.VideoCircle)?.copy(
					uploadState = VideoUploadState.Failed,
				) ?: message.content,
			)
		}
	}

	fun cancelLocalVideoCircle(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		addCancelledClientMessageId(normalizedClientMessageId)
		mediaOutboxEngine?.cancel(normalizedClientMessageId)
		val localMsg = localVideoCircleMessages.remove(normalizedClientMessageId)
		val currentMsg = (_state.value as? DirectChatScreenState.HasData)?.messages?.firstOrNull {
			it.clientMessageId == normalizedClientMessageId
		} ?: localMsg
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
		_state.update { state ->
			if (state is DirectChatScreenState.HasData) {
				val remaining = state.messages.filterNot {
					it.clientMessageId == normalizedClientMessageId || (currentMsg != null && it.id == currentMsg.id)
				}
				if (remaining.isEmpty()) {
					DirectChatScreenState.NoMessages(
						conversationId = state.conversationId,
						header = state.header,
						input = state.input,
						scrollToBottomRequestToken = state.scrollToBottomRequestToken,
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				} else {
					state.copy(messages = remaining)
				}
			} else state
		}
		return true
	}

	private fun flushPendingLocalVideoOutbox(conversationId: Long, header: ChatThreadHeaderModel) {
		localVideoCircleMessages.forEach { (clientMessageId, message) ->
			val content = message.content as? ChatMessageContent.VideoCircle ?: return@forEach
			val localPath = content.localPath ?: return@forEach
			if (content.uploadState == VideoUploadState.Pending && mediaOutboxEngine?.tasks?.value?.containsKey(clientMessageId) != true) {
				val fileReader = me.floow.api.util.PlatformFileReader(localPath)
				if (fileReader.exists && fileReader.length > 0L) {
					scope.launch {
						repository.storeOptimisticOutgoingMessage(conversationId, header, message)
					}
					mediaOutboxEngine?.enqueue(
						clientMessageId = clientMessageId,
						conversationId = conversationId,
						filePath = localPath,
						mimeType = "video/mp4",
						sizeBytes = fileReader.length,
						durationMs = content.durationMs.coerceAtLeast(500L),
						width = if (content.width > 0) content.width else 480,
						height = if (content.height > 0) content.height else 480,
					)
				}
			}
		}
	}

	fun resolveLocalVideoCircleSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		remoteThumbnailUrl: String?,
		durationMs: Long,
		width: Int,
		height: Int,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		if (isCancelledClientMessageId(normalizedClientMessageId)) {
			localVideoCircleMessages.remove(normalizedClientMessageId)
			return
		}
		val localMessage = localVideoCircleMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.VideoCircle
		val resolvedContent = ChatMessageContent.VideoCircle(
			localPath = localContent?.localPath,
			remoteUrl = remoteUrl,
			durationMs = durationMs,
			thumbnailPath = localContent?.thumbnailPath,
			remoteThumbnailUrl = remoteThumbnailUrl ?: localContent?.remoteThumbnailUrl,
			width = if (width > 0) width else (localContent?.width ?: 0),
			height = if (height > 0) height else (localContent?.height ?: 0),
			uploadState = VideoUploadState.Uploaded,
		)
		val resolvedMessage = serverMessage.copy(
			clientMessageId = serverMessage.clientMessageId ?: normalizedClientMessageId,
			deliveryState = ChatDeliveryState.SENT,
			isOutgoing = true,
			content = resolvedContent,
		)
		localVideoCircleMessages.remove(normalizedClientMessageId)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = upsertMessage(state.messages, resolvedMessage)
					state.copy(
						messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
					)
				}
				else -> state
			}
		}
	}

	fun addLocalVoice(clip: RecordedVoiceClip): String {
		val voicePath = clip.filePath
		val current = _state.value
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> return ""
		}
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
		}
		val optimisticId = nextOptimisticMessageId()
		val clientMessageId = nextClientMessageId(optimisticId)
		val currentMessages = when (current) {
			is DirectChatScreenState.HasData -> current.messages
			is DirectChatScreenState.NoMessages -> emptyList()
		}
		val message = ChatMessageItemModel(
			id = optimisticId,
			clientMessageId = clientMessageId,
			senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self",
			text = "",
			createdAtMillis = nextOutgoingCreatedAtMillis(currentMessages),
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENDING,
			content = me.floow.shared.chats.model.ChatMessageContent.Voice(
				localPath = voicePath,
				durationMs = clip.durationMs,
				waveform = clip.waveform,
				uploadState = me.floow.shared.chats.model.VideoUploadState.Pending,
			),
		)
		localVoiceMessages[clientMessageId] = message
		conversationId?.takeIf { it > 0L }?.let { activeConversationId ->
			scope.launch(start = CoroutineStart.UNDISPATCHED) {
				repository.storeOptimisticOutgoingMessage(activeConversationId, header, message)
			}
			mediaOutboxEngine?.enqueue(
				clientMessageId = clientMessageId,
				conversationId = activeConversationId,
				filePath = voicePath,
				mimeType = "audio/m4a",
				durationMs = clip.durationMs,
				waveform = clip.waveform,
			)
		}
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> state.copy(
					messages = upsertMessage(state.messages, message),
					scrollToBottomRequestToken = nextScrollRequestToken(),
				)
				is DirectChatScreenState.NoMessages -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = listOf(message),
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				}
				else -> state
			}
		}
		return "cmid_$clientMessageId"
	}

	data class LocalImageDraft(
		val localPath: String,
		val caption: String? = null,
		val width: Int = 0,
		val height: Int = 0,
		val thumbnailUrl: String? = null,
	)

	fun addLocalImages(
		items: List<LocalImageDraft>,
		groupId: String?,
		fallbackConversationId: Long? = null,
		fallbackHeader: ChatThreadHeaderModel? = null,
		replyToMessageId: Long? = null,
		enqueueOutbox: Boolean = true,
	): List<String> {
		if (items.isEmpty()) return emptyList()
		val current = _state.value
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> fallbackHeader ?: return emptyList()
		}
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
			else -> fallbackConversationId
		}
		val currentMessages = when (current) {
			is DirectChatScreenState.HasData -> current.messages
			is DirectChatScreenState.NoMessages -> emptyList()
			else -> emptyList()
		}
		val baseCreatedAt = nextOutgoingCreatedAtMillis(currentMessages)
		val clientMessageIds = ArrayList<String>(items.size)
		val newMessages = ArrayList<ChatMessageItemModel>(items.size)

		items.forEachIndexed { index, item ->
			val optimisticId = nextOptimisticMessageId()
			val clientMessageId = nextClientMessageId(optimisticId)
			clientMessageIds += clientMessageId
			val message = ChatMessageItemModel(
				id = optimisticId,
				clientMessageId = clientMessageId,
				senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self",
				text = item.caption.orEmpty(),
				createdAtMillis = baseCreatedAt + index,
				isOutgoing = true,
				replyToMessageId = replyToMessageId,
				deliveryState = ChatDeliveryState.SENDING,
				content = me.floow.shared.chats.model.ChatMessageContent.Image(
					localPath = item.localPath,
					remoteUrl = null,
					width = item.width,
					height = item.height,
					groupId = groupId,
					position = index,
					uploadState = me.floow.shared.chats.model.VideoUploadState.Uploading,
					thumbnailUrl = item.thumbnailUrl,
				),
			)
			localImageMessages[clientMessageId] = message
			newMessages += message

			conversationId?.takeIf { it > 0L }?.let { activeConversationId ->
				if (enqueueOutbox) {
					scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
						repository.storeOptimisticOutgoingMessage(activeConversationId, header, message)
					}
					mediaOutboxEngine?.enqueue(
						clientMessageId = clientMessageId,
						conversationId = activeConversationId,
						filePath = item.localPath,
						mimeType = "image/jpeg",
						width = item.width,
						height = item.height,
						caption = item.caption,
						groupId = groupId,
						position = index,
						replyToMessageId = replyToMessageId,
						thumbnailUrl = item.thumbnailUrl,
					)
				}
			}
		}

		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					var updatedMessages = state.messages
					for (msg in newMessages) {
						updatedMessages = upsertMessage(updatedMessages, msg)
					}
					state.copy(
						messages = updatedMessages,
						scrollToBottomRequestToken = nextScrollRequestToken(),
					)
				}
				is DirectChatScreenState.NoMessages -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = newMessages,
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				}
				else -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = newMessages,
						scrollToBottomRequestToken = nextScrollRequestToken(),
					)
				}
			}
		}

		return clientMessageIds
	}

	fun addLocalImage(
		localPath: String,
		caption: String?,
		groupId: String?,
		position: Int,
		width: Int,
		height: Int,
		fallbackConversationId: Long? = null,
		fallbackHeader: ChatThreadHeaderModel? = null,
		replyToMessageId: Long? = null,
	): String {
		val ids = addLocalImages(
			items = listOf(
				LocalImageDraft(
					localPath = localPath,
					caption = caption,
					width = width,
					height = height,
				)
			),
			groupId = groupId,
			fallbackConversationId = fallbackConversationId,
			fallbackHeader = fallbackHeader,
			replyToMessageId = replyToMessageId,
		)
		return ids.firstOrNull()?.let { "cmid_$it" }.orEmpty()
	}

	fun updateLocalImageSource(
		clientMessageId: String,
		localPath: String,
		width: Int,
		height: Int,
		thumbnailUrl: String? = null,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localImageMessages[normalizedClientMessageId] ?: return
		val updatedMessage = currentMessage.copy(
			content = (currentMessage.content as? ChatMessageContent.Image)?.copy(
				localPath = localPath,
				width = if (width > 0) width else (currentMessage.content as? ChatMessageContent.Image)?.width ?: 0,
				height = if (height > 0) height else (currentMessage.content as? ChatMessageContent.Image)?.height ?: 0,
				thumbnailUrl = thumbnailUrl ?: (currentMessage.content as? ChatMessageContent.Image)?.thumbnailUrl,
			) ?: currentMessage.content,
		)
		localImageMessages[normalizedClientMessageId] = updatedMessage
		_state.update { state ->
			if (state is DirectChatScreenState.HasData) {
				state.copy(messages = upsertMessage(state.messages, updatedMessage))
			} else state
		}
		activeConversationIdOrNull()?.let { conversationId ->
			val header = when (val s = _state.value) {
				is DirectChatScreenState.HasData -> s.header
				is DirectChatScreenState.NoMessages -> s.header
				else -> null
			}
			if (header != null) {
				scope.launch {
					repository.storeOptimisticOutgoingMessage(conversationId, header, updatedMessage)
				}
			}
		}
	}

	fun enqueuePreparedImageOutbox(
		clientMessageId: String,
		filePath: String,
		width: Int,
		height: Int,
		caption: String? = null,
		groupId: String? = null,
		position: Int = 0,
		replyToMessageId: Long? = null,
		thumbnailUrl: String? = null,
	) {
		val conversationId = activeConversationIdOrNull() ?: return
		mediaOutboxEngine?.enqueue(
			clientMessageId = clientMessageId,
			conversationId = conversationId,
			filePath = filePath,
			mimeType = "image/jpeg",
			width = width,
			height = height,
			caption = caption,
			groupId = groupId,
			position = position,
			replyToMessageId = replyToMessageId,
			thumbnailUrl = thumbnailUrl,
		)
	}

	fun resolveLocalImageSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		width: Int,
		height: Int,
		groupId: String?,
		position: Int,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		if (isCancelledClientMessageId(normalizedClientMessageId)) {
			localImageMessages.remove(normalizedClientMessageId)
			return
		}
		val localMessage = localImageMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.Image
		val fallbackExisting = (_state.value as? DirectChatScreenState.HasData)?.messages?.find {
			it.clientMessageId?.trim() == normalizedClientMessageId || (serverMessage.id > 0L && it.id == serverMessage.id)
		}
		val existingContent = localContent ?: (fallbackExisting?.content as? ChatMessageContent.Image)
		val serverContent = serverMessage.content as? ChatMessageContent.Image

		val resolvedLocalPath = serverContent?.localPath?.takeIf { it.isNotBlank() }
			?: existingContent?.localPath?.takeIf { it.isNotBlank() }
			?: serverContent?.localPath
			?: existingContent?.localPath

		val resolvedWidth = if (width > 0) width else (serverContent?.width?.takeIf { it > 0 } ?: existingContent?.width ?: 0)
		val resolvedHeight = if (height > 0) height else (serverContent?.height?.takeIf { it > 0 } ?: existingContent?.height ?: 0)
		val resolvedThumbnailUrl = serverContent?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: existingContent?.thumbnailUrl
		val resolvedGroupId = existingContent?.groupId?.takeIf { it.isNotBlank() } ?: groupId ?: serverContent?.groupId

		val resolvedContent = ChatMessageContent.Image(
			localPath = resolvedLocalPath,
			remoteUrl = remoteUrl,
			width = resolvedWidth,
			height = resolvedHeight,
			groupId = resolvedGroupId,
			position = if (position > 0) position else (serverContent?.position?.takeIf { it > 0 } ?: existingContent?.position ?: 0),
			uploadState = VideoUploadState.Uploaded,
			thumbnailUrl = resolvedThumbnailUrl,
			sizeBytes = serverContent?.sizeBytes ?: (existingContent?.sizeBytes ?: 0L),
		)
		val resolvedMessage = serverMessage.copy(
			clientMessageId = serverMessage.clientMessageId ?: normalizedClientMessageId,
			deliveryState = ChatDeliveryState.SENT,
			isOutgoing = true,
			content = resolvedContent,
		)
		localImageMessages.remove(normalizedClientMessageId)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = upsertMessage(state.messages, resolvedMessage)
					state.copy(
						messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
					)
				}
				else -> state
			}
		}
	}

	fun markLocalImageFailed(clientMessageId: String) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localImageMessages[normalizedClientMessageId] ?: return
		val updatedMessage = currentMessage.copy(
			deliveryState = ChatDeliveryState.FAILED,
			content = (currentMessage.content as? ChatMessageContent.Image)?.copy(
				uploadState = VideoUploadState.Failed
			) ?: currentMessage.content
		)
		localImageMessages[normalizedClientMessageId] = updatedMessage
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = upsertMessage(state.messages, updatedMessage)
					state.copy(messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId))
				}
				else -> state
			}
		}
	}

	fun markLocalImageUploading(clientMessageId: String) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localImageMessages[normalizedClientMessageId] ?: return
		val updatedMessage = currentMessage.copy(
			deliveryState = ChatDeliveryState.SENDING,
			content = (currentMessage.content as? ChatMessageContent.Image)?.copy(
				uploadState = VideoUploadState.Uploading,
			) ?: currentMessage.content,
		)
		localImageMessages[normalizedClientMessageId] = updatedMessage
		_state.update { state ->
			if (state is DirectChatScreenState.HasData) state.copy(messages = upsertMessage(state.messages, updatedMessage)) else state
		}
	}

	fun cancelLocalImage(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		addCancelledClientMessageId(normalizedClientMessageId)
		mediaOutboxEngine?.cancel(normalizedClientMessageId)
		val localMsg = localImageMessages.remove(normalizedClientMessageId)
		val currentMsg = (_state.value as? DirectChatScreenState.HasData)?.messages?.firstOrNull {
			it.clientMessageId == normalizedClientMessageId
		} ?: localMsg
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
		_state.update { state ->
			if (state is DirectChatScreenState.HasData) {
				val remaining = state.messages.filterNot {
					it.clientMessageId == normalizedClientMessageId || (currentMsg != null && it.id == currentMsg.id)
				}
				if (remaining.isEmpty()) {
					DirectChatScreenState.NoMessages(
						conversationId = state.conversationId,
						header = state.header,
						input = state.input,
						scrollToBottomRequestToken = state.scrollToBottomRequestToken,
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				} else {
					state.copy(messages = remaining)
				}
			} else state
		}
		return true
	}

	fun markLocalVoiceUploading(clientMessageId: String) {
		updateLocalVoiceMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.SENDING,
				content = (message.content as? ChatMessageContent.Voice)?.copy(
					uploadState = VideoUploadState.Uploading,
				) ?: message.content,
			)
		}
	}

	fun markLocalVoiceFailed(clientMessageId: String) {
		updateLocalVoiceMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.FAILED,
				content = (message.content as? ChatMessageContent.Voice)?.copy(
					uploadState = VideoUploadState.Failed,
				) ?: message.content,
			)
		}
	}

	fun cancelLocalVoice(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		addCancelledClientMessageId(normalizedClientMessageId)
		mediaOutboxEngine?.cancel(normalizedClientMessageId)
		val localMsg = localVoiceMessages.remove(normalizedClientMessageId)
		val currentMsg = (_state.value as? DirectChatScreenState.HasData)?.messages?.firstOrNull {
			it.clientMessageId == normalizedClientMessageId
		} ?: localMsg
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
		_state.update { state ->
			if (state is DirectChatScreenState.HasData) {
				val remaining = state.messages.filterNot {
					it.clientMessageId == normalizedClientMessageId || (currentMsg != null && it.id == currentMsg.id)
				}
				if (remaining.isEmpty()) {
					DirectChatScreenState.NoMessages(
						conversationId = state.conversationId,
						header = state.header,
						input = state.input,
						scrollToBottomRequestToken = state.scrollToBottomRequestToken,
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				} else {
					state.copy(messages = remaining)
				}
			} else state
		}
		return true
	}

	fun resolveLocalVoiceSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		durationMs: Long,
		waveform: List<Int>,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		if (isCancelledClientMessageId(normalizedClientMessageId)) {
			localVoiceMessages.remove(normalizedClientMessageId)
			return
		}
		val localMessage = localVoiceMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.Voice
		val resolvedContent = ChatMessageContent.Voice(
			localPath = localContent?.localPath,
			remoteUrl = remoteUrl,
			durationMs = durationMs,
			waveform = if (waveform.isNotEmpty()) waveform else (localContent?.waveform ?: emptyList()),
			uploadState = VideoUploadState.Uploaded,
		)
		val resolvedMessage = serverMessage.copy(
			clientMessageId = serverMessage.clientMessageId ?: normalizedClientMessageId,
			deliveryState = ChatDeliveryState.SENT,
			isOutgoing = true,
			content = resolvedContent,
		)
		localVoiceMessages.remove(normalizedClientMessageId)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = upsertMessage(state.messages, resolvedMessage)
					state.copy(
						messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
						scrollToBottomRequestToken = nextScrollRequestToken(),
					)
				}
				else -> state
			}
		}
	}

	private fun updateLocalVoiceMessage(
		clientMessageId: String,
		transform: (ChatMessageItemModel) -> ChatMessageItemModel,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localVoiceMessages[normalizedClientMessageId] ?: return
		val updatedMessage = transform(currentMessage)
		localVoiceMessages[normalizedClientMessageId] = updatedMessage
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = upsertMessage(state.messages, updatedMessage)
					state.copy(messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId))
				}
				else -> state
			}
		}
	}

	fun loadMore() {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		if (current.isLoadingMore || !current.canLoadMore) return

		_state.value = current.copy(isLoadingMore = true)
		scope.launch {
			chatLoader.loadMore(
				conversationId = current.conversationId,
				beforeMessageId = current.nextBeforeMessageId ?: current.messages.firstOrNull()?.id,
			)
				.onSuccess { page ->
					seedMediaDownloadStates(page.items)
					_state.update { state ->
						when (state) {
							is DirectChatScreenState.HasData -> state.copy(
								messages = mergeMessages(page.items + state.messages),
								isLoadingMore = false,
								canLoadMore = page.canLoadMore,
								nextBeforeMessageId = page.nextBeforeMessageId,
							)
							else -> state
						}
					}
				}
				.onFailure {
					_state.update { state ->
						when (state) {
							is DirectChatScreenState.HasData -> state.copy(isLoadingMore = false)
							else -> state
						}
					}
					_events.tryEmit(Event.ShowMessage(it.message ?: "load more failed"))
				}
		}
	}

	fun deleteMessage(messageId: Long): DeleteMessageResult {
		val current = _state.value as? DirectChatScreenState.HasData ?: return DeleteMessageResult.IGNORED
		val message = current.messages.firstOrNull { it.id == messageId } ?: return DeleteMessageResult.IGNORED
		if (
			message.id <= 0L ||
			message.deliveryState == ChatDeliveryState.SENDING ||
			message.deliveryState == ChatDeliveryState.FAILED
		) {
			removeMessageImmediately(current.conversationId, message)
			return DeleteMessageResult.DELETED_IMMEDIATELY
		}

		removeMessageFromState(message.id)
		schedulePendingDeleteCommit(message, current.conversationId)
		return DeleteMessageResult.DELETED_WITH_UNDO
	}

	fun deleteAlbum(groupId: String): DeleteMessageResult {
		val current = _state.value as? DirectChatScreenState.HasData ?: return DeleteMessageResult.IGNORED
		val groupMessages = current.messages.filter { candidate ->
			(candidate.content as? ChatMessageContent.Image)?.groupId == groupId
		}
		if (groupMessages.isEmpty()) return DeleteMessageResult.IGNORED
		deleteMessages(groupMessages.map(ChatMessageItemModel::id))
		return DeleteMessageResult.DELETED_IMMEDIATELY
	}

	fun deleteMessages(messageIds: Collection<Long>) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		val ids = messageIds.toSet()
		if (ids.isEmpty()) return
		flushPendingDeleteNow()
		val messagesById = current.messages.associateBy(ChatMessageItemModel::id)
		val immediateMessages = ids.mapNotNull(messagesById::get)
		if (immediateMessages.isEmpty()) return
		val immediateDeleteIds = immediateMessages.map(ChatMessageItemModel::id).toSet()
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val remainingMessages = state.messages.filterNot { it.id in immediateDeleteIds }
					if (remainingMessages.isEmpty()) {
						DirectChatScreenState.NoMessages(
							conversationId = state.conversationId,
							header = state.header,
							input = state.input,
							scrollToBottomRequestToken = state.scrollToBottomRequestToken,
							peerIsOnline = state.peerIsOnline,
							peerLastSeenAtMillis = state.peerLastSeenAtMillis,
						)
					} else {
						state.copy(messages = remainingMessages)
					}
				}
				else -> state
			}
		}

		val pendingLocalMessages = ArrayList<ChatMessageItemModel>()
		val serverMessages = ArrayList<ChatMessageItemModel>()
		immediateMessages.forEach { message ->
			if (
				message.id <= 0L ||
				message.deliveryState == ChatDeliveryState.SENDING ||
				message.deliveryState == ChatDeliveryState.FAILED
			) {
				pendingLocalMessages.add(message)
			} else {
				serverMessages.add(message)
			}
		}

		pendingLocalMessages.forEach { message ->
			cancelPendingOutgoingMessage(message)
		}

		if (serverMessages.isNotEmpty()) {
			val serverIds = serverMessages.map(ChatMessageItemModel::id)
			scope.launch(NonCancellable) {
				val deleteResult = withContext(NonCancellable) {
					if (serverIds.size == 1) {
						repository.deleteMessage(current.conversationId, serverIds[0])
					} else {
						repository.deleteMessagesBatch(current.conversationId, serverIds)
					}
				}
				deleteResult.onFailure {
					serverMessages.forEach { msg ->
						restoreDeletedMessage(msg, current.conversationId)
					}
					_events.tryEmit(Event.ShowMessage(it.message ?: "delete failed"))
				}
			}
		}
	}

	fun undoDeleteMessage() {
		chatActions.undoDeleteMessage { deletedMessage, conversationId ->
			restoreDeletedMessage(deletedMessage, conversationId)
		}
	}

	fun togglePin(messageId: Long, isPinned: Boolean) {
		if (messageId <= 0L) return
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		chatActions.togglePin(current.conversationId, messageId, isPinned) {
			_state.update { state ->
				when (state) {
					is DirectChatScreenState.HasData -> state.copy(
						messages = state.messages.map { message ->
							if (message.id == messageId) message.copy(isPinned = isPinned) else message
						}
					)
					else -> state
				}
			}
		}
	}

	fun pinMessage(messageId: Long) {
		togglePin(messageId, true)
	}

	fun unpinMessage(messageId: Long) {
		togglePin(messageId, false)
	}

	fun clearHighlight() {
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> if (state.highlightedMessageId != null) state.copy(highlightedMessageId = null) else state
				else -> state
			}
		}
	}

	fun highlightRequest(messageId: Long) {
		jumpToMessage(messageId)
	}

	fun highlightRequest(messageId: String) {
		messageId.toLongOrNull()?.let { jumpToMessage(it) }
	}

	fun jumpToMessage(messageId: Long) {
		if (messageId <= 0L) return
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		val nextToken = nextAnchorRequestToken()
		if (current.messages.any { it.id == messageId }) {
			_events.tryEmit(Event.JumpToMessage(messageId))
			_state.update { state ->
				when (state) {
					is DirectChatScreenState.HasData -> state.copy(
						highlightedMessageId = messageId,
						anchorRequestToken = nextToken
					)
					else -> state
				}
			}
			return
		}
		scope.launch {
			chatLoader.loadAroundMessage(current.conversationId, messageId)
				.onSuccess { page ->
					_state.update { state ->
						when (state) {
							is DirectChatScreenState.HasData -> state.copy(
								messages = mergeMessages(page.items + state.messages),
								canLoadMore = page.canLoadMore,
								nextBeforeMessageId = page.nextBeforeMessageId,
								peerLastReadMessageId = page.peerLastReadMessageId ?: state.peerLastReadMessageId,
								highlightedMessageId = messageId,
								anchorRequestMessageId = messageId,
								anchorRequestOffsetPx = 0,
								anchorRequestToken = nextToken,
								keepAnchorRequest = true,
							)
							else -> state
						}
					}
					_events.tryEmit(Event.JumpToMessage(messageId))
				}
				.onFailure {
					_events.tryEmit(Event.ShowMessage(it.message ?: "message not found"))
				}
		}
	}

	private fun observeRealtime(conversationId: Long?) {
		if (conversationId == null || realtimeContract == null) return
		realtimeCoordinator.observeRealtime(conversationId, afterSeq = currentMaxMessageId(conversationId)) { event ->
				when (event) {
					is ChatRealtimeEvent.Hello -> {
						if (event.lastReadMessageId > readTracker.lastMarkedReadMessageId) {
							readTracker.initializeLastMarkedRead(event.lastReadMessageId)
						}
					}
					is ChatRealtimeEvent.MessageUpserted -> {
						_state.update { current ->
							when (current) {
								is DirectChatScreenState.HasData -> {
									val normalizedClientMessageId = event.message.clientMessageId
										?.trim()
										?.takeIf(String::isNotEmpty)
									if (normalizedClientMessageId != null && isCancelledClientMessageId(normalizedClientMessageId)) {
										removeCancelledClientMessageId(normalizedClientMessageId)
										return@update current
									}
									val nextPeerLastReadMessageId = maxOf(
										current.peerLastReadMessageId,
										event.peerLastReadMessageId ?: 0L,
									)
									val normalizedMessage = normalizeRealtimeMessage(
										message = event.message,
										header = current.header,
										peerLastReadMessageId = nextPeerLastReadMessageId,
									)
									val wasAlreadyInTimeline = current.messages.any { existing ->
										existing.identityKey() == normalizedMessage.identityKey() ||
											(existing.id > 0L && existing.id == normalizedMessage.id)
									}
									val updated = upsertMessage(current.messages, normalizedMessage)
									val shouldAutoScrollToBottom = !wasAlreadyInTimeline &&
										!current.keepAnchorRequest &&
										(
											normalizedMessage.isOutgoing ||
												(latestViewportSnapshot?.isAtBottom != false)
											)
									updateUnseenIncomingMessages(
										message = normalizedMessage,
										shouldAutoScrollToBottom = shouldAutoScrollToBottom || wasAlreadyInTimeline,
									)
									current.copy(
										messages = applyPeerReadState(updated, nextPeerLastReadMessageId),
										scrollToBottomRequestToken = if (shouldAutoScrollToBottom) {
											nextScrollRequestToken()
										} else {
											current.scrollToBottomRequestToken
										},
										scrollToBottomBadgeCount = readTracker.unseenIncomingMessageIds.size,
										peerLastReadMessageId = nextPeerLastReadMessageId,
									)
								}
								is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
									conversationId = conversationId,
									header = current.header,
									messages = listOf(
										normalizeRealtimeMessage(
											message = event.message,
											header = current.header,
											peerLastReadMessageId = event.peerLastReadMessageId ?: 0L,
										)
									),
									scrollToBottomRequestToken = nextScrollRequestToken(),
									scrollToBottomBadgeCount = 0,
									peerLastReadMessageId = event.peerLastReadMessageId ?: 0L,
									peerIsOnline = current.peerIsOnline,
									peerLastSeenAtMillis = current.peerLastSeenAtMillis,
								)
								else -> current
							}
						}
					}
					is ChatRealtimeEvent.MessageDeleted -> {
						_state.update { current ->
							when (current) {
								is DirectChatScreenState.HasData -> {
									val remaining = current.messages.filterNot { it.id == event.messageId }
									if (remaining.isEmpty()) {
										DirectChatScreenState.NoMessages(
											conversationId = current.conversationId,
											header = current.header,
											input = current.input,
											peerIsOnline = current.peerIsOnline,
											peerLastSeenAtMillis = current.peerLastSeenAtMillis,
										)
									} else {
										current.copy(
											messages = remaining,
											pinnedMessages = current.pinnedMessages.filterNot { it.id == event.messageId },
										)
									}
								}
								else -> current
							}
						}
					}
					is ChatRealtimeEvent.MessagePinned -> {
						_state.update { current ->
							when (current) {
								is DirectChatScreenState.HasData -> {
									val updatedMessages = current.messages.map { message ->
										if (message.id == event.messageId) {
											message.copy(isPinned = event.isPinned)
										} else {
											message
										}
									}
									val updatedPinnedMessages = if (event.isPinned) {
										normalizePinnedMessages(
											messages = current.pinnedMessages + updatedMessages.filter { it.id == event.messageId },
											peerLastReadMessageId = current.peerLastReadMessageId,
										)
									} else {
										current.pinnedMessages.filterNot { it.id == event.messageId }
									}
									current.copy(
										messages = updatedMessages,
										pinnedMessages = updatedPinnedMessages,
									)
								}
								else -> current
							}
						}
					}
					is ChatRealtimeEvent.PeerReadUpdated -> {
						_state.update { current ->
							when (current) {
								is DirectChatScreenState.HasData -> current.copy(
									peerLastReadMessageId = event.messageId,
									messages = applyPeerReadState(current.messages, event.messageId)
								)
								else -> current
							}
						}
					}
						is ChatRealtimeEvent.TypingUpdated -> {
							val currentHeader = when (val current = _state.value) {
								is DirectChatScreenState.HasData -> current.header
								is DirectChatScreenState.NoMessages -> current.header
								else -> null
							} ?: return@observeRealtime
							typingCoordinator.handleIncomingTyping(event, currentHeader) { state ->
								latestTypingState = state
								applyHeaderSubtitle()
							}
						}
					is ChatRealtimeEvent.ConversationDeleted -> {
						disposeActiveWork(clearLocalVideoCircles = true)
						_state.value = DirectChatScreenState.Error("Диалог удален")
						_events.tryEmit(Event.ShowMessage("Диалог удален"))
					}
					ChatRealtimeEvent.ResyncRequired -> resyncConversation()
				}
		}
	}

	private fun currentMaxMessageId(conversationId: Long): Long {
		val current = _state.value as? DirectChatScreenState.HasData ?: return 0L
		if (current.conversationId != conversationId) return 0L
		return current.messages.maxOfOrNull { message ->
			message.id.takeIf { it > 0L } ?: 0L
		} ?: 0L
	}

	private fun observePinnedMessages(conversationId: Long?) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L } ?: return
		realtimeCoordinator.observePinnedMessages(resolvedConversationId) { messages ->
			_state.update { current ->
				when (current) {
					is DirectChatScreenState.HasData -> {
						if (current.conversationId != resolvedConversationId) {
							current
						} else {
							current.copy(
								pinnedMessages = normalizePinnedMessages(
									messages = messages,
									peerLastReadMessageId = current.peerLastReadMessageId,
								),
								messages = mergePinnedFlagsIntoTimeline(
									timeline = current.messages,
									pinnedMessages = messages,
								),
							)
						}
					}
					else -> current
				}
			}
		}
		scope.launch {
			repository.loadPinnedMessages(resolvedConversationId)
		}
	}

	private fun observeLocalMessages(conversationId: Long?) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L } ?: return
		realtimeCoordinator.observeLocalMessages(resolvedConversationId) { page ->
			applyLocalMessagesPage(
				conversationId = resolvedConversationId,
				page = page,
			)
		}
	}

	private fun applyLocalMessagesPage(
		conversationId: Long,
		page: ChatThreadPage,
	) {
		if (page.items.isEmpty()) return
		var shouldMarkReadAfterUpdate = false
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> {
					if (current.conversationId != conversationId) return@update current
					val previousLatestKey = current.messages.lastOrNull()?.identityKey()
					val pageLatestMessage = page.items.lastOrNull()
					val pageLatestKey = pageLatestMessage?.identityKey()
					val latestWasAlreadyLoaded = pageLatestMessage?.let { incoming ->
						current.messages.any { existing -> existing.matchesMessageIdentity(incoming) }
					} == true
					val isAcknowledgedOutgoing = pageLatestMessage?.isOutgoing == true &&
						(latestWasAlreadyLoaded || current.messages.any { it.isOutgoing && it.matchesMessageIdentity(pageLatestMessage) })
					val hasNewLatestMessage = pageLatestMessage != null &&
						pageLatestKey != null &&
						pageLatestKey != previousLatestKey &&
						!latestWasAlreadyLoaded &&
						!isAcknowledgedOutgoing
					val shouldAutoScrollToBottom = hasNewLatestMessage &&
						!current.keepAnchorRequest &&
						(
							pageLatestMessage.isOutgoing ||
								(latestViewportSnapshot?.isAtBottom != false)
						)
					if (hasNewLatestMessage) {
						updateUnseenIncomingMessages(
							message = checkNotNull(pageLatestMessage),
							shouldAutoScrollToBottom = shouldAutoScrollToBottom,
						)
					}
					shouldMarkReadAfterUpdate = shouldAutoScrollToBottom &&
						!pageLatestMessage.isOutgoing &&
						pageLatestMessage.id > 0L
					val nextPeerLastReadMessageId = page.peerLastReadMessageId ?: current.peerLastReadMessageId
					val mergedMessages = applyPeerReadState(
						mergeMessages(current.messages + page.items),
						nextPeerLastReadMessageId,
					)
					current.copy(
						messages = mergedMessages,
						canLoadMore = current.canLoadMore,
						nextBeforeMessageId = current.nextBeforeMessageId,
						scrollToBottomRequestToken = if (shouldAutoScrollToBottom) {
							nextScrollRequestToken()
						} else {
							current.scrollToBottomRequestToken
						},
						scrollToBottomBadgeCount = readTracker.unseenIncomingMessageIds.size,
						peerLastReadMessageId = nextPeerLastReadMessageId,
						conversationActivityState = resolveConversationActivityState(current.header, mergedMessages, current.input),
					)
				}
				is DirectChatScreenState.NoMessages -> {
					if (current.conversationId != conversationId) return@update current
					shouldMarkReadAfterUpdate = page.items.lastOrNull()?.let { message ->
						!message.isOutgoing && message.id > 0L
					} == true
					val mergedMessages = applyPeerReadState(
						mergeMessages(page.items),
						page.peerLastReadMessageId ?: 0L,
					)
					DirectChatScreenState.HasData(
						conversationId = conversationId,
						header = current.header,
						messages = mergedMessages,
						input = current.input,
						canLoadMore = page.canLoadMore,
						nextBeforeMessageId = page.nextBeforeMessageId,
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerLastReadMessageId = page.peerLastReadMessageId ?: 0L,
						peerIsOnline = current.peerIsOnline,
						peerLastSeenAtMillis = current.peerLastSeenAtMillis,
						conversationActivityState = resolveConversationActivityState(current.header, mergedMessages, current.input),
					)
				}
				else -> current
			}
		}
		if (shouldMarkReadAfterUpdate) {
			markLatestVisibleMessagesReadIfAppropriate()
		}
	}

	private fun buildOptimisticOutgoingMessage(
		header: ChatThreadHeaderModel,
		id: Long,
		clientMessageId: String,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String?,
		createdAtMillis: Long,
		deliveryState: ChatDeliveryState,
	): ChatMessageItemModel {
		val senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: header.peerUserId
		return ChatMessageItemModel(
			id = id,
			clientMessageId = clientMessageId,
			senderUserId = senderUserId,
			senderDisplayName = null,
			text = text,
			createdAtMillis = createdAtMillis,
			isOutgoing = true,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			deliveryState = deliveryState,
		)
	}

	private fun nextOutgoingCreatedAtMillis(currentMessages: List<ChatMessageItemModel>): Long {
		val now = currentChatEpochMillis()
		val latestVisibleMessageAt = currentMessages.maxOfOrNull { it.createdAtMillis } ?: return now
		return if (latestVisibleMessageAt >= now) latestVisibleMessageAt + 1L else now
	}

    private fun upsertMessage(
        currentMessages: List<ChatMessageItemModel>,
        message: ChatMessageItemModel,
    ): List<ChatMessageItemModel> {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		val n = currentMessages.size

		var firstMatchIndex = -1
		var firstMatch: ChatMessageItemModel? = null
		for (i in 0 until n) {
			val candidate = currentMessages[i]
			val matchesById = message.id > 0L && candidate.id == message.id
			val matchesByClient = normalizedClientMessageId != null &&
				candidate.clientMessageId?.trim() == normalizedClientMessageId
			if (matchesById || matchesByClient) {
				firstMatchIndex = i
				firstMatch = candidate
				break
			}
		}

		val normalizedMessage = if (firstMatch != null) {
			val preservedContent = mergeMessageContent(firstMatch.content, message.content)
			val resolvedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
				?: firstMatch.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
			val inFlightText = inFlightEdits[firstMatch.id] ?: inFlightEdits[message.id]
			val resolvedText = inFlightText ?: message.text
			val resolvedDeliveryState = when {
				firstMatch.deliveryState == ChatDeliveryState.SENDING && message.deliveryState == null -> ChatDeliveryState.SENDING
				else -> message.deliveryState ?: firstMatch.deliveryState
			}
			message.copy(
				clientMessageId = resolvedClientMessageId,
				createdAtMillis = if (message.id > 0L && firstMatch.createdAtMillis > 0L) firstMatch.createdAtMillis else message.createdAtMillis,
				content = preservedContent,
				text = resolvedText,
				deliveryState = resolvedDeliveryState,
			)
		} else {
			val inFlightText = inFlightEdits[message.id]
			if (inFlightText != null) message.copy(text = inFlightText) else message
		}

		val result = ArrayList<ChatMessageItemModel>(if (firstMatchIndex >= 0) n else n + 1)
		var inserted = false
		for (i in 0 until n) {
			val existing = currentMessages[i]
			if (i == firstMatchIndex) {
				result += normalizedMessage
				inserted = true
				continue
			}
			val matchesById = normalizedMessage.id > 0L && existing.id == normalizedMessage.id
			val matchesByClient = normalizedClientMessageId != null &&
				existing.clientMessageId?.trim() == normalizedClientMessageId
			if (matchesById || matchesByClient) continue
			result += existing
		}
		if (!inserted) result += normalizedMessage

		return result.sortedBy(ChatMessageItemModel::createdAtMillis)
    }

	private fun mergeMessageContent(existing: ChatMessageContent, incoming: ChatMessageContent): ChatMessageContent {
		return when {
			existing is ChatMessageContent.Image && incoming is ChatMessageContent.Image -> {
				val resolvedLocalPath = incoming.localPath?.takeIf { it.isNotBlank() }
					?: existing.localPath?.takeIf { it.isNotBlank() }
					?: incoming.localPath
					?: existing.localPath
				incoming.copy(
					localPath = resolvedLocalPath,
					remoteUrl = incoming.remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
					width = if (existing.width > 0) existing.width else incoming.width,
					height = if (existing.height > 0) existing.height else incoming.height,
					groupId = existing.groupId?.takeIf { it.isNotBlank() } ?: incoming.groupId,
					position = if (existing.position > 0) existing.position else incoming.position,
					thumbnailUrl = existing.thumbnailUrl?.takeIf { it.isNotBlank() } ?: incoming.thumbnailUrl,
				)
			}
			existing is ChatMessageContent.VideoCircle && incoming is ChatMessageContent.VideoCircle -> {
				val resolvedLocalPath = incoming.localPath?.takeIf { it.isNotBlank() }
					?: existing.localPath?.takeIf { it.isNotBlank() }
					?: incoming.localPath
					?: existing.localPath
				incoming.copy(
					localPath = resolvedLocalPath,
					remoteUrl = incoming.remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
					width = if (incoming.width > 0) incoming.width else existing.width,
					height = if (incoming.height > 0) incoming.height else existing.height,
					durationMs = if (incoming.durationMs > 0) incoming.durationMs else existing.durationMs,
					thumbnailPath = incoming.thumbnailPath ?: existing.thumbnailPath,
					remoteThumbnailUrl = incoming.remoteThumbnailUrl ?: existing.remoteThumbnailUrl,
				)
			}
			existing is ChatMessageContent.Voice && incoming is ChatMessageContent.Voice -> {
				val resolvedLocalPath = incoming.localPath?.takeIf { it.isNotBlank() }
					?: existing.localPath?.takeIf { it.isNotBlank() }
					?: incoming.localPath
					?: existing.localPath
				incoming.copy(
					localPath = resolvedLocalPath,
					remoteUrl = incoming.remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
					durationMs = if (incoming.durationMs > 0) incoming.durationMs else existing.durationMs,
					waveform = if (incoming.waveform.isNotEmpty()) incoming.waveform else existing.waveform,
				)
			}
			existing !is ChatMessageContent.Text && incoming is ChatMessageContent.Text -> existing
			else -> incoming
		}
	}

	private fun ChatMessageItemModel.matchesMessageIdentity(
		other: ChatMessageItemModel,
		otherClientMessageId: String? = other.clientMessageId?.trim()?.takeIf(String::isNotEmpty),
	): Boolean {
		return (id > 0L && other.id > 0L && id == other.id) ||
			(otherClientMessageId != null && clientMessageId?.trim() == otherClientMessageId)
	}

	private fun ChatMessageItemModel.identityKey(): String {
		val normalizedClientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		return normalizedClientMessageId?.let { "client:$it" } ?: "id:$id"
	}

	private fun mergeMessages(messages: List<ChatMessageItemModel>): List<ChatMessageItemModel> {
		if (messages.size <= 1) return messages
		val merged = ArrayList<ChatMessageItemModel>(messages.size)
		val indexByServerId = HashMap<Long, Int>(messages.size)
		val indexByClientMessageId = HashMap<String, Int>(messages.size)
		messages.forEach { incoming ->
			val clientMessageId = incoming.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
			val serverIndex = incoming.id.takeIf { it > 0L }?.let(indexByServerId::get)
			val clientIndex = clientMessageId?.let(indexByClientMessageId::get)
			val existingIndex = serverIndex ?: clientIndex
			if (existingIndex == null) {
				val nextIndex = merged.size
				merged += incoming
				if (incoming.id > 0L) indexByServerId[incoming.id] = nextIndex
				clientMessageId?.let { indexByClientMessageId[it] = nextIndex }
			} else {
				val existing = merged[existingIndex]
				val preservedContent = mergeMessageContent(existing.content, incoming.content)
				val resolvedClientMessageId = incoming.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
					?: existing.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
				val inFlightText = inFlightEdits[existing.id] ?: inFlightEdits[incoming.id]
				val resolvedText = inFlightText ?: incoming.text
				val resolvedDeliveryState = when {
					existing.deliveryState == ChatDeliveryState.SENDING && incoming.deliveryState == null -> ChatDeliveryState.SENDING
					else -> incoming.deliveryState ?: existing.deliveryState
				}
				val normalizedIncoming = if (existing.clientMessageId != null && incoming.id > 0L) {
					incoming.copy(
						clientMessageId = resolvedClientMessageId,
						createdAtMillis = existing.createdAtMillis,
						content = preservedContent,
						text = resolvedText,
						deliveryState = resolvedDeliveryState,
					)
				} else {
					incoming.copy(
						clientMessageId = resolvedClientMessageId,
						content = preservedContent,
						text = resolvedText,
						deliveryState = resolvedDeliveryState,
					)
				}
				merged[existingIndex] = normalizedIncoming
				if (normalizedIncoming.id > 0L) indexByServerId[normalizedIncoming.id] = existingIndex
				clientMessageId?.let { indexByClientMessageId[it] = existingIndex }
				existing.clientMessageId?.trim()?.takeIf(String::isNotEmpty)?.let { indexByClientMessageId[it] = existingIndex }
			}
		}
		return merged.sortedBy(ChatMessageItemModel::createdAtMillis)
	}

    private fun mergeLocalVideoCircleMessages(messages: List<ChatMessageItemModel>): List<ChatMessageItemModel> {
        var result = messages
        if (localVideoCircleMessages.isNotEmpty()) {
            result = localVideoCircleMessages.values.fold(result) { current, localMessage ->
                upsertMessage(current, localMessage)
            }
        }
        if (localVoiceMessages.isNotEmpty()) {
            result = localVoiceMessages.values.fold(result) { current, localMessage ->
                upsertMessage(current, localMessage)
            }
        }
        if (localImageMessages.isNotEmpty()) {
            result = localImageMessages.values.fold(result) { current, localMessage ->
                upsertMessage(current, localMessage)
            }
        }
        return result
    }

	private fun updateLocalVideoCircleMessage(
		clientMessageId: String,
		transform: (ChatMessageItemModel) -> ChatMessageItemModel,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localVideoCircleMessages[normalizedClientMessageId] ?: return
		val updatedMessage = transform(currentMessage)
		localVideoCircleMessages[normalizedClientMessageId] = updatedMessage
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = upsertMessage(state.messages, updatedMessage)
					state.copy(messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId))
				}
				else -> state
			}
		}
	}

    private fun observePresence(header: ChatThreadHeaderModel) {
		if (header.isSavedMessages) {
			realtimeCoordinator.observePresence(header) { presence ->
				latestPresenceState = presence
				hasObservedPresenceState = false
				latestTypingState = ChatTypingState()
				updateHeaderSubtitle(null)
			}
			return
		}
		realtimeCoordinator.observePresence(header) { presence ->
			latestPresenceState = presence
			hasObservedPresenceState = realtimeCoordinator.hasObservedPresenceState
			applyHeaderSubtitle()
		}
	}

	private fun applyHeaderSubtitle() {
		val currentHeader = when (val current = _state.value) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> null
		}
		if (currentHeader?.isSavedMessages == true) {
			updateHeaderSubtitle(null)
			return
		}
		updateHeaderSubtitle(resolveLiveHeaderSubtitle())
	}

	private fun updateHeaderSubtitle(subtitle: String?) {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					header = current.header.copy(subtitle = subtitle),
					peerIsOnline = latestPresenceState.resolveIsOnline(),
					peerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
					typingUserNames = resolveTypingUserNames(current.header),
					conversationActivityState = resolveConversationActivityState(current.header, current.messages, current.input),
				)
				is DirectChatScreenState.NoMessages -> current.copy(
					header = current.header.copy(subtitle = subtitle),
					peerIsOnline = latestPresenceState.resolveIsOnline(),
					peerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
					conversationActivityState = resolveConversationActivityState(current.header, emptyList(), current.input),
				)
				else -> current
			}
		}
	}

	private fun resolveTypingUserNames(header: ChatThreadHeaderModel): List<String> {
		val typing = resolveConversationActivityState(header, emptyList(), "") as? ConversationActivityState.Typing
			?: return emptyList()
		return listOf(typing.displayName.ifBlank { header.title })
	}

	private fun resolveConversationActivityState(
		header: ChatThreadHeaderModel,
		messages: List<ChatMessageItemModel>,
		input: String,
	): ConversationActivityState {
		if (header.isSavedMessages) return ConversationActivityState.None
		if (latestTypingState.isTyping) {
			val ttlMs = latestTypingState.typingTtlMs.takeIf { it > 0L } ?: MIN_INCOMING_TYPING_TTL_MS
			return ConversationActivityState.Typing(
				displayName = latestTypingState.displayName.ifBlank { header.title },
				expiresAtMillis = currentChatEpochMillis() + ttlMs,
			)
		}
		localRecordingStartedAtMillis?.let { startedAt ->
			return ConversationActivityState.RecordingVideo(startedAtMillis = startedAt)
		}
		val latestSendingMessage = messages
			.asSequence()
			.filter { it.isOutgoing && it.deliveryState == ChatDeliveryState.SENDING }
			.maxByOrNull(ChatMessageItemModel::createdAtMillis)
		latestSendingMessage?.let {
			ConversationActivityState.Sending(startedAtMillis = it.createdAtMillis)
		}?.let { return it }
		return if (input.isNotBlank()) {
			ConversationActivityState.Draft(
				updatedAtMillis = currentChatEpochMillis(),
				text = input.trim(),
			)
		} else {
			ConversationActivityState.None
		}
	}

	private fun publishLocalActivityFromCurrentState() {
		when (val current = _state.value) {
			is DirectChatScreenState.HasData -> publishLocalActivity(
				conversationId = current.conversationId,
				header = current.header,
				input = current.input,
				isEditing = current.editingMessageId != null,
			)
			is DirectChatScreenState.NoMessages -> publishLocalActivity(
				conversationId = current.conversationId,
				header = current.header,
				input = current.input,
				isEditing = false,
			)
			else -> Unit
		}
	}

	private fun draftLookupKey(conversationId: Long?, header: ChatThreadHeaderModel): String {
		return conversationId?.takeIf { it > 0L }?.toString()
			?: activeRequest?.conversationId?.takeIf { it > 0L }?.toString()
			?: header.peerUserId.trim().takeIf(String::isNotEmpty)
			?: activeRequest?.peerUserId?.trim()?.takeIf(String::isNotEmpty)
			?: ""
	}

	private fun publishLocalActivity(
		conversationId: Long?,
		header: ChatThreadHeaderModel,
		input: String,
		isEditing: Boolean,
	) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L }
		val draftKey = draftLookupKey(conversationId, header)
		if (header.isSavedMessages) {
			if (resolvedConversationId != null) activityStore?.clearLocalActivity(resolvedConversationId)
			if (draftKey.isNotEmpty()) activityStore?.setDraftTextByKey(draftKey, "")
			return
		}
		if (isEditing) {
			if (resolvedConversationId != null) activityStore?.setDraftText(resolvedConversationId, "")
			if (draftKey.isNotEmpty()) activityStore?.setDraftTextByKey(draftKey, "")
			return
		}
		val isBlankInput = input.isBlank()
		val localActivity = localRecordingStartedAtMillis?.let { startedAt ->
			ConversationActivityState.RecordingVideo(startedAtMillis = startedAt)
		} ?: if (!isBlankInput) {
			ConversationActivityState.Draft(
				updatedAtMillis = currentChatEpochMillis(),
				text = input.trim(),
			)
		} else {
			ConversationActivityState.None
		}
		if (resolvedConversationId != null) {
			activityStore?.setDraftText(resolvedConversationId, input)
			if (localActivity is ConversationActivityState.None) {
				activityStore?.clearLocalActivity(resolvedConversationId)
			} else {
				activityStore?.setLocalActivity(resolvedConversationId, localActivity)
			}
		}
		if (draftKey.isNotEmpty()) {
			activityStore?.setDraftTextByKey(draftKey, input)
		}
	}

	private fun restoredDraftInput(
		conversationId: Long?,
		header: ChatThreadHeaderModel,
	): String {
		if (header.isSavedMessages) return ""
		val draftKey = draftLookupKey(conversationId, header)
		val textByKey = if (draftKey.isNotEmpty()) activityStore?.getDraftTextByKey(draftKey).orEmpty() else ""
		if (textByKey.isNotBlank()) return textByKey
		return activityStore?.getDraftText(conversationId).orEmpty()
	}

	private fun clearRecordingActivityForCurrentConversation() {
		if (localRecordingStartedAtMillis == null) return
		when (val current = _state.value) {
			is DirectChatScreenState.HasData -> activityStore?.clearLocalActivity(current.conversationId)
			is DirectChatScreenState.NoMessages -> current.conversationId
				?.takeIf { it > 0L }
				?.let { activityStore?.clearLocalActivity(it) }
			else -> Unit
		}
	}

	private fun mergeLiveHeader(header: ChatThreadHeaderModel): ChatThreadHeaderModel {
		if (header.isSavedMessages) {
			return header.withSavedMessagesSemantics()
		}
		return header.copy(subtitle = resolveLiveHeaderSubtitle() ?: header.subtitle)
	}

	private fun removeMessageFromState(messageId: Long) {
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val remainingMessages = state.messages.filterNot { it.id == messageId }
					if (remainingMessages.isEmpty()) {
						DirectChatScreenState.NoMessages(
							conversationId = state.conversationId,
							header = state.header,
							input = state.input,
							scrollToBottomRequestToken = state.scrollToBottomRequestToken,
							peerIsOnline = state.peerIsOnline,
							peerLastSeenAtMillis = state.peerLastSeenAtMillis,
						)
					} else {
						state.copy(messages = remainingMessages)
					}
				}
				else -> state
			}
		}
	}

	private fun removeMessageImmediately(
		conversationId: Long,
		message: ChatMessageItemModel,
	) {
		cancelPendingOutgoingMessage(message)
		removeMessageFromState(message.id)
		if (
			message.id > 0L &&
			message.deliveryState != ChatDeliveryState.SENDING &&
			message.deliveryState != ChatDeliveryState.FAILED
		) {
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteMessage(conversationId, message.id)
						.onFailure {
							restoreDeletedMessage(message, conversationId)
							_events.tryEmit(Event.ShowMessage(it.message ?: "delete failed"))
						}
				}
			}
		}
	}

	private fun cancelPendingOutgoingMessage(message: ChatMessageItemModel) {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return
		addCancelledClientMessageId(normalizedClientMessageId)
		localVideoCircleMessages.remove(normalizedClientMessageId)
		localVoiceMessages.remove(normalizedClientMessageId)
		localImageMessages.remove(normalizedClientMessageId)
		val pendingEntry = pendingOutgoingClientMessageIds.entries
			.firstOrNull { (_, clientMessageId) -> clientMessageId == normalizedClientMessageId }
		pendingEntry?.let { entry ->
			pendingOutgoingSendJobs.remove(entry.key)?.cancel()
			pendingOutgoingClientMessageIds.remove(entry.key)
		}
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
	}

	private fun restoreDeletedMessage(
		message: ChatMessageItemModel,
		conversationId: Long?,
	) {
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val restoredMessages = upsertMessage(state.messages, message)
					state.copy(
						messages = applyPeerReadState(restoredMessages, state.peerLastReadMessageId),
						scrollToBottomRequestToken = nextScrollRequestToken(),
					)
				}
				is DirectChatScreenState.NoMessages -> {
					val resolvedConversationId = state.conversationId ?: conversationId
					if (resolvedConversationId == null) {
						state
					} else {
						DirectChatScreenState.HasData(
							conversationId = resolvedConversationId,
							header = state.header,
							messages = listOf(message),
							input = state.input,
							scrollToBottomRequestToken = nextScrollRequestToken(),
							peerIsOnline = state.peerIsOnline,
							peerLastSeenAtMillis = state.peerLastSeenAtMillis,
						)
					}
				}
				else -> state
			}
		}
	}

	private fun schedulePendingDeleteCommit(
		message: ChatMessageItemModel,
		conversationId: Long,
	) {
		chatActions.schedulePendingDeleteCommit(message, conversationId) { commitMessage ->
			restoreDeletedMessage(commitMessage, conversationId)
		}
	}

	private fun flushPendingDeleteNow() {
		chatActions.flushPendingDeleteNow()
	}

	private fun resolveLiveHeaderSubtitle(): String? {
		val lastSeenAtMillis = latestPresenceState.lastSeenAtMillis
		return when {
			latestTypingState.isTyping -> {
				val isRecordingVideo = latestTypingState.action == "recording_video"
				if (latestTypingState.displayName.isNotBlank()) {
					if (isRecordingVideo) "${latestTypingState.displayName} записывает видео…" else "${latestTypingState.displayName} печатает…"
				} else {
					if (isRecordingVideo) "записывает видео…" else "печатает…"
				}
			}
			latestPresenceState.isOnline || lastSeenAtMillis != null || hasObservedPresenceState -> formatPresenceStatusLabel(
				isOnline = latestPresenceState.isOnline,
				lastSeenAtMillis = lastSeenAtMillis,
			)
			else -> null
		}
	}

	private fun updateUnseenIncomingMessages(
		message: ChatMessageItemModel,
		shouldAutoScrollToBottom: Boolean,
	) {
		if (message.isOutgoing || message.isDeleted || message.id <= 0L) return
		val isVisible = latestViewportSnapshot?.visibleMessageIds?.contains(message.id) == true
		if (shouldAutoScrollToBottom || isVisible) {
			readTracker.onBottomReached { badgeCount ->
				updateScrollBadgeCount(badgeCount)
			}
		} else {
			readTracker.registerIncomingMessage(message.id, message.isOutgoing) { badgeCount ->
				updateScrollBadgeCount(badgeCount)
			}
		}
	}

	private fun updateScrollBadgeCount(badgeCount: Int = readTracker.unseenIncomingMessageIds.size) {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					scrollToBottomBadgeCount = badgeCount,
				)
				else -> current
			}
		}
	}

	private fun markReadUpToIfNeeded(
		conversationId: Long,
		messageId: Long,
	) {
		readTracker.markReadUpToIfNeeded(conversationId, messageId)
	}

	private fun flushReadAndOpenAnchorOnClose() {
		val current = _state.value as? DirectChatScreenState.HasData
		readTracker.flushReadAndOpenAnchorOnClose(current?.conversationId)
	}

	private fun clearAnchorRestoreLock(messageId: Long) {
		if (messageId <= 0L) return
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> {
					if (current.anchorRequestMessageId == messageId && current.keepAnchorRequest) {
						current.copy(keepAnchorRequest = false)
					} else {
						current
					}
				}
				else -> current
			}
		}
	}

	private fun scheduleOutgoingTyping(text: String) {
		val conversationId = (_state.value as? DirectChatScreenState.HasData)?.conversationId ?: return
		typingCoordinator.scheduleOutgoingTyping(conversationId, text)
	}

	private fun stopOutgoingTyping(conversationId: Long) {
		typingCoordinator.stopOutgoingTyping(conversationId)
	}

	private fun resyncConversation() {
		val request = when (val current = _state.value) {
			is DirectChatScreenState.HasData -> DirectChatInitialRequest(
				peerUserId = current.header.peerUserId,
				peerDisplayName = current.header.title,
				peerAvatarUrl = current.header.avatarUrl,
				conversationId = current.conversationId,
				anchorMessageId = current.highlightedMessageId,
				isSavedMessages = current.header.isSavedMessages,
			)
			is DirectChatScreenState.NoMessages -> DirectChatInitialRequest(
				peerUserId = current.header.peerUserId,
				peerDisplayName = current.header.title,
				peerAvatarUrl = current.header.avatarUrl,
				conversationId = current.conversationId,
				isSavedMessages = current.header.isSavedMessages,
			)
			else -> null
		} ?: return
		scope.launch {
			val result = runCatching {
				withTimeout(LOAD_TIMEOUT_MS) {
					repository.loadInitial(request)
				}
			}.getOrElse {
				Result.failure(it)
			}
		result.onSuccess { snapshot ->
					_state.update { current ->
						when (current) {
							is DirectChatScreenState.HasData -> {
								if (snapshot.conversationId == null) {
									DirectChatScreenState.Error("chat conversation id is missing")
								} else {
									current.copy(
										conversationId = snapshot.conversationId,
										header = mergeLiveHeader(snapshot.header),
										messages = current.messages
											.filter(::isLocalOutgoingPending)
											.fold(mergeLocalVideoCircleMessages(snapshot.messages)) { messages, pending ->
												upsertMessage(messages, pending)
											},
										canLoadMore = snapshot.canLoadMore,
											nextBeforeMessageId = snapshot.nextBeforeMessageId,
											isLoadingMore = false,
											highlightedMessageId = snapshot.highlightedMessageId ?: current.highlightedMessageId,
											anchorRequestMessageId = snapshot.openAnchorMessageId ?: current.anchorRequestMessageId,
											anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
											anchorRequestToken = if (snapshot.openAnchorMessageId != null) nextAnchorRequestToken() else current.anchorRequestToken,
											keepAnchorRequest = snapshot.keepOpenAnchorLocked,
										)
							}
						}
						is DirectChatScreenState.NoMessages -> {
							if (snapshot.messages.isEmpty()) {
								current.copy(
									conversationId = snapshot.conversationId,
									header = mergeLiveHeader(snapshot.header),
									peerIsOnline = latestPresenceState.resolveIsOnline(),
									peerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
								)
								} else if (snapshot.conversationId == null) {
									DirectChatScreenState.Error("chat conversation id is missing")
								} else {
									DirectChatScreenState.HasData(
										conversationId = snapshot.conversationId,
										header = mergeLiveHeader(snapshot.header),
										messages = mergeLocalVideoCircleMessages(snapshot.messages),
										canLoadMore = snapshot.canLoadMore,
										nextBeforeMessageId = snapshot.nextBeforeMessageId,
						highlightedMessageId = snapshot.highlightedMessageId,
										anchorRequestMessageId = snapshot.openAnchorMessageId,
										anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
										anchorRequestToken = if (snapshot.openAnchorMessageId != null) nextAnchorRequestToken() else 0L,
										keepAnchorRequest = snapshot.keepOpenAnchorLocked,
										peerIsOnline = latestPresenceState.resolveIsOnline(),
										peerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
									)
							}
						}
						else -> current
					}
				}
				}.onFailure {
				_events.tryEmit(Event.ShowMessage(it.message ?: "chat resync failed"))
			}
		}
	}
}
