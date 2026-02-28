package me.floow.feed.uilogic

import me.floow.domain.models.AnalysisResult
import me.floow.domain.models.UserProfile

sealed interface FeedScreenState {
	data object Loading : FeedScreenState
	
	data object Error : FeedScreenState

	data object NoMorePosts : FeedScreenState
	
	data class Success(
		val feedItems: List<FeedItem>,
		val userProfile: UserProfile,
		val recommendationReason: String?,
		val lastSwipeInfo: String?,
		val lastAnalysisResult: AnalysisResult?,
		val canUndo: Boolean = false,
		val isLoadingNext: Boolean = false,
		val isRecordingSwipe: Boolean = false,
		val isDebugMode: Boolean = false,
		val noMoreForNow: Boolean = false
	) : FeedScreenState
}
