package me.floow.shared.profile.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.floow.shared.chats.uilogic.ChatsListRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.domain.cache.PendingPostMutation
import me.floow.domain.cache.PendingPostMutationState
import me.floow.domain.cache.PendingPostMutationType
import me.floow.domain.cache.PendingPostsLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.UsernameToIdCache
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.cache.CachePolicy
import me.floow.domain.data.cache.CacheState
import me.floow.domain.data.repos.PendingPostImageEntryKind
import me.floow.domain.data.repos.PendingPostImagePayload
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostStagedMedia
import me.floow.domain.data.repos.PendingPostsSyncScheduler
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PostContent
import me.floow.domain.models.PublicProfile
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.domain.utils.currentTimeMillis
import kotlinx.coroutines.flow.first
import me.floow.shared.media.outbox.MediaOutboxEngine

class AndroidProfileRepository(
    private val profileRepository: me.floow.domain.data.repos.ProfileRepository,
    private val postsRepository: PostsRepository,
    private val presenceRepository: PresenceRepository,
    private val profileLocalStore: ProfileLocalStore,
    private val postsLocalStore: PostsLocalStore,
    private val usernameToIdCache: UsernameToIdCache,
    private val pendingPostsLocalStore: PendingPostsLocalStore,
    private val pendingPostMediaStore: PendingPostMediaStore,
    private val pendingPostsSyncScheduler: PendingPostsSyncScheduler,
    private val usersApi: me.floow.domain.api.UsersApi,
    private val mediaOutboxEngine: MediaOutboxEngine? = null,
    private val chatsListRepository: ChatsListRepository? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ProfileRepository {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val headerSeeds = mutableMapOf<String, ProfileHeaderSeed>()

    private val selfProfileState: StateFlow<ProfilePayload?> =
        combine(
            profileLocalStore.observeProfile(SELF_USER_ID),
            observeSelfPosts(),
        ) { cachedProfile, selfPosts ->
            if (cachedProfile == null && selfPosts.isEmpty()) {
                null
            } else {
                ProfilePayload(
                    id = cachedProfile?.id ?: SELF_USER_ID,
                    shortUsername = cachedProfile?.username?.value,
                    avatarUrl = cachedProfile?.avatarUrl,
                    avatarThumb = cachedProfile?.avatarThumb,
                    backgroundUrl = cachedProfile?.backgroundUrl,
                    backgroundThumb = cachedProfile?.backgroundThumb,
                    displayName = cachedProfile?.name?.value,
                    description = cachedProfile?.description?.value,
                    totalLikesReceived = cachedProfile?.totalLikesReceived ?: 0,
                    isSelf = true,
                    isOnline = true,
                    lastSeenAtMillis = null,
                    posts = selfPosts.take(MAX_CACHED_POSTS),
                    arePostsError = false,
                    canLoadMorePosts = selfPosts.size >= POSTS_PAGE_SIZE,
                )
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    override fun observeSelfProfile(): StateFlow<ProfilePayload?> = selfProfileState

    override fun putHeaderSeed(seed: ProfileHeaderSeed) {
        val uid = seed.userId.trim()
        if (uid.isEmpty()) return
        headerSeeds[uid] = seed
        seed.username?.trim()?.takeIf { it.isNotEmpty() }?.let { uname ->
            headerSeeds[uname] = seed
            usernameToIdCache.put(uname, uid)
        }
    }

    override fun getHeaderSeed(userId: String): ProfileHeaderSeed? {
        val normalized = userId.trim()
        if (normalized.isEmpty() || normalized == SELF_USER_ID || normalized == SELF_USER_ALIAS) return null
        headerSeeds[normalized]?.let { return it }
        val resolvedId = usernameToIdCache.getUserId(normalized)
        if (resolvedId != null) {
            headerSeeds[resolvedId]?.let { return it }
        }
        chatsListRepository?.observeChats()?.value?.firstOrNull { chat ->
            chat.peerUserId == normalized || (resolvedId != null && chat.peerUserId == resolvedId)
        }?.let { chat ->
            val seed = ProfileHeaderSeed(
                userId = chat.peerUserId ?: normalized,
                displayName = chat.title,
                username = null,
                avatarUrl = chat.avatarUrl,
            )
            headerSeeds[seed.userId] = seed
            return seed
        }
        return null
    }

    override fun observeSelfPosts(): Flow<List<ProfilePost>> {
        return combine(
            postsLocalStore.observePosts(SELF_USER_ID),
            pendingPostsLocalStore.observeAllPendingForSelf(),
        ) { cachedPosts, pendingCommands ->
            mergePendingPosts(
                posts = cachedPosts.map { it.toSharedProfilePost() },
                pendingCommands = pendingCommands,
            )
        }
    }

    override suspend fun loadCachedInitial(userId: String?): ProfilePayload? {
        val isSelf = userId.isNullOrBlank() || userId == SELF_USER_ID || userId == SELF_USER_ALIAS
        val rawUserId = if (isSelf) SELF_USER_ID else userId.orEmpty()
        val resolvedUserId = if (!isSelf && !rawUserId.all(Char::isDigit)) {
            resolveUserIdByUsername(rawUserId) ?: rawUserId
        } else {
            rawUserId
        }

        val cachedProfile = profileLocalStore.getProfile(resolvedUserId)
        val cachedPosts = postsLocalStore.getPosts(resolvedUserId).take(MAX_CACHED_POSTS)
        if (cachedProfile == null && cachedPosts.isEmpty()) return null

        return buildPayloadFromCache(
            cachedProfile = cachedProfile,
            cachedPosts = cachedPosts,
            isSelf = isSelf,
            resolvedUserId = resolvedUserId,
        )
    }

    override suspend fun getProfile(userId: String?): ProfilePayload {
        return coroutineScope {
            val isSelf = userId.isNullOrBlank() || userId == SELF_USER_ID || userId == SELF_USER_ALIAS
            val rawUserId = if (isSelf) SELF_USER_ID else userId.orEmpty()
            val resolvedUserId = if (!isSelf && !rawUserId.all(Char::isDigit)) {
                resolveUserIdByUsernameRemote(rawUserId) ?: rawUserId
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
                return@coroutineScope cachedPayload
            }

            val profileDeferred = async {
                if (isSelf) {
                    when (val response = profileRepository.getSelfData()) {
                        is GetDataResponse.Success -> {
                            val profile = PublicProfile(
                                id = SELF_USER_ID,
                                name = response.data.name,
                                username = response.data.username,
                                avatarUrl = response.data.avatarUrl,
                                avatarThumb = response.data.avatarThumb,
                                backgroundUrl = response.data.backgroundUrl,
                                backgroundThumb = response.data.backgroundThumb,
                                backgroundUpdatedAt = response.data.backgroundUpdatedAt,
                                description = response.data.description,
                                totalLikesReceived = response.data.totalLikesReceived,
                            )
                            profileLocalStore.upsertProfile(
                                userId = SELF_USER_ID,
                                profile = profile,
                                updatedAt = currentTimeMillis(),
                            )
                            cacheKnownUser(profile.username?.value, SELF_USER_ID)
                            ProfilePayload(
                                id = SELF_USER_ID,
                                shortUsername = response.data.username?.value,
                                avatarUrl = response.data.avatarUrl,
                                avatarThumb = response.data.avatarThumb,
                                backgroundUrl = response.data.backgroundUrl,
                                backgroundThumb = response.data.backgroundThumb,
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
                            if (hasCachedProfile) cachedPayload else error("Unable to load self profile: ${response.error}")
                        }
                    }
                } else {
                    when (val response = profileRepository.getUserProfile(resolvedUserId)) {
                        is GetDataResponse.Success -> {
                            profileLocalStore.upsertProfile(
                                userId = resolvedUserId,
                                profile = response.data,
                                updatedAt = currentTimeMillis(),
                            )
                            cacheKnownUser(response.data.username?.value, resolvedUserId)
                            val presence = presenceRepository.presences.value[resolvedUserId]
                            ProfilePayload(
                                id = response.data.id,
                                shortUsername = response.data.username?.value,
                                avatarUrl = response.data.avatarUrl,
                                avatarThumb = response.data.avatarThumb,
                                backgroundUrl = response.data.backgroundUrl,
                                backgroundThumb = response.data.backgroundThumb,
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
                            if (hasCachedProfile) cachedPayload else error("Unable to load profile $resolvedUserId: ${response.error}")
                        }
                    }
                }
            }

            val postsDeferred = async {
                loadPostsMapped(
                    userId = resolvedUserId,
                    offset = 0,
                    limit = POSTS_PAGE_SIZE,
                )
            }

            val profilePayload = profileDeferred.await()
            val (domainPosts, sharedPosts) = postsDeferred.await()
            if (domainPosts.isNotEmpty()) {
                postsLocalStore.replacePosts(
                    userId = resolvedUserId,
                    posts = domainPosts,
                    updatedAt = currentTimeMillis(),
                )
                cacheKnownUsersFromDomainPosts(domainPosts)
            }
            profilePayload.copy(
                posts = sharedPosts,
                canLoadMorePosts = domainPosts.size >= POSTS_PAGE_SIZE,
            )
        }
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
        val localPending = pendingPostsLocalStore.getByLocalPostId(postId)
        if (localPending != null && localPending.remotePostId == null) {
            mediaOutboxEngine?.cancelPost(postId)
            pendingPostsLocalStore.delete(localPending.commandId)
            pendingPostMediaStore.deleteStaged(localPending.commandId)
            return Result.success(Unit)
        }

        return when (val response = postsRepository.deletePost(postId)) {
            is UpdateDataResponse.Success -> {
                syncDeletedPostCaches(
                    postId = postId,
                    authorId = SELF_USER_ID,
                    postsLocalStore = postsLocalStore,
                    profileLocalStore = profileLocalStore,
                    updatedAt = currentTimeMillis(),
                )
                Result.success(Unit)
            }
            is UpdateDataResponse.Failure -> Result.failure(
                IllegalStateException("Unable to delete post $postId: ${response.failureError}")
            )
        }
    }

    override suspend fun retryPendingPost(postId: String): Result<Unit> {
        val pending = pendingPostsLocalStore.getByLocalPostId(postId)
            ?: pendingPostsLocalStore.getByRemotePostId(postId)
            ?: return Result.failure(IllegalArgumentException("Pending post $postId not found"))
        val now = currentTimeMillis()
        val updated = pendingPostsLocalStore.retry(
            commandId = pending.commandId,
            nextRetryAt = now,
            updatedAt = now,
        )
        if (!updated) {
            return Result.failure(IllegalStateException("Pending post $postId is not retryable"))
        }
        if (mediaOutboxEngine != null) {
            mediaOutboxEngine.retryPost(pending.localPostId)
        } else {
            pendingPostsSyncScheduler.enqueueNow()
            pendingPostsSyncScheduler.ensurePeriodic()
        }
        return Result.success(Unit)
    }

    private suspend fun buildPayloadFromCache(
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
            avatarThumb = cachedProfile?.avatarThumb,
            backgroundUrl = cachedProfile?.backgroundUrl,
            backgroundThumb = cachedProfile?.backgroundThumb,
            displayName = cachedProfile?.name?.value,
            description = cachedProfile?.description?.value,
            totalLikesReceived = cachedProfile?.totalLikesReceived ?: 0,
            isSelf = isSelf,
            isOnline = presence?.isOnline == true,
            lastSeenAtMillis = presence?.lastSeenAtMillis,
            posts = mergePendingPosts(
                posts = cachedPosts.map { it.toSharedProfilePost() },
                pendingCommands = if (isSelf) pendingPostsLocalStore.observeAllPendingForSelf().first() else emptyList(),
            ),
            arePostsError = false,
            canLoadMorePosts = cachedPosts.size >= POSTS_PAGE_SIZE,
        )
    }

    private fun resolveUserIdByUsername(username: String): String? {
        val normalized = username.trim()
        usernameToIdCache.getUserId(normalized)?.let { return it }
        return null
    }

    private suspend fun resolveUserIdByUsernameRemote(username: String): String? {
        val normalized = username.trim()
        usernameToIdCache.getUserId(normalized)?.let { return it }
        val response = usersApi.searchUsers(query = normalized, limit = 5)
        if (response is me.floow.domain.api.models.SearchUsersResponse.Success) {
            response.users.forEach { cacheKnownUser(it.username, it.id) }
            response.users.firstOrNull {
                it.username.equals(normalized, ignoreCase = true)
            }?.let { return it.id }
        }
        return null
    }

    private fun cacheKnownUser(username: String?, userId: String) {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty() || normalizedUserId == SELF_USER_ID || normalizedUserId == SELF_USER_ALIAS) return
        val normalizedUsername = username?.trim().orEmpty()
        if (normalizedUsername.isEmpty()) return
        usernameToIdCache.put(normalizedUsername, normalizedUserId)
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
                val shared = mergePendingPosts(
                    posts = domain.map { it.toSharedProfilePost() },
                    pendingCommands = if (userId == SELF_USER_ID) pendingPostsLocalStore.observeAllPendingForSelf().first() else emptyList(),
                )
                domain to shared
            }
            is GetDataResponse.Error -> error("Unable to load posts for $userId: ${response.error}")
        }
    }

    private fun mergePendingPosts(
        posts: List<ProfilePost>,
        pendingCommands: List<PendingPostMutation>,
    ): List<ProfilePost> {
        val activeCommands = pendingCommands.filter { command ->
            command.state != PendingPostMutationState.DONE && command.state != PendingPostMutationState.CANCELED
        }
        if (activeCommands.isEmpty()) return posts

        val hiddenRemoteIds = activeCommands
            .filter { it.type == PendingPostMutationType.DELETE }
            .mapNotNullTo(hashSetOf()) { it.remotePostId }
        val updateCommandsByRemoteId = activeCommands
            .filter { it.type == PendingPostMutationType.UPDATE && it.state != PendingPostMutationState.FAILED }
            .mapNotNull { command -> command.remotePostId?.let { it to command } }
            .toMap()

        val mergedRemote = posts
            .filterNot { post -> post.id in hiddenRemoteIds }
            .map { post ->
                val command = updateCommandsByRemoteId[post.id] ?: return@map post
                command.toPendingProfilePost(basePost = post)
            }

        val pendingCreates = activeCommands
            .filter { it.type == PendingPostMutationType.CREATE }
            .map { it.toPendingProfilePost(basePost = null) }
            .filterNot { pending -> mergedRemote.any { remote -> remote.id == pending.id || remote.id == pending.remotePostId } }

        return pendingCreates + mergedRemote
    }

    private fun PendingPostMutation.toPendingProfilePost(basePost: ProfilePost?): ProfilePost {
        val stagedMedia = decodeStagedMedia(stagedMediaJson)
        val payload = decodeImagePayload(imagePayloadJson)
        val imageUrls = payload.resolveDisplayUrls(stagedMedia)
        val shouldExposePendingUi = type != PendingPostMutationType.UPDATE
        return ProfilePost(
            id = if (type == PendingPostMutationType.CREATE) localPostId else (remotePostId ?: basePost?.id ?: localPostId),
            description = description ?: basePost?.description,
            imageVariants = imageUrls.map { url ->
                ProfileImageVariant(
                    thumbUrl = url,
                    fullUrl = url,
                )
            },
            imageUrls = imageUrls,
            likesCount = basePost?.likesCount ?: 0,
            commentsCount = basePost?.commentsCount ?: 0,
            commentersPreview = basePost?.commentersPreview ?: emptyList(),
            category = category ?: basePost?.category,
            createdAtMillis = basePost?.createdAtMillis ?: createdAt,
            optimisticState = state.toOptimisticState().takeIf { shouldExposePendingUi },
            syncState = state.toSyncState().takeIf { shouldExposePendingUi },
            pendingMutationType = type.toUiMutationType().takeIf { shouldExposePendingUi },
            isOptimistic = shouldExposePendingUi,
            localPostId = localPostId,
            remotePostId = remotePostId ?: basePost?.id,
            lastSyncError = lastError.takeIf { shouldExposePendingUi },
        )
    }

    private fun decodeImagePayload(payloadJson: String): PendingPostImagePayload {
        return runCatching {
            json.decodeFromString(PendingPostImagePayload.serializer(), payloadJson)
        }.getOrDefault(PendingPostImagePayload())
    }

    private fun decodeStagedMedia(payloadJson: String): List<PendingPostStagedMedia> {
        return runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(PendingPostStagedMedia.serializer()), payloadJson)
        }.getOrDefault(emptyList())
    }

    private fun PendingPostImagePayload.resolveDisplayUrls(stagedMedia: List<PendingPostStagedMedia>): List<String> {
        val stagedById = stagedMedia.associateBy(PendingPostStagedMedia::id)
        return entries.mapNotNull { entry ->
            when (entry.kind) {
                PendingPostImageEntryKind.REMOTE -> entry.remoteUrl
                PendingPostImageEntryKind.STAGED -> stagedById[entry.stagedMediaId]?.let(pendingPostMediaStore::previewUri)
            }
        }
    }

    private fun Post.toSharedProfilePost(): ProfilePost {
        return ProfilePost(
            id = id,
            description = content.description,
            imageVariants = content.resolvedImageVariants().map { variant ->
                ProfileImageVariant(
                    thumbUrl = variant.thumbUrl,
                    fullUrl = variant.fullUrl,
                )
            },
            imageUrls = content.viewerImageUrls().ifEmpty { content.previewImageUrls() },
            likesCount = likesCount,
            commentsCount = commentsCount,
            commentersPreview = commentersPreview,
            category = category,
            createdAtMillis = createdAt,
            remotePostId = id,
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

private fun PendingPostMutationState.toOptimisticState(): ProfilePostOptimisticState {
    return when (this) {
        PendingPostMutationState.FAILED -> ProfilePostOptimisticState.FAILED
        PendingPostMutationState.PENDING,
        PendingPostMutationState.UPLOADING,
        PendingPostMutationState.SYNCING,
        PendingPostMutationState.DONE,
        PendingPostMutationState.CANCELED -> ProfilePostOptimisticState.PENDING
    }
}

private fun PendingPostMutationState.toSyncState(): ProfilePostSyncState {
    return when (this) {
        PendingPostMutationState.PENDING -> ProfilePostSyncState.PENDING
        PendingPostMutationState.UPLOADING -> ProfilePostSyncState.UPLOADING
        PendingPostMutationState.SYNCING -> ProfilePostSyncState.SYNCING
        PendingPostMutationState.FAILED -> ProfilePostSyncState.FAILED
        PendingPostMutationState.DONE,
        PendingPostMutationState.CANCELED -> ProfilePostSyncState.PENDING
    }
}

private fun PendingPostMutationType.toUiMutationType(): ProfilePostPendingMutationType {
    return when (this) {
        PendingPostMutationType.CREATE -> ProfilePostPendingMutationType.CREATE
        PendingPostMutationType.UPDATE -> ProfilePostPendingMutationType.UPDATE
        PendingPostMutationType.DELETE -> ProfilePostPendingMutationType.DELETE
    }
}

private fun maxOfNotNull(vararg values: Long?): Long? {
    return values.filterNotNull().maxOrNull()
}
