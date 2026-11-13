package me.floow.domain.api

import me.floow.domain.api.models.GetFeedResponse
import me.floow.domain.api.models.RecordSwipeResponse

interface FeedApi {
	suspend fun getFeed(limit: Int): GetFeedResponse
	suspend fun recordSwipe(postId: String, isLiked: Boolean): RecordSwipeResponse
	suspend fun undoSwipe(postId: String): RecordSwipeResponse
}
