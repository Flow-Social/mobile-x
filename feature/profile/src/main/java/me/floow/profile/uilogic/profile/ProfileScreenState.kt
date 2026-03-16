package me.floow.profile.uilogic.profile

import android.net.Uri
import me.floow.domain.models.Post

sealed interface ProfileScreenState {
	data object Loading : ProfileScreenState

	data object Error : ProfileScreenState

	data class Success(
		val id: String,
		val shortUsername: String?,
		val avatarUri: Uri?,
		val backgroundUri: Uri?,
		val displayName: String?,
		val description: String?,
		val totalLikesReceived: Int,
		val isSelf: Boolean,
		val isOnline: Boolean = false,
		val lastSeenAtMillis: Long? = null,
		val posts: List<Post>,
		val arePostsLoading: Boolean,
		val arePostsError: Boolean,
		val canLoadMorePosts: Boolean,
		val isLoadingMorePosts: Boolean,
	) : ProfileScreenState
}
