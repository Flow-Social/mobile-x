package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.FeedPost

interface FeedRepository {
    suspend fun getNextPost(): GetDataResponse<FeedPost>
    suspend fun recordSwipe(postId: String, isLiked: Boolean): UpdateDataResponse
    suspend fun undoSwipe(postId: String): UpdateDataResponse
	fun clearSession()
}
