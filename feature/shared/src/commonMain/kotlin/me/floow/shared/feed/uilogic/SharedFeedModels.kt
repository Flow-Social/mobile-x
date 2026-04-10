package me.floow.shared.feed.uilogic

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.models.AnalysisResult
import me.floow.domain.models.Post
import me.floow.domain.models.UserProfile

data class SharedFeedItem(
    val entryId: Long,
    val post: Post,
    val recommendationReason: String? = null,
)

sealed interface SharedFeedScreenState {
    data object Loading : SharedFeedScreenState

    data object Error : SharedFeedScreenState

    data object NoMorePosts : SharedFeedScreenState

    data class Success(
        val feedItems: List<SharedFeedItem>,
        val userProfile: UserProfile,
        val recommendationReason: String?,
        val lastSwipeInfo: String?,
        val lastAnalysisResult: AnalysisResult?,
        val canUndo: Boolean = false,
        val isLoadingNext: Boolean = false,
        val isRecordingSwipe: Boolean = false,
        val isDebugMode: Boolean = false,
        val noMoreForNow: Boolean = false,
    ) : SharedFeedScreenState
}

sealed interface SharedFeedUiEffect {
    data class PlayUndoAnimation(val isLiked: Boolean) : SharedFeedUiEffect
}

interface SharedFeedOwner {
    val state: StateFlow<SharedFeedScreenState>
    val uiEffects: SharedFlow<SharedFeedUiEffect>

    fun onFeedScreenVisible()
    fun onFeedScreenHidden()
    fun loadData()
    fun onSwipeLeft()
    fun onSwipeRight()
    fun onSwipeUp()
    fun onSwipeDown()
    fun undoLastSwipe()
    fun toggleDebugMode()
    fun clearProfile()
    fun resetRecommendations()
    fun deletePost(postId: String)
    fun updatePost(postId: String, description: String, imageUrls: List<String>)
    fun applyPostEdit(postId: String, description: String?, imageUrls: List<String>)
}
