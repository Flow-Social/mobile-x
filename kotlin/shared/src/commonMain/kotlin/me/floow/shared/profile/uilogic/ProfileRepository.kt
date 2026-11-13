package me.floow.shared.profile.uilogic

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface ProfileRepository {
    suspend fun getProfile(userId: String?): ProfilePayload

    suspend fun loadCachedInitial(userId: String?): ProfilePayload? = null

    suspend fun getMorePosts(
        userId: String?,
        offset: Int,
        limit: Int,
    ): List<ProfilePost>

    suspend fun deletePost(postId: String): Result<Unit>

    suspend fun retryPendingPost(postId: String): Result<Unit> = Result.failure(UnsupportedOperationException())

    fun observeSelfPosts(): Flow<List<ProfilePost>> = emptyFlow()
}

data class ProfilePayload(
    val id: String,
    val shortUsername: String?,
    val avatarUrl: String?,
    val avatarThumb: String? = null,
    val backgroundUrl: String?,
    val backgroundThumb: String? = null,
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
    val commentersPreview: List<String> = emptyList(),
    val category: String? = null,
    val createdAtMillis: Long = 0L,
    val optimisticState: ProfilePostOptimisticState? = null,
    val syncState: ProfilePostSyncState? = null,
    val pendingMutationType: ProfilePostPendingMutationType? = null,
    val isOptimistic: Boolean = false,
    val localPostId: String? = null,
    val remotePostId: String? = null,
    val lastSyncError: String? = null,
)

enum class ProfilePostOptimisticState {
    PENDING,
    FAILED
}

enum class ProfilePostSyncState {
    PENDING,
    UPLOADING,
    SYNCING,
    FAILED,
}

enum class ProfilePostPendingMutationType {
    CREATE,
    UPDATE,
    DELETE,
}

data class ProfileImageVariant(
    val thumbUrl: String? = null,
    val fullUrl: String? = null,
) {
    val lqUrl: String? get() = thumbUrl
    val previewUrl: String? get() = fullUrl
}
