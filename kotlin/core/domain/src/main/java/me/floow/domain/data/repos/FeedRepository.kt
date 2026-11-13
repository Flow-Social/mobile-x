package me.floow.domain.data.repos

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.FeedPost

interface FeedRepository {
    suspend fun getNextPost(): GetDataResponse<FeedPost>
    suspend fun getNextPosts(limit: Int): GetDataResponse<List<FeedPost>> {
        val first = getNextPost()
        return when (first) {
            is GetDataResponse.Success -> {
                val list = mutableListOf(first.data)
                while (list.size < limit) {
                    val next = getNextPost()
                    if (next is GetDataResponse.Success) {
                        list.add(next.data)
                    } else {
                        break
                    }
                }
                GetDataResponse.Success(list)
            }
            is GetDataResponse.Error -> GetDataResponse.Error(first.error)
        }
    }
    suspend fun recordSwipe(postId: String, isLiked: Boolean): UpdateDataResponse
    suspend fun undoSwipe(postId: String): UpdateDataResponse
	fun clearSession()
}
