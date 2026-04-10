package me.floow.shared.profile.uilogic

interface ProfileRepository {
    suspend fun getProfile(userId: String?): ProfilePayload

    suspend fun getMorePosts(
        userId: String?,
        offset: Int,
        limit: Int,
    ): List<ProfilePost>

    suspend fun deletePost(postId: String): Result<Unit>
}

data class ProfilePayload(
    val id: String,
    val shortUsername: String?,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val displayName: String?,
    val description: String?,
    val totalLikesReceived: Int,
    val isSelf: Boolean,
    val isOnline: Boolean,
    val lastSeenAtMillis: Long?,
    val posts: List<ProfilePost>,
    val arePostsError: Boolean = false,
    val canLoadMorePosts: Boolean,
)

data class ProfilePost(
    val id: String,
    val description: String?,
    val imageVariants: List<ProfileImageVariant>,
    val imageUrls: List<String>,
    val likesCount: Int,
    val commentsCount: Int,
    val category: String? = null,
    val createdAtMillis: Long = 0L,
)

data class ProfileImageVariant(
    val lqUrl: String? = null,
    val previewUrl: String? = null,
    val fullUrl: String? = null,
)
