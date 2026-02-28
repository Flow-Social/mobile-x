package me.floow.feed.uilogic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.cache.FeedSyncCommandType
import me.floow.domain.cache.FeedSyncLocalStore
import me.floow.domain.cache.PostsCacheKeys
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.GetDataError
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.models.FeedPost
import me.floow.domain.models.UserProfile
import me.floow.domain.utils.Logger
import me.floow.domain.utils.TelemetryOperation
import me.floow.domain.utils.logLoadTiming

private data class FeedScreenVmState(
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val isNoMorePosts: Boolean = false,
	val isLoadingNext: Boolean = false,
	val isRecordingSwipe: Boolean = false,
	val noMoreForNow: Boolean = false,
	val items: List<FeedItem> = emptyList(),
	val userProfile: UserProfile = UserProfile(),
	val recommendationReason: String? = null,
	val lastSwipeInfo: String? = null,
	val isDebugMode: Boolean = false,
	val lastUndoSwipe: LastUndoSwipe? = null
)

private data class LastUndoSwipe(
	val item: FeedItem,
	val isLiked: Boolean
)

sealed interface FeedUiEffect {
	data class PlayUndoAnimation(val isLiked: Boolean) : FeedUiEffect
}

class FeedViewModel(
	private val logger: Logger,
	private val feedRepository: FeedRepository,
	private val categoryCatalogRepository: CategoryCatalogRepository,
	private val postsRepository: PostsRepository,
	private val userProfileRepository: UserProfileRepository,
	private val profileLocalStore: ProfileLocalStore,
	private val postsLocalStore: PostsLocalStore,
	private val authenticationManager: AuthenticationManager,
	private val feedSyncLocalStore: FeedSyncLocalStore
) : ViewModel() {
	private companion object {
		private const val TARGET_STACK_SIZE = 3
		private const val SEEN_POST_IDS_LIMIT = 1_200
		private const val EXCLUDED_POST_IDS_LIMIT = 400
		private const val LOAD_MAX_ATTEMPTS_PER_CYCLE = 8
		private const val SYNC_BACKOFF_BASE_MS = 2_000L
		private const val SYNC_BACKOFF_MAX_MS = 5 * 60 * 1000L
		private const val SYNC_MAX_ATTEMPTS = 6
		private val NO_PROGRESS_BACKOFF_STEPS_MS = longArrayOf(
			3_000L,
			10_000L,
			30_000L,
			60_000L
		)
	}

	private val _vmState = MutableStateFlow(FeedScreenVmState())
	private val _uiEffects = MutableSharedFlow<FeedUiEffect>(extraBufferCapacity = 8)
	private val loadMutex = Mutex()
	private val excludedPostIds = LinkedHashSet<String>()
	private val seenPostIds = LinkedHashSet<String>()

	private var activeUserId: String? = null
	private var activeSyncQueueKey: String? = null
	private var syncJob: Job? = null
	private var syncRetryJob: Job? = null
	private var noMoreFlagResetJob: Job? = null
	private var isFeedScreenActive: Boolean = false
	private var noProgressCycles: Int = 0
	private var noMoreCooldownUntilMs: Long = 0L

	val uiEffects: SharedFlow<FeedUiEffect> = _uiEffects.asSharedFlow()

	init {
		ensureActiveUserState(forceRestore = true)
		triggerSync()
	}

	val state: StateFlow<FeedScreenState> = _vmState
		.map { vmState ->
			if (vmState.isLoading && vmState.items.isEmpty()) FeedScreenState.Loading
			else if (vmState.isNoMorePosts && vmState.items.isEmpty()) FeedScreenState.NoMorePosts
			else if (vmState.isError && vmState.items.isEmpty()) FeedScreenState.Error
			else {
				FeedScreenState.Success(
					feedItems = vmState.items,
					userProfile = vmState.userProfile,
					recommendationReason = vmState.recommendationReason,
					lastSwipeInfo = vmState.lastSwipeInfo,
					isLoadingNext = vmState.isLoadingNext,
					isRecordingSwipe = vmState.isRecordingSwipe,
					canUndo = vmState.lastUndoSwipe != null,
					isDebugMode = vmState.isDebugMode,
					noMoreForNow = vmState.noMoreForNow,
					lastAnalysisResult = vmState.userProfile.analysisHistory.lastOrNull()
				)
			}
		}
		.stateIn(viewModelScope, SharingStarted.Eagerly, FeedScreenState.Loading)

	fun onFeedScreenVisible() {
		if (isFeedScreenActive) return
		isFeedScreenActive = true
		syncNoMoreForNowFlag()
		loadData()
	}

	fun onFeedScreenHidden() {
		isFeedScreenActive = false
	}

	private fun resolveCurrentUserId(): String {
		return authenticationManager
			.getSelfUserIdOrNull()
			?.takeIf { it.isNotBlank() }
			?: PostsCacheKeys.SELF_USER_ID
	}

	private fun syncQueueUserId(userId: String): String {
		return PostsCacheKeys.feedSyncQueueUserId(userId)
	}

	private fun rememberSeenPost(postId: String) {
		addPostIdWithLimit(
			target = seenPostIds,
			postId = postId,
			limit = SEEN_POST_IDS_LIMIT
		)
	}

	private fun rememberExcludedPost(postId: String) {
		addPostIdWithLimit(
			target = excludedPostIds,
			postId = postId,
			limit = EXCLUDED_POST_IDS_LIMIT
		)
	}

	private fun addPostIdWithLimit(
		target: LinkedHashSet<String>,
		postId: String,
		limit: Int
	) {
		if (target.remove(postId)) {
			// Move existing id to the end to keep recent ids longer.
		}
		target.add(postId)
		while (target.size > limit) {
			val iterator = target.iterator()
			if (!iterator.hasNext()) break
			iterator.next()
			iterator.remove()
		}
	}

	private fun ensureActiveUserState(forceRestore: Boolean = false) {
		val resolvedUserId = resolveCurrentUserId()
		val userChanged = activeUserId != null && activeUserId != resolvedUserId

		if (!forceRestore && !userChanged && activeUserId == resolvedUserId) {
			return
		}
		syncRetryJob?.cancel()

		val previousSyncQueueKey = activeSyncQueueKey

		activeUserId = resolvedUserId
		activeSyncQueueKey = syncQueueUserId(resolvedUserId)

		if (userChanged) {
			viewModelScope.launch {
				previousSyncQueueKey?.let { feedSyncLocalStore.clearUser(it) }
			}
		}

		if (userChanged) {
			feedRepository.clearSession()
			excludedPostIds.clear()
			seenPostIds.clear()
			noProgressCycles = 0
			noMoreCooldownUntilMs = 0L
			noMoreFlagResetJob?.cancel()
			_vmState.update { current -> FeedScreenVmState(isDebugMode = current.isDebugMode) }
		}

		triggerSync()
	}

	private fun setLoadingNext(isLoadingNext: Boolean) {
		_vmState.update { state ->
			if (state.isLoadingNext == isLoadingNext) state
			else state.copy(isLoadingNext = isLoadingNext)
		}
	}

	private fun triggerSync() {
		val syncQueueKey = activeSyncQueueKey ?: return
		if (syncJob?.isActive == true) return

		syncJob = viewModelScope.launch {
			processSyncQueue(syncQueueKey)
		}
	}

	private suspend fun processSyncQueue(syncQueueKey: String) {
		refreshSyncIndicator(syncQueueKey)
		while (true) {
			val nowMs = System.currentTimeMillis()
			val command = feedSyncLocalStore.peekNext(syncQueueKey, nowMs) ?: break
			val result = when (command.type) {
				FeedSyncCommandType.SWIPE -> {
					feedRepository.recordSwipe(
						postId = command.postId,
						isLiked = command.isLiked == true
					)
				}

				FeedSyncCommandType.UNDO -> {
					feedRepository.undoSwipe(command.postId)
				}
			}

			if (result is UpdateDataResponse.Success) {
				feedSyncLocalStore.remove(command.id)
			} else {
				val nextAttempt = command.attempts + 1
				if (nextAttempt >= SYNC_MAX_ATTEMPTS) {
					logger.d(
						"FeedViewModel.processSyncQueue",
						"Dropping sync command id=${command.id} after $nextAttempt attempts"
					)
					feedSyncLocalStore.remove(command.id)
				} else {
					val retryDelayMs = computeRetryDelayMs(nextAttempt)
					feedSyncLocalStore.incrementAttempts(
						id = command.id,
						nextAttemptAt = nowMs + retryDelayMs
					)
				}
				break
			}
		}
		refreshSyncIndicator(syncQueueKey)
		scheduleSyncRetryIfNeeded(syncQueueKey)
	}

	private suspend fun refreshSyncIndicator(syncQueueKey: String) {
		val hasPending = feedSyncLocalStore.pendingCount(syncQueueKey) > 0
		_vmState.update { state ->
			if (state.isRecordingSwipe == hasPending) state
			else state.copy(isRecordingSwipe = hasPending)
		}
	}

	private fun scheduleSyncRetryIfNeeded(syncQueueKey: String) {
		syncRetryJob?.cancel()
		syncRetryJob = viewModelScope.launch {
			val pending = feedSyncLocalStore.pendingCount(syncQueueKey)
			if (pending <= 0) return@launch

			val nextAttemptAt = feedSyncLocalStore.nextAttemptAt(syncQueueKey) ?: return@launch
			val delayMs = (nextAttemptAt - System.currentTimeMillis()).coerceAtLeast(0L)
			if (delayMs > 0) delay(delayMs)
			triggerSync()
		}
	}

	private fun computeRetryDelayMs(attempt: Int): Long {
		val exponent = (attempt - 1).coerceIn(0, 10)
		val multiplier = 1L shl exponent
		return (SYNC_BACKOFF_BASE_MS * multiplier).coerceAtMost(SYNC_BACKOFF_MAX_MS)
	}

	private fun inNoMoreCooldown(nowMs: Long = System.currentTimeMillis()): Boolean {
		return nowMs < noMoreCooldownUntilMs
	}

	private fun syncNoMoreForNowFlag(nowMs: Long = System.currentTimeMillis()) {
		val shouldShow = inNoMoreCooldown(nowMs)
		_vmState.update { state ->
			if (state.noMoreForNow == shouldShow) state else state.copy(noMoreForNow = shouldShow)
		}
	}

	private fun resetNoProgressState() {
		noProgressCycles = 0
		noMoreCooldownUntilMs = 0L
		noMoreFlagResetJob?.cancel()
		_vmState.update { state ->
			if (!state.noMoreForNow) state else state.copy(noMoreForNow = false)
		}
	}

	private fun markNoProgressCycle() {
		val nextCycle = (noProgressCycles + 1).coerceAtMost(NO_PROGRESS_BACKOFF_STEPS_MS.size)
		noProgressCycles = nextCycle
		val delayMs = NO_PROGRESS_BACKOFF_STEPS_MS[nextCycle - 1]
		noMoreCooldownUntilMs = System.currentTimeMillis() + delayMs
		_vmState.update { state ->
			if (state.noMoreForNow) state else state.copy(noMoreForNow = true)
		}
		scheduleNoMoreFlagReset(delayMs)
	}

	private fun scheduleNoMoreFlagReset(delayMs: Long) {
		noMoreFlagResetJob?.cancel()
		noMoreFlagResetJob = viewModelScope.launch {
			if (delayMs > 0) delay(delayMs)
			syncNoMoreForNowFlag()
		}
	}

	private fun enqueueSwipeSync(postId: String, isLiked: Boolean) {
		ensureActiveUserState()
		val syncQueueKey = activeSyncQueueKey ?: return
		viewModelScope.launch {
			feedSyncLocalStore.enqueueSwipe(
				userId = syncQueueKey,
				postId = postId,
				isLiked = isLiked
			)
			refreshSyncIndicator(syncQueueKey)
			triggerSync()
		}
	}

	private fun enqueueUndoSync(postId: String) {
		ensureActiveUserState()
		val syncQueueKey = activeSyncQueueKey ?: return
		viewModelScope.launch {
			feedSyncLocalStore.enqueueUndo(
				userId = syncQueueKey,
				postId = postId
			)
			refreshSyncIndicator(syncQueueKey)
			triggerSync()
		}
	}

	fun loadData() {
		if (!isFeedScreenActive) return
		ensureActiveUserState()
		syncNoMoreForNowFlag()
		if (_vmState.value.isLoading) return
		if (inNoMoreCooldown()) return

		val currentSize = _vmState.value.items.size
		if (currentSize >= TARGET_STACK_SIZE) return
		if (currentSize > 0) {
			viewModelScope.launch { softRevalidateStack() }
			return
		}

		_vmState.update {
			it.copy(
				isLoading = true,
				isError = false,
				isNoMorePosts = false,
				isLoadingNext = true
			)
		}

		viewModelScope.launch {
			val startedAt = System.currentTimeMillis()
			viewModelScope.launch {
				runCatching {
					categoryCatalogRepository.refresh(force = false)
				}.onFailure { error ->
					logger.d(
						"FeedViewModel.loadData",
						"Background categories refresh failed: ${error.message}"
					)
				}
			}
			repeat(TARGET_STACK_SIZE) { loadOneMore() }

			logger.logLoadTiming(
				tag = "FeedViewModel.loadData",
				operation = TelemetryOperation.FeedInitialLoad,
				startedAtMs = startedAt,
				extras = "items=${_vmState.value.items.size}"
			)
			_vmState.update { it.copy(isLoading = false, isLoadingNext = false) }
		}
	}

	private suspend fun softRevalidateStack() {
		if (inNoMoreCooldown()) {
			syncNoMoreForNowFlag()
			return
		}
		val currentSize = _vmState.value.items.size
		if (currentSize >= TARGET_STACK_SIZE) return
		val toLoad = TARGET_STACK_SIZE - currentSize
		val startedAt = System.currentTimeMillis()
		repeat(toLoad) { loadOneMore() }
		logger.logLoadTiming(
			tag = "FeedViewModel.loadData",
			operation = TelemetryOperation.FeedSoftRevalidate,
			startedAtMs = startedAt,
			extras = "requested=$toLoad size=${_vmState.value.items.size}"
		)
	}

	private suspend fun loadOneMore() {
		loadMutex.withLock {
			when {
				!isFeedScreenActive -> {
					setLoadingNext(isLoadingNext = false)
					return
				}

				_vmState.value.items.size >= TARGET_STACK_SIZE -> return

				inNoMoreCooldown() -> {
					syncNoMoreForNowFlag()
					setLoadingNext(isLoadingNext = false)
					return
				}
			}

			setLoadingNext(isLoadingNext = true)

			var attempts = 0
			while (attempts < LOAD_MAX_ATTEMPTS_PER_CYCLE) {
				attempts++
				when (val result = feedRepository.getNextPost()) {
					is GetDataResponse.Success -> {
						if (tryAppendFeedItem(result.data)) {
							return
						}
					}

					is GetDataResponse.Error -> {
						if (handleInitialLoadError(result.error)) {
							return
						}
						break
					}
				}
			}

			if (_vmState.value.items.size < TARGET_STACK_SIZE) {
				markNoProgressCycle()
				if (_vmState.value.items.isEmpty()) {
					_vmState.update { state ->
						state.copy(isError = false, isNoMorePosts = true)
					}
				}
			}
			setLoadingNext(isLoadingNext = false)
		}
	}

	private fun tryAppendFeedItem(feedPost: FeedPost): Boolean {
		val postId = feedPost.post.id
		val alreadyInStack = _vmState.value.items.any { it.post.id == postId }
		if (postId in excludedPostIds || postId in seenPostIds || alreadyInStack) {
			return false
		}

		val reason = mapReason(feedPost.reason)
		val newItem = FeedItem(
			post = feedPost.post,
			recommendationReason = reason
		)

		rememberSeenPost(postId)
		resetNoProgressState()
		_vmState.update {
			it.copy(
				items = it.items + newItem,
				recommendationReason = reason,
				isLoading = false,
				isError = false,
				isNoMorePosts = false,
				isLoadingNext = false
			)
		}
		return true
	}

	private fun handleInitialLoadError(error: GetDataError): Boolean {
		if (_vmState.value.items.isNotEmpty()) return false

		val isNoData = error == GetDataError.NoData
		_vmState.update {
			it.copy(
				isError = !isNoData,
				isNoMorePosts = isNoData,
				isLoading = false,
				isLoadingNext = false
			)
		}
		return true
	}

	fun onSwipeLeft() = onSwipe(false)
	fun onSwipeRight() = onSwipe(true)
	fun onSwipeUp() = onSwipe(true)
	fun onSwipeDown() = onSwipe(false)

	private fun onSwipe(isLiked: Boolean) {
		val currentItems = _vmState.value.items
		if (currentItems.isEmpty()) return

		val swipedItem = currentItems.first()
		rememberExcludedPost(swipedItem.post.id)
		rememberSeenPost(swipedItem.post.id)
		val remainingItems = currentItems.drop(1)
		val undoState = LastUndoSwipe(item = swipedItem, isLiked = isLiked)

		_vmState.update {
			it.copy(
				items = remainingItems,
				lastUndoSwipe = undoState
			)
		}
		enqueueSwipeSync(postId = swipedItem.post.id, isLiked = isLiked)
		viewModelScope.launch { loadOneMore() }
	}

	fun undoLastSwipe() {
		val undoState = _vmState.value.lastUndoSwipe ?: return
		val restoredItem = undoState.item
		val restoredPostId = restoredItem.post.id

		excludedPostIds.remove(restoredPostId)
		rememberSeenPost(restoredPostId)
		_vmState.update { current ->
			val withoutDuplicate = current.items.filterNot { it.post.id == restoredPostId }
			val restored = listOf(restoredItem) + withoutDuplicate
			current.copy(
				items = restored.take(TARGET_STACK_SIZE),
				recommendationReason = restoredItem.recommendationReason,
				lastUndoSwipe = null
			)
		}
		_uiEffects.tryEmit(FeedUiEffect.PlayUndoAnimation(isLiked = undoState.isLiked))
		enqueueUndoSync(postId = restoredPostId)
	}

	fun toggleDebugMode() {
		_vmState.update { it.copy(isDebugMode = !it.isDebugMode) }
	}

	fun clearProfile() {
		viewModelScope.launch {
			val currentSyncQueueKey = activeSyncQueueKey

			userProfileRepository.clearProfile()
			syncRetryJob?.cancel()
			noMoreFlagResetJob?.cancel()
			profileLocalStore.clearUser(PostsCacheKeys.SELF_USER_ID)
			postsLocalStore.clearUser(PostsCacheKeys.SELF_USER_ID)
			postsLocalStore.clearUser(PostsCacheKeys.FEED_STACK_USER_ID)
			currentSyncQueueKey?.let { feedSyncLocalStore.clearUser(it) }
			feedRepository.clearSession()
			authenticationManager.clearAuth()

			_vmState.update { FeedScreenVmState() }
			noProgressCycles = 0
			noMoreCooldownUntilMs = 0L
			excludedPostIds.clear()
			seenPostIds.clear()
			activeUserId = null
			activeSyncQueueKey = null
			ensureActiveUserState(forceRestore = true)
			loadData()
		}
	}

	fun resetRecommendations() {
		viewModelScope.launch {
			val currentSyncQueueKey = activeSyncQueueKey

			userProfileRepository.resetRecommendations()
			syncRetryJob?.cancel()
			noMoreFlagResetJob?.cancel()
			noProgressCycles = 0
			noMoreCooldownUntilMs = 0L
			currentSyncQueueKey?.let { feedSyncLocalStore.clearUser(it) }
			_vmState.update {
				it.copy(
					items = emptyList(),
					lastUndoSwipe = null,
					isRecordingSwipe = false,
					noMoreForNow = false,
					isNoMorePosts = false,
					isError = false
				)
			}
			postsLocalStore.clearUser(PostsCacheKeys.FEED_STACK_USER_ID)
			feedRepository.clearSession()
			excludedPostIds.clear()
			seenPostIds.clear()
			loadData()
		}
	}

	fun deletePost(postId: String) {
		viewModelScope.launch {
			val result = postsRepository.deletePost(postId)
			if (result is UpdateDataResponse.Success) {
				rememberExcludedPost(postId)
				rememberSeenPost(postId)
				_vmState.update {
					it.copy(
						items = it.items.filterNot { item -> item.post.id == postId },
						lastUndoSwipe = it.lastUndoSwipe?.takeIf { undo -> undo.item.post.id != postId }
					)
				}
				loadOneMore()
			} else {
				logger.d("FeedViewModel.deletePost", "deletePost failed: $result")
			}
		}
	}

	fun updatePost(postId: String, description: String, imageUrls: List<String>) {
		viewModelScope.launch {
			val result = postsRepository.updatePost(postId, description, imageUrls)
			if (result is UpdateDataResponse.Success) {
				applyPostEditLocally(
					postId = postId,
					description = description,
					imageUrls = imageUrls
				)
			} else {
				logger.d("FeedViewModel.updatePost", "updatePost failed: $result")
			}
		}
	}

	fun applyPostEdit(postId: String, description: String?, imageUrls: List<String>) {
		applyPostEditLocally(
			postId = postId,
			description = description.orEmpty(),
			imageUrls = imageUrls
		)
	}

	private fun applyPostEditLocally(postId: String, description: String, imageUrls: List<String>) {
		val normalizedUrls = imageUrls.filter { it.isNotBlank() }
		_vmState.update { state ->
			state.copy(
				items = state.items.map { item ->
					if (item.post.id == postId) {
						item.copy(
							post = item.post.copy(
								content = item.post.content.copy(
									description = description,
									imageUrls = normalizedUrls,
									imageVariants = emptyList()
								)
							)
						)
					} else {
						item
					}
				}
			)
		}
	}

	override fun onCleared() {
		syncRetryJob?.cancel()
		noMoreFlagResetJob?.cancel()
		super.onCleared()
	}
}

private fun mapReason(reasonCode: String): String {
	return when (reasonCode) {
		"FAVORITE" -> "✨ Любимое"
		"FAVORITE_AUTHOR" -> "👤 Любимый автор"
		"FAVORITE_CATEGORY" -> "🏷️ Любимая категория"
		"SIMILAR" -> "👥 Похожие интересы"
		"NEW" -> "🆕 Новое"
		else -> "🎯 Специально для вас"
	}
}
