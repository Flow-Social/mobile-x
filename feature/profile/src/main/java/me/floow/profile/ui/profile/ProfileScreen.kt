package me.floow.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.floow.profile.uilogic.bump.ProfileBumpUiState
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot

@Composable
fun ProfileScreen(
	onProfileEditClick: () -> Unit,
    onAddPostButtonClick: () -> Unit,
	onMessageButtonClick: () -> Unit,
	onShareProfileClick: () -> Unit,
	onOpenBumpSheet: () -> Unit,
	onHideBumpSheet: () -> Unit,
	onStartBumpClick: () -> Unit,
	onCancelBumpClick: () -> Unit,
	onBumpImpactDetected: (Float) -> Unit,
	onBumpPeerDetected: (String, Int) -> Unit,
	bumpUiState: ProfileBumpUiState,
	bumpMatchSignal: Int,
	bumpEnabled: Boolean,
    onBackClick: () -> Unit = {},
    onPostClick: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit,
    onSharePost: (me.floow.domain.models.Post) -> Unit = {},
	onEditPost: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onDeletePost: (String) -> Unit = {},
	onLoadMorePosts: () -> Unit = {},
	suppressStatusBarStyle: Boolean = false,
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
						onShareProfileClick = onShareProfileClick,
						onOpenBumpSheet = onOpenBumpSheet,
						onHideBumpSheet = onHideBumpSheet,
						onStartBumpClick = onStartBumpClick,
						onCancelBumpClick = onCancelBumpClick,
					onBumpImpactDetected = onBumpImpactDetected,
					onBumpPeerDetected = onBumpPeerDetected,
					bumpUiState = bumpUiState,
					bumpMatchSignal = bumpMatchSignal,
					bumpEnabled = bumpEnabled,
	                    onBackClick = onBackClick,
	                    onPostClick = onPostClick,
	                onSharePost = onSharePost,
					onEditPost = onEditPost,
	                onDeletePost = onDeletePost,
					onLoadMorePosts = onLoadMorePosts,
					suppressStatusBarStyle = suppressStatusBarStyle,
						modifier = Modifier
					)
				}
		}
	}
}
