package me.floow.shared.profile.uilogic

import me.floow.shared.profile.ui.model.ProfilePostItem

sealed interface ProfileScreenState {
    data object Loading : ProfileScreenState

    data class Error(
        val message: String,
    ) : ProfileScreenState

    data class Success(
        val id: String,
        val shortUsername: String?,
        val avatarUri: String?,
        val backgroundUri: String?,
        val displayName: String?,
        val description: String?,
        val totalLikesReceived: Int,
        val isSelf: Boolean,
        val isOnline: Boolean = false,
        val lastSeenAtMillis: Long? = null,
        val posts: List<ProfilePostItem>,
        val arePostsLoading: Boolean,
        val arePostsError: Boolean,
        val canLoadMorePosts: Boolean,
        val isLoadingMorePosts: Boolean,
    ) : ProfileScreenState
}
