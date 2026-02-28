package me.floow.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot

@Composable
fun ProfileScreen(
	onProfileEditClick: () -> Unit,
    onAddPostButtonClick: () -> Unit,
	onMessageButtonClick: () -> Unit,
	onShareButtonClick: () -> Unit,
    onBackClick: () -> Unit = {},
    onPostClick: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit,
    onSharePost: (me.floow.domain.models.Post) -> Unit = {},
    onEditPost: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onDeletePost: (String) -> Unit = {},
	onLoadMorePosts: () -> Unit = {},
	state: ProfileScreenState,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier = modifier
	) {
		when (state) {
			is ProfileScreenState.Loading -> {
				FlowLoadingIndicator( // todo
					modifier = Modifier
						.align(Alignment.Center)
				)
			}

			is ProfileScreenState.Error -> {
				Text( // todo
					text = "error :(",
					modifier = Modifier
				)
			}

			is ProfileScreenState.Success -> {
				ProfileScreenSuccessState(
					state = state,
					onProfileEditClick = onProfileEditClick,
					onAddPostButtonClick = onAddPostButtonClick,
					onMessageButtonClick = onMessageButtonClick,
					onShareButtonClick = onShareButtonClick,
	                    onBackClick = onBackClick,
	                    onPostClick = onPostClick,
	                onSharePost = onSharePost,
	                onEditPost = onEditPost,
	                onDeletePost = onDeletePost,
					onLoadMorePosts = onLoadMorePosts,
						modifier = Modifier
					)
				}
		}
	}
}
