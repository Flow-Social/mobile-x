package me.floow.shared.profile.uilogic

import kotlinx.coroutines.flow.first
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.models.PublicProfile

suspend fun syncDeletedPostCaches(
    postId: String,
    authorId: String,
    postsLocalStore: PostsLocalStore,
    profileLocalStore: ProfileLocalStore,
    updatedAt: Long,
) {
    val cacheKeys = buildList {
        add("me")
        if (authorId.isNotBlank()) add(authorId)
    }.distinct()

    cacheKeys.forEach { userId ->
        val currentPosts = postsLocalStore.observePosts(userId).first()
        val deletedPost = currentPosts.firstOrNull { post -> post.id == postId }
        val updatedPosts = currentPosts.filterNot { post -> post.id == postId }
        if (updatedPosts.size == currentPosts.size) return@forEach

        postsLocalStore.replacePosts(
            userId = userId,
            posts = updatedPosts,
            updatedAt = updatedAt,
        )

        val likesToSubtract = deletedPost?.likesCount ?: 0
        if (likesToSubtract <= 0) return@forEach

        val cachedProfile = profileLocalStore.observeProfile(userId).first() ?: return@forEach
        profileLocalStore.upsertProfile(
            userId = userId,
            profile = cachedProfile.copy(
                totalLikesReceived = (cachedProfile.totalLikesReceived - likesToSubtract).coerceAtLeast(0),
            ),
            updatedAt = updatedAt,
        )
    }
}

suspend fun upsertSelfProfileCache(
    profileLocalStore: ProfileLocalStore,
    updatedAt: Long,
    transform: (PublicProfile?) -> PublicProfile,
) {
    val current = profileLocalStore.observeProfile(SELF_PROFILE_CACHE_KEY).first()
    profileLocalStore.upsertProfile(
        userId = SELF_PROFILE_CACHE_KEY,
        profile = transform(current),
        updatedAt = updatedAt,
    )
}

const val SELF_PROFILE_CACHE_KEY = "me"
