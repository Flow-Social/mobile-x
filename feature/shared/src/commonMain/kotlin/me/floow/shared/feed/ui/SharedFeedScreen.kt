package me.floow.shared.feed.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.done_icon
import flow.feature.shared.generated.resources.ic_delete
import flow.feature.shared.generated.resources.ic_refresh
import flow.feature.shared.generated.resources.nav_back_icon
import flow.feature.shared.generated.resources.plus_icon
import flow.feature.shared.generated.resources.reply_icon
import flow.feature.shared.generated.resources.search_icon
import kotlinx.coroutines.launch
import me.floow.domain.models.Post
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.UserProfile
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.shared.chats.ui.formatChatClockTime
import me.floow.shared.feed.uilogic.SharedFeedItem
import me.floow.shared.feed.uilogic.SharedFeedScreenState
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.buttons.BlurGlassButton
import me.floow.uikit.components.buttons.WideOutlinedIconButton
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.misc.PostActionsSheetContent
import me.floow.uikit.theme.NinehedronShape
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

internal enum class SharedUndoAnimationFrom {
    LEFT,
    RIGHT,
}

private enum class SharedSwipeDirection {
    Left,
    Right,
    Up,
    Down,
}

private data class SharedSwipeConfig(
    val swipeThreshold: Float = 0.25f,
    val swipeThresholdDown: Float = 0.2f,
    val swipeThresholdUp: Float = 0.2f,
    val maxRotation: Float = 5f,
    val dismissDistance: Float = 1500f,
    val dismissDuration: Int = 400,
    val springDamping: Float = Spring.DampingRatioLowBouncy,
    val springStiffness: Float = Spring.StiffnessMediumLow,
    val stackSpacing: Dp = 40.dp,
    val maxVisibleItems: Int = 3,
    val stackCardScales: List<Float> = listOf(0.9f, 0.8f),
    val stackCardAlphas: List<Float> = listOf(0.1f, 0.2f),
    val stackCardOffsets: List<Dp> = listOf(70.dp, 120.dp),
    val resistanceHorizontal: Float = 0.8f,
    val resistanceVerticalUp: Float = 0.6f,
    val resistanceVerticalDown: Float = 0.4f,
)

private val DefaultSharedSwipeConfig = SharedSwipeConfig()
private val SharedPostCardShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
private const val SingleImageCardRotation = -5f
private const val SingleImageAspectRatio = 180f / 240f
private val SharedFeedCardMaxWidth = 420.dp
private const val TagProfile = "profile"
private const val TagPost = "post"
private const val TagTrailing = "trailing"
private val LocalSharedSwipeItemIndex = staticCompositionLocalOf { 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedFeedScreen(
    state: SharedFeedScreenState,
    undoAnimationSignal: Long,
    undoAnimationFrom: SharedUndoAnimationFrom,
    onSkipClick: () -> Unit,
    onLikeClick: () -> Unit,
    onToggleDebug: () -> Unit,
    onClearProfile: () -> Unit,
    onResetRecommendations: () -> Unit,
    onDeletePost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onSharePost: (Post) -> Unit,
    onEditPost: (ProfilePostItem) -> Unit,
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)?,
    isMockBuild: Boolean,
    isDebugBuild: Boolean,
    modifier: Modifier = Modifier,
) {
    when (state) {
        SharedFeedScreenState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FlowLoadingIndicator() }
        SharedFeedScreenState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("ошибка загрузки ленты") }
        SharedFeedScreenState.NoMorePosts -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("нет новых постов") }
        is SharedFeedScreenState.Success -> SharedFeedSuccessState(
            state = state,
            undoAnimationSignal = undoAnimationSignal,
            undoAnimationFrom = undoAnimationFrom,
            onSkipClick = onSkipClick,
            onLikeClick = onLikeClick,
            onToggleDebug = onToggleDebug,
            onClearProfile = onClearProfile,
            onResetRecommendations = onResetRecommendations,
            onDeletePost = onDeletePost,
            onProfileClick = onProfileClick,
            onProfileTagClick = onProfileTagClick,
            onPostLinkClick = onPostLinkClick,
            onCommentsClick = onCommentsClick,
            onSharePost = onSharePost,
            onEditPost = onEditPost,
            onPresentFullscreenViewer = onPresentFullscreenViewer,
            isMockBuild = isMockBuild,
            isDebugBuild = isDebugBuild,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedFeedSuccessState(
    state: SharedFeedScreenState.Success,
    undoAnimationSignal: Long,
    undoAnimationFrom: SharedUndoAnimationFrom,
    onSkipClick: () -> Unit,
    onLikeClick: () -> Unit,
    onToggleDebug: () -> Unit,
    onClearProfile: () -> Unit,
    onResetRecommendations: () -> Unit,
    onDeletePost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onSharePost: (Post) -> Unit,
    onEditPost: (ProfilePostItem) -> Unit,
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)?,
    isMockBuild: Boolean,
    isDebugBuild: Boolean,
    modifier: Modifier = Modifier,
) {
    var showProfileDeleteDialog by remember { mutableStateOf(false) }
    var showPostMenuFor by remember { mutableStateOf<Post?>(null) }
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var viewerPost by remember { mutableStateOf<Post?>(null) }
    val viewerState = rememberFullscreenImageViewerState()
    val viewerModelProvider = {
        viewerPost?.let { post ->
            FullscreenImageViewerModel(
                images = post.content.viewerImageUrls(),
                title = post.author.name?.value ?: "@${post.author.username?.value ?: "unknown"}",
                subtitleProvider = { "Фото" },
            )
        }
    }
    val viewerModel = viewerModelProvider()
    val hostedViewer = remember(viewerState, onPresentFullscreenViewer) {
        HostedFullscreenImageViewer(
            modelProvider = viewerModelProvider,
            state = viewerState,
            onAction = { action ->
                val imageCount = viewerModelProvider()?.images?.size ?: 0
                viewerState.reduce(action, imageCount)
                if (action == FullscreenImageViewerAction.CloseAnimationFinished) {
                    viewerPost = null
                    onPresentFullscreenViewer?.invoke(null)
                }
            },
        )
    }
    val sheetState = rememberModalBottomSheetState()

    DisposableEffect(onPresentFullscreenViewer) {
        onDispose { onPresentFullscreenViewer?.invoke(null) }
    }

    if (showProfileDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDeleteDialog = false },
            title = { Text("Удалить профиль?") },
            text = { Text("Это полностью очистит все данные и потребует повторного входа.") },
            confirmButton = {
                TextButton(onClick = {
                    showProfileDeleteDialog = false
                    onClearProfile()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showProfileDeleteDialog = false }) { Text("Отмена") } },
        )
    }

    showPostMenuFor?.let { post ->
        ModalBottomSheet(
            onDismissRequest = { showPostMenuFor = null },
            sheetState = sheetState,
        ) {
            PostActionsSheetContent(
                canEdit = post.author.id == "me",
                editText = "Редактировать",
                deleteText = "Удалить",
                shareText = "Поделиться",
                onEdit = {
                    onEditPost(post.toProfilePostItem())
                    showPostMenuFor = null
                },
                onDelete = {
                    postToDelete = post
                    showPostMenuFor = null
                },
                onShare = {
                    onSharePost(post)
                    showPostMenuFor = null
                },
            )
        }
    }

    postToDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePost(post.id)
                    postToDelete = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { postToDelete = null }) { Text("Отмена") } },
        )
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                SharedFeedSwipeablePostCard(
                    items = state.feedItems,
                    undoAnimationSignal = undoAnimationSignal,
                    undoAnimationFrom = undoAnimationFrom,
                    onSwipeLeft = onSkipClick,
                    onSwipeRight = onLikeClick,
                    onProfileClick = onProfileClick,
                    onProfileTagClick = onProfileTagClick,
                    onPostLinkClick = onPostLinkClick,
                    onCommentsClick = onCommentsClick,
                    onPostMenuClick = { showPostMenuFor = it },
                    onViewImagesClick = { post, index ->
                        val openRequest = resolveSharedFeedFullscreenOpenRequest(post, index) ?: return@SharedFeedSwipeablePostCard
                        viewerPost = post
                        onPresentFullscreenViewer?.invoke(hostedViewer)
                        hostedViewer.onAction(
                            FullscreenImageViewerAction.Open(page = openRequest.page, origin = null),
                        )
                    },
                    modifier = Modifier.fillMaxHeight().widthIn(max = SharedFeedCardMaxWidth),
                )
            }

            if (isDebugBuild) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SharedFeedSwipeButtons(
                            onSkipClick = onSkipClick,
                            onLikeClick = onLikeClick,
                            enabled = !state.isLoadingNext,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onToggleDebug, modifier = Modifier.size(48.dp)) {
                            Icon(
                                painter = painterResource(if (state.isDebugMode) Res.drawable.done_icon else Res.drawable.search_icon),
                                contentDescription = "Debug",
                                tint = if (state.isDebugMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (state.isDebugMode && isMockBuild) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = onResetRecommendations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Сброс ленты", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { showProfileDeleteDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Icon(painter = painterResource(Res.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Удалить всё", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (state.isDebugMode) {
                state.lastAnalysisResult?.let { analysisResult ->
                    SharedFeedAnalysisDebugCard(
                        analysisResult = analysisResult,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.isDebugMode) {
                SharedFeedUserProfileInfoBlock(
                    userProfile = state.userProfile,
                    lastSwipeInfo = state.lastSwipeInfo,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SharedFeedAnalysisToast(
            analysisResult = if (!state.isDebugMode) state.lastAnalysisResult else null,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (onPresentFullscreenViewer == null && viewerModel != null) {
            SharedFullscreenImageViewer(
                model = viewerModel,
                state = viewerState,
                onAction = hostedViewer.onAction,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SharedFeedSwipeablePostCard(
    items: List<SharedFeedItem>,
    undoAnimationSignal: Long,
    undoAnimationFrom: SharedUndoAnimationFrom,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onPostMenuClick: (Post) -> Unit,
    onViewImagesClick: (Post, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val undoTopOffsetX = remember { Animatable(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val undoStartOffsetPx = with(density) { 18.dp.toPx() }
    var nonSwipeBottomInsetPx by remember { mutableStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(undoAnimationSignal) {
        if (undoAnimationSignal == 0L) return@LaunchedEffect
        val startOffset = if (undoAnimationFrom == SharedUndoAnimationFrom.LEFT) -undoStartOffsetPx else undoStartOffsetPx
        undoTopOffsetX.snapTo(startOffset)
        undoTopOffsetX.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }

    SharedSwipeableCardStack(
        items = items,
        onSwipe = { _, direction ->
            when (direction) {
                SharedSwipeDirection.Left -> onSwipeLeft()
                SharedSwipeDirection.Right -> onSwipeRight()
                else -> Unit
            }
        },
        canDismissDirection = { it == SharedSwipeDirection.Left || it == SharedSwipeDirection.Right },
        onSwipeWithoutDismiss = { item, direction -> if (direction == SharedSwipeDirection.Down) onPostMenuClick(item.post) },
        topCardExternalOffsetX = undoTopOffsetX.value,
        nonSwipeBottomInsetPx = nonSwipeBottomInsetPx,
        keySelector = { it.entryId },
        modifier = modifier.padding(bottom = 60.dp),
        overlayContent = { direction, progress -> SharedSwipeOverlayContent(direction, progress) },
    ) { item ->
        SharedFeedPostCard(
            post = item.post,
            recommendationReason = item.recommendationReason,
            onProfileClick = onProfileClick,
            onProfileTagClick = onProfileTagClick,
            onPostLinkClick = onPostLinkClick,
            onCommentsClick = onCommentsClick,
            onMoreClick = onPostMenuClick,
            onViewImagesClick = { index -> onViewImagesClick(item.post, index) },
            onNonSwipeBottomInsetChanged = { insetPx -> nonSwipeBottomInsetPx = insetPx.toFloat() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Stable
private class SharedSwipeCardState(
    private val config: SharedSwipeConfig,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    val rotation = Animatable(0f)
    val scale = Animatable(1f)
    val overlayAlpha = Animatable(0f)
    var isDragging by mutableStateOf(false)
    var containerSize by mutableStateOf(IntSize.Zero)

    fun onDragStart() { isDragging = true }

    fun onDrag(dragAmount: Offset) {
        scope.launch {
            val resistanceY = if (dragAmount.y > 0) config.resistanceVerticalDown else config.resistanceVerticalUp
            offset.snapTo(
                Offset(
                    x = offset.value.x + dragAmount.x * config.resistanceHorizontal,
                    y = offset.value.y + dragAmount.y * resistanceY,
                ),
            )
            val maxWidth = containerSize.width.toFloat().takeIf { it > 0f } ?: 1000f
            rotation.snapTo((offset.value.x / maxWidth) * config.maxRotation)
        }
    }

    fun onDragEnd(
        onSwipe: (SharedSwipeDirection) -> Unit,
        canDismiss: (SharedSwipeDirection) -> Boolean,
        onSwipeWithoutDismiss: (SharedSwipeDirection) -> Unit,
    ): Boolean {
        isDragging = false
        val width = containerSize.width.toFloat().takeIf { it > 0f } ?: 1000f
        val height = containerSize.height.toFloat().takeIf { it > 0f } ?: 1000f
        val x = offset.value.x
        val y = offset.value.y
        val direction = if (abs(x) > abs(y)) {
            when {
                x > width * config.swipeThreshold -> SharedSwipeDirection.Right
                x < -width * config.swipeThreshold -> SharedSwipeDirection.Left
                else -> null
            }
        } else {
            when {
                y > height * config.swipeThresholdDown -> SharedSwipeDirection.Down
                y < -height * config.swipeThresholdUp -> SharedSwipeDirection.Up
                else -> null
            }
        }
        if (direction == null) {
            snapBack()
            return false
        }
        if (canDismiss(direction)) {
            onSwipe(direction)
            return true
        }
        onSwipeWithoutDismiss(direction)
        snapBack()
        return false
    }

    fun onDragCancel() {
        isDragging = false
        snapBack()
    }

    fun currentDirection(): SharedSwipeDirection? {
        val x = offset.value.x
        val y = offset.value.y
        if (x == 0f && y == 0f) return null
        return if (abs(x) > abs(y)) {
            if (x >= 0f) SharedSwipeDirection.Right else SharedSwipeDirection.Left
        } else {
            if (y >= 0f) SharedSwipeDirection.Down else SharedSwipeDirection.Up
        }
    }

    private fun snapBack() {
        scope.launch {
            launch { offset.animateTo(Offset.Zero, spring(config.springDamping, config.springStiffness)) }
            launch { rotation.animateTo(0f, spring(config.springDamping, config.springStiffness)) }
        }
    }
}

@Composable
private fun rememberSharedSwipeCardState(config: SharedSwipeConfig = DefaultSharedSwipeConfig): SharedSwipeCardState {
    val scope = rememberCoroutineScope()
    return remember(config, scope) { SharedSwipeCardState(config, scope) }
}

private data class SharedGhostCardData<T>(
    val key: String,
    val item: T,
    val startOffset: Offset,
    val startRotation: Float,
    val direction: SharedSwipeDirection,
)

@Composable
private fun <T : Any> SharedSwipeableCardStack(
    items: List<T>,
    onSwipe: (T, SharedSwipeDirection) -> Unit,
    canDismissDirection: (SharedSwipeDirection) -> Boolean,
    onSwipeWithoutDismiss: (T, SharedSwipeDirection) -> Unit,
    topCardExternalOffsetX: Float,
    nonSwipeBottomInsetPx: Float,
    keySelector: (T) -> Any,
    modifier: Modifier = Modifier,
    config: SharedSwipeConfig = DefaultSharedSwipeConfig,
    overlayContent: @Composable (SharedSwipeDirection, Float) -> Unit,
    content: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    val state = rememberSharedSwipeCardState(config)
    val scope = rememberCoroutineScope()
    val ghosts = remember { mutableStateListOf<SharedGhostCardData<T>>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val visibleItems = remember(items, config.maxVisibleItems) {
        items.take(config.maxVisibleItems).mapIndexed { index, item -> item to index }.reversed()
    }

    Box(
        modifier = modifier.fillMaxSize().onGloballyPositioned { state.containerSize = it.size },
        contentAlignment = Alignment.Center,
    ) {
        visibleItems.forEach { (item, index) ->
            key(keySelector(item)) {
                if (index == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = state.offset.value.x + topCardExternalOffsetX
                                translationY = state.offset.value.y
                                rotationZ = state.rotation.value
                            }
                            .pointerInput(item, nonSwipeBottomInsetPx) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val interactiveBoundary = size.height - nonSwipeBottomInsetPx
                                    if (down.position.y >= interactiveBoundary.coerceAtLeast(0f)) {
                                        waitForUpOrCancellation()
                                        return@awaitEachGesture
                                    }

                                    val dragChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        change.consume()
                                        state.onDragStart()
                                        state.onDrag(Offset(over.x, over.y))
                                    }

                                    if (dragChange == null) {
                                        state.onDragCancel()
                                        return@awaitEachGesture
                                    }

                                    val completed = drag(dragChange.id) { change ->
                                        val dragAmount = change.positionChange()
                                        change.consume()
                                        state.onDrag(Offset(dragAmount.x, dragAmount.y))
                                    }

                                    if (completed) {
                                        state.onDragEnd(
                                            onSwipe = { direction ->
                                                ghosts += SharedGhostCardData(
                                                    key = keySelector(item).toString(),
                                                    item = item,
                                                    startOffset = state.offset.value,
                                                    startRotation = state.rotation.value,
                                                    direction = direction,
                                                )
                                                scope.launch {
                                                    launch { state.offset.snapTo(Offset.Zero) }
                                                    launch { state.rotation.snapTo(0f) }
                                                }
                                                onSwipe(item, direction)
                                            },
                                            canDismiss = canDismissDirection,
                                            onSwipeWithoutDismiss = { direction -> onSwipeWithoutDismiss(item, direction) },
                                        )
                                    } else {
                                        state.onDragCancel()
                                    }
                                }
                            },
                    ) {
                        CompositionLocalProvider(LocalSharedSwipeItemIndex provides 0) { content(item) }
                        if (state.isDragging) {
                            val direction = state.currentDirection() ?: SharedSwipeDirection.Right
                            val width = state.containerSize.width.toFloat().takeIf { it > 0f } ?: 1f
                            val height = state.containerSize.height.toFloat().takeIf { it > 0f } ?: 1f
                            val rawProgress = when (direction) {
                                SharedSwipeDirection.Left, SharedSwipeDirection.Right -> abs(state.offset.value.x) / (width * config.swipeThreshold)
                                SharedSwipeDirection.Up -> abs(state.offset.value.y) / (height * config.swipeThresholdUp)
                                SharedSwipeDirection.Down -> abs(state.offset.value.y) / (height * config.swipeThresholdDown)
                            }.coerceIn(0f, 1f)
                            overlayContent(direction, 1f - (1f - rawProgress) * (1f - rawProgress))
                        }
                    }
                } else {
                    val scale = config.stackCardScales.getOrNull(index - 1) ?: 1f
                    val offsetY = with(density) {
                        (config.stackCardOffsets.getOrNull(index - 1) ?: (config.stackSpacing * index)).toPx()
                    }
                    val overlayAlpha = config.stackCardAlphas.getOrNull(index - 1) ?: 0f
                    Box(
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationY = offsetY
                        },
                    ) {
                        CompositionLocalProvider(LocalSharedSwipeItemIndex provides index) { content(item) }
                        Box(
                            modifier = Modifier.fillMaxSize().clip(SharedPostCardShape).background(Color.Black.copy(alpha = overlayAlpha)),
                        )
                    }
                }
            }
        }

        ghosts.forEach { ghost ->
            key(ghost.key + "_ghost") {
                val offset = remember { Animatable(ghost.startOffset, Offset.VectorConverter) }
                androidx.compose.runtime.LaunchedEffect(ghost.key) {
                    val targetX = when (ghost.direction) {
                        SharedSwipeDirection.Left -> -config.dismissDistance
                        SharedSwipeDirection.Right -> config.dismissDistance
                        else -> ghost.startOffset.x
                    }
                    val targetY = when (ghost.direction) {
                        SharedSwipeDirection.Up -> -config.dismissDistance
                        SharedSwipeDirection.Down -> config.dismissDistance
                        else -> ghost.startOffset.y
                    }
                    offset.animateTo(Offset(targetX, targetY), tween(config.dismissDuration))
                    ghosts.remove(ghost)
                }
                Box(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = offset.value.x
                        translationY = offset.value.y
                        rotationZ = ghost.startRotation
                    },
                ) {
                    CompositionLocalProvider(LocalSharedSwipeItemIndex provides 0) { content(ghost.item) }
                }
            }
        }
    }
}

@Composable
private fun SharedSwipeOverlayContent(direction: SharedSwipeDirection, progress: Float) {
    Box(Modifier.fillMaxSize()) {
        when (direction) {
            SharedSwipeDirection.Left -> SwipeIndicator(painterResource(Res.drawable.nav_back_icon), MaterialTheme.colorScheme.errorContainer, Alignment.CenterEnd, progress, (-32).dp, 0.dp)
            SharedSwipeDirection.Right -> SwipeIndicator(painterResource(Res.drawable.done_icon), MaterialTheme.colorScheme.primaryContainer, Alignment.CenterStart, progress, 32.dp, 0.dp)
            SharedSwipeDirection.Up -> SwipeIndicator(painterResource(Res.drawable.plus_icon), Color(0xFF2196F3).copy(alpha = 0.9f), Alignment.TopCenter, progress, 0.dp, 60.dp)
            SharedSwipeDirection.Down -> SwipeIndicator(painterResource(Res.drawable.reply_icon), MaterialTheme.colorScheme.secondaryContainer, Alignment.BottomCenter, progress, 0.dp, (-60).dp)
        }
    }
}

@Composable
private fun BoxScope.SwipeIndicator(painter: Painter, color: Color, alignment: Alignment, progress: Float, offsetX: androidx.compose.ui.unit.Dp, offsetY: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer {
                scaleX = 0.5f + (progress * 0.5f)
                scaleY = 0.5f + (progress * 0.5f)
                alpha = progress
                shadowElevation = 12.dp.toPx()
                shape = CircleShape
                clip = true
            }
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painter, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
    }
}

@Composable
private fun SharedFeedPostCard(
    post: Post,
    recommendationReason: String?,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onMoreClick: (Post) -> Unit,
    onViewImagesClick: (Int) -> Unit,
    onNonSwipeBottomInsetChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeItemIndex = LocalSharedSwipeItemIndex.current
    Card(modifier = modifier, shape = SharedPostCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxSize().padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 6.dp)) {
            SharedPostHeader(post, recommendationReason, onProfileClick, onMoreClick)
            Spacer(Modifier.height(16.dp))
            SharedImageGallery(post.content.resolvedImageVariants(), onViewImagesClick, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(16.dp))
            SharedTextContent(post.content.description, post.createdAt, onProfileTagClick, onPostLinkClick, { onCommentsClick(post) }, categoryColor(post.category))
            Spacer(Modifier.height(16.dp))
            SharedPostFooter(
                commentsCount = post.commentsCount,
                commentersPreview = post.commentersPreview,
                onCommentsClick = { onCommentsClick(post) },
                onHeightChanged = if (swipeItemIndex == 0) onNonSwipeBottomInsetChanged else null,
            )
        }
    }
}

@Composable
private fun SharedPostHeader(post: Post, recommendationReason: String?, onProfileClick: (String) -> Unit, onMoreClick: (Post) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.weight(1f).clickable { onProfileClick(post.author.id) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("@${post.author.username?.value ?: "unknown"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            androidx.compose.material3.Surface(color = categoryColor(post.category), shape = RoundedCornerShape(8.dp)) {
                Text(toDisplayTag(post.category), color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            recommendationReason?.takeIf(String::isNotBlank)?.let { reason ->
                androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.widthIn(max = 170.dp)) {
                    Text(reason, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
        IconButton(onClick = { onMoreClick(post) }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SharedImageGallery(imageVariants: List<PostImageVariant>, onViewClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val items = imageVariants.take(4)
    if (items.isEmpty()) return
    if (items.size == 1) {
        SharedSingleImageGallery(items.first(), { onViewClick(0) }, modifier)
        return
    }
    val front = items[0]
    val left = items.getOrNull(1)
    val right = items.getOrNull(2)
    val back = items.getOrNull(3)
    val extraCount = (items.size - 3).coerceAtLeast(0)
    var frontPainter by remember(front) { mutableStateOf<Painter>(ColorPainter(Color(0xFF1A1A1A))) }
    val frontBounds = remember { mutableStateOf<Rect?>(null) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        back?.let { LayeredImageCard(it, Modifier.size(176.dp, 210.dp).graphicsLayer { translationY = 16.dp.toPx(); scaleX = 0.96f; scaleY = 0.96f }, 0.55f) }
        left?.let { LayeredImageCard(it, Modifier.size(170.dp, 200.dp).graphicsLayer { rotationZ = -10f; translationX = -50.dp.toPx() }, 0.5f) }
        right?.let { LayeredImageCard(it, Modifier.size(170.dp, 200.dp).graphicsLayer { rotationZ = 10f; translationX = 50.dp.toPx() }, 0.5f) }
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(180.dp, 230.dp).clickable { onViewClick(0) }.onGloballyPositioned { frontBounds.value = it.boundsInWindow() }.clip(RoundedCornerShape(14.dp)),
            ) {
                ProgressiveImage(front.lqUrl, front.previewUrl, front.fullUrl, ProgressiveImageMode.LIST, null, Modifier.fillMaxSize(), ContentScale.Crop) {
                    if (it != null) frontPainter = it
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                if (extraCount > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("+$extraCount", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            BlurGlassButton(painter = frontPainter, backgroundBoundsInWindow = frontBounds.value, backgroundContentScale = ContentScale.Crop, refractionScale = 1.14f, blurRadius = 2.5.dp, onClick = { onViewClick(0) }) {
                Text("смотреть", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LayeredImageCard(variant: PostImageVariant, modifier: Modifier, shadeAlpha: Float) {
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        ProgressiveImage(variant.lqUrl, variant.previewUrl, variant.fullUrl, ProgressiveImageMode.LIST, null, Modifier.fillMaxSize(), ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shadeAlpha)))
    }
}

@Composable
private fun SharedSingleImageGallery(variant: PostImageVariant, onViewClick: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val widthByDesign = maxWidth * 0.6f
        val widthByHeight = maxHeight * SingleImageAspectRatio
        val cardWidth = if (widthByDesign < widthByHeight) widthByDesign else widthByHeight
        Box(Modifier.width(cardWidth).aspectRatio(SingleImageAspectRatio).graphicsLayer { rotationZ = SingleImageCardRotation }.clickable { onViewClick() }.clip(RoundedCornerShape(18.dp))) {
            ProgressiveImage(variant.lqUrl, variant.previewUrl, variant.fullUrl, ProgressiveImageMode.LIST, null, Modifier.fillMaxSize(), ContentScale.Crop)
        }
    }
}

@Composable
private fun SharedTextContent(description: String?, timestamp: Long, onProfileTagClick: (String) -> Unit, onPostLinkClick: (String, String) -> Unit, onCommentsClick: () -> Unit, linkColor: Color) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!description.isNullOrBlank()) {
            val (title, body) = extractTitleAndBody(description)
            if (title.isNotBlank()) {
                SharedLinkifiedText(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface), linkColor = linkColor, onProfileTagClick = onProfileTagClick, onPostLinkClick = onPostLinkClick)
            }
            if (body.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val maxLines = 8
                        val readMoreText = "читать дальше"
                        val textMeasurer = rememberTextMeasurer()
                        val density = LocalDensity.current
                        val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(0)

                        val trimResult = remember(body, maxWidthPx, bodyStyle) {
                            if (maxWidthPx == 0) {
                                SharedTrimResult(body = body, isTruncated = false)
                            } else {
                                val fullLayout = textMeasurer.measure(
                                    text = body,
                                    style = bodyStyle,
                                    maxLines = maxLines,
                                    overflow = TextOverflow.Ellipsis,
                                    constraints = Constraints(maxWidth = maxWidthPx)
                                )
                                if (!fullLayout.hasVisualOverflow) {
                                    SharedTrimResult(body = body, isTruncated = false)
                                } else {
                                    val suffix = "… $readMoreText"
                                    var cutIndex = fullLayout.getLineEnd(maxLines - 1, visibleEnd = true)
                                    var candidate = body.take(cutIndex).trimEnd()
                                    while (candidate.isNotEmpty()) {
                                        val layout = textMeasurer.measure(
                                            text = candidate + suffix,
                                            style = bodyStyle,
                                            maxLines = maxLines,
                                            overflow = TextOverflow.Clip,
                                            constraints = Constraints(maxWidth = maxWidthPx)
                                        )
                                        if (!layout.hasVisualOverflow) break
                                        cutIndex -= 1
                                        candidate = body.take(cutIndex.coerceAtLeast(0)).trimEnd()
                                    }
                                    SharedTrimResult(body = candidate, isTruncated = true)
                                }
                            }
                        }

                        val displayBody = if (trimResult.isTruncated) "${trimResult.body}… " else trimResult.body

                        SharedLinkifiedText(
                            text = displayBody,
                            style = bodyStyle,
                            linkColor = linkColor,
                            trailingText = if (trimResult.isTruncated) readMoreText else null,
                            trailingStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 16.sp,
                                color = linkColor,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = maxLines,
                            overflow = TextOverflow.Clip,
                            onProfileTagClick = onProfileTagClick,
                            onPostLinkClick = onPostLinkClick,
                            onTrailingClick = if (trimResult.isTruncated) onCommentsClick else null,
                            onTextClick = if (trimResult.isTruncated) onCommentsClick else null
                        )
                    }
                    Text(formatChatClockTime(timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SharedPostFooter(
    commentsCount: Int,
    commentersPreview: List<String>,
    onCommentsClick: () -> Unit,
    onHeightChanged: ((Int) -> Unit)? = null,
) {
    val previewAvatars = commentersPreview.toCommenterAvatarUiModels(limit = 3)
    val avatarSize = 28.dp
    val overlapStep = 12.dp
    val avatarsGroupWidth = if (previewAvatars.isEmpty()) 0.dp else avatarSize + overlapStep * (previewAvatars.size - 1)
    Column(
        Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates -> onHeightChanged?.invoke(coordinates.size.height) },
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().height(52.dp).clickable { onCommentsClick() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previewAvatars.isNotEmpty()) {
                    Box(Modifier.width(avatarsGroupWidth)) {
                        previewAvatars.forEachIndexed { index, avatar ->
                            val avatarModifier = Modifier.offset(x = overlapStep * index).border(1.2.dp, MaterialTheme.colorScheme.surfaceContainer, NinehedronShape).size(avatarSize)
                            when (avatar) {
                                is CommenterAvatarUiModel.Remote -> NetworkAvatar(avatar.fallbackName, avatar.url, modifier = avatarModifier, size = avatarSize, shape = NinehedronShape)
                                is CommenterAvatarUiModel.Initial -> InitialAvatar(avatar.name, modifier = avatarModifier, size = avatarSize, shape = NinehedronShape)
                            }
                        }
                    }
                }
                Text("$commentsCount комментариев", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Открыть комментарии", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SharedFeedSwipeButtons(onSkipClick: () -> Unit, onLikeClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        WideOutlinedIconButton(onClick = onSkipClick, enabled = enabled, modifier = Modifier.weight(1f).height(48.dp)) {
            Icon(painterResource(Res.drawable.nav_back_icon), contentDescription = "Скип")
        }
        Button(onClick = onLikeClick, enabled = enabled, modifier = Modifier.weight(2f).height(48.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(Res.drawable.done_icon), contentDescription = null)
                Text("ЛАЙК")
            }
        }
    }
}

@Composable
private fun SharedLinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    trailingText: String? = null,
    trailingStyle: TextStyle = style.copy(color = linkColor, fontWeight = FontWeight.SemiBold),
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onTrailingClick: (() -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
) {
    val annotated = remember(text, linkColor, trailingText, trailingStyle) {
        buildAnnotatedPostText(
            text = text,
            linkColor = linkColor,
            trailingText = trailingText,
            trailingStyle = trailingStyle,
        )
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = annotated,
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { tapOffset ->
                val layout = textLayout ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(tapOffset)
                annotated.getStringAnnotations(TagPost, offset, offset).firstOrNull()?.let { ann ->
                    val parts = ann.item.split("/", limit = 2)
                    if (parts.size == 2) {
                        onPostLinkClick(parts[0], parts[1])
                    }
                    return@detectTapGestures
                }

                annotated.getStringAnnotations(TagProfile, offset, offset).firstOrNull()?.let { ann ->
                    onProfileTagClick(ann.item)
                    return@detectTapGestures
                }

                annotated.getStringAnnotations(TagTrailing, offset, offset).firstOrNull()?.let {
                    onTrailingClick?.invoke()
                    return@detectTapGestures
                }

                onTextClick?.invoke()
            }
        },
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { textLayout = it },
    )
}

private fun buildAnnotatedPostText(
    text: String,
    linkColor: Color,
    trailingText: String? = null,
    trailingStyle: TextStyle = TextStyle.Default,
): AnnotatedString {
    val links = findPostLinks(text)
    if (links.isEmpty() && trailingText.isNullOrEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        links.forEach { link ->
            if (cursor < link.start) append(text.substring(cursor, link.start))
            val segment = text.substring(link.start, link.end)
            withStyle(SpanStyle(color = linkColor)) { append(segment) }
            addStringAnnotation(link.tag, link.value, length - segment.length, length)
            cursor = link.end
        }
        if (cursor < text.length) append(text.substring(cursor))
        if (!trailingText.isNullOrEmpty()) {
            withStyle(
                SpanStyle(
                    color = trailingStyle.color,
                    fontWeight = trailingStyle.fontWeight,
                    fontStyle = trailingStyle.fontStyle,
                    fontSize = trailingStyle.fontSize,
                )
            ) {
                append(trailingText)
            }
            addStringAnnotation(
                tag = TagTrailing,
                annotation = trailingText,
                start = length - trailingText.length,
                end = length,
            )
        }
    }
}

private data class LinkMatch(val start: Int, val end: Int, val tag: String, val value: String)
private data class SharedTrimResult(val body: String, val isTruncated: Boolean)

private fun findPostLinks(text: String): List<LinkMatch> {
    val matches = mutableListOf<LinkMatch>()
    val postPattern = Regex("https?://[^\\s]+/([a-z0-9_]+)/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
    val mentionPattern = Regex("@([a-z0-9_]+)", RegexOption.IGNORE_CASE)
    postPattern.findAll(text).forEach { match ->
        matches += LinkMatch(match.range.first, match.range.last + 1, TagPost, "${match.groupValues[1].lowercase()}/${match.groupValues[2]}")
    }
    mentionPattern.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (matches.any { it.start < end && start < it.end }) return@forEach
        matches += LinkMatch(start, end, TagProfile, match.groupValues[1].lowercase())
    }
    return matches.sortedBy { it.start }
}

private sealed interface CommenterAvatarUiModel {
    data class Remote(val url: String, val fallbackName: String = "?") : CommenterAvatarUiModel
    data class Initial(val name: String) : CommenterAvatarUiModel
}

private fun List<String>.toCommenterAvatarUiModels(limit: Int = 3): List<CommenterAvatarUiModel> = asSequence().map(String::trim).filter(String::isNotEmpty).take(limit).map { raw ->
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) CommenterAvatarUiModel.Remote(raw) else CommenterAvatarUiModel.Initial(raw)
}.toList()

private fun extractTitleAndBody(description: String): Pair<String, String> {
    val words = description.trim().split("\\s+".toRegex())
    return words.take(3).joinToString(" ") to words.drop(3).joinToString(" ")
}

private fun toDisplayTag(category: String): String = if (category.isBlank()) "#unknown" else "#${category.trim().lowercase().replace('_', ' ')}"

private fun categoryColor(category: String): Color {
    return when (category.trim().uppercase()) {
        "NATURE" -> Color(0xFF2E7D32)
        "FOOD" -> Color(0xFFF57C00)
        "ART" -> Color(0xFFD81B60)
        "TRAVEL" -> Color(0xFF0288D1)
        "TECH" -> Color(0xFF3949AB)
        "MEMES" -> Color(0xFF8E24AA)
        "LIFESTYLE" -> Color(0xFF00897B)
        "SPACE" -> Color(0xFF5E35B1)
        "PETS" -> Color(0xFF6D4C41)
        "SPORT" -> Color(0xFFC62828)
        "" -> Color(0xFF546E7A)
        else -> {
            val normalized = category.trim().uppercase()
            val seed = abs(normalized.hashCode())
            val r = 60 + (seed and 0x7F)
            val g = 60 + ((seed shr 8) and 0x7F)
            val b = 60 + ((seed shr 16) and 0x7F)
            Color(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
        }
    }
}

private fun Post.toProfilePostItem(): ProfilePostItem {
    val variants = content.resolvedImageVariants()
    val fallbackUrls = content.imageUrls.filter(String::isNotBlank)
    val firstVariant = variants.firstOrNull()
    return ProfilePostItem(
        id = id,
        description = content.description,
        lqUrl = firstVariant?.lqUrl ?: fallbackUrls.firstOrNull(),
        previewUrl = firstVariant?.previewUrl ?: fallbackUrls.firstOrNull(),
        fullUrl = firstVariant?.fullUrl ?: fallbackUrls.firstOrNull(),
        viewerUrls = content.viewerImageUrls().ifEmpty { fallbackUrls },
        previewUrls = content.previewImageUrls().ifEmpty { fallbackUrls },
        likesCount = likesCount,
        commentsCount = commentsCount,
        category = category,
        createdAtMillis = createdAt,
    )
}
