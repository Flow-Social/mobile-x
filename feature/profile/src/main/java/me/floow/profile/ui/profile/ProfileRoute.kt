package me.floow.profile.ui.profile

import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.profile.uilogic.profile.ProfileScreenViewModel
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.util.SetNavigationBarColor

@Composable
fun ProfileRoute(
	goToProfileEditScreen: (name: String, username: String, description: String, avatarUrl: String?, backgroundUrl: String?) -> Unit,
	goToAddPostScreen: () -> Unit,
	onPostClick: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit,
	goToChatScreen: (userId: String, name: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
	shareProfile: (url: String) -> Unit,
	sharePost: (url: String) -> Unit,
	onEditPost: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
	refreshPostsSignal: Boolean = false,
	consumeRefreshPostsSignal: () -> Unit = {},
	modifier: Modifier = Modifier,
	viewModel: ProfileScreenViewModel
) {
	val state: ProfileScreenState by viewModel.state.collectAsStateWithLifecycle()
	val successState = state as? ProfileScreenState.Success
	LaunchedEffect(Unit) {
		viewModel.loadData()
	}

	LaunchedEffect(refreshPostsSignal) {
		if (refreshPostsSignal) {
			viewModel.loadData(forceRemote = true)
			consumeRefreshPostsSignal()
		}
	}

		ProfileScreen(
		onProfileEditClick = {
			if (successState != null) {
				goToProfileEditScreen(
					successState.displayName ?: "",
					successState.shortUsername ?: "",
					successState.description ?: "",
					successState.avatarUri?.toString(),
					successState.backgroundUri?.toString(),
				)
			}
		},
		onAddPostButtonClick = goToAddPostScreen,
		onMessageButtonClick = {
			if (successState != null) {
				goToChatScreen(
					successState.id,
					successState.displayName ?: "",
					successState.avatarUri?.toString()
				)
			}
		},
		onShareButtonClick = {
			val shareSlug = when (val current = state) {
				is ProfileScreenState.Success -> current.shortUsername?.takeIf { it.isNotBlank() } ?: current.id
				else -> ""
			}
			shareProfile(DeepLinkUrls.profileUrl(shareSlug))
		},
		onBackClick = onBackClick,
			onPostClick = onPostClick,
			onSharePost = { post ->
				sharePost(DeepLinkUrls.postUrl(post.id, post.author.username?.value))
			},
			onEditPost = onEditPost,
	        onDeletePost = viewModel::deletePost,
			onLoadMorePosts = viewModel::loadMorePosts,
			modifier = modifier,
			state = state
		)

	SetNavigationBarColor(
		NavigationBarDefaults.containerColor
	)
}
