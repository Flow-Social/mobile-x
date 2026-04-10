package me.floow.shared.feed.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.FeedRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UserProfileRepository
import me.floow.domain.models.FeedPost
import me.floow.domain.models.UserProfile
import me.floow.domain.utils.Logger
import me.floow.domain.utils.TelemetryOperation
import me.floow.domain.utils.currentTimeMillis
import me.floow.domain.utils.logLoadTiming

private data class FeedHolderVmState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
    val isNoMorePosts: Boolean = false,
    val isLoadingNext: Boolean = false,
    val isRecordingSwipe: Boolean = false,
    val noMoreForNow: Boolean = false,
    val items: List<SharedFeedItem> = emptyList(),
    val userProfile: UserProfile = UserProfile(),
    val recommendationReason: String? = null,
    val lastSwipeInfo: String? = null,
    val isDebugMode: Boolean = false,
    val lastUndoSwipe: LastUndoSwipe? = null,
)

private data class LastUndoSwipe(
    val item: SharedFeedItem,
    val isLiked: Boolean,
)

class SharedFeedStateHolder(
    private val logger: Logger,
    private val feedRepository: FeedRepository,
    private val categoryCatalogRepository: CategoryCatalogRepository,
    private val postsRepository: PostsRepository,
    private val userProfileRepository: UserProfileRepository,
    private val profileLocalStore: ProfileLocalStore,
    private val postsLocalStore: PostsLocalStore,
    private val authenticationManager: AuthenticationManager,
    private val feedSyncLocalStore: FeedSyncLocalStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : SharedFeedOwner {
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
            60_000L,
        )
    }

    private val vmState = MutableStateFlow(FeedHolderVmState())
    private val mutableUiEffects = MutableSharedFlow<SharedFeedUiEffect>(extraBufferCapacity = 8)
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
    private var nextEntryId: Long = 0L

    override val uiEffects: SharedFlow<SharedFeedUiEffect> = mutableUiEffects.asSharedFlow()

    override val state: StateFlow<SharedFeedScreenState> = vmState
        .map { current ->
            when {
                current.isLoading && current.items.isEmpty() -> SharedFeedScreenState.Loading
                current.isNoMorePosts && current.items.isEmpty() -> SharedFeedScreenState.NoMorePosts
                current.isError && current.items.isEmpty() -> SharedFeedScreenState.Error
                else -> SharedFeedScreenState.Success(
                    feedItems = current.items,
                    userProfile = current.userProfile,
                    recommendationReason = current.recommendationReason,
                    lastSwipeInfo = current.lastSwipeInfo,
                    isLoadingNext = current.isLoadingNext,
                    isRecordingSwipe = current.isRecordingSwipe,
                    canUndo = current.lastUndoSwipe != null,
                    isDebugMode = current.isDebugMode,
                    noMoreForNow = current.noMoreForNow,
                    lastAnalysisResult = current.userProfile.analysisHistory.lastOrNull(),
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, SharedFeedScreenState.Loading)

    init {
        ensureActiveUserState(forceRestore = true)
        triggerSync()
    }

    override fun onFeedScreenVisible() {
        if (isFeedScreenActive) return
        isFeedScreenActive = true
        syncNoMoreForNowFlag()
        loadData()
    }

    override fun onFeedScreenHidden() {
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
        addPostIdWithLimit(seenPostIds, postId, SEEN_POST_IDS_LIMIT)
    }

    private fun rememberExcludedPost(postId: String) {
        addPostIdWithLimit(excludedPostIds, postId, EXCLUDED_POST_IDS_LIMIT)
    }

    private fun addPostIdWithLimit(target: LinkedHashSet<String>, postId: String, limit: Int) {
        target.remove(postId)
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
        if (!forceRestore && !userChanged && activeUserId == resolvedUserId) return

        syncRetryJob?.cancel()
        val previousSyncQueueKey = activeSyncQueueKey
        activeUserId = resolvedUserId
        activeSyncQueueKey = syncQueueUserId(resolvedUserId)

        if (userChanged) {
            scope.launch {
                if (previousSyncQueueKey != null) {
                    feedSyncLocalStore.clearUser(previousSyncQueueKey)
                }
            }
        }

        if (userChanged) {
            feedRepository.clearSession()
            excludedPostIds.clear()
            seenPostIds.clear()
            noProgressCycles = 0
            noMoreCooldownUntilMs = 0L
            nextEntryId = 0L
            noMoreFlagResetJob?.cancel()
            vmState.update { current -> FeedHolderVmState(isDebugMode = current.isDebugMode) }
        }

        triggerSync()
    }

    private fun setLoadingNext(isLoadingNext: Boolean) {
        vmState.update { current ->
            if (current.isLoadingNext == isLoadingNext) current else current.copy(isLoadingNext = isLoadingNext)
        }
    }

    private fun triggerSync() {
        val syncQueueKey = activeSyncQueueKey ?: return
        if (syncJob?.isActive == true) return
        syncJob = scope.launch { processSyncQueue(syncQueueKey) }
    }

    private suspend fun processSyncQueue(syncQueueKey: String) {
        refreshSyncIndicator(syncQueueKey)
        while (true) {
            val nowMs = currentTimeMillis()
            val command = feedSyncLocalStore.peekNext(syncQueueKey, nowMs) ?: break
            val result = when (command.type) {
                FeedSyncCommandType.SWIPE -> feedRepository.recordSwipe(
                    postId = command.postId,
                    isLiked = command.isLiked == true,
                )
                FeedSyncCommandType.UNDO -> feedRepository.undoSwipe(command.postId)
            }

            if (result is UpdateDataResponse.Success) {
                feedSyncLocalStore.remove(command.id)
            } else {
                val nextAttempt = command.attempts + 1
                if (nextAttempt >= SYNC_MAX_ATTEMPTS) {
                    logger.d(
                        "SharedFeedStateHolder.processSyncQueue",
                        "Dropping sync command id=${command.id} after $nextAttempt attempts",
                    )
                    feedSyncLocalStore.remove(command.id)
                } else {
                    val retryDelayMs = computeRetryDelayMs(nextAttempt)
                    feedSyncLocalStore.incrementAttempts(
                        id = command.id,
                        nextAttemptAt = nowMs + retryDelayMs,
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
        vmState.update { current ->
            if (current.isRecordingSwipe == hasPending) current else current.copy(isRecordingSwipe = hasPending)
        }
    }

    private fun scheduleSyncRetryIfNeeded(syncQueueKey: String) {
        syncRetryJob?.cancel()
        syncRetryJob = scope.launch {
            val pending = feedSyncLocalStore.pendingCount(syncQueueKey)
            if (pending <= 0) return@launch
            val nextAttemptAt = feedSyncLocalStore.nextAttemptAt(syncQueueKey) ?: return@launch
            val delayMs = (nextAttemptAt - currentTimeMillis()).coerceAtLeast(0L)
            if (delayMs > 0) delay(delayMs)
            triggerSync()
        }
    }

    private fun computeRetryDelayMs(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 10)
        val multiplier = 1L shl exponent
        return (SYNC_BACKOFF_BASE_MS * multiplier).coerceAtMost(SYNC_BACKOFF_MAX_MS)
    }

    private fun inNoMoreCooldown(nowMs: Long = currentTimeMillis()): Boolean {
        return nowMs < noMoreCooldownUntilMs
    }

    private fun syncNoMoreForNowFlag(nowMs: Long = currentTimeMillis()) {
        val shouldShow = inNoMoreCooldown(nowMs)
        vmState.update { current ->
            if (current.noMoreForNow == shouldShow) current else current.copy(noMoreForNow = shouldShow)
        }
    }

    private fun resetNoProgressState() {
        noProgressCycles = 0
        noMoreCooldownUntilMs = 0L
        noMoreFlagResetJob?.cancel()
        vmState.update { current ->
            if (!current.noMoreForNow) current else current.copy(noMoreForNow = false)
        }
    }

    private fun markNoProgressCycle() {
        val nextCycle = (noProgressCycles + 1).coerceAtMost(NO_PROGRESS_BACKOFF_STEPS_MS.size)
        noProgressCycles = nextCycle
        val delayMs = NO_PROGRESS_BACKOFF_STEPS_MS[nextCycle - 1]
        noMoreCooldownUntilMs = currentTimeMillis() + delayMs
        vmState.update { current ->
            if (current.noMoreForNow) current else current.copy(noMoreForNow = true)
        }
        scheduleNoMoreFlagReset(delayMs)
    }

    private fun scheduleNoMoreFlagReset(delayMs: Long) {
        noMoreFlagResetJob?.cancel()
        noMoreFlagResetJob = scope.launch {
            if (delayMs > 0) delay(delayMs)
            syncNoMoreForNowFlag()
        }
    }

    private fun enqueueSwipeSync(postId: String, isLiked: Boolean) {
        ensureActiveUserState()
        val syncQueueKey = activeSyncQueueKey ?: return
        scope.launch {
            feedSyncLocalStore.enqueueSwipe(syncQueueKey, postId, isLiked)
            refreshSyncIndicator(syncQueueKey)
            triggerSync()
        }
    }

    private fun enqueueUndoSync(postId: String) {
        ensureActiveUserState()
        val syncQueueKey = activeSyncQueueKey ?: return
        scope.launch {
            feedSyncLocalStore.enqueueUndo(syncQueueKey, postId)
            refreshSyncIndicator(syncQueueKey)
            triggerSync()
        }
    }

    override fun loadData() {
        if (!isFeedScreenActive) return
        ensureActiveUserState()
        syncNoMoreForNowFlag()
        if (vmState.value.isLoading) return
        if (inNoMoreCooldown()) return

        val currentSize = vmState.value.items.size
        if (currentSize >= TARGET_STACK_SIZE) return
        if (currentSize > 0) {
            scope.launch { softRevalidateStack() }
            return
        }

        vmState.update {
            it.copy(
                isLoading = true,
                isError = false,
                isNoMorePosts = false,
                isLoadingNext = true,
            )
        }

        scope.launch {
            val startedAt = currentTimeMillis()
            scope.launch {
                runCatching { categoryCatalogRepository.refresh(force = false) }
                    .onFailure { error ->
                        logger.d(
                            "SharedFeedStateHolder.loadData",
                            "Background categories refresh failed: ${error.message}",
                        )
                    }
            }
            repeat(TARGET_STACK_SIZE) { loadOneMore() }
            logger.logLoadTiming(
                tag = "SharedFeedStateHolder.loadData",
                operation = TelemetryOperation.FeedInitialLoad,
                startedAtMs = startedAt,
                extras = "items=${vmState.value.items.size}",
            )
            vmState.update { it.copy(isLoading = false, isLoadingNext = false) }
        }
    }

    private suspend fun softRevalidateStack() {
        if (inNoMoreCooldown()) {
            syncNoMoreForNowFlag()
            return
        }
        val currentSize = vmState.value.items.size
        if (currentSize >= TARGET_STACK_SIZE) return
        val toLoad = TARGET_STACK_SIZE - currentSize
        val startedAt = currentTimeMillis()
        repeat(toLoad) { loadOneMore() }
        logger.logLoadTiming(
            tag = "SharedFeedStateHolder.loadData",
            operation = TelemetryOperation.FeedSoftRevalidate,
            startedAtMs = startedAt,
            extras = "requested=$toLoad size=${vmState.value.items.size}",
        )
    }

    private suspend fun loadOneMore() {
        loadMutex.withLock {
            when {
                !isFeedScreenActive -> {
                    setLoadingNext(false)
                    return
                }
                vmState.value.items.size >= TARGET_STACK_SIZE -> return
                inNoMoreCooldown() -> {
                    syncNoMoreForNowFlag()
                    setLoadingNext(false)
                    return
                }
            }

            setLoadingNext(true)
            var attempts = 0
            while (attempts < LOAD_MAX_ATTEMPTS_PER_CYCLE) {
                attempts++
                when (val result = feedRepository.getNextPost()) {
                    is GetDataResponse.Success -> if (tryAppendFeedItem(result.data)) return
                    is GetDataResponse.Error -> {
                        if (handleInitialLoadError(result.error)) return
                        break
                    }
                }
            }

            if (vmState.value.items.size < TARGET_STACK_SIZE) {
                markNoProgressCycle()
                if (vmState.value.items.isEmpty()) {
                    vmState.update { current -> current.copy(isError = false, isNoMorePosts = true) }
                }
            }
            setLoadingNext(false)
        }
    }

    private fun tryAppendFeedItem(feedPost: FeedPost): Boolean {
        val postId = feedPost.post.id
        val alreadyInStack = vmState.value.items.any { it.post.id == postId }
        if (postId in excludedPostIds || postId in seenPostIds || alreadyInStack) return false

        val reason = mapReason(feedPost.reason)
        val newItem = SharedFeedItem(
            entryId = nextEntryId++,
            post = feedPost.post,
            recommendationReason = reason,
        )
        rememberSeenPost(postId)
        resetNoProgressState()
        vmState.update {
            it.copy(
                items = it.items + newItem,
                recommendationReason = reason,
                isLoading = false,
                isError = false,
                isNoMorePosts = false,
                isLoadingNext = false,
            )
        }
        return true
    }

    private fun handleInitialLoadError(error: GetDataError): Boolean {
        if (vmState.value.items.isNotEmpty()) return false
        val isNoData = error == GetDataError.NoData
        vmState.update {
            it.copy(
                isError = !isNoData,
                isNoMorePosts = isNoData,
                isLoading = false,
                isLoadingNext = false,
            )
        }
        return true
    }

    override fun onSwipeLeft() = onSwipe(isLiked = false)
    override fun onSwipeRight() = onSwipe(isLiked = true)
    override fun onSwipeUp() = onSwipe(isLiked = true)
    override fun onSwipeDown() = onSwipe(isLiked = false)

    private fun onSwipe(isLiked: Boolean) {
        val currentItems = vmState.value.items
        if (currentItems.isEmpty()) return
        val swipedItem = currentItems.first()
        rememberExcludedPost(swipedItem.post.id)
        rememberSeenPost(swipedItem.post.id)
        val remainingItems = currentItems.drop(1)
        val undoState = LastUndoSwipe(swipedItem, isLiked)
        vmState.update { it.copy(items = remainingItems, lastUndoSwipe = undoState) }
        enqueueSwipeSync(swipedItem.post.id, isLiked)
        scope.launch { loadOneMore() }
    }

    override fun undoLastSwipe() {
        val undoState = vmState.value.lastUndoSwipe ?: return
        val restoredItem = undoState.item
        val restoredPostId = restoredItem.post.id
        excludedPostIds.remove(restoredPostId)
        rememberSeenPost(restoredPostId)
        vmState.update { current ->
            val withoutDuplicate = current.items.filterNot { it.post.id == restoredPostId }
            val restored = listOf(restoredItem) + withoutDuplicate
            current.copy(
                items = restored.take(TARGET_STACK_SIZE),
                recommendationReason = restoredItem.recommendationReason,
                lastUndoSwipe = null,
            )
        }
        mutableUiEffects.tryEmit(SharedFeedUiEffect.PlayUndoAnimation(isLiked = undoState.isLiked))
        enqueueUndoSync(restoredPostId)
    }

    override fun toggleDebugMode() {
        vmState.update { it.copy(isDebugMode = !it.isDebugMode) }
    }

    override fun clearProfile() {
        scope.launch {
            val currentSyncQueueKey = activeSyncQueueKey
            userProfileRepository.clearProfile()
            syncRetryJob?.cancel()
            noMoreFlagResetJob?.cancel()
            profileLocalStore.clearUser(PostsCacheKeys.SELF_USER_ID)
            postsLocalStore.clearUser(PostsCacheKeys.SELF_USER_ID)
            postsLocalStore.clearUser(PostsCacheKeys.FEED_STACK_USER_ID)
            if (currentSyncQueueKey != null) {
                feedSyncLocalStore.clearUser(currentSyncQueueKey)
            }
            feedRepository.clearSession()
            authenticationManager.clearAuth()

            vmState.update { FeedHolderVmState() }
            noProgressCycles = 0
            noMoreCooldownUntilMs = 0L
            nextEntryId = 0L
            excludedPostIds.clear()
            seenPostIds.clear()
            activeUserId = null
            activeSyncQueueKey = null
            ensureActiveUserState(forceRestore = true)
            loadData()
        }
    }

    override fun resetRecommendations() {
        scope.launch {
            val currentSyncQueueKey = activeSyncQueueKey
            userProfileRepository.resetRecommendations()
            syncRetryJob?.cancel()
            noMoreFlagResetJob?.cancel()
            noProgressCycles = 0
            noMoreCooldownUntilMs = 0L
            if (currentSyncQueueKey != null) {
                feedSyncLocalStore.clearUser(currentSyncQueueKey)
            }
            vmState.update {
                it.copy(
                    items = emptyList(),
                    lastUndoSwipe = null,
                    isRecordingSwipe = false,
                    noMoreForNow = false,
                    isNoMorePosts = false,
                    isError = false,
                )
            }
            postsLocalStore.clearUser(PostsCacheKeys.FEED_STACK_USER_ID)
            feedRepository.clearSession()
            nextEntryId = 0L
            excludedPostIds.clear()
            seenPostIds.clear()
            loadData()
        }
    }

    override fun deletePost(postId: String) {
        scope.launch {
            val result = postsRepository.deletePost(postId)
            if (result is UpdateDataResponse.Success) {
                rememberExcludedPost(postId)
                rememberSeenPost(postId)
                vmState.update {
                    it.copy(
                        items = it.items.filterNot { item -> item.post.id == postId },
                        lastUndoSwipe = it.lastUndoSwipe?.takeIf { undo -> undo.item.post.id != postId },
                    )
                }
                loadOneMore()
            } else {
                logger.d("SharedFeedStateHolder.deletePost", "deletePost failed: $result")
            }
        }
    }

    override fun updatePost(postId: String, description: String, imageUrls: List<String>) {
        scope.launch {
            val result = postsRepository.updatePost(postId, description, imageUrls)
            if (result is UpdateDataResponse.Success) {
                applyPostEditLocally(postId, description, imageUrls)
            } else {
                logger.d("SharedFeedStateHolder.updatePost", "updatePost failed: $result")
            }
        }
    }

    override fun applyPostEdit(postId: String, description: String?, imageUrls: List<String>) {
        applyPostEditLocally(postId, description.orEmpty(), imageUrls)
    }

    private fun applyPostEditLocally(postId: String, description: String, imageUrls: List<String>) {
        val normalizedUrls = imageUrls.filter(String::isNotBlank)
        vmState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.post.id == postId) {
                        item.copy(
                            post = item.post.copy(
                                content = item.post.content.copy(
                                    description = description,
                                    imageUrls = normalizedUrls,
                                    imageVariants = emptyList(),
                                ),
                            ),
                        )
                    } else {
                        item
                    }
                },
            )
        }
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
