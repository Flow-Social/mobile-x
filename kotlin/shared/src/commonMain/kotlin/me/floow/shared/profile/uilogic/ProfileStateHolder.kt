package me.floow.shared.profile.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import me.floow.domain.network.NetworkMonitor
import me.floow.domain.utils.currentTimeMillis
import me.floow.shared.profile.uilogic.edit.UpdatedProfileData
import me.floow.shared.profile.ui.model.ProfilePostItem

class ProfileStateHolder(
    private val repository: ProfileRepository,
    private val userId: String?,
    initialState: ProfileScreenState = ProfileScreenState.Loading,
    private val networkMonitor: NetworkMonitor? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private companion object {
        const val POSTS_PAGE_SIZE = 20
        private const val REMOTE_REVALIDATE_TTL_MS = 60_000L
    }

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<ProfileScreenState> = _state.asStateFlow()

    private var didLoad = initialState is ProfileScreenState.Success
    private var isLoading = false
    private var nextOffset = (initialState as? ProfileScreenState.Success)?.posts?.size ?: 0
    private var lastRemoteRefreshAtMs = 0L

    init {
        if (userId.isNullOrBlank() || userId == "me" || userId == "self") {
            scope.launch {
                repository.observeSelfPosts().collectLatest { posts ->
                    val current = _state.value as? ProfileScreenState.Success ?: return@collectLatest
                    if (!current.isSelf) return@collectLatest
                    _state.value = current.copy(posts = posts.map(::profilePostToUiModel))
                }
            }
        }
        networkMonitor?.let { monitor ->
            scope.launch {
                monitor.isOnline
                    .filter { it }
                    .collect {
                        when (val current = _state.value) {
                            is ProfileScreenState.Error -> load(force = true)
                            is ProfileScreenState.Success -> {
                                if (current.arePostsError) {
                                    load(force = true)
                                }
                            }
                            else -> Unit
                        }
                    }
            }
        }
    }

    fun loadIfNeeded() {
        if (isLoading) return
        if (didLoad && shouldRevalidateRemote()) {
            load(force = true)
            return
        }
        if (didLoad) return
        load()
    }

    fun load(force: Boolean = false) {
        if (isLoading && !force) return
        isLoading = true
        scope.launch {
            val cachedInitial = runCatching { repository.loadCachedInitial(userId) }.getOrNull()
            if (cachedInitial != null) {
                didLoad = true
                nextOffset = countRemotePosts(cachedInitial.posts)
                _state.value = cachedInitial.toUiState(
                    arePostsLoading = false,
                    arePostsError = cachedInitial.arePostsError,
                    isLoadingMorePosts = false,
                )
            } else if (!didLoad) {
                _state.value = ProfileScreenState.Loading
            }

            runCatching { repository.getProfile(userId) }
                .onSuccess { payload ->
                    didLoad = true
                    lastRemoteRefreshAtMs = currentTimeMillis()
                    nextOffset = countRemotePosts(payload.posts)
                    _state.value = payload.toUiState(
                        arePostsLoading = false,
                        arePostsError = payload.arePostsError,
                        isLoadingMorePosts = false,
                    )
                }
                .onFailure {
                    _state.value = when (val current = _state.value) {
                        is ProfileScreenState.Success -> current.copy(
                            arePostsLoading = false,
                            arePostsError = true,
                            isLoadingMorePosts = false,
                        )
                        else -> ProfileScreenState.Error(
                            message = it.message ?: "profile request failed"
                        )
                    }
                }
            isLoading = false
        }
    }

    private fun shouldRevalidateRemote(nowMs: Long = currentTimeMillis()): Boolean {
        return lastRemoteRefreshAtMs == 0L || nowMs - lastRemoteRefreshAtMs >= REMOTE_REVALIDATE_TTL_MS
    }

    fun loadMorePosts() {
        val current = _state.value as? ProfileScreenState.Success ?: return
        if (isLoading || !current.canLoadMorePosts || current.isLoadingMorePosts) return

        isLoading = true
        _state.value = current.copy(isLoadingMorePosts = true, arePostsError = false)
        scope.launch {
            runCatching {
                repository.getMorePosts(
                    userId = userId,
                    offset = nextOffset,
                    limit = POSTS_PAGE_SIZE,
                )
            }.onSuccess { posts ->
                nextOffset += countRemotePosts(posts)
                val updatedState = (_state.value as? ProfileScreenState.Success) ?: current
                val combinedPosts = (updatedState.posts + posts.map(::profilePostToUiModel)).distinctBy { it.id }
                _state.value = updatedState.copy(
                    posts = combinedPosts,
                    canLoadMorePosts = posts.size >= POSTS_PAGE_SIZE,
                    isLoadingMorePosts = false,
                    arePostsError = false,
                )
            }.onFailure {
                val updatedState = (_state.value as? ProfileScreenState.Success) ?: current
                _state.value = updatedState.copy(
                    isLoadingMorePosts = false,
                    arePostsError = true,
                )
            }
            isLoading = false
        }
    }

    fun insertCreatedPost(post: ProfilePost) {
        val current = _state.value as? ProfileScreenState.Success ?: return
        val createdItem = profilePostToUiModel(post)
        _state.value = current.copy(
            posts = listOf(createdItem) + current.posts.filterNot { existing -> existing.id == createdItem.id },
        )
    }

    fun replaceEditedPost(post: ProfilePost) {
        val current = _state.value as? ProfileScreenState.Success ?: return
        val updatedItem = profilePostToUiModel(post)
        _state.value = current.copy(
            posts = current.posts.map { item ->
                if (item.id == post.id) updatedItem else item
            }
        )
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        val current = _state.value as? ProfileScreenState.Success
            ?: return Result.failure(IllegalStateException("Profile is not loaded"))
        val deletedPost = current.posts.firstOrNull { it.id == postId }
            ?: return Result.failure(IllegalArgumentException("Post not found"))
        _state.value = current.copy(
            posts = current.posts.filterNot { it.id == postId },
            totalLikesReceived = (current.totalLikesReceived - deletedPost.likesCount).coerceAtLeast(0),
        )
        return repository.deletePost(postId)
            .onFailure {
                load(force = true)
            }
    }

    suspend fun retryPendingPost(postId: String): Result<Unit> {
        val current = _state.value as? ProfileScreenState.Success
            ?: return Result.failure(IllegalStateException("Profile is not loaded"))
        return repository.retryPendingPost(postId)
            .onSuccess {
                _state.value = current.copy(
                    posts = current.posts.map { post ->
                        if (post.id == postId) {
                            post.copy(
                                optimisticState = ProfilePostOptimisticState.PENDING.name,
                                syncState = ProfilePostSyncState.PENDING,
                                lastSyncError = null,
                            )
                        } else post
                    }
                )
            }
    }

    fun updateProfileHeader(data: UpdatedProfileData) {
        val current = _state.value as? ProfileScreenState.Success ?: return
        _state.value = current.copy(
            displayName = data.name,
            shortUsername = data.username,
            description = data.bio,
            avatarUri = data.avatarUrl,
            backgroundUri = data.backgroundUrl,
        )
    }

    private fun ProfilePayload.toUiState(
        arePostsLoading: Boolean,
        arePostsError: Boolean,
        isLoadingMorePosts: Boolean,
    ): ProfileScreenState.Success {
        return ProfileScreenState.Success(
            id = id,
            shortUsername = shortUsername,
            avatarUri = avatarUrl,
            avatarThumb = avatarThumb,
            backgroundUri = backgroundUrl,
            backgroundThumb = backgroundThumb,
            displayName = displayName,
            description = description,
            totalLikesReceived = totalLikesReceived,
            isSelf = isSelf,
            isOnline = isOnline,
            lastSeenAtMillis = lastSeenAtMillis,
            posts = posts.map(::profilePostToUiModel),
            arePostsLoading = arePostsLoading,
            arePostsError = arePostsError,
            canLoadMorePosts = canLoadMorePosts,
            isLoadingMorePosts = isLoadingMorePosts,
        )
    }

}

internal fun profilePostToUiModel(post: ProfilePost): ProfilePostItem {
    val normalizedVariants = post.imageVariants.map { variant ->
        ProfileImageVariant(
            thumbUrl = variant.thumbUrl?.takeIf(String::isNotBlank),
            fullUrl = variant.fullUrl?.takeIf(String::isNotBlank),
        )
    }
    val fallbackUrls = post.imageUrls.filter(String::isNotBlank)
    val firstVariant = normalizedVariants.firstOrNull()
    val viewerUrls = normalizedVariants.mapNotNull {
        it.fullUrl ?: it.thumbUrl
    }.ifEmpty { fallbackUrls }
    val previewUrls = normalizedVariants.mapNotNull {
        it.thumbUrl ?: it.fullUrl
    }.ifEmpty { fallbackUrls }

    return ProfilePostItem(
        id = post.id,
        description = post.description,
        lqUrl = firstVariant?.lqUrl ?: fallbackUrls.firstOrNull(),
        previewUrl = firstVariant?.previewUrl ?: fallbackUrls.firstOrNull(),
        fullUrl = firstVariant?.fullUrl ?: fallbackUrls.firstOrNull(),
        viewerUrls = viewerUrls,
        previewUrls = previewUrls,
        likesCount = post.likesCount,
        commentsCount = post.commentsCount,
        commentersPreview = post.commentersPreview,
        category = post.category,
        createdAtMillis = post.createdAtMillis,
        optimisticState = post.optimisticState?.name,
        syncState = post.syncState,
        pendingMutationType = post.pendingMutationType,
        isOptimistic = post.isOptimistic,
        localPostId = post.localPostId,
        remotePostId = post.remotePostId,
        lastSyncError = post.lastSyncError,
    )
}

private fun countRemotePosts(posts: List<ProfilePost>): Int {
    return posts.count { post -> !post.isOptimistic || post.pendingMutationType != ProfilePostPendingMutationType.CREATE }
}
