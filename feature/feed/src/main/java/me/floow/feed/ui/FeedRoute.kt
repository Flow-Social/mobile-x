package me.floow.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.floow.feed.ui.components.UndoAnimationFrom
import me.floow.feed.uilogic.FeedUiEffect
import me.floow.feed.uilogic.FeedScreenState
import me.floow.feed.uilogic.FeedViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun FeedRoute(
	onPostCreateClick: () -> Unit,
	onLogout: () -> Unit, // Callback для возврата на экран входа
	onProfileClick: (String) -> Unit = {},
	onProfileTagClick: (String) -> Unit = {},
	onPostLinkClick: (String, String) -> Unit = { _, _ -> },
	onPostOpen: (me.floow.domain.models.Post, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
	onCommentsClick: (me.floow.domain.models.Post) -> Unit = {},
	onSharePost: (me.floow.domain.models.Post) -> Unit = {},
	onEditPost: (me.floow.domain.models.Post) -> Unit = {},
	onFeedVisible: () -> Unit = {},
	isMockBuild: Boolean = false, // Передается из app модуля
	isDebugBuild: Boolean = false,
	modifier: Modifier = Modifier,
	viewModel: FeedViewModel = koinViewModel()
) {
	val state: FeedScreenState by viewModel.state.collectAsStateWithLifecycle()
	var undoAnimationSignal by remember { mutableLongStateOf(0L) }
	var undoAnimationFrom by remember { mutableStateOf(UndoAnimationFrom.LEFT) }
	// Наблюдаем за авторизацией без привязки к recomposition.
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

	DisposableEffect(lifecycleOwner, viewModel) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_RESUME -> {
					onFeedVisible()
					viewModel.onFeedScreenVisible()
				}
				Lifecycle.Event.ON_PAUSE -> viewModel.onFeedScreenHidden()
				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
			viewModel.onFeedScreenHidden()
		}
	}

	LaunchedEffect(viewModel) {
		viewModel.uiEffects.collect { effect ->
			when (effect) {
				is FeedUiEffect.PlayUndoAnimation -> {
					undoAnimationFrom = if (effect.isLiked) UndoAnimationFrom.RIGHT else UndoAnimationFrom.LEFT
					undoAnimationSignal += 1L
				}
			}
		}
	}

	FeedScreen(
		onPostCreateClick = onPostCreateClick,
		onSkipClick = { viewModel.onSwipeLeft() },
		onLikeClick = { viewModel.onSwipeRight() },
		onSwipeUp = { viewModel.onSwipeUp() },
		onSwipeDown = { viewModel.onSwipeDown() },
		onToggleDebug = { viewModel.toggleDebugMode() },
		onClearProfile = { viewModel.clearProfile() },
		onResetRecommendations = { viewModel.resetRecommendations() },
		onProfileClick = onProfileClick,
		onProfileTagClick = onProfileTagClick,
		onPostLinkClick = onPostLinkClick,
		onPostOpen = onPostOpen,
		onCommentsClick = onCommentsClick,
		onSharePost = onSharePost,
		onEditPost = onEditPost,
		onDeletePost = { viewModel.deletePost(it) },
		undoAnimationSignal = undoAnimationSignal,
		undoAnimationFrom = undoAnimationFrom,
		state = state,
		isMockBuild = isMockBuild,
		isDebugBuild = isDebugBuild,
		modifier = modifier
	)

}
