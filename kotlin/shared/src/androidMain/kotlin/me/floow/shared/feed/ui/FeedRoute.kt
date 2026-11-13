package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.models.Post
import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import me.floow.shared.profile.ui.model.ProfilePostItem
import org.koin.compose.koinInject

@Composable
fun FeedRoute(
    onPostCreateClick: () -> Unit,
    onLogout: () -> Unit,
    onProfileClick: (String) -> Unit = {},
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onCommentsClick: (Post) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (ProfilePostItem) -> Unit = {},
    onFeedVisible: () -> Unit = {},
    modifier: Modifier = Modifier,
    stateHolder: SharedFeedStateHolder? = null,
) {
    val routeStateHolder = stateHolder ?: koinInject<SharedFeedStateHolder>()
    val authManager: AuthenticationManager = koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, authManager) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            authManager.authenticationStateFlow.collect { authState ->
                if (authState is AuthState.NoIdToken) {
                    onLogout()
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, routeStateHolder) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    onFeedVisible()
                    routeStateHolder.onFeedScreenVisible()
                }
                Lifecycle.Event.ON_PAUSE -> routeStateHolder.onFeedScreenHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onFeedVisible()
            routeStateHolder.onFeedScreenVisible()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            routeStateHolder.onFeedScreenHidden()
        }
    }

    SharedFeedRoute(
        stateHolder = routeStateHolder,
        onPostCreateClick = onPostCreateClick,
        onProfileClick = onProfileClick,
        onProfileTagClick = onProfileTagClick,
        onPostLinkClick = onPostLinkClick,
        onCommentsClick = onCommentsClick,
        onSharePost = onSharePost,
        onEditPost = onEditPost,
        manageLifecycle = false,
        modifier = modifier,
    )
}
