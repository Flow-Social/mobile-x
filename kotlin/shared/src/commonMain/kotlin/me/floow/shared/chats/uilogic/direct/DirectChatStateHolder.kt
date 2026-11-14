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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.floow.domain.utils.Logger
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.model.ConversationActivityState
import me.floow.shared.chats.uilogic.ConversationActivityStore
import me.floow.shared.chats.uilogic.direct.coordinator.ChatActions
import me.floow.shared.chats.uilogic.direct.coordinator.ChatLoader
import me.floow.shared.chats.uilogic.direct.coordinator.ChatReadTracker
import me.floow.shared.chats.uilogic.direct.coordinator.ChatRealtimeCoordinator
import me.floow.shared.chats.uilogic.direct.coordinator.ChatTypingCoordinator
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache
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

	val timelineReducer: ChatTimelineReducer = ChatTimelineReducer()

	private val typingCoordinator: ChatTypingCoordinator = ChatTypingCoordinator(repository, scope)
	private val readTracker: ChatReadTracker = ChatReadTracker(repository, scope)
	private val chatActions: ChatActions = ChatActions(repository, scope) { message ->
		_events.tryEmit(Event.ShowMessage(message))
	}
	private val chatLoader: ChatLoader = ChatLoader(repository, sessionCache, logger)
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
		scrollAnchorController.requestScrollToBottom()
	}

	fun onUserStartedScroll() {
		scrollAnchorController.onUserStartedScroll()
	}

	fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
		scrollAnchorController.onViewportSnapshotChanged(snapshot)
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
		return mediaController.addLocalVideoCircle(clip, enqueueOutbox)
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
		return mediaController.addLocalVoice(clip)
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
		)
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
	}

	private fun disposeActiveWork(clearLocalVideoCircles: Boolean) {
		scrollAnchorController.flushReadAndOpenAnchorOnClose()
		loadJob?.cancel()
		loadJob = null
		remoteRefreshRetryJob?.cancel()
		remoteRefreshRetryJob = null
		realtimeCoordinator.stopAll()
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
		val instantCached = chatLoader.getInstantCached(request)
		if (instantCached != null) {
			_state.value = snapshotToScreenState(
				snapshot = instantCached,
				showUnreadIndicators = true,
			)
			inputController.publishLocalActivityFromCurrentState()
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
					inputController.publishLocalActivityFromCurrentState()
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
							timelineReducer.mergeFreshSnapshotIntoCurrent(current, loadedState)
						} else {
							loadedState
						}
					}
					inputController.publishLocalActivityFromCurrentState()
					snapshot.conversationId?.takeIf { it > 0L }?.let { conversationId ->
						readTracker.initializeLastMarkedRead(repository.getLocalLastReadMessageId(conversationId))
						mediaController.flushPendingLocalVideoOutbox(conversationId, snapshot.header)
					}
					scrollAnchorController.markLatestVisibleMessagesReadIfAppropriate()
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
				timelineReducer.mergeFreshSnapshotIntoCurrent(current, loadedState)
			}
			snapshot.conversationId?.takeIf { it > 0L }?.let { conversationId ->
				readTracker.initializeLastMarkedRead(repository.getLocalLastReadMessageId(conversationId))
			}
			scrollAnchorController.markLatestVisibleMessagesReadIfAppropriate()
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
			messages = snapshot.pinnedMessages,
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
						currentReplyMessageId = null,
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
								scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
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
								input = originalEditingMessage.text,
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
		inputController.stopOutgoingTyping(conversationId)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val messages = timelineReducer.upsertMessage(state.messages, optimisticMessage, inputController.inFlightEdits)
					state.copy(
						messages = messages,
						input = "",
						currentReplyMessageId = null,
						editingMessageId = null,
						scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
						conversationActivityState = inputController.resolveConversationActivityState(state.header, messages, state.input),
					)
				}
				is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
					conversationId = conversationId,
					header = header,
					messages = listOf(optimisticMessage),
					input = "",
					currentReplyMessageId = null,
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
									inputController.normalizePinnedMessages(
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
					val previousLatestKey = current.messages.lastOrNull()?.let { timelineReducer.identityKey(it) }
					val pageLatestMessage = page.items.lastOrNull()
					val pageLatestKey = pageLatestMessage?.let { timelineReducer.identityKey(it) }
					val latestWasAlreadyLoaded = pageLatestMessage?.let { incoming ->
						current.messages.any { existing -> timelineReducer.matchesMessageIdentity(existing, incoming) }
					} == true
					val isAcknowledgedOutgoing = pageLatestMessage?.isOutgoing == true &&
						(latestWasAlreadyLoaded || current.messages.any { it.isOutgoing && timelineReducer.matchesMessageIdentity(it, pageLatestMessage) })
					val hasNewLatestMessage = pageLatestMessage != null &&
						pageLatestKey != null &&
						pageLatestKey != previousLatestKey &&
						!latestWasAlreadyLoaded &&
						!isAcknowledgedOutgoing
					val shouldAutoScrollToBottom = hasNewLatestMessage &&
						!current.keepAnchorRequest &&
						(
							pageLatestMessage.isOutgoing ||
								(scrollAnchorController.latestViewportSnapshot?.isAtBottom != false)
						)
					if (hasNewLatestMessage) {
						scrollAnchorController.updateUnseenIncomingMessages(
							message = checkNotNull(pageLatestMessage),
							shouldAutoScrollToBottom = shouldAutoScrollToBottom,
						)
					}
					shouldMarkReadAfterUpdate = shouldAutoScrollToBottom &&
						!pageLatestMessage.isOutgoing &&
						pageLatestMessage.id > 0L
					val nextPeerLastReadMessageId = page.peerLastReadMessageId ?: current.peerLastReadMessageId
					val mergedMessages = timelineReducer.applyPeerReadState(
						timelineReducer.mergeMessages(current.messages + page.items, inputController.inFlightEdits),
						nextPeerLastReadMessageId,
					)
					current.copy(
						messages = mergedMessages,
						canLoadMore = current.canLoadMore,
						nextBeforeMessageId = current.nextBeforeMessageId,
						scrollToBottomRequestToken = if (shouldAutoScrollToBottom) {
							scrollAnchorController.nextScrollRequestToken()
						} else {
							current.scrollToBottomRequestToken
						},
						scrollToBottomBadgeCount = readTracker.unseenIncomingMessageIds.size,
						peerLastReadMessageId = nextPeerLastReadMessageId,
						conversationActivityState = inputController.resolveConversationActivityState(current.header, mergedMessages, current.input),
					)
				}
				is DirectChatScreenState.NoMessages -> {
					if (current.conversationId != conversationId) return@update current
					shouldMarkReadAfterUpdate = page.items.lastOrNull()?.let { message ->
						!message.isOutgoing && message.id > 0L
					} == true
					val mergedMessages = timelineReducer.applyPeerReadState(
						timelineReducer.mergeMessages(page.items, inputController.inFlightEdits),
						page.peerLastReadMessageId ?: 0L,
					)
					DirectChatScreenState.HasData(
						conversationId = conversationId,
						header = current.header,
						messages = mergedMessages,
						input = current.input,
						canLoadMore = page.canLoadMore,
						nextBeforeMessageId = page.nextBeforeMessageId,
						scrollToBottomRequestToken = scrollAnchorController.nextScrollRequestToken(),
						peerLastReadMessageId = page.peerLastReadMessageId ?: 0L,
						peerIsOnline = current.peerIsOnline,
						peerLastSeenAtMillis = current.peerLastSeenAtMillis,
						conversationActivityState = inputController.resolveConversationActivityState(current.header, mergedMessages, current.input),
					)
				}
				else -> current
			}
		}
		if (shouldMarkReadAfterUpdate) {
			scrollAnchorController.markLatestVisibleMessagesReadIfAppropriate()
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
									header = inputController.mergeLiveHeader(snapshot.header),
									messages = current.messages
										.filter(timelineReducer::isLocalOutgoingPending)
										.fold(mediaController.mergeLocalMediaMessages(snapshot.messages)) { messages, pending ->
											timelineReducer.upsertMessage(messages, pending, inputController.inFlightEdits)
										},
									canLoadMore = snapshot.canLoadMore,
									nextBeforeMessageId = snapshot.nextBeforeMessageId,
									isLoadingMore = false,
									highlightedMessageId = snapshot.highlightedMessageId ?: current.highlightedMessageId,
									anchorRequestMessageId = snapshot.openAnchorMessageId ?: current.anchorRequestMessageId,
									anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
									anchorRequestToken = if (snapshot.openAnchorMessageId != null) scrollAnchorController.nextAnchorRequestToken() else current.anchorRequestToken,
									keepAnchorRequest = snapshot.keepOpenAnchorLocked,
								)
							}
						}
						is DirectChatScreenState.NoMessages -> {
							if (snapshot.messages.isEmpty()) {
								current.copy(
									conversationId = snapshot.conversationId,
									header = inputController.mergeLiveHeader(snapshot.header),
									peerIsOnline = inputController.latestPresenceState.resolveIsOnline(),
									peerLastSeenAtMillis = inputController.latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
								)
							} else if (snapshot.conversationId == null) {
								DirectChatScreenState.Error("chat conversation id is missing")
							} else {
								DirectChatScreenState.HasData(
									conversationId = snapshot.conversationId,
									header = inputController.mergeLiveHeader(snapshot.header),
									messages = mediaController.mergeLocalMediaMessages(snapshot.messages),
									canLoadMore = snapshot.canLoadMore,
									nextBeforeMessageId = snapshot.nextBeforeMessageId,
									highlightedMessageId = snapshot.highlightedMessageId,
									anchorRequestMessageId = snapshot.openAnchorMessageId,
									anchorRequestOffsetPx = snapshot.openAnchorOffsetPx,
									anchorRequestToken = if (snapshot.openAnchorMessageId != null) scrollAnchorController.nextAnchorRequestToken() else 0L,
									keepAnchorRequest = snapshot.keepOpenAnchorLocked,
									peerIsOnline = inputController.latestPresenceState.resolveIsOnline(),
									peerLastSeenAtMillis = inputController.latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
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
