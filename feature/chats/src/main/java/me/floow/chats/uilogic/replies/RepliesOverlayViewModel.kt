package me.floow.chats.uilogic.replies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.floow.chats.ReplyThreadNavigationTarget
import me.floow.chats.uilogic.shared.mergePendingReadCursor
import me.floow.chats.uilogic.shared.removeReadMessagesUpToCursor
import me.floow.chats.uilogic.shared.resolveAnchorMessageIdByCursor
import me.floow.chats.uilogic.shared.resolveMaxVisibleUnreadCursor
import me.floow.chats.uilogic.shared.shouldApplyVisibleReadCandidate
import me.floow.chats.uilogic.shared.shouldEnqueueReadCursor
import me.floow.chats.uilogic.shared.toTimelineReadOpenMode
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.data.repos.RepliesRealtimeState
import me.floow.domain.readmodel.TimelineReadItem
import me.floow.domain.readmodel.TimelineReadProjectorInput
import me.floow.domain.readmodel.projectTimelineReadModel
import me.floow.domain.models.UserNotification
import me.floow.domain.models.UserNotificationsPage
import me.floow.domain.utils.Logger
import me.floow.domain.utils.toLocalDateTimeFromEpochMillis
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatAnchorRequest
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.resolveDefaultContextMenuActions

private const val REPLIES_INTERLOCUTOR_ID = "system_replies_inbox"
private const val DEFAULT_REPLIES_INTERLOCUTOR_NAME = "Replies"
private const val REPLIES_CHANNEL = "replies"
internal val REPLY_NOTIFICATION_TYPES = setOf("comment_on_post", "reply_to_comment", "comment_reply")
private const val READ_PIPELINE_FLUSH_DELAY_MS = 250L
private const val VISIBLE_READ_APPLY_DELAY_MS = 80L
private const val OPEN_ANCHOR_SAVE_DELAY_MS = 200L
private const val REPLIES_METRIC_OPEN_TO_ANCHOR = "RepliesMetrics.open_to_anchor_ms"
private const val REPLIES_METRIC_ANCHOR_MISS_RATE = "RepliesMetrics.anchor_miss_rate"
private const val REPLIES_METRIC_OVERLAY_FIRST_FRAME = "RepliesMetrics.overlay_first_frame_ms"

data class RepliesOpenThreadEvent(
	val eventId: Long,
	val target: ReplyThreadNavigationTarget
)

internal data class RepliesTimeline(
	val groupedMessages: List<DatedChatMessages>,
	val targetByMessageId: Map<Long, ReplyThreadNavigationTarget>,
	val notificationSeqByMessageId: Map<Long, Long>,
	val actorIdByMessageId: Map<Long, String>,
	val unreadMessageIds: Set<Long>,
	val unreadBoundaryMessageId: Long?,
	val openAnchorMessageId: Long?,
	val openAnchorSeq: Long?,
	val readUpToSeq: Long
)

internal data class RepliesTimelineStatic(
	val groupedMessages: List<DatedChatMessages>,
	val targetByMessageId: Map<Long, ReplyThreadNavigationTarget>,
	val notificationSeqByMessageId: Map<Long, Long>,
	val actorIdByMessageId: Map<Long, String>,
	val loadedMaxSeq: Long
)

internal data class RepliesReadModelInput(
	val seqByMessageId: Map<Long, Long>,
	val serverReadUpToSeq: Long,
	val localReadUpToSeq: Long,
	val firstUnreadSeq: Long?,
	val storedOpenAnchorSeq: Long?,
	val resolvedOpenAnchorSeq: Long?,
	val openMode: RepliesOverlayOpenMode,
	val messageLinkOpenAnchorSeq: Long?
)

internal data class RepliesReadModelProjection(
	val unreadMessageIds: Set<Long>,
	val unreadBoundaryMessageId: Long?,
	val openAnchorMessageId: Long?,
	val openAnchorSeq: Long?,
	val readUpToSeq: Long
)

private data class TimelineStaticSignature(
	val itemsFingerprint: Long,
	val fallbackActorName: String,
	val fallbackMessageText: String
)

private data class TimelineProjectionSignature(
	val pageVersion: Long,
	val localReadUpToSeq: Long,
	val storedOpenAnchorSeq: Long,
	val resolvedOpenAnchorSeq: Long?,
	val openMode: RepliesOverlayOpenMode,
	val messageLinkOpenAnchorSeq: Long?,
	val anchorSessionId: Long,
	val keepOpenAnchorLocked: Boolean,
	val fallbackActorName: String,
	val fallbackMessageText: String
)

private data class RepliesOverlayVmState(
	val interlocutorName: String = DEFAULT_REPLIES_INTERLOCUTOR_NAME,
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val messages: List<DatedChatMessages> = emptyList(),
	val targetByMessageId: Map<Long, ReplyThreadNavigationTarget> = emptyMap(),
	val notificationSeqByMessageId: Map<Long, Long> = emptyMap(),
	val actorIdByMessageId: Map<Long, String> = emptyMap(),
	val unreadMessageIds: Set<Long> = emptySet(),
	val sessionUnreadBoundaryMessageId: Long? = null,
	val openAnchorMessageId: Long? = null,
	val openAnchorRequestToken: Long = 0L,
	val keepOpenAnchorLocked: Boolean = false
) {
	fun toUiState(): ChatScreenUiState {
		val title = interlocutorName.ifBlank { DEFAULT_REPLIES_INTERLOCUTOR_NAME }
		if (isLoading) {
			return ChatScreenUiState.Loading(
				chatInterlocutorId = REPLIES_INTERLOCUTOR_ID,
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = title,
				messageFieldReply = null,
				peerIsOnline = false,
				peerLastSeenAtMillis = null
			)
		}
		if (isError) {
			return ChatScreenUiState.Error(
				chatInterlocutorId = REPLIES_INTERLOCUTOR_ID,
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = title,
				messageFieldReply = null,
				peerIsOnline = false,
				peerLastSeenAtMillis = null
			)
		}
		if (messages.isEmpty()) {
			return ChatScreenUiState.NoMessages(
				chatInterlocutorId = REPLIES_INTERLOCUTOR_ID,
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = title,
				messageFieldReply = null,
				peerIsOnline = false,
				peerLastSeenAtMillis = null
			)
		}
			return ChatScreenUiState.HasData(
				messages = messages,
			chatInterlocutorId = REPLIES_INTERLOCUTOR_ID,
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = title,
				messageFieldReply = null,
				anchorRequest = openAnchorMessageId?.let { anchorId ->
					ChatAnchorRequest(
						messageId = anchorId,
						requestToken = openAnchorRequestToken,
						keepAnchored = keepOpenAnchorLocked
					)
				},
				unreadBoundaryMessageId = sessionUnreadBoundaryMessageId,
				scrollToBottomBadgeCount = unreadMessageIds.size,
				canLoadMore = false,
				isLoadingMore = false
			)
		}
}

class RepliesOverlayViewModel(
	private val notificationsRealtimeRepository: NotificationsRealtimeRepository,
	private val notificationsReadCursorStore: NotificationsReadCursorStore,
	private val logger: Logger
) : ViewModel() {
	fun resolveContextMenuActions(message: ChatMessage): List<ChatContextMenuAction> {
		return message.resolveDefaultContextMenuActions(allowEditAndDelete = false)
	}

	private val _state = MutableStateFlow(RepliesOverlayVmState())
	private val _openThreadEvents = MutableSharedFlow<RepliesOpenThreadEvent>(extraBufferCapacity = 1)
	private var localReadUpToSeq: Long = 0L
	private var lastAppliedReadUpToSeq: Long = 0L
	private var storedOpenAnchorSeq: Long = 0L
	private var resolvedOpenAnchorSeq: Long? = null
	private var openMode: RepliesOverlayOpenMode = RepliesOverlayOpenMode.FROM_UNREAD
	private var messageLinkOpenAnchorSeq: Long? = null
	private var anchorSessionId: Long = 0L
	private var hasUserStartedScroll: Boolean = false
	private var canMarkVisibleAsRead: Boolean = false
	private var isViewportAtBottom: Boolean = false
	private var lastVisibleMessageIds: Set<Long> = emptySet()
	private var currentFirstVisibleMessageId: Long? = null
	private var currentFallbackActorName: String = ""
	private var currentFallbackMessageText: String = ""
	private var nextOpenThreadEventId: Long = 1L
	private var timelineBuildJob: Job? = null
	private var isOverlayLoaded: Boolean = false
	private var lastTimelineProjectionSignature: TimelineProjectionSignature? = null
	private var inFlightTimelineProjectionSignature: TimelineProjectionSignature? = null
	private var lastTimelineStaticSignature: TimelineStaticSignature? = null
	private var inFlightTimelineStaticSignature: TimelineStaticSignature? = null
	private var cachedTimelineStatic: RepliesTimelineStatic? = null
	private var maxVisibleUnreadSeqCandidate: Long = 0L
	private var lastAppliedVisibleUnreadSeq: Long = 0L
	private var applyVisibleReadJob: Job? = null
	private var flushReadJob: Job? = null
	private var pendingReadUpToSeq: Long? = null
	private var persistOpenAnchorJob: Job? = null
	private var pendingOpenAnchorSeq: Long? = null
	private var overlayOpenedAtMs: Long = 0L
	private var hasReportedFirstFrame: Boolean = false
	private var hasReportedAnchorResolution: Boolean = false

	val openThreadEvents: SharedFlow<RepliesOpenThreadEvent> = _openThreadEvents
	val state: StateFlow<ChatScreenUiState> = _state
		.map(RepliesOverlayVmState::toUiState)
		.stateIn(
			viewModelScope,
			SharingStarted.Eagerly,
			ChatScreenUiState.Loading(
				chatInterlocutorId = REPLIES_INTERLOCUTOR_ID,
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = DEFAULT_REPLIES_INTERLOCUTOR_NAME,
				messageFieldReply = null,
				peerIsOnline = false,
				peerLastSeenAtMillis = null
			)
		)

	init {
		viewModelScope.launch {
			notificationsRealtimeRepository.repliesState.collectLatest { realtimeState ->
				if (!isOverlayLoaded) return@collectLatest
				applyRealtimeState(realtimeState)
			}
		}
	}

	fun load(
		interlocutorName: String,
		fallbackActorName: String,
		fallbackMessageText: String,
		openMode: RepliesOverlayOpenMode = RepliesOverlayOpenMode.FROM_UNREAD,
		messageLinkAnchorSeq: Long? = null
	) {
		viewModelScope.launch {
			anchorSessionId += 1L
			isOverlayLoaded = true
			this@RepliesOverlayViewModel.openMode = openMode
			this@RepliesOverlayViewModel.messageLinkOpenAnchorSeq = messageLinkAnchorSeq?.takeIf { seq -> seq > 0L }
			hasUserStartedScroll = false
			canMarkVisibleAsRead = false
			lastVisibleMessageIds = emptySet()
			currentFirstVisibleMessageId = null
			flushReadJob?.cancel()
			flushReadJob = null
			pendingReadUpToSeq = null
			persistOpenAnchorJob?.cancel()
			persistOpenAnchorJob = null
			pendingOpenAnchorSeq = null
			resolvedOpenAnchorSeq = null
			maxVisibleUnreadSeqCandidate = 0L
			lastAppliedVisibleUnreadSeq = 0L
			applyVisibleReadJob?.cancel()
			applyVisibleReadJob = null
			lastTimelineProjectionSignature = null
			inFlightTimelineProjectionSignature = null
			lastTimelineStaticSignature = null
			inFlightTimelineStaticSignature = null
			cachedTimelineStatic = null
			currentFallbackActorName = fallbackActorName
			currentFallbackMessageText = fallbackMessageText
			overlayOpenedAtMs = System.currentTimeMillis()
			hasReportedFirstFrame = false
			hasReportedAnchorResolution = false
			notificationsRealtimeRepository.start()
			val realtimeSnapshot = notificationsRealtimeRepository.repliesState.value
			val hasLocalMessages = realtimeSnapshot.page.items.isNotEmpty()
				_state.update { state ->
					state.copy(
						interlocutorName = interlocutorName,
						isLoading = !hasLocalMessages,
						isError = false,
						openAnchorMessageId = null,
						openAnchorRequestToken = 0L,
						keepOpenAnchorLocked = false,
						sessionUnreadBoundaryMessageId = null
					)
				}
			localReadUpToSeq = notificationsReadCursorStore.getLocalLastReadSeq(REPLIES_CHANNEL)
			lastAppliedReadUpToSeq = localReadUpToSeq
			lastAppliedVisibleUnreadSeq = localReadUpToSeq
			maxVisibleUnreadSeqCandidate = localReadUpToSeq
			storedOpenAnchorSeq = notificationsReadCursorStore.getOpenAnchorSeq(REPLIES_CHANNEL)
			applyRealtimeState(realtimeSnapshot)
			viewModelScope.launch {
				notificationsRealtimeRepository.refresh()
			}
		}
	}

	fun onMessageOpenRequested(messageId: Long) {
		val current = _state.value
		val target = current.targetByMessageId[messageId] ?: return
		val messageSeq = current.notificationSeqByMessageId[messageId]
		if (messageSeq != null) {
			maxVisibleUnreadSeqCandidate = maxOf(maxVisibleUnreadSeqCandidate, messageSeq)
			lastAppliedVisibleUnreadSeq = maxOf(lastAppliedVisibleUnreadSeq, messageSeq)
			applyReadUpToLocally(messageSeq)
		}
		_openThreadEvents.tryEmit(
			RepliesOpenThreadEvent(
				eventId = nextOpenThreadEventId++,
				target = target
			)
		)
	}

	fun getActorIdForMessage(messageId: Long): String? {
		return _state.value.actorIdByMessageId[messageId]
			?.takeIf { id -> id.isNotBlank() }
	}

	fun onSeeAllRequested() {
		val current = _state.value
		val unreadMessageIds = current.unreadMessageIds
		val latestLoadedSeq = current.notificationSeqByMessageId.values.maxOrNull()
		val maxUnreadSeq = unreadMessageIds
			.mapNotNull(current.notificationSeqByMessageId::get)
			.maxOrNull()
		if (maxUnreadSeq == null && latestLoadedSeq == null) return

		if (!hasUserStartedScroll) {
			anchorSessionId += 1L
			hasUserStartedScroll = true
			canMarkVisibleAsRead = true
		}
		if (maxUnreadSeq != null && maxUnreadSeq > 0L) {
			maxVisibleUnreadSeqCandidate = maxOf(maxVisibleUnreadSeqCandidate, maxUnreadSeq)
			lastAppliedVisibleUnreadSeq = maxOf(lastAppliedVisibleUnreadSeq, maxUnreadSeq)
			applyReadUpToLocally(maxUnreadSeq)
		}
		if (latestLoadedSeq != null && latestLoadedSeq > 0L) {
			resolvedOpenAnchorSeq = latestLoadedSeq
			enqueuePersistOpenAnchor(latestLoadedSeq)
		}
		_state.update { state ->
			state.copy(
				openAnchorMessageId = null,
				keepOpenAnchorLocked = false
			)
		}
	}

	fun onVisibleMessageIdsChanged(visibleMessageIds: Set<Long>) {
		if (visibleMessageIds.isEmpty()) return
		lastVisibleMessageIds = visibleMessageIds
		if (!canMarkVisibleAsRead && !isViewportAtBottom) return
		submitVisibleReadCandidate(visibleMessageIds)
	}

	fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
		isViewportAtBottom = snapshot.isAtBottom
		if (snapshot.isAtBottom) {
			canMarkVisibleAsRead = true
		}
		onVisibleMessageIdsChanged(snapshot.visibleMessageIds)
		onFirstVisibleMessageIdChanged(snapshot.firstVisibleMessageId)
	}

	fun onFirstVisibleMessageIdChanged(messageId: Long?) {
		currentFirstVisibleMessageId = messageId
		if (!hasUserStartedScroll) return
		val firstVisibleMessageId = messageId ?: return
		val anchorSeq = _state.value.notificationSeqByMessageId[firstVisibleMessageId] ?: return
		if (anchorSeq <= 0L) return
		resolvedOpenAnchorSeq = anchorSeq
		enqueuePersistOpenAnchor(anchorSeq)
	}

	fun onUserStartedScroll() {
		if (hasUserStartedScroll) return
		anchorSessionId += 1L
		hasUserStartedScroll = true
		canMarkVisibleAsRead = true
		_state.update { state ->
			state.copy(
				openAnchorMessageId = null,
				keepOpenAnchorLocked = false
			)
		}
		currentFirstVisibleMessageId
			?.let { messageId -> _state.value.notificationSeqByMessageId[messageId] }
			?.takeIf { seq -> seq > 0L }
			?.let { seq ->
				resolvedOpenAnchorSeq = seq
				enqueuePersistOpenAnchor(seq)
			}
		submitVisibleReadCandidate(lastVisibleMessageIds)
	}

	fun onOverlayClosed() {
		isOverlayLoaded = false
		isViewportAtBottom = false
		applyVisibleReadJob?.cancel()
		applyVisibleReadJob = null
		applyVisibleReadCandidate(maxVisibleUnreadSeqCandidate)

		val pendingReadSeq = pendingReadUpToSeq
		pendingReadUpToSeq = null
		flushReadJob?.cancel()
		flushReadJob = null

		val pendingAnchorSeq = pendingOpenAnchorSeq
		pendingOpenAnchorSeq = null
		persistOpenAnchorJob?.cancel()
		persistOpenAnchorJob = null

		if (pendingReadSeq == null && pendingAnchorSeq == null) return

		viewModelScope.launch(Dispatchers.IO + NonCancellable) {
			pendingReadSeq?.let { seq -> flushReadUpToNow(seq) }
			pendingAnchorSeq?.let { anchorSeq -> persistOpenAnchorNow(anchorSeq) }
		}
	}

	private fun submitVisibleReadCandidate(visibleMessageIds: Set<Long>) {
		if (visibleMessageIds.isEmpty()) return
		val current = _state.value
		val candidateSeq = resolveMaxVisibleUnreadCursor(
			visibleMessageIds = visibleMessageIds,
			unreadMessageIds = current.unreadMessageIds,
			cursorByMessageId = current.notificationSeqByMessageId
		)
		if (!shouldApplyVisibleReadCandidate(candidateSeq, maxVisibleUnreadSeqCandidate)) return
		maxVisibleUnreadSeqCandidate = candidateSeq
		applyVisibleReadJob?.cancel()
		applyVisibleReadJob = viewModelScope.launch {
			delay(VISIBLE_READ_APPLY_DELAY_MS)
			applyVisibleReadCandidate(maxVisibleUnreadSeqCandidate)
		}
	}

	private fun applyVisibleReadCandidate(readUpToSeq: Long) {
		if (readUpToSeq <= 0L) return
		if (readUpToSeq <= lastAppliedVisibleUnreadSeq) return
		lastAppliedVisibleUnreadSeq = readUpToSeq
		applyReadUpToLocally(readUpToSeq)
	}

	private fun applyReadUpToLocally(readUpToSeq: Long) {
		if (readUpToSeq <= 0L) return
		_state.update { state ->
			val updatedUnread = removeReadMessagesUpTo(
				messageIds = state.unreadMessageIds,
				seqByMessageId = state.notificationSeqByMessageId,
				readUpToSeq = readUpToSeq
			)
			if (updatedUnread.size == state.unreadMessageIds.size) {
				state
			} else {
				state.copy(unreadMessageIds = updatedUnread)
			}
		}
		enqueueReadUpToSeq(readUpToSeq)
	}

	private fun applyRealtimeState(realtimeState: RepliesRealtimeState) {
		if (!isOverlayLoaded) return
		val hasMessages = realtimeState.page.items.isNotEmpty()
		if (realtimeState.isBootstrapping && !hasMessages) {
			_state.update { state ->
				state.copy(
					isLoading = true,
					isError = false
				)
			}
			return
		}
		if (realtimeState.hasError && !hasMessages) {
			_state.update { state ->
				state.copy(
					isLoading = false,
					isError = true,
					messages = emptyList(),
					targetByMessageId = emptyMap(),
					notificationSeqByMessageId = emptyMap(),
					actorIdByMessageId = emptyMap(),
					unreadMessageIds = emptySet(),
					sessionUnreadBoundaryMessageId = null,
					openAnchorMessageId = null,
					openAnchorRequestToken = 0L,
					keepOpenAnchorLocked = false
				)
			}
			lastTimelineProjectionSignature = null
			inFlightTimelineProjectionSignature = null
			lastTimelineStaticSignature = null
			inFlightTimelineStaticSignature = null
			cachedTimelineStatic = null
			return
		}

		val localReadSeqSnapshot = localReadUpToSeq
		val storedOpenAnchorSeqSnapshot = storedOpenAnchorSeq
		val resolvedOpenAnchorSeqSnapshot = resolvedOpenAnchorSeq
		val openModeSnapshot = openMode
		val messageLinkAnchorSeqSnapshot = messageLinkOpenAnchorSeq
		val sessionSnapshot = anchorSessionId
		val keepOpenAnchorLockedSnapshot = !hasUserStartedScroll
		val fallbackActorNameSnapshot = currentFallbackActorName
		val fallbackMessageTextSnapshot = currentFallbackMessageText

		val itemsFingerprint = if (realtimeState.pageVersion > 0L) {
			realtimeState.pageVersion
		} else {
			computeRepliesItemsFingerprint(realtimeState.page)
		}
		val staticSignature = TimelineStaticSignature(
			itemsFingerprint = itemsFingerprint,
			fallbackActorName = fallbackActorNameSnapshot,
			fallbackMessageText = fallbackMessageTextSnapshot
		)
		val projectionSignature = TimelineProjectionSignature(
			pageVersion = realtimeState.pageVersion,
			localReadUpToSeq = localReadSeqSnapshot,
			storedOpenAnchorSeq = storedOpenAnchorSeqSnapshot,
			resolvedOpenAnchorSeq = resolvedOpenAnchorSeqSnapshot,
			openMode = openModeSnapshot,
			messageLinkOpenAnchorSeq = messageLinkAnchorSeqSnapshot,
			anchorSessionId = sessionSnapshot,
			keepOpenAnchorLocked = keepOpenAnchorLockedSnapshot,
			fallbackActorName = fallbackActorNameSnapshot,
			fallbackMessageText = fallbackMessageTextSnapshot
		)

		if (
			projectionSignature == lastTimelineProjectionSignature ||
			projectionSignature == inFlightTimelineProjectionSignature
		) {
			if (hasMessages) {
				_state.update { state ->
					if (state.isLoading || state.isError) {
						state.copy(isLoading = false, isError = false)
					} else {
						state
					}
				}
			}
			return
		}

		timelineBuildJob?.cancel()
		inFlightTimelineProjectionSignature = projectionSignature
		timelineBuildJob = viewModelScope.launch {
			try {
				val timelineStatic = resolveTimelineStatic(
					page = realtimeState.page,
					staticSignature = staticSignature,
					fallbackActorName = fallbackActorNameSnapshot,
					fallbackMessageText = fallbackMessageTextSnapshot
				)
				if (sessionSnapshot != anchorSessionId) return@launch

				val timeline = projectTimeline(
					timelineStatic = timelineStatic,
					page = realtimeState.page,
					localReadUpToSeq = localReadSeqSnapshot,
					storedOpenAnchorSeq = storedOpenAnchorSeqSnapshot,
					resolvedOpenAnchorSeq = resolvedOpenAnchorSeqSnapshot,
					openMode = openModeSnapshot,
					messageLinkOpenAnchorSeq = messageLinkAnchorSeqSnapshot
				)
				if (sessionSnapshot != anchorSessionId) return@launch

				lastAppliedReadUpToSeq = maxOf(lastAppliedReadUpToSeq, timeline.readUpToSeq)
				localReadUpToSeq = maxOf(localReadUpToSeq, timeline.readUpToSeq)
				lastAppliedVisibleUnreadSeq = maxOf(lastAppliedVisibleUnreadSeq, timeline.readUpToSeq)
				maxVisibleUnreadSeqCandidate = maxOf(maxVisibleUnreadSeqCandidate, lastAppliedVisibleUnreadSeq)
				notificationsReadCursorStore.setLocalLastReadSeq(REPLIES_CHANNEL, timeline.readUpToSeq)
				if (keepOpenAnchorLockedSnapshot) {
					resolvedOpenAnchorSeq = timeline.openAnchorSeq
				}

				_state.update { state ->
					val nextOpenAnchorRequestToken = if (timeline.openAnchorMessageId != state.openAnchorMessageId) {
						state.openAnchorRequestToken + 1L
					} else {
						state.openAnchorRequestToken
					}
					val sessionUnreadBoundaryMessageId = state.sessionUnreadBoundaryMessageId
						?: timeline.unreadBoundaryMessageId
					state.copy(
						isLoading = false,
						isError = false,
						messages = timeline.groupedMessages,
						targetByMessageId = timeline.targetByMessageId,
						notificationSeqByMessageId = timeline.notificationSeqByMessageId,
						actorIdByMessageId = timeline.actorIdByMessageId,
						unreadMessageIds = timeline.unreadMessageIds,
						sessionUnreadBoundaryMessageId = sessionUnreadBoundaryMessageId,
						openAnchorMessageId = if (keepOpenAnchorLockedSnapshot) timeline.openAnchorMessageId else null,
						openAnchorRequestToken = nextOpenAnchorRequestToken,
						keepOpenAnchorLocked = keepOpenAnchorLockedSnapshot && timeline.openAnchorMessageId != null
					)
				}
				lastTimelineProjectionSignature = projectionSignature
				reportOpenMetricsIfNeeded(timeline, keepOpenAnchorLockedSnapshot)
				if (canMarkVisibleAsRead) {
					submitVisibleReadCandidate(lastVisibleMessageIds)
				}
			} finally {
				if (inFlightTimelineProjectionSignature == projectionSignature) {
					inFlightTimelineProjectionSignature = null
				}
			}
		}
	}

	private suspend fun resolveTimelineStatic(
		page: UserNotificationsPage,
		staticSignature: TimelineStaticSignature,
		fallbackActorName: String,
		fallbackMessageText: String
	): RepliesTimelineStatic {
		val cachedSignature = lastTimelineStaticSignature
		val cachedStatic = cachedTimelineStatic
		if (cachedSignature == staticSignature && cachedStatic != null) {
			return cachedStatic
		}
		if (inFlightTimelineStaticSignature == staticSignature && cachedStatic != null) {
			return cachedStatic
		}

		inFlightTimelineStaticSignature = staticSignature
		val built = withContext(Dispatchers.Default) {
			buildRepliesTimelineStatic(
				page = page,
				fallbackActorName = fallbackActorName,
				fallbackMessageText = fallbackMessageText
			)
		}
		cachedTimelineStatic = built
		lastTimelineStaticSignature = staticSignature
		if (inFlightTimelineStaticSignature == staticSignature) {
			inFlightTimelineStaticSignature = null
		}
		return built
	}

	private fun reportOpenMetricsIfNeeded(
		timeline: RepliesTimeline,
		keepOpenAnchorLocked: Boolean
	) {
		val now = System.currentTimeMillis()
		val startedAt = overlayOpenedAtMs.takeIf { it > 0L } ?: now
		val openLatency = (now - startedAt).coerceAtLeast(0L)
		if (!hasReportedFirstFrame && timeline.groupedMessages.isNotEmpty()) {
			hasReportedFirstFrame = true
			logger.d(REPLIES_METRIC_OVERLAY_FIRST_FRAME, openLatency.toString())
		}
		if (!keepOpenAnchorLocked || hasReportedAnchorResolution) return
		when {
			timeline.openAnchorMessageId != null -> {
				hasReportedAnchorResolution = true
				logger.d(REPLIES_METRIC_OPEN_TO_ANCHOR, openLatency.toString())
			}
			timeline.unreadMessageIds.isNotEmpty() -> {
				hasReportedAnchorResolution = true
				logger.d(REPLIES_METRIC_ANCHOR_MISS_RATE, "1")
			}
		}
	}

	private fun enqueueReadUpToSeq(readUpToSeq: Long) {
		if (readUpToSeq <= 0L) return
		if (!shouldEnqueueReadCursor(readUpToSeq, lastAppliedReadUpToSeq)) return
		lastAppliedReadUpToSeq = readUpToSeq
		localReadUpToSeq = maxOf(localReadUpToSeq, readUpToSeq)
		pendingReadUpToSeq = mergePendingReadCursor(pendingReadUpToSeq, readUpToSeq)
		flushReadJob?.cancel()
		flushReadJob = viewModelScope.launch {
			delay(READ_PIPELINE_FLUSH_DELAY_MS)
			flushPendingReadNow()
		}
	}

	private suspend fun flushPendingReadNow() {
		val seqToFlush = pendingReadUpToSeq ?: return
		pendingReadUpToSeq = null
		flushReadUpToNow(seqToFlush)
	}

	private fun enqueuePersistOpenAnchor(anchorSeq: Long) {
		if (anchorSeq <= 0L) return
		pendingOpenAnchorSeq = anchorSeq
		persistOpenAnchorJob?.cancel()
		persistOpenAnchorJob = viewModelScope.launch {
			delay(OPEN_ANCHOR_SAVE_DELAY_MS)
			flushPendingOpenAnchorNow()
		}
	}

	private suspend fun flushPendingOpenAnchorNow() {
		val anchorSeq = pendingOpenAnchorSeq ?: return
		pendingOpenAnchorSeq = null
		persistOpenAnchorNow(anchorSeq)
	}

	private suspend fun flushReadUpToNow(readUpToSeq: Long) {
		if (readUpToSeq <= 0L) return
		notificationsReadCursorStore.enqueueReadUpTo(REPLIES_CHANNEL, readUpToSeq)
		notificationsRealtimeRepository.applyLocalReadState(readUpToSeq)
	}

	private suspend fun persistOpenAnchorNow(anchorSeq: Long) {
		if (anchorSeq <= 0L) return
		storedOpenAnchorSeq = anchorSeq
		notificationsReadCursorStore.setOpenAnchorSeq(REPLIES_CHANNEL, anchorSeq)
	}

	override fun onCleared() {
		if (isOverlayLoaded) {
			onOverlayClosed()
		}
		isOverlayLoaded = false
		timelineBuildJob?.cancel()
		applyVisibleReadJob?.cancel()
		flushReadJob?.cancel()
		persistOpenAnchorJob?.cancel()
		super.onCleared()
	}
}

internal fun buildRepliesTimelineStatic(
	page: UserNotificationsPage,
	fallbackActorName: String,
	fallbackMessageText: String
): RepliesTimelineStatic {
	val notifications = page.items
		.filter(::isReplyNotification)
		.filter { notification -> notification.postId.isNotBlank() && notification.commentId.isNotBlank() }
		.sortedBy { notification ->
			notification.seq.takeIf { seq -> seq > 0L } ?: Long.MAX_VALUE
		}

	val loadedMaxSeq = notifications
		.asSequence()
		.map { notification -> notification.seq.takeIf { seq -> seq > 0L } ?: 0L }
		.maxOrNull()
		?: 0L

	val rawMessages = mutableListOf<ChatMessage>()
	val targetByMessageId = linkedMapOf<Long, ReplyThreadNavigationTarget>()
	val notificationSeqByMessageId = linkedMapOf<Long, Long>()
	val actorIdByMessageId = linkedMapOf<Long, String>()

	notifications.forEachIndexed { index, notification ->
		val messageId = notification.seq.takeIf { it > 0L } ?: (index + 1).toLong()
		val commentText = notification.commentText
			?.trim()
			?.takeIf(String::isNotEmpty)
		val replyToText = notification.replyToCommentText
			?.trim()
			?.takeIf(String::isNotEmpty)

		val actorName = notification.actor.name
			?.trim()
			?.takeIf(String::isNotEmpty)
			?: notification.actor.username
				?.trim()
				?.takeIf(String::isNotEmpty)
			?: fallbackActorName

		val messageText = commentText
			?: notification.body.trim().takeIf(String::isNotEmpty)
			?: notification.title.trim().takeIf(String::isNotEmpty)
			?: fallbackMessageText

		val message = if (replyToText != null) {
			ReplyInMessage(
				id = messageId,
				replyMessageId = messageId * -1L,
				replyMessageText = replyToText,
				messageText = messageText,
				dateTime = notification.createdAt.toLocalDateTimeFromEpochMillis(),
				authorName = actorName,
				authorUsername = notification.actor.username,
				authorAvatarUrl = notification.actor.avatarUrl
			)
		} else {
			PrimaryInMessage(
				id = messageId,
				messageText = messageText,
				dateTime = notification.createdAt.toLocalDateTimeFromEpochMillis(),
				authorName = actorName,
				authorUsername = notification.actor.username,
				authorAvatarUrl = notification.actor.avatarUrl
			)
		}

		rawMessages += message
		targetByMessageId[messageId] = ReplyThreadNavigationTarget(
			postId = notification.postId,
			commentId = notification.commentId,
			replyToCommentId = notification.replyToCommentId,
			threadId = notification.threadId
		)
		notificationSeqByMessageId[messageId] = notification.seq.takeIf { it > 0L } ?: messageId
		val actorId = notification.actor.id.trim()
		if (actorId.isNotEmpty()) {
			actorIdByMessageId[messageId] = actorId
		}
	}

	val groupedMessages = rawMessages
		.groupBy { message -> message.dateTime.toLocalDate() }
		.map { (date, messages) ->
			DatedChatMessages(
				datetime = date,
				messages = messages
			)
		}
		.sortedBy { group -> group.datetime }
	return RepliesTimelineStatic(
		groupedMessages = groupedMessages,
		targetByMessageId = targetByMessageId,
		notificationSeqByMessageId = notificationSeqByMessageId,
		actorIdByMessageId = actorIdByMessageId,
		loadedMaxSeq = loadedMaxSeq
	)
}

internal fun projectTimeline(
	timelineStatic: RepliesTimelineStatic,
	page: UserNotificationsPage,
	localReadUpToSeq: Long,
	storedOpenAnchorSeq: Long,
	resolvedOpenAnchorSeq: Long?,
	openMode: RepliesOverlayOpenMode,
	messageLinkOpenAnchorSeq: Long?
): RepliesTimeline {
	val seqClampMax = timelineStatic.loadedMaxSeq.takeIf { maxSeq -> maxSeq > 0L }
	val normalizedServerReadUpToSeq = seqClampMax
		?.let { maxSeq -> page.lastReadSeq.coerceAtLeast(0L).coerceAtMost(maxSeq) }
		?: page.lastReadSeq.coerceAtLeast(0L)
	val normalizedLocalReadUpToSeq = seqClampMax
		?.let { maxSeq -> localReadUpToSeq.coerceAtLeast(0L).coerceAtMost(maxSeq) }
		?: localReadUpToSeq.coerceAtLeast(0L)
	val readModelProjection = projectRepliesReadModel(
		RepliesReadModelInput(
			seqByMessageId = timelineStatic.notificationSeqByMessageId,
			serverReadUpToSeq = normalizedServerReadUpToSeq,
			localReadUpToSeq = normalizedLocalReadUpToSeq,
			firstUnreadSeq = page.firstUnreadSeq,
			storedOpenAnchorSeq = storedOpenAnchorSeq,
			resolvedOpenAnchorSeq = resolvedOpenAnchorSeq,
			openMode = openMode,
			messageLinkOpenAnchorSeq = messageLinkOpenAnchorSeq
		)
	)

	return RepliesTimeline(
		groupedMessages = timelineStatic.groupedMessages,
		targetByMessageId = timelineStatic.targetByMessageId,
		notificationSeqByMessageId = timelineStatic.notificationSeqByMessageId,
		actorIdByMessageId = timelineStatic.actorIdByMessageId,
		unreadMessageIds = readModelProjection.unreadMessageIds,
		unreadBoundaryMessageId = readModelProjection.unreadBoundaryMessageId,
		openAnchorMessageId = readModelProjection.openAnchorMessageId,
		openAnchorSeq = readModelProjection.openAnchorSeq,
		readUpToSeq = readModelProjection.readUpToSeq
	)
}

private fun computeRepliesItemsFingerprint(page: UserNotificationsPage): Long {
	var fingerprint = 1125899906842597L
	var count = 0L
	page.items
		.asSequence()
		.filter(::isReplyNotification)
		.filter { notification -> notification.postId.isNotBlank() && notification.commentId.isNotBlank() }
		.forEach { notification ->
			count += 1L
			fingerprint = fingerprint * 31 + notification.seq
			fingerprint = fingerprint * 31 + notification.createdAt
			fingerprint = fingerprint * 31 + notification.updatedAt
			fingerprint = fingerprint * 31 + notification.type.hashCode().toLong()
			fingerprint = fingerprint * 31 + notification.postId.hashCode().toLong()
			fingerprint = fingerprint * 31 + notification.commentId.hashCode().toLong()
			fingerprint = fingerprint * 31 + (notification.replyToCommentId?.hashCode()?.toLong() ?: 0L)
			fingerprint = fingerprint * 31 + (notification.commentText?.hashCode()?.toLong() ?: 0L)
			fingerprint = fingerprint * 31 + (notification.replyToCommentText?.hashCode()?.toLong() ?: 0L)
			fingerprint = fingerprint * 31 + notification.body.hashCode().toLong()
			fingerprint = fingerprint * 31 + notification.title.hashCode().toLong()
			fingerprint = fingerprint * 31 + notification.actor.id.hashCode().toLong()
		}
	return fingerprint * 31 + count
}

internal fun resolveRepliesAnchorMessageId(
	anchorSeq: Long?,
	seqByMessageId: Map<Long, Long>
): Long? {
	return resolveAnchorMessageIdByCursor(
		targetCursor = anchorSeq,
		cursorByMessageId = seqByMessageId,
		preferCeil = true,
		fallbackToOldest = true
	)
}

internal fun removeReadMessagesUpTo(
	messageIds: Set<Long>,
	seqByMessageId: Map<Long, Long>,
	readUpToSeq: Long
): Set<Long> {
	return removeReadMessagesUpToCursor(
		messageIds = messageIds,
		cursorByMessageId = seqByMessageId,
		readUpToCursor = readUpToSeq
	)
}

internal fun projectRepliesReadModel(
	input: RepliesReadModelInput
): RepliesReadModelProjection {
	val projection = projectTimelineReadModel(
		input = TimelineReadProjectorInput(
			items = input.seqByMessageId.map { (messageId, seq) ->
				TimelineReadItem(
					messageId = messageId,
					cursor = seq,
					isIncoming = true
				)
			},
			serverReadUpToCursor = input.serverReadUpToSeq,
			localReadUpToCursor = input.localReadUpToSeq,
			firstUnreadCursor = input.firstUnreadSeq,
			storedOpenAnchorCursor = input.storedOpenAnchorSeq?.takeIf { seq -> seq > 0L },
			resolvedOpenAnchorCursor = input.resolvedOpenAnchorSeq?.takeIf { seq -> seq > 0L },
			openMode = input.openMode.toTimelineReadOpenMode(),
			messageLinkAnchorCursor = input.messageLinkOpenAnchorSeq?.takeIf { seq -> seq > 0L },
			preferCeilOpenAnchor = input.openMode == RepliesOverlayOpenMode.FROM_UNREAD ||
				input.openMode == RepliesOverlayOpenMode.FROM_MESSAGE_LINK,
			fallbackToOldestOpenAnchor = true
		)
	)

	return RepliesReadModelProjection(
		unreadMessageIds = projection.unreadMessageIds,
		unreadBoundaryMessageId = projection.unreadBoundaryMessageId,
		openAnchorMessageId = projection.openAnchorMessageId,
		openAnchorSeq = projection.openAnchorCursor,
		readUpToSeq = projection.readUpToCursor
	)
}

private fun isReplyNotification(notification: UserNotification): Boolean {
	return notification.type in REPLY_NOTIFICATION_TYPES
}
