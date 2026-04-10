package me.floow.shared.main.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.ProfileMessageTarget
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.shareProfileSlug
import me.floow.shared.profile.uilogic.toActionContext
import me.floow.shared.profile.uilogic.toEditProfileOverlayData
import me.floow.shared.profile.uilogic.toMessageTarget
import me.floow.shared.profile.uilogic.toPostScreenModel
import me.floow.shared.profile.uilogic.ProfileScreenState
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.ui.ProfileScreenSuccessState
import me.floow.uikit.components.loading.FlowLoadingIndicator

@Composable
fun ProfileScreen(
    stateHolder: ProfileStateHolder,
    modifier: Modifier = Modifier,
    onAddPostButtonClick: () -> Unit = {},
    onProfileEditClick: (EditProfileOverlayData) -> Unit = {},
    onMessageButtonClick: (ProfileMessageTarget) -> Unit = {},
    onShareProfileClick: (String) -> Unit = {},
    onEditPost: (ProfilePostItem) -> Unit = {},
    onOpenPost: (PostScreenModel) -> Unit = {},
    onBackClick: () -> Unit = {},
) {
    val state by stateHolder.state.collectAsState()

    LaunchedEffect(stateHolder) {
        stateHolder.loadIfNeeded()
    }

    when (val currentState = state) {
        ProfileScreenState.Loading -> {
            Box(modifier = modifier) {
                FlowLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        is ProfileScreenState.Error -> {
            Box(modifier = modifier) {
                Text(
                    text = currentState.message,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        is ProfileScreenState.Success -> {
            val actionContext = currentState.toActionContext()
            ProfileScreenSuccessState(
                id = currentState.id,
                shortUsername = currentState.shortUsername,
                avatarUri = currentState.avatarUri,
                backgroundUri = currentState.backgroundUri,
                displayName = currentState.displayName,
                description = currentState.description,
                totalLikesReceived = currentState.totalLikesReceived,
                isSelf = currentState.isSelf,
                posts = currentState.posts,
                arePostsLoading = currentState.arePostsLoading,
                arePostsError = currentState.arePostsError,
                canLoadMorePosts = currentState.canLoadMorePosts,
                isLoadingMorePosts = currentState.isLoadingMorePosts,
                isOnline = currentState.isOnline,
                lastSeenAtMillis = currentState.lastSeenAtMillis,
                onProfileEditClick = {
                    onProfileEditClick(actionContext.toEditProfileOverlayData())
                },
                onAddPostButtonClick = onAddPostButtonClick,
                onMessageButtonClick = { onMessageButtonClick(actionContext.toMessageTarget()) },
                onShareProfileClick = { onShareProfileClick(actionContext.shareProfileSlug()) },
                onTopBarActionClick = { onShareProfileClick(actionContext.shareProfileSlug()) },
                onBackClick = onBackClick,
                onPostClick = { postId, sourceSnapshot ->
                    currentState.toPostScreenModel(postId, sourceSnapshot)?.let(onOpenPost)
                },
                onEditPost = { postId, _ ->
                    currentState.posts.firstOrNull { it.id == postId }?.let(onEditPost)
                },
                onLoadMorePosts = stateHolder::loadMorePosts,
                modifier = modifier
            )
        }
    }
}
