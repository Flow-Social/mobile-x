package me.floow.shared.feed.ui

import androidx.compose.foundation.layout.PaddingValues
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
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.plus_icon
import me.floow.domain.models.Post
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import flow.shared.generated.resources.feed_bottom_nav_label
import me.floow.shared.feed.uilogic.SharedFeedOwner
import me.floow.shared.feed.uilogic.SharedFeedUiEffect
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.topbar.TitleTopBarWithActionButton
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

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
    manageLifecycle: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by stateHolder.state.collectAsState()
    var undoAnimationSignal by remember { mutableLongStateOf(0L) }
    var undoAnimationFrom by remember { mutableStateOf(SharedUndoAnimationFrom.LEFT) }

    if (manageLifecycle) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, stateHolder) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> stateHolder.onFeedScreenVisible()
                    Lifecycle.Event.ON_PAUSE -> stateHolder.onFeedScreenHidden()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                stateHolder.onFeedScreenVisible()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                stateHolder.onFeedScreenHidden()
            }
        }

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
                titleText = stringResource(Res.string.feed_bottom_nav_label),
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
            onDeletePost = stateHolder::deletePost,
            onProfileClick = onProfileClick,
            onProfileTagClick = onProfileTagClick,
            onPostLinkClick = onPostLinkClick,
            onCommentsClick = onCommentsClick,
            onSharePost = onSharePost,
            onEditPost = onEditPost,
            contentPadding = innerPadding,
            onRefreshClick = stateHolder::refreshFeed,
            modifier = Modifier,
        )
    }
}
