package me.floow.shared.profile.uilogic

import me.floow.domain.models.UserPresence
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
        val avatarThumb: String? = null,
        val backgroundUri: String?,
        val backgroundThumb: String? = null,
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

fun ProfileScreenState.withResolvedPresence(presence: UserPresence?): ProfileScreenState {
    return when (this) {
        is ProfileScreenState.Success -> withResolvedPresence(presence)
        else -> this
    }
}

fun ProfileScreenState.Success.withResolvedPresence(
    presence: UserPresence?,
): ProfileScreenState.Success {
    if (isSelf) return this
    return if (presence != null) {
        copy(
            isOnline = presence.isOnline,
            lastSeenAtMillis = presence.lastSeenAtMillis ?: lastSeenAtMillis,
        )
    } else {
        copy(isOnline = false)
    }
}
