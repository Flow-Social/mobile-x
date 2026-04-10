package me.floow.shared.feed.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.plus_icon
import me.floow.domain.models.Post
import me.floow.shared.feed.uilogic.SharedFeedOwner
import me.floow.shared.feed.uilogic.SharedFeedUiEffect
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.topbar.TitleTopBarWithActionButton
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer

@Composable
fun SharedFeedRoute(
    stateHolder: SharedFeedOwner,
    onPostCreateClick: () -> Unit,
    onProfileClick: (String) -> Unit = {},
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onCommentsClick: (Post) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (ProfilePostItem) -> Unit = {},
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    isMockBuild: Boolean = false,
    isDebugBuild: Boolean = false,
    manageLifecycle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by stateHolder.state.collectAsState()
    var undoAnimationSignal by remember { mutableLongStateOf(0L) }
    var undoAnimationFrom by remember { mutableStateOf(SharedUndoAnimationFrom.LEFT) }

    if (manageLifecycle) {
        LaunchedEffect(stateHolder) {
            stateHolder.onFeedScreenVisible()
            stateHolder.uiEffects.collect { effect ->
                when (effect) {
                    is SharedFeedUiEffect.PlayUndoAnimation -> {
                        undoAnimationFrom = if (effect.isLiked) SharedUndoAnimationFrom.RIGHT else SharedUndoAnimationFrom.LEFT
                        undoAnimationSignal += 1L
                    }
                }
            }
        }

        DisposableEffect(stateHolder) {
            onDispose { stateHolder.onFeedScreenHidden() }
        }
    } else {
        LaunchedEffect(stateHolder) {
            stateHolder.uiEffects.collect { effect ->
                when (effect) {
                    is SharedFeedUiEffect.PlayUndoAnimation -> {
                        undoAnimationFrom = if (effect.isLiked) SharedUndoAnimationFrom.RIGHT else SharedUndoAnimationFrom.LEFT
                        undoAnimationSignal += 1L
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TitleTopBarWithActionButton(
                titleText = "Лента",
                titleTextStyle = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
                onActionButtonClick = onPostCreateClick,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.plus_icon),
                        contentDescription = null,
                    )
                },
            )
        },
    ) { innerPadding ->
        SharedFeedScreen(
            state = state,
            undoAnimationSignal = undoAnimationSignal,
            undoAnimationFrom = undoAnimationFrom,
            onSkipClick = stateHolder::onSwipeLeft,
            onLikeClick = stateHolder::onSwipeRight,
            onToggleDebug = stateHolder::toggleDebugMode,
            onClearProfile = stateHolder::clearProfile,
            onResetRecommendations = stateHolder::resetRecommendations,
            onDeletePost = stateHolder::deletePost,
            onProfileClick = onProfileClick,
            onProfileTagClick = onProfileTagClick,
            onPostLinkClick = onPostLinkClick,
            onCommentsClick = onCommentsClick,
            onSharePost = onSharePost,
            onEditPost = onEditPost,
            onPresentFullscreenViewer = onPresentFullscreenViewer,
            isMockBuild = isMockBuild,
            isDebugBuild = isDebugBuild,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
