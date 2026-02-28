package me.floow.feed.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate
import me.floow.feed.uilogic.FeedItem
import me.floow.uikit.R
import me.floow.uikit.components.swipe.SwipeConfig
import me.floow.uikit.components.swipe.SwipeDirection
import me.floow.uikit.components.swipe.SwipeableCardStack

import me.floow.uikit.components.swipe.SwipePresets

private val PostCardShape = RoundedCornerShape(
    topStart = 14.dp,
    topEnd = 14.dp,
    bottomEnd = 14.dp,
    bottomStart = 0.dp
)

private val DefaultFeedSwipeConfig = SwipePresets.BottomStack.copy(
    maxRotation = 5f,
    swipeThreshold = 0.25f,
    hapticEnabled = true
)

enum class UndoAnimationFrom {
    LEFT,
    RIGHT
}

@Composable
internal fun SwipeablePostCard(
    items: List<FeedItem>,
    undoAnimationSignal: Long = 0L,
    undoAnimationFrom: UndoAnimationFrom = UndoAnimationFrom.LEFT,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onProfileClick: (String) -> Unit = {},
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onPostOpen: (Post, OverlayLaunchData) -> Unit = { _, _ -> },
    onOverlaySourceSnapshot: (String, OverlayLaunchData) -> Unit = { _, _ -> },
    onCommentsClick: (Post) -> Unit = {},
    overlayPostId: String? = null,
    overlayVisible: Boolean = false,
    overlayDetachedCount: Int = 0,
    onPostMenuClick: (Post) -> Unit = {},
    config: SwipeConfig = DefaultFeedSwipeConfig,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val undoTopOffsetX = remember { Animatable(0f) }
    val undoStartOffsetPx = with(density) { 18.dp.toPx() }

    LaunchedEffect(undoAnimationSignal) {
        if (undoAnimationSignal == 0L) return@LaunchedEffect
        val startOffset = when (undoAnimationFrom) {
            UndoAnimationFrom.LEFT -> -undoStartOffsetPx
            UndoAnimationFrom.RIGHT -> undoStartOffsetPx
        }
        undoTopOffsetX.snapTo(startOffset)
        undoTopOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(
                durationMillis = 280,
                easing = FastOutSlowInEasing
            )
        )
    }

    SwipeableCardStack(
        items = items,
        config = config,
        canDismissDirection = { direction ->
            direction == SwipeDirection.Left || direction == SwipeDirection.Right
        },
        onSwipeWithoutDismiss = { feedItem, direction ->
            when (direction) {
                SwipeDirection.Down -> onPostMenuClick(feedItem.post)
                SwipeDirection.Up -> Unit
                else -> Unit
            }
        },
        keySelector = { it.uniqueId },
        clipShape = PostCardShape,
        onSwipe = { _, direction ->
            when (direction) {
                SwipeDirection.Left -> onSwipeLeft()
                SwipeDirection.Right -> onSwipeRight()
                SwipeDirection.Up -> Unit
                SwipeDirection.Down -> Unit
            }
        },
        overlayContent = { direction, progress ->
            SwipeOverlayContent(
                direction = direction,
                progress = progress
            )
        },
        topCardExternalOffsetX = undoTopOffsetX.value,
        modifier = modifier.padding(bottom = 60.dp)
    ) { feedItem ->
        PostCard(
            post = feedItem.post,
            recommendationReason = feedItem.recommendationReason,
            onProfileClick = onProfileClick,
            onProfileTagClick = onProfileTagClick,
            onPostLinkClick = onPostLinkClick,
            onViewImagesClick = onPostOpen,
            onOverlaySourceSnapshot = { launchData ->
                onOverlaySourceSnapshot(feedItem.post.id, launchData)
            },
            isOverlayActive = overlayPostId == feedItem.post.id,
            overlayDetachedCount = overlayDetachedCount,
            onMoreClick = onPostMenuClick,
            onCommentsClick = onCommentsClick,
            modifier = Modifier
                .fillMaxSize()
        )
    }
}

@Composable
private fun SwipeOverlayContent(
    direction: SwipeDirection,
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when (direction) {
            SwipeDirection.Left -> DislikeIndicator(
                alpha = progress,
                scale = 0.5f + (progress * 0.5f)
            )
            SwipeDirection.Right -> LikeIndicator(
                alpha = progress,
                scale = 0.5f + (progress * 0.5f)
            )
            SwipeDirection.Up -> SuperLikeIndicator(
                alpha = progress,
                scale = 0.5f + (progress * 0.5f)
            )
            SwipeDirection.Down -> SkipIndicator(
                alpha = progress,
                scale = 0.5f + (progress * 0.5f)
            )
        }
    }
}

@Composable
private fun BoxScope.LikeIndicator(
    alpha: Float,
    scale: Float
) {
    IndicatorCircle(
        alpha = alpha,
        scale = scale,
        color = MaterialTheme.colorScheme.primaryContainer,
        iconRes = R.drawable.done_icon,
        alignment = Alignment.CenterStart,
        offsetX = 32.dp
    )
}

@Composable
private fun BoxScope.DislikeIndicator(
    alpha: Float,
    scale: Float
) {
    IndicatorCircle(
        alpha = alpha,
        scale = scale,
        color = MaterialTheme.colorScheme.errorContainer,
        iconRes = R.drawable.nav_back_icon,
        alignment = Alignment.CenterEnd,
        offsetX = (-32.dp)
    )
}

@Composable
private fun BoxScope.SuperLikeIndicator(
    alpha: Float,
    scale: Float
) {
    IndicatorCircle(
        alpha = alpha,
        scale = scale,
        color = Color(0xFF2196F3).copy(alpha = 0.9f),
        iconRes = R.drawable.plus_icon,
        alignment = Alignment.TopCenter,
        offsetY = 60.dp
    )
}

@Composable
private fun BoxScope.SkipIndicator(
    alpha: Float,
    scale: Float
) {
    IndicatorCircle(
        alpha = alpha,
        scale = scale,
        color = MaterialTheme.colorScheme.secondaryContainer,
        iconRes = R.drawable.reply_icon,
        alignment = Alignment.BottomCenter,
        offsetY = (-60.dp)
    )
}

@Composable
private fun BoxScope.IndicatorCircle(
    alpha: Float,
    scale: Float,
    color: Color,
    iconRes: Int,
    alignment: Alignment,
    offsetX: androidx.compose.ui.unit.Dp = 0.dp,
    offsetY: androidx.compose.ui.unit.Dp = 0.dp
) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
                this.shadowElevation = 12.dp.toPx()
                this.shape = CircleShape
                this.clip = true
                this.renderEffect = null
            }
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(36.dp)
        )
    }
}

// Preview функции в стилистике проекта
@OptIn(RawValueObjectCreate::class)
@Preview
@Composable
private fun SwipeablePostCardPreview() {
    val mockItems = listOf(
        FeedItem(
            post = Post(
                id = "preview_1",
                author = PostAuthor(
                    id = "preview_user_1",
                    name = ProfileName.createRaw("Анна Котова"),
                    username = ProfileUsername.createRaw("vatarase"),
                    avatarUrl = "https://picsum.photos/100/100?random=1"
                ),
                content = PostContent(
                    imageUrls = listOf(
                        "https://picsum.photos/400/600?nature=1",
                        "https://picsum.photos/400/600?nature=2",
                        "https://picsum.photos/400/600?nature=3",
                        "https://picsum.photos/400/600?nature=4"
                    ),
                    description = "Новый учебный год начался с переезда в Санкт-Петербург. Я рада, что мы смогли пойти на такой серьёзный шаг!!!!"
                ),
                category = PostCategories.LIFESTYLE,
                createdAt = System.currentTimeMillis() - 3600000
            ),
            recommendationReason = null
        ),
        FeedItem(
            post = Post(
                id = "preview_2",
                author = PostAuthor(
                    id = "preview_user_2",
                    name = ProfileName.createRaw("Петр Путешественник"),
                    username = ProfileUsername.createRaw("petr_travel"),
                    avatarUrl = "https://picsum.photos/100/100?random=2"
                ),
                content = PostContent(
                    imageUrls = listOf(
                        "https://picsum.photos/400/600?travel=1",
                        "https://picsum.photos/400/600?travel=2"
                    ),
                    description = "Париж весной красив"
                ),
                category = PostCategories.TRAVEL,
                createdAt = System.currentTimeMillis() - 7200000
            ),
            recommendationReason = null
        ),
        FeedItem(
            post = Post(
                id = "preview_3",
                author = PostAuthor(
                    id = "preview_user_3",
                    name = ProfileName.createRaw("Мария Художница"),
                    username = ProfileUsername.createRaw("maria_art"),
                    avatarUrl = "https://picsum.photos/100/100?random=3"
                ),
                content = PostContent(
                    imageUrls = listOf("https://picsum.photos/400/600?art=1"),
                    description = "Новая картина готова"
                ),
                category = PostCategories.ART,
                createdAt = System.currentTimeMillis() - 10800000
            ),
            recommendationReason = null
        )
    )
    
    SwipeablePostCard(
        items = mockItems,
        onSwipeLeft = {},
        onSwipeRight = {},
        onSwipeUp = {},
        onSwipeDown = {},
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}

@OptIn(RawValueObjectCreate::class)
@Preview
@Composable
private fun SwipeablePostCardPreview_SingleItem() {
    val mockItem = FeedItem(
        post = Post(
            id = "single_preview",
            author = PostAuthor(
                id = "preview_user_4",
                name = ProfileName.createRaw("Тест Пользователь"),
                username = ProfileUsername.createRaw("test_user"),
                avatarUrl = "https://picsum.photos/100/100?random=4"
            ),
            content = PostContent(
                imageUrls = listOf("https://picsum.photos/400/600?test=1"),
                description = "Тестовый пост для превью"
            ),
            category = PostCategories.TECH,
            createdAt = System.currentTimeMillis()
        ),
        recommendationReason = "Рекомендовано для вас"
    )
    
    SwipeablePostCard(
        items = listOf(mockItem),
        onSwipeLeft = {},
        onSwipeRight = {},
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
