package me.floow.shared.profile.ui.segments.content

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot

private val ProfilePostsGridContentPadding = PaddingValues(bottom = 24.dp, start = 12.dp, end = 12.dp, top = 0.dp)
private val ProfilePostsGridItemSpacing = 10.dp
private val ProfilePostsSkeletonShape = RoundedCornerShape(24.dp)
private const val ProfileSkeletonItemsCount = 4
private const val ProfileLoadMoreThreshold = 6

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileContentSegment(
    posts: List<ProfilePostItem>,
    arePostsLoading: Boolean,
    arePostsError: Boolean,
    canLoadMorePosts: Boolean,
    isLoadingMorePosts: Boolean,
    onLoadMorePosts: () -> Unit = {},
    onPostLongClick: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onPostClick: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    gridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier = Modifier
) {
    val skeletonAlpha = rememberProfilePostsSkeletonAlpha()

    LaunchedEffect(gridState, posts.size, canLoadMorePosts, isLoadingMorePosts) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val shouldLoadMore = canLoadMorePosts &&
                    !isLoadingMorePosts &&
                    posts.isNotEmpty() &&
                    lastVisibleIndex >= posts.size - ProfileLoadMoreThreshold
                if (shouldLoadMore) onLoadMorePosts()
            }
    }

    when {
        arePostsLoading && posts.isEmpty() -> ProfilePostsSkeletonGrid(alpha = skeletonAlpha, modifier = modifier)
        arePostsError -> Text("Не удалось загрузить посты", style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(16.dp))
        posts.isEmpty() -> Text("Постов пока нет", style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(16.dp))
        else -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = ProfilePostsGridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(ProfilePostsGridItemSpacing),
                verticalArrangement = Arrangement.spacedBy(ProfilePostsGridItemSpacing),
                modifier = modifier,
            ) {
                items(items = posts, key = { it.id }) { post ->
                    ProfilePostCard(
                        post = post,
                        onClick = onPostClick,
                        onLongClick = onPostLongClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isLoadingMorePosts) {
                    item(key = "profile_posts_loading_more", span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            FlowLoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePostsSkeletonGrid(alpha: Float, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = ProfilePostsGridContentPadding,
        horizontalArrangement = Arrangement.spacedBy(ProfilePostsGridItemSpacing),
        verticalArrangement = Arrangement.spacedBy(ProfilePostsGridItemSpacing),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(count = ProfileSkeletonItemsCount, key = { index -> "profile_posts_skeleton_$index" }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(136f / 153f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha),
                        shape = ProfilePostsSkeletonShape
                    )
            )
        }
    }
}

@Composable
private fun rememberProfilePostsSkeletonAlpha(): Float {
    val skeletonTransition = rememberInfiniteTransition(label = "profile_posts_skeleton_transition")
    val skeletonAlpha by skeletonTransition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "profile_posts_skeleton_alpha"
    )
    return skeletonAlpha
}
