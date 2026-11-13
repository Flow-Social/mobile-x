package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import me.floow.domain.models.PostImageVariant

@Serializable
data class FeedStackSnapshot(
    val items: List<FeedStackSnapshotItem> = emptyList(),
)

@Serializable
data class FeedStackSnapshotItem(
    val postId: String,
    val authorId: String,
    val authorName: String? = null,
    val authorUsername: String? = null,
    val authorAvatarUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val imageVariants: List<PostImageVariant> = emptyList(),
    val commentersPreview: List<String> = emptyList(),
    val description: String? = null,
    val category: String = "",
    val createdAt: Long = 0L,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val recommendationReason: String? = null,
)

interface FeedStackSnapshotLocalStore {
    fun observeSnapshot(userId: String): Flow<FeedStackSnapshot?>

    suspend fun getSnapshot(userId: String): FeedStackSnapshot?

    suspend fun getLastUpdatedAt(userId: String): Long?

    suspend fun replaceSnapshot(userId: String, snapshot: FeedStackSnapshot, updatedAt: Long)

    suspend fun clearUser(userId: String)
}
