package me.floow.shared.profile.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.floow.shared.profile.uilogic.edit.UpdatedProfileData
import me.floow.shared.profile.ui.model.ProfilePostItem

class ProfileStateHolder(
    private val repository: ProfileRepository,
    private val userId: String?,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow<ProfileScreenState>(ProfileScreenState.Loading)
    val state: StateFlow<ProfileScreenState> = _state.asStateFlow()

    private var didLoad = false
    private var isLoading = false
    private var nextOffset = 0

    fun loadIfNeeded() {
        if (didLoad || isLoading) return
        load()
    }

    fun load(force: Boolean = false) {
        if (isLoading && !force) return
        isLoading = true
        if (!didLoad) {
            _state.value = ProfileScreenState.Loading
        }
        scope.launch {
            runCatching { repository.getProfile(userId) }
                .onSuccess { payload ->
                    didLoad = true
                    nextOffset = payload.posts.size
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
                nextOffset += posts.size
                val updatedState = (_state.value as? ProfileScreenState.Success) ?: current
                _state.value = updatedState.copy(
                    posts = updatedState.posts + posts.map(::profilePostToUiModel),
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
            posts = listOf(createdItem) + current.posts,
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
        return repository.deletePost(postId)
            .onSuccess {
                _state.value = current.copy(
                    posts = current.posts.filterNot { it.id == postId },
                    totalLikesReceived = (current.totalLikesReceived - deletedPost.likesCount).coerceAtLeast(0),
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
            backgroundUri = backgroundUrl,
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

    private fun profilePostToUiModel(post: ProfilePost): ProfilePostItem {
        val normalizedVariants = post.imageVariants.map { variant ->
            ProfileImageVariant(
                lqUrl = variant.lqUrl?.takeIf(String::isNotBlank),
                previewUrl = variant.previewUrl?.takeIf(String::isNotBlank),
                fullUrl = variant.fullUrl?.takeIf(String::isNotBlank),
            )
        }
        val fallbackUrls = post.imageUrls.filter(String::isNotBlank)
        val firstVariant = normalizedVariants.firstOrNull()
        val viewerUrls = normalizedVariants.mapNotNull {
            it.fullUrl ?: it.previewUrl ?: it.lqUrl
        }.ifEmpty { fallbackUrls }
        val previewUrls = normalizedVariants.mapNotNull {
            it.previewUrl ?: it.lqUrl ?: it.fullUrl
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
            category = post.category,
            createdAtMillis = post.createdAtMillis,
        )
    }

    private companion object {
        const val POSTS_PAGE_SIZE = 20
    }
}
