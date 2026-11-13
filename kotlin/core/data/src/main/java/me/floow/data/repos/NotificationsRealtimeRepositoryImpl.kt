package me.floow.data.repos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.floow.domain.api.NotificationsRealtimeApi
import me.floow.domain.api.models.NotificationItem
import me.floow.domain.api.models.NotificationsRealtimeEvent
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.cache.RepliesInboxLocalStore
import me.floow.domain.cache.RepliesInboxMeta
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.data.repos.RepliesRealtimeState
import me.floow.domain.models.UserNotification
import me.floow.domain.models.UserNotificationActor
import me.floow.domain.models.UserNotificationsPage
import me.floow.domain.realtime.shouldReloadOnHelloRealtimeGap
import me.floow.domain.realtime.shouldReloadOnSequentialGap
import me.floow.domain.utils.Logger

private const val REPLIES_CHANNEL = "replies"
private const val REPLIES_SNAPSHOT_LIMIT = 100
private const val RECONNECT_BACKOFF_MIN_MS = 1_000L
private const val RECONNECT_BACKOFF_MAX_MS = 10_000L
private const val MAX_REALTIME_REPLAY_WINDOW = 500L
private val REPLY_NOTIFICATION_TYPES = setOf("comment_on_post", "reply_to_comment", "comment_reply")

class NotificationsRealtimeRepositoryImpl(
	private val logger: Logger,
	private val notificationsRepository: NotificationsRepository,
	private val notificationsRealtimeApi: NotificationsRealtimeApi,
	private val authenticationManager: AuthenticationManager,
	private val repliesInboxLocalStore: RepliesInboxLocalStore
) : NotificationsRealtimeRepository {
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private val _repliesState = MutableStateFlow(RepliesRealtimeState())
	private var realtimeJob: Job? = null
	private var lastKnownMaxSeq: Long = 0L

	override val repliesState: StateFlow<RepliesRealtimeState> = _repliesState

	init {
		scope.launch {
			repliesInboxLocalStore.observeRepliesPage().collect { page ->
				lastKnownMaxSeq = maxOf(lastKnownMaxSeq, page.maxSeq.coerceAtLeast(0L))
				_repliesState.update { state ->
					state.copy(
						page = page,
						pageVersion = state.pageVersion + 1L
					)
				}
			}
		}
	}

	override fun start() {
		if (realtimeJob?.isActive == true) return
		realtimeJob = scope.launch {
			runRepliesRealtimeLoop()
		}
	}

	override fun stop(resetState: Boolean) {
		realtimeJob?.cancel()
		realtimeJob = null
		if (resetState) {
			scope.launch {
				repliesInboxLocalStore.clear()
				lastKnownMaxSeq = 0L
			}
			_repliesState.value = RepliesRealtimeState(
				isBootstrapping = false,
				isConnected = false,
				hasError = false,
				page = UserNotificationsPage(items = emptyList(), nextCursor = null)
			)
		} else {
			_repliesState.update { state ->
				state.copy(
					isBootstrapping = false,
					isConnected = false,
					hasError = false
				)
			}
		}
	}

	override suspend fun refresh() {
		loadRepliesSnapshot()
	}

	override suspend fun applyLocalReadState(lastReadSeq: Long) {
		if (lastReadSeq <= 0L) return
		repliesInboxLocalStore.applyReadCursor(lastReadSeq)
		_repliesState.update { state ->
			state.copy(hasError = false)
		}
	}

	private suspend fun runRepliesRealtimeLoop() {
		var reconnectDelay = RECONNECT_BACKOFF_MIN_MS
		while (currentCoroutineContext().isActive) {
			if (!authenticationManager.isSignedIn()) {
				repliesInboxLocalStore.clear()
				lastKnownMaxSeq = 0L
				_repliesState.update {
					it.copy(
						isConnected = false,
						isBootstrapping = false,
						hasError = false,
						pageVersion = it.pageVersion + 1L,
						page = UserNotificationsPage(items = emptyList(), nextCursor = null)
					)
				}
				delay(RECONNECT_BACKOFF_MIN_MS)
				continue
			}

			val localPage = repliesInboxLocalStore.getRepliesPage()
			val shouldBootstrap = localPage.items.isEmpty() && localPage.maxSeq <= 0L
			if (shouldBootstrap) {
				loadRepliesSnapshot()
			}

			val afterSeq = repliesInboxLocalStore.getRepliesPage().maxSeq.coerceAtLeast(0L)
			_repliesState.update { state -> state.copy(isConnected = false) }

			val streamResult = runCatching {
				notificationsRealtimeApi.subscribeReplies(afterSeq).collect { event ->
					applyRepliesRealtimeEvent(event, streamAfterSeq = afterSeq)
				}
			}
			streamResult.onFailure { throwable ->
				logger.d(
					"NotificationsRealtimeRepositoryImpl.runRepliesRealtimeLoop",
					"realtime stream failure: ${throwable.message}"
				)
			}
			if (streamResult.isSuccess) {
				reconnectDelay = RECONNECT_BACKOFF_MIN_MS
			}

			_repliesState.update { state -> state.copy(isConnected = false) }
			delay(reconnectDelay)
			reconnectDelay = (reconnectDelay * 2).coerceAtMost(RECONNECT_BACKOFF_MAX_MS)
		}
	}

	private suspend fun loadRepliesSnapshot() {
		_repliesState.update { state ->
			state.copy(
				isBootstrapping = true,
				hasError = false
			)
		}
		when (
			val result = notificationsRepository.getNotifications(
				cursor = null,
				limit = REPLIES_SNAPSHOT_LIMIT,
				types = REPLY_NOTIFICATION_TYPES,
				channel = REPLIES_CHANNEL
			)
		) {
			is GetDataResponse.Success -> {
				val sortedItems = result.data.items
					.filter { notification -> notification.channel == REPLIES_CHANNEL }
					.sortedBy(UserNotification::seq)
				repliesInboxLocalStore.replaceRepliesPage(result.data.copy(items = sortedItems))
				lastKnownMaxSeq = sortedItems.maxOfOrNull(UserNotification::seq)?.coerceAtLeast(0L) ?: 0L
				_repliesState.update { state ->
					state.copy(
						isBootstrapping = false,
						isConnected = false,
						hasError = false
					)
				}
			}

			is GetDataResponse.Error -> {
				_repliesState.update { state ->
					state.copy(
						isBootstrapping = false,
						isConnected = false,
						hasError = true
					)
				}
			}
		}
	}

	private suspend fun applyRepliesRealtimeEvent(event: NotificationsRealtimeEvent, streamAfterSeq: Long) {
		if (event.channel != REPLIES_CHANNEL) return
		when (event) {
			is NotificationsRealtimeEvent.Hello -> {
				if (shouldReloadRepliesSnapshotOnHello(
						localMaxSeq = lastKnownMaxSeq,
						eventMaxSeq = event.maxSeq,
						streamAfterSeq = streamAfterSeq,
						maxReplayWindow = MAX_REALTIME_REPLAY_WINDOW
					)
				) {
					logger.d(
						"NotificationsRealtimeRepositoryImpl.applyRepliesRealtimeEvent",
						"Detected hello gap/stale max_seq=${event.maxSeq}, local=$lastKnownMaxSeq, after_seq=$streamAfterSeq; forcing snapshot reconcile"
					)
					loadRepliesSnapshot()
					_repliesState.update { state ->
						state.copy(
							isBootstrapping = false,
							isConnected = true,
							hasError = false
						)
					}
					return
				}
				repliesInboxLocalStore.applyRepliesMeta(event.toMeta())
			}

			is NotificationsRealtimeEvent.NotificationCreated -> {
				val seq = event.notification.seq.coerceAtLeast(0L)
				if (seq > 0L) {
					if (seq <= lastKnownMaxSeq) {
						repliesInboxLocalStore.applyRepliesMeta(event.toMeta())
						_repliesState.update { state ->
							state.copy(
								isBootstrapping = false,
								isConnected = true,
								hasError = false
							)
						}
						return
					}
					if (shouldReloadRepliesSnapshotOnCreated(
							localMaxSeq = lastKnownMaxSeq,
							eventSeq = seq
						)
					) {
						logger.d(
							"NotificationsRealtimeRepositoryImpl.applyRepliesRealtimeEvent",
							"Detected seq gap local=$lastKnownMaxSeq event=$seq, forcing snapshot reconcile"
						)
						loadRepliesSnapshot()
						_repliesState.update { state ->
							state.copy(
								isBootstrapping = false,
								isConnected = true,
								hasError = false
							)
						}
						return
					}
				}
				repliesInboxLocalStore.upsertReplyNotification(
					notification = event.notification.toDomainNotification(),
					meta = event.toMeta()
				)
				lastKnownMaxSeq = maxOf(lastKnownMaxSeq, seq)
			}

			is NotificationsRealtimeEvent.ReadStateUpdated -> {
				repliesInboxLocalStore.applyRepliesMeta(event.toMeta())
			}
		}
		_repliesState.update { state ->
			state.copy(
				isBootstrapping = false,
				isConnected = true,
				hasError = false
			)
		}
	}
}

internal fun shouldReloadRepliesSnapshotOnHello(
	localMaxSeq: Long,
	eventMaxSeq: Long,
	streamAfterSeq: Long,
	maxReplayWindow: Long
): Boolean {
	return shouldReloadOnHelloRealtimeGap(
		localMaxCursor = localMaxSeq,
		eventMaxCursor = eventMaxSeq,
		streamAfterCursor = streamAfterSeq,
		maxReplayWindow = maxReplayWindow
	)
}

internal fun shouldReloadRepliesSnapshotOnCreated(
	localMaxSeq: Long,
	eventSeq: Long
): Boolean {
	return shouldReloadOnSequentialGap(
		localMaxCursor = localMaxSeq,
		eventCursor = eventSeq
	)
}

private fun NotificationsRealtimeEvent.toMeta(): RepliesInboxMeta {
	return RepliesInboxMeta(
		lastReadSeq = lastReadSeq.coerceAtLeast(0L),
		unreadCount = unreadCount.coerceAtLeast(0),
		firstUnreadSeq = firstUnreadSeq,
		maxSeq = maxSeq.coerceAtLeast(0L)
	)
}

private fun NotificationItem.toDomainNotification(): UserNotification {
	return UserNotification(
		id = id,
		seq = seq,
		type = type,
		channel = channel,
		actor = UserNotificationActor(
			id = actor.id,
			username = actor.username,
			name = actor.name,
			avatarUrl = actor.avatar
		),
		postId = postId,
		commentId = commentId,
		threadId = threadId,
		replyToCommentId = replyToCommentId,
		commentText = commentText,
		replyToCommentText = replyToCommentText,
		title = title,
		body = body,
		isRead = isRead,
		readAt = readAt,
		createdAt = createdAt,
		updatedAt = updatedAt
	)
}
