package me.floow.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import me.floow.shared.feed.ui.SharedFeedRoute
import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import org.koin.compose.koinInject

@Composable
fun FeedRoute(
    onPostCreateClick: () -> Unit,
    onLogout: () -> Unit,
    onProfileClick: (String) -> Unit = {},
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onCommentsClick: (me.floow.domain.models.Post) -> Unit = {},
    onSharePost: (me.floow.domain.models.Post) -> Unit = {},
    onEditPost: (ProfilePostItem) -> Unit = {},
    onFeedVisible: () -> Unit = {},
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    isMockBuild: Boolean = false,
    isDebugBuild: Boolean = false,
    modifier: Modifier = Modifier,
    stateHolder: SharedFeedStateHolder? = null,
) {
    val routeStateHolder = stateHolder ?: koinInject<SharedFeedStateHolder>()
    val authManager: me.floow.domain.auth.AuthenticationManager = org.koin.compose.koinInject()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, authManager) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            authManager.authenticationStateFlow.collect { authState ->
                if (authState is me.floow.domain.auth.models.AuthState.NoIdToken) {
                    onLogout()
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, routeStateHolder) {
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
        onPresentFullscreenViewer = onPresentFullscreenViewer,
        isMockBuild = isMockBuild,
        isDebugBuild = isDebugBuild,
        manageLifecycle = false,
        modifier = modifier,
    )
}
