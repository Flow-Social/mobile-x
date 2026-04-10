package me.floow.shared.profile.uilogic

import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.UsernameToIdCache
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.cache.CachePolicy
import me.floow.domain.data.cache.CacheState
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PublicProfile
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls

class AndroidProfileRepository(
    private val profileRepository: me.floow.domain.data.repos.ProfileRepository,
    private val postsRepository: PostsRepository,
    private val presenceRepository: PresenceRepository,
    private val profileLocalStore: ProfileLocalStore,
    private val postsLocalStore: PostsLocalStore,
    private val usernameToIdCache: UsernameToIdCache,
) : ProfileRepository {
    override suspend fun getProfile(userId: String?): ProfilePayload {
        val isSelf = userId.isNullOrBlank() || userId == SELF_USER_ID || userId == SELF_USER_ALIAS
        val rawUserId = if (isSelf) SELF_USER_ID else userId.orEmpty()
        val resolvedUserId = if (!isSelf && !rawUserId.all(Char::isDigit)) {
            resolveUserIdByUsername(rawUserId) ?: rawUserId
        } else {
            rawUserId
        }

        val cachedProfile = profileLocalStore.getProfile(resolvedUserId)
        val cachedPosts = postsLocalStore.getPosts(resolvedUserId).take(MAX_CACHED_POSTS)
        val hasCachedProfile = cachedProfile != null
        val hasCachedPosts = cachedPosts.isNotEmpty()

        val cachedPayload = buildPayloadFromCache(
            cachedProfile = cachedProfile,
            cachedPosts = cachedPosts,
            isSelf = isSelf,
            resolvedUserId = resolvedUserId,
        )

        val persistedProfileUpdatedAt = profileLocalStore.getLastUpdatedAt(resolvedUserId)
        val persistedPostsUpdatedAt = postsLocalStore.getLastUpdatedAt(resolvedUserId)
        val effectiveUpdatedAt = maxOfNotNull(persistedProfileUpdatedAt, persistedPostsUpdatedAt)
        val cache = remoteCachePolicy.evaluate(
            hasCachedData = hasCachedProfile || hasCachedPosts,
            lastUpdatedAtMs = effectiveUpdatedAt,
        )

        if (cache.state == CacheState.Fresh && hasCachedProfile) {
            return cachedPayload
        }

        val profilePayload = if (isSelf) {
            when (val response = profileRepository.getSelfData()) {
                is GetDataResponse.Success -> {
                    val profile = PublicProfile(
                        id = SELF_USER_ID,
                        name = response.data.name,
                        username = response.data.username,
                        avatarUrl = response.data.avatarUrl,
                        backgroundUrl = response.data.backgroundUrl,
                        backgroundUpdatedAt = response.data.backgroundUpdatedAt,
                        description = response.data.description,
                        totalLikesReceived = response.data.totalLikesReceived,
                    )
                    profileLocalStore.upsertProfile(
                        userId = SELF_USER_ID,
                        profile = profile,
                        updatedAt = System.currentTimeMillis(),
                    )
                    cacheKnownUser(profile.username?.value, SELF_USER_ID)
                    ProfilePayload(
                        id = SELF_USER_ID,
                        shortUsername = response.data.username?.value,
                        avatarUrl = response.data.avatarUrl,
                        backgroundUrl = response.data.backgroundUrl,
                        displayName = response.data.name?.value,
                        description = response.data.description?.value,
                        totalLikesReceived = response.data.totalLikesReceived,
                        isSelf = true,
                        isOnline = false,
                        lastSeenAtMillis = null,
                        posts = emptyList(),
                        canLoadMorePosts = false,
                    )
                }
                is GetDataResponse.Error -> {
                    if (hasCachedProfile) return cachedPayload
                    error("Unable to load self profile: ${response.error}")
                }
            }
        } else {
            when (val response = profileRepository.getUserProfile(resolvedUserId)) {
                is GetDataResponse.Success -> {
                    profileLocalStore.upsertProfile(
                        userId = resolvedUserId,
                        profile = response.data,
                        updatedAt = System.currentTimeMillis(),
                    )
                    cacheKnownUser(response.data.username?.value, resolvedUserId)
                    val presence = presenceRepository.presences.value[resolvedUserId]
                    ProfilePayload(
                        id = response.data.id,
                        shortUsername = response.data.username?.value,
                        avatarUrl = response.data.avatarUrl,
                        backgroundUrl = response.data.backgroundUrl,
                        displayName = response.data.name?.value,
                        description = response.data.description?.value,
                        totalLikesReceived = response.data.totalLikesReceived,
                        isSelf = false,
                        isOnline = presence?.isOnline == true,
                        lastSeenAtMillis = presence?.lastSeenAtMillis,
                        posts = emptyList(),
                        canLoadMorePosts = false,
                    )
                }
                is GetDataResponse.Error -> {
                    if (hasCachedProfile) return cachedPayload
                    error("Unable to load profile $resolvedUserId: ${response.error}")
                }
            }
        }

        val (domainPosts, sharedPosts) = loadPostsMapped(
            userId = resolvedUserId,
            offset = 0,
            limit = POSTS_PAGE_SIZE,
        )
        if (domainPosts.isNotEmpty()) {
            postsLocalStore.replacePosts(
                userId = resolvedUserId,
                posts = domainPosts,
                updatedAt = System.currentTimeMillis(),
            )
            cacheKnownUsersFromDomainPosts(domainPosts)
        }
        return profilePayload.copy(
            posts = sharedPosts,
            canLoadMorePosts = sharedPosts.size >= POSTS_PAGE_SIZE,
        )
    }

    override suspend fun getMorePosts(
        userId: String?,
        offset: Int,
        limit: Int,
    ): List<ProfilePost> {
        val resolvedUserId = if (userId.isNullOrBlank() || userId == SELF_USER_ALIAS) {
            SELF_USER_ID
        } else {
            userId
        }
        return loadPosts(
            userId = resolvedUserId,
            offset = offset,
            limit = limit,
        )
    }

    override suspend fun deletePost(postId: String): Result<Unit> {
        return when (val response = postsRepository.deletePost(postId)) {
            UpdateDataResponse.Success -> Result.success(Unit)
            is UpdateDataResponse.Failure -> Result.failure(
                IllegalStateException("Unable to delete post $postId: ${response.failureError}")
            )
        }
    }

    private fun buildPayloadFromCache(
        cachedProfile: PublicProfile?,
        cachedPosts: List<Post>,
        isSelf: Boolean,
        resolvedUserId: String,
    ): ProfilePayload {
        val presence = if (!isSelf) {
            presenceRepository.presences.value[resolvedUserId]
        } else null
        return ProfilePayload(
            id = cachedProfile?.id ?: resolvedUserId,
            shortUsername = cachedProfile?.username?.value,
            avatarUrl = cachedProfile?.avatarUrl,
            backgroundUrl = cachedProfile?.backgroundUrl,
            displayName = cachedProfile?.name?.value,
            description = cachedProfile?.description?.value,
            totalLikesReceived = cachedProfile?.totalLikesReceived ?: 0,
            isSelf = isSelf,
            isOnline = presence?.isOnline == true,
            lastSeenAtMillis = presence?.lastSeenAtMillis,
            posts = cachedPosts.map { it.toSharedProfilePost() },
            arePostsError = false,
            canLoadMorePosts = cachedPosts.size >= POSTS_PAGE_SIZE,
        )
    }

    private fun resolveUserIdByUsername(username: String): String? {
        val normalized = username.trim()
        usernameToIdCache.getUserId(normalized)?.let { return it }
        return null
    }

    private fun cacheKnownUser(username: String?, userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty() || normalizedUserId == SELF_USER_ID || normalizedUserId == SELF_USER_ALIAS) return
        val normalizedUsername = username?.trim().orEmpty()
        if (normalizedUsername.isEmpty()) return
        usernameToIdCache.put(normalizedUsername, normalizedUserId)
    }

    private fun cacheKnownUsersFromPosts(posts: List<ProfilePost>) {
        // no-op; see cacheKnownUsersFromDomainPosts
    }

    private fun cacheKnownUsersFromDomainPosts(posts: List<Post>) {
        posts.forEach { post ->
            cacheKnownUser(
                username = post.author.username?.value,
                userId = post.author.id,
            )
        }
    }

    private suspend fun loadPosts(
        userId: String,
        offset: Int,
        limit: Int,
    ): List<ProfilePost> {
        return loadPostsMapped(userId, offset, limit).second
    }

    private suspend fun loadPostsMapped(
        userId: String,
        offset: Int,
        limit: Int,
    ): Pair<List<Post>, List<ProfilePost>> {
        return when (
            val response = postsRepository.getUserPosts(
                userId = userId,
                offset = offset,
                limit = limit,
            )
        ) {
            is GetDataResponse.Success -> {
                val domain = response.data
                val shared = domain.map { it.toSharedProfilePost() }
                domain to shared
            }
            is GetDataResponse.Error -> error("Unable to load posts for $userId: ${response.error}")
        }
    }

    private fun Post.toSharedProfilePost(): ProfilePost {
        return ProfilePost(
            id = id,
            description = content.description,
            imageVariants = content.resolvedImageVariants().map { variant ->
                ProfileImageVariant(
                    lqUrl = variant.lqUrl,
                    previewUrl = variant.previewUrl,
                    fullUrl = variant.fullUrl,
                )
            },
            imageUrls = content.viewerImageUrls().ifEmpty { content.previewImageUrls() },
            likesCount = likesCount,
            commentsCount = commentsCount,
            category = category,
            createdAtMillis = createdAt,
        )
    }

    private companion object {
        const val POSTS_PAGE_SIZE = 20
        const val MAX_CACHED_POSTS = 120
        const val SELF_USER_ID = "me"
        const val SELF_USER_ALIAS = "self"
        val remoteCachePolicy = CachePolicy(
            maxAgeMs = 5 * 60 * 1000L,
            staleWhileRevalidateMs = 30 * 60 * 1000L,
        )
    }
}

private fun maxOfNotNull(a: Long?, b: Long?): Long? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}
