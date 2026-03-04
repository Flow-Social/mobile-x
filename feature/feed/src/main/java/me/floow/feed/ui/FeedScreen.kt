package me.floow.feed.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import me.floow.feed.ui.components.UndoAnimationFrom
import me.floow.feed.ui.states.HasDataState
import me.floow.feed.uilogic.FeedScreenState
import me.floow.uikit.R
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.topbar.TitleTopBarWithActionButton

@Composable
internal fun FeedScreen(
	onPostCreateClick: () -> Unit,
	onSkipClick: () -> Unit,
	onLikeClick: () -> Unit,
	onSwipeUp: () -> Unit,
	onSwipeDown: () -> Unit,
	onToggleDebug: () -> Unit,
	onClearProfile: () -> Unit,
	onResetRecommendations: () -> Unit,
	onProfileClick: (String) -> Unit = {},
	onProfileTagClick: (String) -> Unit = {},
	onPostLinkClick: (String, String) -> Unit = { _, _ -> },
	onPostOpen: (me.floow.domain.models.Post, androidx.compose.ui.geometry.Rect) -> Unit = { _, _ -> },
	onCommentsClick: (me.floow.domain.models.Post) -> Unit = {},
	onSharePost: (me.floow.domain.models.Post) -> Unit = {},
	onEditPost: (me.floow.domain.models.Post) -> Unit = {},
	onDeletePost: (String) -> Unit = {},
	undoAnimationSignal: Long = 0L,
	undoAnimationFrom: UndoAnimationFrom = UndoAnimationFrom.LEFT,
	state: FeedScreenState,
	isMockBuild: Boolean = false,
	isDebugBuild: Boolean = false,
	modifier: Modifier = Modifier
) {
	Scaffold(
		topBar = {
			TitleTopBarWithActionButton(
				titleText = stringResource(R.string.feed_topbar_title),
				titleTextStyle = MaterialTheme.typography.titleMedium.copy(
					fontSize = 18.sp,
					fontWeight = FontWeight.Medium
				),
				onActionButtonClick = onPostCreateClick,
				icon = {
					Icon(
						painter = painterResource(R.drawable.plus_icon),
						contentDescription = null
					)
				}
			)
		},
		contentWindowInsets = WindowInsets(0.dp),
		modifier = modifier
	) { innerPadding ->
		val commonModifier = Modifier
			.fillMaxSize()
			.padding(innerPadding)
			.zIndex(2f)

		when (state) {
			is FeedScreenState.Loading -> {
				Box(commonModifier, Alignment.Center) {
					FlowLoadingIndicator()
				}
			}
			
			is FeedScreenState.Error -> {
				Box(commonModifier, Alignment.Center) {
					Text(text = "ошибка загрузки ленты")
				}
			}

			is FeedScreenState.NoMorePosts -> {
				Box(commonModifier, Alignment.Center) {
					Text(text = "нет новых постов")
				}
			}
			
			is FeedScreenState.Success -> {
				HasDataState(
					state = state,
					feedItems = state.feedItems,
					undoAnimationSignal = undoAnimationSignal,
					undoAnimationFrom = undoAnimationFrom,
					onSkipClick = onSkipClick,
					onLikeClick = onLikeClick,
					onSwipeUp = onSwipeUp,
					onSwipeDown = onSwipeDown,
					onToggleDebug = onToggleDebug,
					onClearProfile = onClearProfile,
					onResetRecommendations = onResetRecommendations,
					onProfileClick = onProfileClick,
					onProfileTagClick = onProfileTagClick,
					onPostLinkClick = onPostLinkClick,
					onPostOpen = onPostOpen,
					onCommentsClick = onCommentsClick,
					onSharePost = onSharePost,
					onEditPost = onEditPost,
					onDeletePost = onDeletePost,
					isMockBuild = isMockBuild,
					isDebugBuild = isDebugBuild,
					modifier = commonModifier
				)
			}
		}
	}
}

@Preview
@Composable
private fun FeedScreenPreview_Loading() {
	FeedScreen(
		onPostCreateClick = {},
		onSkipClick = {},
		onLikeClick = {},
		onSwipeUp = {},
		onSwipeDown = {},
		onToggleDebug = {},
		onClearProfile = {},
		onResetRecommendations = {},
		onProfileTagClick = {},
		onPostLinkClick = { _, _ -> },
		state = FeedScreenState.Loading,
		modifier = Modifier.fillMaxSize()
	)
}

@Preview
@Composable
private fun FeedScreenPreview_Error() {
	FeedScreen(
		onPostCreateClick = {},
		onSkipClick = {},
		onLikeClick = {},
		onSwipeUp = {},
		onSwipeDown = {},
		onToggleDebug = {},
		onClearProfile = {},
		onResetRecommendations = {},
		onProfileTagClick = {},
		onPostLinkClick = { _, _ -> },
		state = FeedScreenState.Error,
		modifier = Modifier.fillMaxSize()
	)
}
