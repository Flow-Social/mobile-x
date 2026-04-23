package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatTypingState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.shared.chats.ui.formatChatClockTime
import me.floow.uikit.chat.model.ChatViewportSnapshot
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
		val scrollToBottomRequestToken: Long = 0L,
		val scrollToBottomBadgeCount: Int = 0,
		val peerLastReadMessageId: Long = 0L,
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
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
	private companion object {
		const val LOAD_TIMEOUT_MS = 15_000L
		const val TYPING_THROTTLE_MS = 3_000L
		const val TYPING_IDLE_STOP_MS = 1_200L
		const val MIN_INCOMING_TYPING_TTL_MS = 800L
		const val UNDO_DELETE_TIMEOUT_MS = 4_000L
		const val MAX_CANCELLED_CLIENT_MESSAGE_IDS = 64
		const val LOCAL_ONLY_CONVERSATION_ID = -1L
	}

	private fun isOutgoingMessage(senderUserId: String?, header: ChatThreadHeaderModel): Boolean {
		val normalizedSenderUserId = senderUserId?.trim()?.takeIf(String::isNotEmpty) ?: return false
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
		if (normalizedPeerLastReadMessageId <= 0L) return messages
		return messages.map { message ->
			if (!message.isOutgoing || message.id <= 0L) {
				message
			} else {
				val deliveryState = when (message.deliveryState) {
					ChatDeliveryState.SENDING,
					ChatDeliveryState.FAILED -> message.deliveryState
					ChatDeliveryState.READ -> ChatDeliveryState.READ
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

	sealed interface Event {
		data class ShowMessage(val message: String) : Event
		data class JumpToMessage(val messageId: Long) : Event
	}

	private val _state = MutableStateFlow<DirectChatScreenState>(DirectChatScreenState.Loading)
	val state: StateFlow<DirectChatScreenState> = _state.asStateFlow()

	private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 16)
	val events: SharedFlow<Event> = _events.asSharedFlow()
	private var realtimeJob: Job? = null
	private var presenceJob: Job? = null
	private var outgoingTypingJob: Job? = null
	private var incomingTypingClearJob: Job? = null
	private var latestPresenceState: ChatPresenceState = ChatPresenceState()
	private var hasObservedPresenceState: Boolean = false
	private var latestTypingState: ChatTypingState = ChatTypingState()
	private var latestViewportSnapshot: ChatViewportSnapshot? = null
	private var unseenIncomingMessageIds: Set<Long> = emptySet()
	private var lastMarkedReadMessageId: Long = 0L
	private var isOutgoingTypingActive: Boolean = false
	private var lastOutgoingTypingSentMark: TimeMark? = null
	private var optimisticMessageIdSeed: Long = -1L
	private val pendingOutgoingClientMessageIds = mutableMapOf<Long, String>()
    private val pendingOutgoingSendJobs = mutableMapOf<Long, Job>()
    private val cancelledClientMessageIds = LinkedHashSet<String>()
    private var pendingDeleteJob: Job? = null
    private var pendingDeletedMessage: ChatMessageItemModel? = null
    private var pendingDeleteConversationId: Long? = null
    private val localVideoCircleMessages = LinkedHashMap<String, ChatMessageItemModel>()

	private fun nextScrollRequestToken(): Long = currentChatEpochMillis()
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
		realtimeJob?.cancel()
		realtimeJob = null
		presenceJob?.cancel()
		presenceJob = null
		outgoingTypingJob?.cancel()
		outgoingTypingJob = null
		incomingTypingClearJob?.cancel()
		incomingTypingClearJob = null
		latestPresenceState = ChatPresenceState()
		hasObservedPresenceState = false
		latestTypingState = ChatTypingState()
		latestViewportSnapshot = null
		unseenIncomingMessageIds = emptySet()
		lastMarkedReadMessageId = 0L
		isOutgoingTypingActive = false
		lastOutgoingTypingSentMark = null
		pendingOutgoingClientMessageIds.clear()
		pendingOutgoingSendJobs.values.forEach(Job::cancel)
		pendingOutgoingSendJobs.clear()
		cancelledClientMessageIds.clear()
		if (clearLocalVideoCircles) {
			localVideoCircleMessages.clear()
		}
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		pendingDeletedMessage = null
		pendingDeleteConversationId = null
	}

	fun load(request: DirectChatInitialRequest) {
		disposeActiveWork(clearLocalVideoCircles = false)
		scope.launch {
			val cachedSnapshot = repository.loadCachedInitial(request).getOrNull()
			if (cachedSnapshot != null) {
				_state.value = snapshotToScreenState(cachedSnapshot)
				observeRealtime(cachedSnapshot.conversationId)
				observePresence(cachedSnapshot.header.peerUserId)
			} else {
				_state.value = DirectChatScreenState.Loading
			}

			runCatching {
				withTimeout(LOAD_TIMEOUT_MS) {
					repository.loadInitial(request)
				}
			}.getOrElse {
				val message = if (it is kotlinx.coroutines.TimeoutCancellationException) {
					"chat load timed out"
				} else {
					it.message ?: "chat load failed"
				}
				if (cachedSnapshot == null) {
					_state.value = DirectChatScreenState.Error(message)
				} else {
					_events.tryEmit(Event.ShowMessage(message))
				}
				return@launch
			}
				.onSuccess { snapshot ->
					val latestMessageId = snapshot.messages.lastOrNull()?.id
					_state.value = snapshotToScreenState(snapshot)
					if (snapshot.conversationId != null && latestMessageId != null) {
						repository.markReadUpTo(snapshot.conversationId, latestMessageId)
					}
					observeRealtime(snapshot.conversationId)
					observePresence(snapshot.header.peerUserId)
				}
				.onFailure {
					if (cachedSnapshot == null) {
						_state.value = DirectChatScreenState.Error(it.message ?: "chat load failed")
					} else {
						_events.tryEmit(Event.ShowMessage(it.message ?: "chat load failed"))
					}
				}
		}
	}

    private fun snapshotToScreenState(snapshot: me.floow.shared.chats.model.ChatThreadSnapshot): DirectChatScreenState {
        val peerLastReadMessageId = snapshot.peerLastReadMessageId ?: 0L
        val normalizedMessages = applyPeerReadState(
            mergeLocalVideoCircleMessages(snapshot.messages),
            peerLastReadMessageId,
        )
		return if (normalizedMessages.isEmpty()) {
			DirectChatScreenState.NoMessages(
				conversationId = snapshot.conversationId,
				header = mergeLiveHeader(snapshot.header),
				input = "",
				scrollToBottomRequestToken = 0L,
			)
		} else if (snapshot.conversationId == null) {
			if (snapshot.messages.isEmpty()) {
				DirectChatScreenState.HasData(
					conversationId = LOCAL_ONLY_CONVERSATION_ID,
					header = mergeLiveHeader(snapshot.header),
					messages = normalizedMessages,
					canLoadMore = snapshot.canLoadMore,
					nextBeforeMessageId = snapshot.nextBeforeMessageId,
					highlightedMessageId = snapshot.highlightedMessageId,
					scrollToBottomBadgeCount = 0,
					peerLastReadMessageId = peerLastReadMessageId,
				)
			} else {
				DirectChatScreenState.Error("chat conversation id is missing")
			}
		} else {
			DirectChatScreenState.HasData(
				conversationId = snapshot.conversationId,
				header = mergeLiveHeader(snapshot.header),
				messages = normalizedMessages,
				canLoadMore = snapshot.canLoadMore,
				nextBeforeMessageId = snapshot.nextBeforeMessageId,
				highlightedMessageId = snapshot.highlightedMessageId,
				scrollToBottomBadgeCount = 0,
				peerLastReadMessageId = peerLastReadMessageId,
			)
		}
	}

	fun updateInput(text: String) {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(input = text)
				is DirectChatScreenState.NoMessages -> current.copy(input = text)
				else -> current
			}
		}
		scheduleOutgoingTyping(text)
	}

	fun addReply(messageId: Long) {
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
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> {
					val message = current.messages.firstOrNull { it.id == messageId }
					current.copy(editingMessageId = messageId, input = message?.text ?: current.input)
				}
				else -> current
			}
		}
	}

	fun cancelEditing() {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(editingMessageId = null, input = "")
				else -> current
			}
		}
	}

	fun requestScrollToBottom() {
		val requestToken = nextScrollRequestToken()
		unseenIncomingMessageIds = emptySet()
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
		unseenIncomingMessageIds = when {
			snapshot.isAtBottom -> emptySet()
			else -> unseenIncomingMessageIds - snapshot.visibleMessageIds
		}
		updateScrollBadgeCount()
		snapshot.visibleReadCandidateId
			?.takeIf { it > 0L }
			?.let { candidateId -> markReadUpToIfNeeded(current.conversationId, candidateId) }
	}

	fun retryMessage(messageId: Long) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		val message = current.messages.firstOrNull { it.id == messageId } ?: return
		if (message.deliveryState != ChatDeliveryState.FAILED || !message.isOutgoing) return
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
		val editingMessageId = (current as? DirectChatScreenState.HasData)?.editingMessageId
		val currentReplyMessageId = (current as? DirectChatScreenState.HasData)?.currentReplyMessageId
		val currentReplyMessageText = (current as? DirectChatScreenState.HasData)
			?.messages
			?.firstOrNull { it.id == currentReplyMessageId }
			?.text
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
		}
		if (text.isEmpty()) return

		_state.value = when (current) {
			is DirectChatScreenState.HasData -> if (editingMessageId != null) current.copy(sending = true) else current
			is DirectChatScreenState.NoMessages -> current
		}
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
		scope.launch {
			val result = repository.editMessage(conversationId, editingMessageId, text)

			result.onSuccess { message ->
				stopOutgoingTyping(conversationId)
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> {
							val updatedMessages = state.messages.map { existing -> if (existing.id == message.id) message else existing }
							state.copy(
								messages = applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
								input = "",
								sending = false,
								currentReplyMessageId = null,
								editingMessageId = null,
								scrollToBottomRequestToken = nextScrollRequestToken(),
							)
						}
						is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
							conversationId = conversationId,
							header = header,
							messages = listOf(message),
							scrollToBottomRequestToken = nextScrollRequestToken(),
						)
						else -> state
					}
				}
			}.onFailure {
				_state.update { state ->
					when (state) {
						is DirectChatScreenState.HasData -> state.copy(sending = false)
						else -> state
					}
				}
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
		val optimisticMessage = buildOptimisticOutgoingMessage(
			header = header,
			id = optimisticId,
			clientMessageId = normalizedClientMessageId,
			text = text,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			createdAtMillis = currentChatEpochMillis(),
			deliveryState = ChatDeliveryState.SENDING,
		)
		stopOutgoingTyping(conversationId)
		_state.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> state.copy(
					messages = upsertMessage(state.messages, optimisticMessage),
					input = "",
					currentReplyMessageId = null,
					editingMessageId = null,
					scrollToBottomRequestToken = nextScrollRequestToken(),
				)
				is DirectChatScreenState.NoMessages -> DirectChatScreenState.HasData(
					conversationId = conversationId,
					header = header,
					messages = listOf(optimisticMessage),
					input = "",
					currentReplyMessageId = null,
					editingMessageId = null,
					scrollToBottomRequestToken = nextScrollRequestToken(),
				)
				else -> state
			}
		}
		if (optimisticId <= 0L) {
			pendingOutgoingClientMessageIds[optimisticId] = normalizedClientMessageId
		}
		val sendJob = scope.launch {
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
						is DirectChatScreenState.HasData -> state.copy(
							messages = upsertMessage(
								state.messages,
								message.copy(
									clientMessageId = message.clientMessageId ?: normalizedClientMessageId,
									deliveryState = ChatDeliveryState.SENT,
									isOutgoing = true,
								)
							),
							peerLastReadMessageId = state.peerLastReadMessageId,
							scrollToBottomRequestToken = nextScrollRequestToken(),
						).let { updated ->
							updated.copy(messages = applyPeerReadState(updated.messages, updated.peerLastReadMessageId))
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
						is DirectChatScreenState.HasData -> state.copy(
							messages = upsertMessage(
								state.messages,
								optimisticMessage.copy(deliveryState = ChatDeliveryState.FAILED)
							),
						).let { updated ->
							updated.copy(messages = applyPeerReadState(updated.messages, updated.peerLastReadMessageId))
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

	fun addLocalVideoCircle(clip: RecordedClip): String {
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
			else -> null
		}
		val optimisticId = nextOptimisticMessageId()
			val clientMessageId = nextClientMessageId(optimisticId)
			val message = ChatMessageItemModel(
				id = optimisticId,
				clientMessageId = clientMessageId,
				senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: header.peerUserId,
				text = "",
				createdAtMillis = currentChatEpochMillis(),
				isOutgoing = true,
				deliveryState = ChatDeliveryState.SENDING,
				content = me.floow.shared.chats.model.ChatMessageContent.VideoCircle(
					localPath = videoPath,
					durationMs = clip.durationMs,
					width = clip.width,
					height = clip.height,
					uploadState = me.floow.shared.chats.model.VideoUploadState.Pending,
				),
			)
			localVideoCircleMessages[clientMessageId] = message
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

	fun resolveLocalVideoCircleSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		durationMs: Long,
		width: Int,
		height: Int,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val localMessage = localVideoCircleMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.VideoCircle
		val resolvedContent = ChatMessageContent.VideoCircle(
			localPath = localContent?.localPath,
			remoteUrl = remoteUrl,
			durationMs = durationMs,
			thumbnailPath = localContent?.thumbnailPath,
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
		localVideoCircleMessages[normalizedClientMessageId] = resolvedMessage
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

	fun loadMore() {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		if (current.isLoadingMore || !current.canLoadMore) return

		_state.value = current.copy(isLoadingMore = true)
		scope.launch {
			repository.loadMore(
				conversationId = current.conversationId,
				beforeMessageId = current.nextBeforeMessageId ?: current.messages.firstOrNull()?.id,
			)
				.onSuccess { page ->
					_state.update { state ->
						when (state) {
							is DirectChatScreenState.HasData -> state.copy(
								messages = (page.items + state.messages)
									.distinctBy(ChatMessageItemModel::id)
									.sortedBy(ChatMessageItemModel::id),
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
						)
					} else {
						state.copy(messages = remainingMessages)
					}
				}
				else -> state
			}
		}
		immediateMessages.forEach { message ->
			if (
				message.id <= 0L ||
				message.deliveryState == ChatDeliveryState.SENDING ||
				message.deliveryState == ChatDeliveryState.FAILED
			) {
				cancelPendingOutgoingMessage(message)
				return@forEach
			}
			scope.launch {
				repository.deleteMessage(current.conversationId, message.id)
					.onFailure {
						restoreDeletedMessage(message, current.conversationId)
						_events.tryEmit(Event.ShowMessage(it.message ?: "delete failed"))
					}
			}
		}
	}

	fun undoDeleteMessage() {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		val deletedMessage = pendingDeletedMessage ?: return
		val conversationId = pendingDeleteConversationId
		pendingDeletedMessage = null
		pendingDeleteConversationId = null
		restoreDeletedMessage(deletedMessage, conversationId)
	}

	fun togglePin(messageId: Long, isPinned: Boolean) {
		val current = _state.value as? DirectChatScreenState.HasData ?: return
		scope.launch {
			repository.setMessagePinned(current.conversationId, messageId, isPinned)
				.onSuccess {
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
				.onFailure {
					_events.tryEmit(Event.ShowMessage(it.message ?: "pin action failed"))
				}
		}
	}

	fun jumpToMessage(messageId: Long) {
		_events.tryEmit(Event.JumpToMessage(messageId))
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(highlightedMessageId = messageId)
				else -> current
			}
		}
	}

	private fun observeRealtime(conversationId: Long?) {
		if (conversationId == null || realtimeContract == null) return
		realtimeJob?.cancel()
		realtimeJob = scope.launch {
			realtimeContract.observeConversation(conversationId).collectLatest { event ->
				when (event) {
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
									val updated = upsertMessage(current.messages, normalizedMessage)
									val shouldAutoScrollToBottom = normalizedMessage.isOutgoing ||
										(latestViewportSnapshot?.isAtBottom != false)
									updateUnseenIncomingMessages(
										message = normalizedMessage,
										shouldAutoScrollToBottom = shouldAutoScrollToBottom,
									)
									current.copy(
										messages = applyPeerReadState(updated, nextPeerLastReadMessageId),
										scrollToBottomRequestToken = if (shouldAutoScrollToBottom) {
											nextScrollRequestToken()
										} else {
											current.scrollToBottomRequestToken
										},
										scrollToBottomBadgeCount = unseenIncomingMessageIds.size,
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
										)
									} else {
										current.copy(messages = remaining)
									}
								}
								else -> current
							}
						}
					}
					is ChatRealtimeEvent.MessagePinned -> {
						_state.update { current ->
							when (current) {
								is DirectChatScreenState.HasData -> current.copy(
									messages = current.messages.map { message ->
										if (message.id == event.messageId) message.copy(isPinned = event.isPinned) else message
									}
								)
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
						} ?: return@collectLatest
						if (event.actorUserId.isNotBlank() && event.actorUserId != currentHeader.peerUserId) {
							return@collectLatest
						}
						latestTypingState = ChatTypingState(
							isTyping = event.isTyping,
							displayName = event.displayName.ifBlank { currentHeader.title },
							typingTtlMs = event.typingTtlMs,
						)
						applyHeaderSubtitle()
						incomingTypingClearJob?.cancel()
						if (event.isTyping && event.typingTtlMs > 0L) {
							incomingTypingClearJob = scope.launch {
								delay(event.typingTtlMs.coerceAtLeast(MIN_INCOMING_TYPING_TTL_MS))
								latestTypingState = ChatTypingState()
								applyHeaderSubtitle()
							}
						}
					}
					ChatRealtimeEvent.ResyncRequired -> resyncConversation()
				}
			}
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

    private fun upsertMessage(
        currentMessages: List<ChatMessageItemModel>,
        message: ChatMessageItemModel,
    ): List<ChatMessageItemModel> {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		val optimistic = normalizedClientMessageId?.let { clientMessageId ->
			currentMessages.firstOrNull { it.clientMessageId == clientMessageId }
		}
		val normalizedMessage = if (optimistic != null && optimistic.id <= 0L && message.id > 0L) {
			// Preserve non-default content (e.g. VideoCircle) from the optimistic message
			// when the server message falls back to Text because the API doesn't carry media type yet.
			val preservedContent = if (
				optimistic.content !is ChatMessageContent.Text &&
				message.content is ChatMessageContent.Text
			) optimistic.content else message.content
			message.copy(
				createdAtMillis = optimistic.createdAtMillis,
				content = preservedContent,
			)
		} else {
			message
		}
		return (currentMessages.filterNot { existing ->
			existing.id == normalizedMessage.id ||
				(normalizedClientMessageId != null && existing.clientMessageId == normalizedClientMessageId)
            } + normalizedMessage).sortedBy(ChatMessageItemModel::createdAtMillis)
    }

    private fun mergeLocalVideoCircleMessages(messages: List<ChatMessageItemModel>): List<ChatMessageItemModel> {
        if (localVideoCircleMessages.isEmpty()) return messages
        return localVideoCircleMessages.values.fold(messages) { current, localMessage ->
            upsertMessage(current, localMessage)
        }
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

    private fun observePresence(peerUserId: String) {
		if (peerUserId.isBlank() || presenceContract == null) return
		presenceJob?.cancel()
		presenceJob = scope.launch {
			presenceContract.observePeerPresence(peerUserId).collectLatest { presence ->
				latestPresenceState = presence
				hasObservedPresenceState = true
				applyHeaderSubtitle()
			}
		}
	}

	private fun applyHeaderSubtitle() {
		updateHeaderSubtitle(resolveLiveHeaderSubtitle())
	}

	private fun updateHeaderSubtitle(subtitle: String?) {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(header = current.header.copy(subtitle = subtitle))
				is DirectChatScreenState.NoMessages -> current.copy(header = current.header.copy(subtitle = subtitle))
				else -> current
			}
		}
	}

	private fun mergeLiveHeader(header: ChatThreadHeaderModel): ChatThreadHeaderModel {
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
			scope.launch {
				repository.deleteMessage(conversationId, message.id)
					.onFailure {
						restoreDeletedMessage(message, conversationId)
						_events.tryEmit(Event.ShowMessage(it.message ?: "delete failed"))
					}
			}
		}
	}

	private fun cancelPendingOutgoingMessage(message: ChatMessageItemModel) {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return
		addCancelledClientMessageId(normalizedClientMessageId)
		val pendingEntry = pendingOutgoingClientMessageIds.entries
			.firstOrNull { (_, clientMessageId) -> clientMessageId == normalizedClientMessageId }
		pendingEntry?.let { entry ->
			pendingOutgoingSendJobs.remove(entry.key)?.cancel()
			pendingOutgoingClientMessageIds.remove(entry.key)
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
		flushPendingDeleteNow()
		pendingDeletedMessage = message
		pendingDeleteConversationId = conversationId
		pendingDeleteJob = scope.launch {
			delay(UNDO_DELETE_TIMEOUT_MS)
			commitPendingDelete()
		}
	}

	private fun flushPendingDeleteNow() {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		commitPendingDelete()
	}

	private fun commitPendingDelete() {
		val deletedMessage = pendingDeletedMessage ?: return
		val conversationId = pendingDeleteConversationId
		pendingDeletedMessage = null
		pendingDeleteConversationId = null
		if (conversationId == null) return
		scope.launch {
			repository.deleteMessage(conversationId, deletedMessage.id)
				.onFailure {
					restoreDeletedMessage(deletedMessage, conversationId)
					_events.tryEmit(Event.ShowMessage(it.message ?: "delete failed"))
				}
		}
	}

	private fun resolveLiveHeaderSubtitle(): String? {
		val lastSeenAtMillis = latestPresenceState.lastSeenAtMillis
		return when {
			latestTypingState.isTyping -> if (latestTypingState.displayName.isNotBlank()) {
				"${latestTypingState.displayName} печатает…"
			} else {
				"печатает…"
			}
			latestPresenceState.isOnline -> "online"
			lastSeenAtMillis != null -> "был(а) ${formatTimeLabel(lastSeenAtMillis)}"
			hasObservedPresenceState -> "offline"
			else -> null
		}
	}

	private fun updateUnseenIncomingMessages(
		message: ChatMessageItemModel,
		shouldAutoScrollToBottom: Boolean,
	) {
		if (message.isOutgoing || message.isDeleted || message.id <= 0L) return
		val isVisible = latestViewportSnapshot?.visibleMessageIds?.contains(message.id) == true
		unseenIncomingMessageIds = when {
			shouldAutoScrollToBottom || isVisible -> unseenIncomingMessageIds - message.id
			else -> unseenIncomingMessageIds + message.id
		}
	}

	private fun updateScrollBadgeCount() {
		_state.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					scrollToBottomBadgeCount = unseenIncomingMessageIds.size,
				)
				else -> current
			}
		}
	}

	private fun markReadUpToIfNeeded(
		conversationId: Long,
		messageId: Long,
	) {
		if (conversationId <= 0L || messageId <= 0L || messageId <= lastMarkedReadMessageId) return
		lastMarkedReadMessageId = messageId
		scope.launch {
			repository.markReadUpTo(conversationId, messageId)
		}
	}

	private fun scheduleOutgoingTyping(text: String) {
		val conversationId = (_state.value as? DirectChatScreenState.HasData)?.conversationId ?: return
		outgoingTypingJob?.cancel()
		if (text.isBlank()) {
			stopOutgoingTyping(conversationId)
			return
		}
		val shouldSendTyping = !isOutgoingTypingActive || (
			lastOutgoingTypingSentMark?.elapsedNow()?.inWholeMilliseconds ?: Long.MAX_VALUE
		) >= TYPING_THROTTLE_MS
		if (shouldSendTyping) {
			isOutgoingTypingActive = true
			lastOutgoingTypingSentMark = TimeSource.Monotonic.markNow()
			scope.launch {
				repository.setTyping(conversationId, true)
			}
		}
		outgoingTypingJob = scope.launch {
			delay(TYPING_IDLE_STOP_MS)
			stopOutgoingTyping(conversationId)
		}
	}

	private fun stopOutgoingTyping(conversationId: Long) {
		outgoingTypingJob?.cancel()
		if (!isOutgoingTypingActive) return
		isOutgoingTypingActive = false
		lastOutgoingTypingSentMark = null
		scope.launch {
			repository.setTyping(conversationId, false)
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
			)
			is DirectChatScreenState.NoMessages -> DirectChatInitialRequest(
				peerUserId = current.header.peerUserId,
				peerDisplayName = current.header.title,
				peerAvatarUrl = current.header.avatarUrl,
				conversationId = current.conversationId,
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
					val latestMessageId = snapshot.messages.lastOrNull()?.id
					_state.update { current ->
						when (current) {
							is DirectChatScreenState.HasData -> {
								if (snapshot.conversationId == null) {
									DirectChatScreenState.Error("chat conversation id is missing")
								} else {
									current.copy(
										conversationId = snapshot.conversationId,
										header = mergeLiveHeader(snapshot.header),
										messages = mergeLocalVideoCircleMessages(snapshot.messages),
										canLoadMore = snapshot.canLoadMore,
										nextBeforeMessageId = snapshot.nextBeforeMessageId,
										isLoadingMore = false,
										highlightedMessageId = snapshot.highlightedMessageId ?: current.highlightedMessageId,
									)
							}
						}
						is DirectChatScreenState.NoMessages -> {
							if (snapshot.messages.isEmpty()) {
								current.copy(
									conversationId = snapshot.conversationId,
									header = mergeLiveHeader(snapshot.header),
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
									)
							}
						}
						else -> current
					}
				}
				if (snapshot.conversationId != null && latestMessageId != null) {
					repository.markReadUpTo(snapshot.conversationId, latestMessageId)
				}
			}.onFailure {
				_events.tryEmit(Event.ShowMessage(it.message ?: "chat resync failed"))
			}
		}
	}
}

private fun formatTimeLabel(createdAtMillis: Long): String {
	return formatChatClockTime(createdAtMillis)
}
