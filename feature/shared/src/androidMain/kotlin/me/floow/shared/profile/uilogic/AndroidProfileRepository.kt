package me.floow.shared.profile.uilogic

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.Post
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls

class AndroidProfileRepository(
    private val profileRepository: me.floow.domain.data.repos.ProfileRepository,
    private val postsRepository: PostsRepository,
    private val presenceRepository: PresenceRepository,
) : ProfileRepository {
    override suspend fun getProfile(userId: String?): ProfilePayload {
        val isSelf = userId.isNullOrBlank() || userId == SELF_USER_ID || userId == SELF_USER_ALIAS
        val resolvedUserId = if (isSelf) SELF_USER_ID else requireNotNull(userId)
        val profilePayload = if (isSelf) {
            when (val response = profileRepository.getSelfData()) {
                is GetDataResponse.Success -> ProfilePayload(
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
                is GetDataResponse.Error -> error("Unable to load self profile: ${response.error}")
            }
        } else {
            when (val response = profileRepository.getUserProfile(resolvedUserId)) {
                is GetDataResponse.Success -> {
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
                is GetDataResponse.Error -> error("Unable to load profile $resolvedUserId: ${response.error}")
            }
        }

        val posts = loadPosts(
            userId = resolvedUserId,
            offset = 0,
            limit = POSTS_PAGE_SIZE,
        )
        return profilePayload.copy(
            posts = posts,
            canLoadMorePosts = posts.size >= POSTS_PAGE_SIZE,
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

    private suspend fun loadPosts(
        userId: String,
        offset: Int,
        limit: Int,
    ): List<ProfilePost> {
        return when (
            val response = postsRepository.getUserPosts(
                userId = userId,
                offset = offset,
                limit = limit,
            )
        ) {
            is GetDataResponse.Success -> response.data.map { post -> post.toSharedProfilePost() }
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
        const val SELF_USER_ID = "me"
        const val SELF_USER_ALIAS = "self"
    }
}
