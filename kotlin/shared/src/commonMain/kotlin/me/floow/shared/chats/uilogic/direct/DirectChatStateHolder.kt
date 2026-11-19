package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.floow.domain.utils.Logger
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatReplyDraft
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.model.ConversationActivityState
import me.floow.shared.chats.uilogic.ConversationActivityStore
import me.floow.shared.chats.uilogic.direct.coordinator.ChatActions
import me.floow.shared.chats.uilogic.direct.coordinator.ChatLoader
import me.floow.shared.chats.uilogic.direct.coordinator.ChatReadTracker
import me.floow.shared.chats.uilogic.direct.coordinator.ChatRealtimeCoordinator
import me.floow.shared.chats.uilogic.direct.coordinator.ChatTypingCoordinator
import me.floow.shared.media.outbox.MediaOutboxEngine
import me.floow.shared.media.outbox.MediaOutboxTask
import me.floow.uikit.chat.model.ChatViewportSnapshot

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
        val hasNewerMessages: Boolean = false,
        val historyUpperBound: Long? = null,
        val provisionalHistory: Boolean = false,
        val firstUnreadMessageId: Long? = null,
        val unreadBoundaryMessageId: Long? = null,
		val sending: Boolean = false,
		val replyDraft: ChatReplyDraft? = null,
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
        val messageEntryTimes: Map<String, Long> = emptyMap(),
		val conversationActivityState: ConversationActivityState = ConversationActivityState.None,
	) : DirectChatScreenState
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
	private val logger: Logger? = null,
	private val mediaOutboxEngine: MediaOutboxEngine? = null,
	private val mediaDownloadController: me.floow.domain.media.MediaDownloadController? = null,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
	sealed interface Event {
		data class ShowMessage(val message: String) : Event
	}

	data class LocalImageDraft(
		val localPath: String,
		val caption: String? = null,
		val width: Int = 0,
		val height: Int = 0,
		val thumbnailUrl: String? = null,
	)

	private companion object {
		const val LOAD_TIMEOUT_MS = 15_000L
	}

	private val _state = MutableStateFlow<DirectChatScreenState>(DirectChatScreenState.Loading)
	val state: StateFlow<DirectChatScreenState> = _state.asStateFlow()

	private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
	val events: SharedFlow<Event> = _events.asSharedFlow()

	private var readStateJob: Job? = null
    private var pinnedRefreshJob: Job? = null
    private var pinsConversationId: Long? = null
    private var observedPins: List<ChatMessageItemModel> = emptyList()
    private var resyncJob: Job? = null

    val timelineReducer: ChatTimelineReducer = ChatTimelineReducer()

	private val typingCoordinator: ChatTypingCoordinator = ChatTypingCoordinator(repository, scope)
	private val readTracker: ChatReadTracker = ChatReadTracker(repository, scope)
	private val chatActions: ChatActions = ChatActions(repository, scope) { message ->
		_events.tryEmit(Event.ShowMessage(message))
	}
	private val chatLoader: ChatLoader = ChatLoader(repository, logger)
	private val realtimeCoordinator: ChatRealtimeCoordinator = ChatRealtimeCoordinator(
		repository = repository,
		realtimeContract = realtimeContract,
		presenceContract = presenceContract,
		scope = scope,
	)

	val scrollAnchorController: ChatScrollAnchorController = ChatScrollAnchorController(
		repository = repository,
		readTracker = readTracker,
		chatLoader = chatLoader,
		timelineReducer = timelineReducer,
		stateFlow = _state,
		eventsFlow = _events,
		scope = scope,
		logger = logger,
	)

	val mediaController: ChatMediaController = ChatMediaController(
		repository = repository,
		mediaOutboxEngine = mediaOutboxEngine,
		mediaDownloadController = mediaDownloadController,
		timelineReducer = timelineReducer,
		stateFlow = _state,
		scope = scope,
		logger = logger,
		nextScrollRequestToken = { scrollAnchorController.nextScrollRequestToken() },
		onActivityChanged = {
			_state.update { current ->
				when (current) {
					is DirectChatScreenState.HasData -> current.copy(
						conversationActivityState = inputController.resolveConversationActivityState(
							header = current.header,
							messages = current.messages,
							input = current.input,
						),
					)
					is DirectChatScreenState.NoMessages -> current.copy(
						conversationActivityState = inputController.resolveConversationActivityState(
							header = current.header,
							messages = emptyList(),
							input = current.input,
						),
					)
					else -> current
				}
			}
			inputController.publishLocalActivityFromCurrentState()
		},
		activeConversationIdOrNull = { activeConversationIdOrNull() },
	)

	val inputController: ChatInputInteractionController = ChatInputInteractionController(
		repository = repository,
		activityStore = activityStore,
		typingCoordinator = typingCoordinator,
		timelineReducer = timelineReducer,
		stateFlow = _state,
		scope = scope,
		localRecordingStartedAtMillisProvider = { mediaController.localRecordingStartedAtMillis },
		activeRequestProvider = { activeRequest },
	)

	val mediaOutboxTasks: StateFlow<Map<String, MediaOutboxTask>> = mediaController.mediaOutboxTasks
	val mediaDownloadStates: StateFlow<Map<String, me.floow.domain.media.MediaDownloadState>> =
		mediaController.mediaDownloadStates

	var activeRequest: DirectChatInitialRequest? = null
		private set

	private var loadJob: Job? = null
	private var remoteRefreshRetryJob: Job? = null
	private val pendingOutgoingClientMessageIds = mutableMapOf<Long, String>()
	private val pendingOutgoingSendJobs = mutableMapOf<Long, Job>()

	fun downloadImage(url: String) {
		mediaController.downloadImage(url)
	}

	fun cancelImageDownload(url: String) {
		mediaController.cancelImageDownload(url)
	}

	fun updateInput(text: String) {
		inputController.updateInput(text)
	}

	fun setLocalRecordingActive(isActive: Boolean) {
		mediaController.setLocalRecordingActive(isActive)
	}

	fun addReply(messageId: Long) {
		inputController.addReply(messageId)
	}

	fun clearReply() {
		inputController.clearReply()
	}

	fun startEditing(messageId: Long) {
		inputController.startEditing(messageId)
	}

	fun cancelEditing() {
		inputController.cancelEditing()
	}

    fun requestScrollToBottom() {
        val current = _state.value as? DirectChatScreenState.HasData
        if (current == null || (!current.hasNewerMessages && !current.provisionalHistory)) {
            scrollAnchorController.invalidateWindow()
            _state.update { if (it is DirectChatScreenState.HasData) it.copy(
                isLoadingMore = false, keepAnchorRequest = false, highlightedMessageId = null,
            ) else it }
            scrollAnchorController.requestScrollToBottom()
            return
        }
        val generation = scrollAnchorController.invalidateWindow()
        _state.update { if (it is DirectChatScreenState.HasData) it.copy(isLoadingMore = false) else it }
        val request = activeRequest?.copy(
            conversationId = current.conversationId,
            anchorMessageId = null,
            openMode = me.floow.shared.chats.model.ChatOpenMode.LATEST,
        ) ?: return
        scope.launch {
            chatLoader.loadInitialRemote(request).onSuccess { snapshot ->
                if (generation != scrollAnchorController.windowGeneration || activeConversationIdOrNull() != current.conversationId) return@onSuccess
                val loaded = snapshotToScreenState(snapshot, showUnreadIndicators = true)
                _state.update { timelineReducer.mergeFreshSnapshotIntoCurrent(it, loaded) }
                scrollAnchorController.requestScrollToBottom()
            }.onFailure {
                if (generation == scrollAnchorController.windowGeneration) {
                    _events.tryEmit(Event.ShowMessage("Не удалось загрузить последние сообщения"))
                }
            }
        }
    }

    fun onUserStartedScroll() {
        failedOlderEdge = null
        if ((_state.value as? DirectChatScreenState.HasData)?.isLoadingMore != true) lastNewerEdgeRequested = null
        scrollAnchorController.onUserStartedScroll()
    }

	fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
        scrollAnchorController.onViewportSnapshotChanged(snapshot)
        val state = _state.value as? DirectChatScreenState.HasData ?: return
        if (snapshot.isAtBottom && !state.keepAnchorRequest && state.hasNewerMessages && !state.provisionalHistory) {
            loadNewer(state)
        }
    }

    private fun followLatestAfterSend() {
        if ((_state.value as? DirectChatScreenState.HasData)?.hasNewerMessages == true) requestScrollToBottom()
    }

    private var lastNewerEdgeRequested: Pair<Long, Long>? = null
    private var failedOlderEdge: Pair<Long, Long?>? = null

    private fun loadNewer(current: DirectChatScreenState.HasData) {
        if (current.isLoadingMore) return
        val newest = current.historyUpperBound ?: return
        val generation = scrollAnchorController.windowGeneration
        val requestKey = generation to newest
        if (requestKey == lastNewerEdgeRequested) return
        lastNewerEdgeRequested = requestKey
        _state.update { if (it is DirectChatScreenState.HasData) it.copy(isLoadingMore = true) else it }
        scope.launch {
            val page = repository.loadNewer(current.conversationId, newest).getOrNull()
            if (generation != scrollAnchorController.windowGeneration) return@launch
            _state.update { state ->
                if (state !is DirectChatScreenState.HasData || state.conversationId != current.conversationId ||
                    state.historyUpperBound != newest) return@update state
                if (page == null) state.copy(isLoadingMore = false) else state.copy(
                    messages = timelineReducer.mergeMessages(state.messages + page.items, inputController.inFlightEdits),
                    hasNewerMessages = page.hasNewerMessages,
                    historyUpperBound = if (page.hasNewerMessages) page.items.maxOfOrNull { it.id } ?: newest else null,
                    isLoadingMore = false,
                )
            }
        }
    }

	fun onScrollSettled() {
		scrollAnchorController.onScrollSettled()
	}

	fun onAnchorRestoreSettled(messageId: Long, offsetPx: Int) {
		scrollAnchorController.onAnchorRestoreSettled(messageId, offsetPx)
	}

	fun onAnchorRestoreTimedOut(messageId: Long) {
		scrollAnchorController.onAnchorRestoreTimedOut(messageId)
	}

	fun retryMediaMessage(clientMessageId: String): Boolean {
		return mediaController.retryMediaMessage(clientMessageId)
	}

	fun addLocalVideoCircle(clip: RecordedClip, enqueueOutbox: Boolean = true): String {
		return mediaController.addLocalVideoCircle(clip, enqueueOutbox).also { if (it.isNotEmpty()) followLatestAfterSend() }
	}

	fun updateLocalVideoCircleClip(clientMessageId: String, clip: RecordedClip) {
		mediaController.updateLocalVideoCircleClip(clientMessageId, clip)
	}

	fun markLocalVideoCircleUploading(clientMessageId: String) {
		mediaController.markLocalVideoCircleUploading(clientMessageId)
	}

	fun markLocalVideoCircleFailed(clientMessageId: String) {
		mediaController.markLocalVideoCircleFailed(clientMessageId)
	}

	fun cancelLocalVideoCircle(clientMessageId: String): Boolean {
		return mediaController.cancelLocalVideoCircle(clientMessageId)
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
		mediaController.resolveLocalVideoCircleSent(
			clientMessageId = clientMessageId,
			serverMessage = serverMessage,
			remoteUrl = remoteUrl,
			remoteThumbnailUrl = remoteThumbnailUrl,
			durationMs = durationMs,
			width = width,
			height = height,
		)
	}

	fun addLocalVoice(clip: RecordedVoiceClip): String {
		return mediaController.addLocalVoice(clip).also { if (it.isNotEmpty()) followLatestAfterSend() }
	}

	fun markLocalVoiceUploading(clientMessageId: String) {
		mediaController.markLocalVoiceUploading(clientMessageId)
	}

	fun markLocalVoiceFailed(clientMessageId: String) {
		mediaController.markLocalVoiceFailed(clientMessageId)
	}

	fun cancelLocalVoice(clientMessageId: String): Boolean {
		return mediaController.cancelLocalVoice(clientMessageId)
	}

	fun resolveLocalVoiceSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		durationMs: Long,
		waveform: List<Int>,
	) {
		mediaController.resolveLocalVoiceSent(
			clientMessageId = clientMessageId,
			serverMessage = serverMessage,
			remoteUrl = remoteUrl,
			durationMs = durationMs,
			waveform = waveform,
		)
	}

	fun addLocalImages(
		items: List<LocalImageDraft>,
		groupId: String?,
		fallbackConversationId: Long? = null,
		fallbackHeader: ChatThreadHeaderModel? = null,
		replyToMessageId: Long? = null,
		enqueueOutbox: Boolean = true,
	): List<String> {
		return mediaController.addLocalImages(
			items = items,
			groupId = groupId,
			fallbackConversationId = fallbackConversationId,
			fallbackHeader = fallbackHeader,
			replyToMessageId = replyToMessageId,
			enqueueOutbox = enqueueOutbox,
		).also { if (it.isNotEmpty()) followLatestAfterSend() }
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
		return mediaController.addLocalImage(
			localPath = localPath,
			caption = caption,
			groupId = groupId,
			position = position,
			width = width,
			height = height,
			fallbackConversationId = fallbackConversationId,
			fallbackHeader = fallbackHeader,
			replyToMessageId = replyToMessageId,
		)
	}

	fun updateLocalImageSource(
		clientMessageId: String,
		localPath: String,
		width: Int,
		height: Int,
		thumbnailUrl: String? = null,
	) {
		mediaController.updateLocalImageSource(clientMessageId, localPath, width, height, thumbnailUrl)
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
		mediaController.enqueuePreparedImageOutbox(
			clientMessageId = clientMessageId,
			filePath = filePath,
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
		mediaController.resolveLocalImageSent(
			clientMessageId = clientMessageId,
			serverMessage = serverMessage,
			remoteUrl = remoteUrl,
			width = width,
			height = height,
			groupId = groupId,
			position = position,
		)
	}

	fun markLocalImageFailed(clientMessageId: String) {
		mediaController.markLocalImageFailed(clientMessageId)
	}

	fun markLocalImageUploading(clientMessageId: String) {
		mediaController.markLocalImageUploading(clientMessageId)
	}

	fun cancelLocalImage(clientMessageId: String): Boolean {
		return mediaController.cancelLocalImage(clientMessageId)
	}

	fun togglePin(messageId: Long, isPinned: Boolean) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		inputController.togglePin(current.conversationId, messageId, isPinned, chatActions)
	}

	fun pinMessage(messageId: Long) {
		togglePin(messageId, true)
	}

	fun unpinMessage(messageId: Long) {
		togglePin(messageId, false)
	}

	fun clearHighlight() {
		scrollAnchorController.clearHighlight()
	}

	fun highlightRequest(messageId: Long) {
		scrollAnchorController.highlightRequest(messageId)
	}

	fun highlightRequest(messageId: String) {
		scrollAnchorController.highlightRequest(messageId)
	}

	fun jumpToMessage(messageId: Long) {
		scrollAnchorController.jumpToMessage(messageId)
	}

	fun activeConversationIdOrNull(): Long? {
		return when (val current = _state.value) {
			is DirectChatScreenState.HasData -> current.conversationId.takeIf { it > 0L }
			is DirectChatScreenState.NoMessages -> current.conversationId?.takeIf { it > 0L }
			else -> null
		}
	}

    fun dispose() {
        disposeActiveWork(clearLocalVideoCircles = true)
        activeRequest = null
    }

	private fun disposeActiveWork(clearLocalVideoCircles: Boolean) {
		scrollAnchorController.flushReadAndOpenAnchorOnClose()
		loadJob?.cancel()
		loadJob = null
		remoteRefreshRetryJob?.cancel()
		remoteRefreshRetryJob = null
        realtimeCoordinator.stopAll()
        readStateJob?.cancel()
        readStateJob = null
        pinnedRefreshJob?.cancel()
        pinnedRefreshJob = null
        pinsConversationId = null
        observedPins = emptyList()
        resyncJob?.cancel()
        resyncJob = null
		typingCoordinator.reset()
		inputController.reset()
		inputController.clearRecordingActivityForCurrentConversation()
		scrollAnchorController.reset()
		readTracker.reset()
		mediaController.dispose(clearLocalMedia = clearLocalVideoCircles)
		flushPendingDeleteNow()
	}

	fun load(request: DirectChatInitialRequest) {
		activeRequest = request
		disposeActiveWork(clearLocalVideoCircles = false)
        val generation = scrollAnchorController.windowGeneration
        _state.value = DirectChatScreenState.Loading
        loadJob = scope.launch {
            val cachedSnapshot = chatLoader.loadCachedInitial(request)
            if (activeRequest != request || generation != scrollAnchorController.windowGeneration) return@launch
            if (cachedSnapshot != null) {
                _state.value = snapshotToScreenState(cachedSnapshot, showUnreadIndicators = true)
                inputController.publishLocalActivityFromCurrentState()
                observePinnedMessages(cachedSnapshot.conversationId)
                observePresence(cachedSnapshot.header)
            }

			chatLoader.loadInitialRemote(request)
				.onSuccess { snapshot ->
                    if (generation != scrollAnchorController.windowGeneration) return@onSuccess
                    val pendingRead = snapshot.conversationId?.let { repository.getLocalLastReadMessageId(it) } ?: 0L
                    if (activeRequest != request || generation != scrollAnchorController.windowGeneration) return@onSuccess
                    readTracker.initializeLastMarkedRead(maxOf(snapshot.lastReadMessageId, pendingRead))
                    _state.update { current ->
						val loadedState = snapshotToScreenState(
							snapshot = snapshot,
							showUnreadIndicators = true,
						)
						if (cachedSnapshot != null) {
							timelineReducer.mergeFreshSnapshotIntoCurrent(current, loadedState)
						} else {
							loadedState
						}
					}
					inputController.publishLocalActivityFromCurrentState()
					snapshot.conversationId?.takeIf { it > 0L }?.let { conversationId ->
						mediaController.flushPendingLocalVideoOutbox(conversationId, snapshot.header)
					}
					observeRealtime(snapshot.conversationId)
					observeLocalMessages(snapshot.conversationId)
					observePinnedMessages(snapshot.conversationId)
					observePresence(snapshot.header)
				}
                .onFailure {
                    if (activeRequest != request || generation != scrollAnchorController.windowGeneration) return@onFailure
                    val message = if (it is kotlinx.coroutines.TimeoutCancellationException) {
						"chat load timed out"
					} else {
						it.message ?: "chat load failed"
					}
					logger?.d(
						tag = "DirectChatStateHolder",
						message = "load failed conversationId=${request.conversationId} peer=${request.peerUserId} cached=${cachedSnapshot != null} reason=$message"
					)
                    if (cachedSnapshot == null || it is ChatMessageUnavailableException) {
                        _state.value = DirectChatScreenState.Error(message)
                        if (it is ChatMessageUnavailableException) _events.tryEmit(Event.ShowMessage(message))
                    }
                    if (it !is ChatMessageUnavailableException && (cachedSnapshot != null || repository.networkOnline?.value == false)) {
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
		val generation = scrollAnchorController.windowGeneration
        remoteRefreshRetryJob = scope.launch {
            delay(delayMs)
            repository.networkOnline?.first { it }
            val snapshot = chatLoader.loadInitialRemote(request).getOrNull() ?: return@launch
            if (activeRequest != request || generation != scrollAnchorController.windowGeneration) return@launch
            readTracker.initializeLastMarkedRead(snapshot.lastReadMessageId)
            _state.update { current ->
				val loadedState = snapshotToScreenState(
					snapshot = snapshot,
					showUnreadIndicators = true,
				)
				timelineReducer.mergeFreshSnapshotIntoCurrent(current, loadedState)
			}
			snapshot.conversationId?.takeIf { it > 0L }?.let { conversationId ->
				readTracker.initializeLastMarkedRead(repository.getLocalLastReadMessageId(conversationId))
			}
			observeRealtime(snapshot.conversationId)
			observeLocalMessages(snapshot.conversationId)
			observePinnedMessages(snapshot.conversationId)
			observePresence(snapshot.header)
		}
	}

	private fun snapshotToScreenState(
		snapshot: ChatThreadSnapshot,
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
		val normalizedMessages = timelineReducer.applyPeerReadState(
			mediaController.mergeLocalMediaMessages(snapshot.messages),
			peerLastReadMessageId,
		)
		mediaController.seedMediaDownloadStates(normalizedMessages)
		val normalizedPinnedMessages = inputController.normalizePinnedMessages(
			messages = if (pinsConversationId == snapshot.conversationId) observedPins else snapshot.pinnedMessages,
			peerLastReadMessageId = peerLastReadMessageId,
		)
		val resolvedPeerIsOnline = inputController.latestPresenceState.resolveIsOnline()
		val resolvedPeerLastSeenAtMillis = inputController.latestPresenceState.resolveLastSeenAtMillis()
		val restoredInput = inputController.restoredDraftInput(snapshot.conversationId, snapshot.header)
		scrollAnchorController.setPendingAnchorDiagnostics(
			snapshot.conversationId,
			snapshot.openAnchorMessageId,
			snapshot.openAnchorOffsetPx,
		)
		return if (normalizedMessages.isEmpty()) {
			DirectChatScreenState.NoMessages(
				conversationId = snapshot.conversationId,
				header = inputController.mergeLiveHeader(snapshot.header),
				input = restoredInput,
				scrollToBottomRequestToken = 0L,
				peerIsOnline = resolvedPeerIsOnline,
				peerLastSeenAtMillis = resolvedPeerLastSeenAtMillis,
			)
		} else if (snapshot.conversationId == null) {
			if (snapshot.messages.isEmpty()) {
				DirectChatScreenState.HasData(
					conversationId = ChatTimelineReducer.LOCAL_ONLY_CONVERSATION_ID,
					header = inputController.mergeLiveHeader(snapshot.header),
					messages = normalizedMessages,
					input = restoredInput,
					pinnedMessages = normalizedPinnedMessages,
					canLoadMore = snapshot.canLoadMore,
                    hasNewerMessages = snapshot.hasNewerMessages,
                    historyUpperBound = snapshot.messages.maxOfOrNull { it.id }?.takeIf { snapshot.hasNewerMessages },
                    provisionalHistory = snapshot.provisionalHistory,
                    firstUnreadMessageId = snapshot.firstUnreadMessageId,
                    unreadBoundaryMessageId = snapshot.firstUnreadMessageId,
                    nextBeforeMessageId = snapshot.nextBeforeMessageId,
					highlightedMessageId = snapshot.highlightedMessageId,
					anchorRequestMessageId = snapshot.openAnchorMessageId,
					anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
					anchorRequestToken = if (snapshot.openAnchorMessageId != null) scrollAnchorController.nextAnchorRequestToken() else 0L,
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
				header = inputController.mergeLiveHeader(snapshot.header),
				messages = normalizedMessages,
				input = restoredInput,
				pinnedMessages = normalizedPinnedMessages,
				canLoadMore = snapshot.canLoadMore,
                hasNewerMessages = snapshot.hasNewerMessages,
                    historyUpperBound = snapshot.messages.maxOfOrNull { it.id }?.takeIf { snapshot.hasNewerMessages },
                provisionalHistory = snapshot.provisionalHistory,
                firstUnreadMessageId = snapshot.firstUnreadMessageId,
                unreadBoundaryMessageId = snapshot.firstUnreadMessageId,
                nextBeforeMessageId = snapshot.nextBeforeMessageId,
				highlightedMessageId = snapshot.highlightedMessageId,
				anchorRequestMessageId = snapshot.openAnchorMessageId,
				anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
				anchorRequestToken = if (snapshot.openAnchorMessageId != null) scrollAnchorController.nextAnchorRequestToken() else 0L,
				keepAnchorRequest = snapshot.keepOpenAnchorLocked,
				scrollToBottomBadgeCount = 0,
				peerLastReadMessageId = peerLastReadMessageId,
				showUnreadIndicators = resolvedShowUnreadIndicators,
				peerIsOnline = resolvedPeerIsOnline,
				peerLastSeenAtMillis = resolvedPeerLastSeenAtMillis,
			)
		}
	}

	fun retryMessage(messageId: Long) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		val message = current.messages.firstOrNull { it.id == messageId } ?: return
		if (message.deliveryState != ChatDeliveryState.FAILED || !message.isOutgoing) return
		val clientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		if (clientMessageId != null && (message.content is ChatMessageContent.Image || message.content is ChatMessageContent.Voice || message.content is ChatMessageContent.VideoCircle)) {
			mediaController.retryMediaMessage(clientMessageId)
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

	fun sendOrEdit(): String? {
		val current = _state.value
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
			else -> return null
		} ?: run {
			_events.tryEmit(Event.ShowMessage("chat conversation id is missing"))
			return null
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
        val replyDraft = currentHasData?.replyDraft
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
		}
		if (text.isEmpty()) return null

		if (editingMessageId == null) {
			val clientId = sendOptimisticMessage(
				conversationId = conversationId,
				header = header,
				text = text,
				replyToMessageId = replyDraft?.messageId,
                replyToMessageText = replyDraft?.preview,
            )
            followLatestAfterSend()
            return clientId
		}
		if (originalEditingMessage == null) {
			_events.tryEmit(Event.ShowMessage("message action failed"))
			return null
		}

		inputController.stopOutgoingTyping(conversationId)
		inputController.inFlightEdits[editingMessageId] = text
		val optimisticEditedMessage = originalEditingMessage.copy(text = text)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = state.messages.map { existing ->
						if (existing.id == editingMessageId) optimisticEditedMessage else existing
					}
					state.copy(
						messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
						input = "",
						sending = true,
						replyDraft = null,
						editingMessageId = null,
					)
				}
				else -> state
			}
		}
		inputController.publishLocalActivityFromCurrentState()
		scope.launch {
			val result = repository.editMessage(conversationId, editingMessageId, text)

			result.onSuccess { message ->
				inputController.inFlightEdits.remove(editingMessageId)
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val updatedMessages = state.messages.map { existing -> if (existing.id == message.id) message else existing }
							state.copy(
								messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
								sending = false,
							)
						}
						is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
							conversationId = conversationId,
							header = header,
							messages = listOf(message),
							scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
							peerIsOnline = state.peerIsOnline,
							peerLastSeenAtMillis = state.peerLastSeenAtMillis,
						)
						else -> state
					}
				}
				inputController.publishLocalActivityFromCurrentState()
			}.onFailure {
				inputController.inFlightEdits.remove(editingMessageId)
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val rolledBackMessages = state.messages.map { existing ->
								if (existing.id == originalEditingMessage.id) originalEditingMessage else existing
							}
							state.copy(
								messages = timelineReducer.applyPeerReadState(rolledBackMessages, state.peerLastReadMessageId),
								input = text,
								sending = false,
								editingMessageId = originalEditingMessage.id,
							)
						}
						else -> state
					}
				}
				inputController.publishLocalActivityFromCurrentState()
				_events.tryEmit(Event.ShowMessage(it.message ?: "message action failed"))
			}
		}
        return null
	}

	private fun sendOptimisticMessage(
		conversationId: Long,
		header: ChatThreadHeaderModel,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String? = null,
		existingMessageId: Long? = null,
		clientMessageId: String? = null,
	): String {
		val optimisticId = existingMessageId ?: timelineReducer.nextOptimisticMessageId()
		val normalizedClientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty)
			?: timelineReducer.nextClientMessageId(optimisticId)
		val currentMessages = when (val current = _state.value) {
			is DirectChatScreenState.HasData -> current.messages
			else -> emptyList()
		}
		val existingMessage = existingMessageId?.let { messageId ->
			currentMessages.firstOrNull { it.id == messageId }
		} ?: currentMessages.firstOrNull { it.clientMessageId?.trim() == normalizedClientMessageId }
		val createdAtMillis = existingMessage?.createdAtMillis
			?: timelineReducer.nextOutgoingCreatedAtMillis(currentMessages)
		val optimisticMessage = timelineReducer.buildOptimisticOutgoingMessage(
			header = header,
			id = optimisticId,
			clientMessageId = normalizedClientMessageId,
			text = text,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			createdAtMillis = createdAtMillis,
			deliveryState = ChatDeliveryState.SENDING,
		)
        val newEntry = existingMessageId == null && existingMessage == null
        inputController.stopOutgoingTyping(conversationId)
        _state.update { state ->
            when (state) {
                is DirectChatScreenState.HasData -> {
                    val messages = timelineReducer.upsertMessage(state.messages, optimisticMessage, inputController.inFlightEdits)
					state.copy(
						messages = messages,
                        messageEntryTimes = if (newEntry) state.messageEntryTimes.withEntry(optimisticMessage) else state.messageEntryTimes,
						input = "",
						replyDraft = null,
						editingMessageId = null,
						scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
						conversationActivityState = inputController.resolveConversationActivityState(state.header, messages, state.input),
					)
				}
				is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
					conversationId = conversationId,
					header = header,
					messages = listOf(optimisticMessage),
                    messageEntryTimes = if (newEntry) emptyMap<String, Long>().withEntry(optimisticMessage) else emptyMap(),
					input = "",
					replyDraft = null,
					editingMessageId = null,
					scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
					peerIsOnline = state.peerIsOnline,
					peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					conversationActivityState = inputController.resolveConversationActivityState(header, listOf(optimisticMessage), ""),
				)
				else -> state
			}
		}
		inputController.publishLocalActivityFromCurrentState()
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
				if (timelineReducer.isCancelledClientMessageId(normalizedClientMessageId)) {
					timelineReducer.removeCancelledClientMessageId(normalizedClientMessageId)
					return@onSuccess
				}
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val messages = timelineReducer.upsertMessage(
								state.messages,
								message.copy(
									clientMessageId = message.clientMessageId ?: normalizedClientMessageId,
									deliveryState = ChatDeliveryState.SENT,
									isOutgoing = true,
								),
								inputController.inFlightEdits,
							)
							state.copy(
								messages = messages,
								peerLastReadMessageId = state.peerLastReadMessageId,
							).let { updated ->
								val readMessages = timelineReducer.applyPeerReadState(updated.messages, updated.peerLastReadMessageId)
								updated.copy(
									messages = readMessages,
									conversationActivityState = inputController.resolveConversationActivityState(updated.header, readMessages, updated.input),
								)
							}
						}
						else -> state
					}
				}
			}.onFailure { error ->
				pendingOutgoingClientMessageIds.remove(optimisticId)
				if (timelineReducer.isCancelledClientMessageId(normalizedClientMessageId)) {
					timelineReducer.removeCancelledClientMessageId(normalizedClientMessageId)
					return@onFailure
				}
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val messages = timelineReducer.upsertMessage(
								state.messages,
								optimisticMessage.copy(deliveryState = ChatDeliveryState.FAILED),
								inputController.inFlightEdits,
							)
							state.copy(
								messages = messages,
							).let { updated ->
								val readMessages = timelineReducer.applyPeerReadState(updated.messages, updated.peerLastReadMessageId)
								updated.copy(
									messages = readMessages,
									conversationActivityState = inputController.resolveConversationActivityState(updated.header, readMessages, updated.input),
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
        return normalizedClientMessageId
	}

	fun loadMore() {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		if (current.isLoadingMore || !current.canLoadMore) return

        val generation = scrollAnchorController.windowGeneration
        val cursor = current.nextBeforeMessageId ?: current.messages.firstOrNull { it.id > 0L }?.id
        val requestKey = generation to cursor
        if (failedOlderEdge == requestKey) return
        _state.value = current.copy(isLoadingMore = true)
        scope.launch {
            chatLoader.loadMore(
				conversationId = current.conversationId,
				beforeMessageId = cursor,
			)
                .onSuccess { page ->
                    if (generation != scrollAnchorController.windowGeneration) return@onSuccess
                    mediaController.seedMediaDownloadStates(page.items)
					_state.update { state ->
						when (state) {
							is DirectChatScreenState.HasData -> state.copy(
								messages = timelineReducer.mergeMessages(page.items + state.messages, inputController.inFlightEdits),
								isLoadingMore = false,
								canLoadMore = page.canLoadMore,
								nextBeforeMessageId = page.nextBeforeMessageId,
							)
							else -> state
						}
					}
				}
                .onFailure {
                    if (generation != scrollAnchorController.windowGeneration) return@onFailure
                    failedOlderEdge = requestKey
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
						state.copy(
                        messages = remainingMessages.map { if (it.replyToMessageId in immediateDeleteIds) it.copy(replyToMessageText = null) else it },
                        replyDraft = state.replyDraft?.takeUnless { it.messageId in immediateDeleteIds },
                    )
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
						state.copy(messages = remainingMessages, replyDraft = state.replyDraft?.takeUnless { it.messageId == messageId })
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
		timelineReducer.addCancelledClientMessageId(normalizedClientMessageId)
		mediaController.removeLocalMessage(normalizedClientMessageId)
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
					val restoredMessages = timelineReducer.upsertMessage(state.messages, message, inputController.inFlightEdits)
					state.copy(
						messages = timelineReducer.applyPeerReadState(restoredMessages, state.peerLastReadMessageId),
						scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
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
							scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
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

	private fun observeRealtime(conversationId: Long?) {
		if (conversationId == null || realtimeContract == null) return
        readStateJob?.cancel()
        readStateJob = scope.launch {
            repository.observeReadState(conversationId).collect { read ->
                if (read == null || activeConversationIdOrNull() != conversationId) return@collect
                readTracker.initializeLastMarkedRead(read.lastReadMessageId)
                _state.update { current ->
                    if (current !is DirectChatScreenState.HasData || current.conversationId != conversationId) current
                    else current.copy(
                        firstUnreadMessageId = read.firstUnreadId,
                        unreadBoundaryMessageId = current.unreadBoundaryMessageId ?: read.firstUnreadId,
                    )
                }
                scrollAnchorController.latestViewportSnapshot?.let(scrollAnchorController::onViewportSnapshotChanged)
            }
        }
        var receivedHello = false
        realtimeCoordinator.observeRealtime(conversationId, afterSeq = currentMaxMessageId(conversationId)) { event ->
			when (event) {
                is ChatRealtimeEvent.Hello -> {
                    if (receivedHello) {
                        failedOlderEdge = null
                        lastNewerEdgeRequested = null
                        refreshPins(conversationId)
                        resyncConversation()
                    }
                    receivedHello = true
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
								if (normalizedClientMessageId != null && timelineReducer.isCancelledClientMessageId(normalizedClientMessageId)) {
									timelineReducer.removeCancelledClientMessageId(normalizedClientMessageId)
									return@update current
								}
								val nextPeerLastReadMessageId = maxOf(
									current.peerLastReadMessageId,
									event.peerLastReadMessageId ?: 0L,
								)
								val normalizedMessage = timelineReducer.normalizeRealtimeMessage(
									message = event.message,
									header = current.header,
									peerLastReadMessageId = nextPeerLastReadMessageId,
								)
								val wasAlreadyInTimeline = current.messages.any { existing ->
									timelineReducer.identityKey(existing) == timelineReducer.identityKey(normalizedMessage) ||
										(existing.id > 0L && existing.id == normalizedMessage.id)
								}
                                val windowStart = current.messages.firstOrNull { it.id > 0L }?.id ?: 0L
                                val windowEnd = current.historyUpperBound ?: current.messages.maxOfOrNull { it.id } ?: 0L
                                if (!wasAlreadyInTimeline && (!event.isNew || current.provisionalHistory ||
                                    normalizedMessage.id < windowStart ||
                                    (current.hasNewerMessages && normalizedMessage.id > windowEnd))
                                ) {
                                    if (event.isNew && normalizedMessage.id > windowEnd) {
                                        readTracker.registerIncomingMessage(normalizedMessage.id, normalizedMessage.isOutgoing) {}
                                    }
                                    return@update current.copy(scrollToBottomBadgeCount = readTracker.unseenIncomingMessageIds.size)
                                }
								val updated = timelineReducer.upsertMessage(current.messages, normalizedMessage, inputController.inFlightEdits)
								val shouldAutoScrollToBottom = !wasAlreadyInTimeline &&
									!current.keepAnchorRequest &&
									(
										normalizedMessage.isOutgoing ||
											(scrollAnchorController.latestViewportSnapshot?.isAtBottom != false)
										)
								scrollAnchorController.updateUnseenIncomingMessages(
									message = normalizedMessage,
									shouldAutoScrollToBottom = shouldAutoScrollToBottom || wasAlreadyInTimeline,
								)
								current.copy(
									messages = timelineReducer.applyPeerReadState(updated, nextPeerLastReadMessageId),
                                    messageEntryTimes = if (!wasAlreadyInTimeline && event.animateEntry &&
                                        !normalizedMessage.isOutgoing && normalizedMessage.id > windowEnd &&
                                        !current.keepAnchorRequest && normalizedMessage.content is ChatMessageContent.Text
                                    ) current.messageEntryTimes.withEntry(normalizedMessage) else current.messageEntryTimes,
									scrollToBottomRequestToken = if (shouldAutoScrollToBottom) {
										scrollAnchorController.nextScrollRequestToken()
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
									timelineReducer.normalizeRealtimeMessage(
										message = event.message,
										header = current.header,
										peerLastReadMessageId = event.peerLastReadMessageId ?: 0L,
									)
								),
								scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
								scrollToBottomBadgeCount = 0,
                                messageEntryTimes = if (event.animateEntry && event.message.content is ChatMessageContent.Text &&
                                    !timelineReducer.isOutgoingMessage(event.message.senderUserId, current.header)
                                ) emptyMap<String, Long>().withEntry(event.message) else emptyMap(),
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
								if (remaining.isEmpty() && !current.hasNewerMessages) {
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
                is ChatRealtimeEvent.MessagePinned, ChatRealtimeEvent.OwnReadUpdated -> Unit // Room already applied the event.
				is ChatRealtimeEvent.PeerReadUpdated -> {
					_state.update { current ->
						when (current) {
							is DirectChatScreenState.HasData -> current.copy(
								peerLastReadMessageId = event.messageId,
								messages = timelineReducer.applyPeerReadState(current.messages, event.messageId)
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
					typingCoordinator.handleIncomingTyping(event, currentHeader) { typingState ->
						inputController.updateTyping(typingState)
						inputController.applyHeaderSubtitle()
					}
				}
				is ChatRealtimeEvent.ConversationDeleted -> {
					disposeActiveWork(clearLocalVideoCircles = true)
					_state.value = DirectChatScreenState.Error("Диалог удален")
					_events.tryEmit(Event.ShowMessage("Диалог удален"))
				}
                ChatRealtimeEvent.ResyncRequired -> {
                    refreshPins(conversationId)
                    resyncConversation()
                }
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
        if (pinsConversationId == resolvedConversationId) return
        pinsConversationId = resolvedConversationId
        realtimeCoordinator.observePinnedMessages(resolvedConversationId) { messages ->
            observedPins = messages
            _state.update { current ->
				when (current) {
					is DirectChatScreenState.HasData -> {
						if (current.conversationId != resolvedConversationId) {
							current
						} else {
							current.copy(
								pinnedMessages = inputController.normalizePinnedMessages(
									messages = messages,
									peerLastReadMessageId = current.peerLastReadMessageId,
								),
								messages = inputController.mergePinnedFlagsIntoTimeline(
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
        refreshPins(resolvedConversationId)
    }

    private fun refreshPins(conversationId: Long) {
        pinnedRefreshJob?.cancel()
        pinnedRefreshJob = scope.launch { repository.loadPinnedMessages(conversationId) }
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
        _state.update { current ->
            when (current) {
                is DirectChatScreenState.HasData -> {
                    if (current.conversationId != conversationId) return@update current
                    val knownIds = current.messages.mapTo(mutableSetOf()) { it.id }
                    val clientIds = current.messages.mapNotNullTo(mutableSetOf()) { it.clientMessageId }
                    val currentMax = current.messages.maxOfOrNull { it.id } ?: 0L
                    val updates = page.items.filter { item ->
                        item.id in knownIds || item.clientMessageId?.let(clientIds::contains) == true ||
                            (!current.hasNewerMessages && !current.provisionalHistory &&
                                (item.id <= 0L || (realtimeContract == null && item.id > currentMax)))
                    }
                    val additions = updates.filter { it.id > currentMax && it.id !in knownIds && it.clientMessageId !in clientIds }
                    val shouldAutoScroll = additions.isNotEmpty() && !current.keepAnchorRequest &&
                        scrollAnchorController.latestViewportSnapshot?.isAtBottom != false
                    if (!shouldAutoScroll) additions.forEach {
                        readTracker.registerIncomingMessage(it.id, it.isOutgoing) {}
                    }
                    val peerRead = maxOf(current.peerLastReadMessageId, page.peerLastReadMessageId ?: 0L)
                    val merged = timelineReducer.applyPeerReadState(
                        timelineReducer.mergeMessages(current.messages + updates, inputController.inFlightEdits),
                        peerRead,
                    )
                    current.copy(
                        messages = merged,
                        peerLastReadMessageId = peerRead,
                        scrollToBottomRequestToken = if (shouldAutoScroll) scrollAnchorController.nextScrollRequestToken() else current.scrollToBottomRequestToken,
                        scrollToBottomBadgeCount = readTracker.unseenIncomingMessageIds.size,
                        conversationActivityState = inputController.resolveConversationActivityState(current.header, merged, current.input),
                    )
                }
                is DirectChatScreenState.NoMessages -> {
                    if (current.conversationId != conversationId) return@update current
                    val items = if (realtimeContract == null) page.items else page.items.filter { it.isOutgoing && it.id <= 0L }
                    if (items.isEmpty()) return@update current
                    val peerRead = page.peerLastReadMessageId ?: 0L
                    DirectChatScreenState.HasData(
                        conversationId = conversationId,
                        header = current.header,
                        messages = timelineReducer.applyPeerReadState(items, peerRead),
                        input = current.input,
                        canLoadMore = page.canLoadMore,
                        nextBeforeMessageId = page.nextBeforeMessageId,
                        scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
                        peerLastReadMessageId = peerRead,
                        peerIsOnline = current.peerIsOnline,
                        peerLastSeenAtMillis = current.peerLastSeenAtMillis,
                    )
                }
                else -> current
            }
        }
    }

	private fun observePresence(header: ChatThreadHeaderModel) {
		if (header.isSavedMessages) {
			realtimeCoordinator.observePresence(header) { presence ->
				inputController.updatePresence(presence, hasObserved = false)
				inputController.updateTyping(me.floow.shared.chats.model.ChatTypingState())
				inputController.updateHeaderSubtitle(null)
			}
			return
		}
		realtimeCoordinator.observePresence(header) { presence ->
			inputController.updatePresence(presence, hasObserved = realtimeCoordinator.hasObservedPresenceState)
			inputController.applyHeaderSubtitle()
		}
	}

    private fun resyncConversation() {
        val request = activeRequest ?: return
        val conversationId = activeConversationIdOrNull() ?: return
        if ((_state.value as? DirectChatScreenState.HasData)?.isLoadingMore == true) return
        val visible = scrollAnchorController.latestViewportSnapshot
        val current = _state.value as? DirectChatScreenState.HasData
        val generation = scrollAnchorController.windowGeneration
        resyncJob?.cancel()
        resyncJob = scope.launch {
            if (current?.hasNewerMessages == true) {
                val target = visible?.firstVisibleMessageId ?: current.messages.firstOrNull { it.id > 0L }?.id ?: return@launch
                val page = chatLoader.loadAroundMessage(conversationId, target).getOrNull() ?: return@launch
                if (generation != scrollAnchorController.windowGeneration) return@launch
                _state.update { state ->
                    if (state !is DirectChatScreenState.HasData || state.conversationId != conversationId) return@update state
                    val first = page.items.minOfOrNull { it.id } ?: return@update state
                    val last = page.items.maxOfOrNull { it.id } ?: return@update state
                    val earlier = state.messages.filter { it.id in 1 until first }
                    val newer = state.messages.filter { it.id > last && it.id <= (state.historyUpperBound ?: last) }
                    val pending = state.messages.filter { it.id <= 0L }
                    state.copy(
                        messages = timelineReducer.mergeMessages(earlier + page.items + newer + pending),
                        hasNewerMessages = page.hasNewerMessages,
                        historyUpperBound = if (page.hasNewerMessages) maxOf(last, state.historyUpperBound ?: 0L) else null,
                        peerLastReadMessageId = maxOf(state.peerLastReadMessageId, page.peerLastReadMessageId ?: 0L),
                    )
                }
                return@launch
            }
            val snapshot = chatLoader.loadInitialRemote(request.copy(
                conversationId = conversationId,
                openMode = me.floow.shared.chats.model.ChatOpenMode.LATEST,
                anchorMessageId = null,
            )).getOrNull() ?: return@launch
            if (generation != scrollAnchorController.windowGeneration) return@launch
            val loaded = snapshotToScreenState(snapshot, showUnreadIndicators = true)
            _state.update { state ->
                if (state !is DirectChatScreenState.HasData || loaded !is DirectChatScreenState.HasData) {
                    return@update timelineReducer.mergeFreshSnapshotIntoCurrent(state, loaded)
                }
                val newest = state.messages.maxOfOrNull { it.id } ?: 0L
                val first = loaded.messages.firstOrNull { it.id > 0L }?.id ?: 0L
                val latestIds = loaded.messages.asSequence().map { it.id }.filter { it > 0L }.toSet()
                val overlaps = state.messages.any { it.id in latestIds }
                if (newest < first && visible?.isAtBottom == false) {
                    // While reading an older window, leave its viewport intact and expose a newer edge.
                    return@update state.copy(hasNewerMessages = true, historyUpperBound = newest)
                }
                val merged = timelineReducer.mergeFreshSnapshotIntoCurrent(state, loaded) as DirectChatScreenState.HasData
                if (!overlaps) merged else {
                    val older = state.messages.filter { it.id in 1 until first }
                    merged.copy(
                        messages = timelineReducer.mergeMessages(older + merged.messages),
                        canLoadMore = if (older.isEmpty()) merged.canLoadMore else state.canLoadMore,
                        nextBeforeMessageId = if (older.isEmpty()) merged.nextBeforeMessageId else state.nextBeforeMessageId,
                    )
                }
            }
        }
    }
}

private fun Map<String, Long>.withEntry(message: ChatMessageItemModel): Map<String, Long> {
    val key = message.clientMessageId?.let { "cmid_$it" } ?: "msg_${message.id}"
    val now = me.floow.shared.chats.ui.currentChatEpochMillis()
    return filterValues { now - it in 0..1_000L }.entries.toList().takeLast(15)
        .associate { it.key to it.value } + (key to now)
}
