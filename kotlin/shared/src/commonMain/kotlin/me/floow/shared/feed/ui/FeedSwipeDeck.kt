package me.floow.shared.feed.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.heart_icon
import flow.shared.generated.resources.nav_back_icon
import flow.shared.generated.resources.plus_icon
import flow.shared.generated.resources.reply_icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.floow.domain.models.Post
import me.floow.shared.feed.uilogic.SharedFeedItem
import me.floow.uikit.components.swipe.SwipeDirection
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs

internal enum class SharedUndoAnimationFrom {
    LEFT,
    RIGHT,
}

internal data class FeedSwipeConfig(
    val swipeThreshold: Float = 0.25f,
    val swipeThresholdDown: Float = 0.2f,
    val swipeThresholdUp: Float = 0.2f,
    val maxRotation: Float = 10f,
    val dismissDistance: Float = 1500f,
    val dismissDuration: Int = 400,
    val springDamping: Float = 0.7f,
    val springStiffness: Float = Spring.StiffnessMedium,
    val stackSpacing: Dp = 40.dp,
    val maxVisibleItems: Int = 3,
    val stackCardScales: List<Float> = listOf(0.95f, 0.90f),
    val stackCardAlphas: List<Float> = listOf(0.12f, 0.08f),
    val stackCardOffsets: List<Dp> = listOf(48.dp, 88.dp),
    val stackCardStartInsets: List<Dp> = listOf(4.dp, 4.dp),
    val stackCardEndInsets: List<Dp> = listOf(12.dp, 0.dp),
    val stackCardRotations: List<Float> = listOf(-1.1f, 0f),
    val resistanceHorizontal: Float = 0.8f,
    val resistanceVerticalUp: Float = 0.6f,
    val resistanceVerticalDown: Float = 0.4f,
    val hapticEnabled: Boolean = true,
)

internal val DefaultFeedSwipeConfig = FeedSwipeConfig()
internal val SharedFeedStackBottomBleed = 12.dp
internal val SharedFeedTopCardExtraHeight = 40.dp
internal val SharedFeedSecondCardExtraHeight = 18.dp
internal val SharedFeedCardMaxWidth = 420.dp
internal val FeedPrefetchImageSize = IntSize(420, 560)

@Stable
internal class FeedSwipeCardState(
    val config: FeedSwipeConfig,
    val density: Density,
    private val scope: CoroutineScope,
) {
    var dragOffsetX by mutableFloatStateOf(0f)
    var dragOffsetY by mutableFloatStateOf(0f)
    var dragRotation by mutableFloatStateOf(0f)
    var topCardAlpha by mutableFloatStateOf(1f)
    var stackProgress by mutableFloatStateOf(0f)

    var isDragging by mutableStateOf(false)
    var isDismissing by mutableStateOf(false)
    var dismissDirection by mutableStateOf<SwipeDirection?>(null)
    var containerSize by mutableStateOf(IntSize.Zero)

    private var activeAnimJob: Job? = null

    fun onDragStart() {
        if (isDismissing) return
        activeAnimJob?.cancel()
        isDragging = true
        topCardAlpha = 1f
    }

    fun onDrag(dragAmount: Offset) {
        if (isDismissing) return
        val resistanceY = if (dragAmount.y > 0) config.resistanceVerticalDown else config.resistanceVerticalUp
        dragOffsetX += dragAmount.x * config.resistanceHorizontal
        dragOffsetY += dragAmount.y * resistanceY

        val maxWidth = containerSize.width.toFloat().takeIf { it > 0f } ?: 1000f
        val horizontalProgress = (dragOffsetX / maxWidth).coerceIn(-1f, 1f)
        dragRotation = horizontalProgress * config.maxRotation

        val rawDragProgress = (abs(dragOffsetX) / maxWidth).coerceIn(0f, 1f)
        stackProgress = (rawDragProgress * 1.6f).coerceIn(0f, 0.65f)
    }

    fun onDragEnd(
        velocityX: Float,
        velocityY: Float,
        onSwipe: (SwipeDirection) -> Unit,
        canDismiss: (SwipeDirection) -> Boolean,
        onSwipeWithoutDismiss: (SwipeDirection) -> Unit,
    ) {
        if (isDismissing) return
        isDragging = false

        val width = containerSize.width.toFloat().takeIf { it > 0f } ?: 1000f
        val height = containerSize.height.toFloat().takeIf { it > 0f } ?: 1000f
        val x = dragOffsetX
        val y = dragOffsetY

        val isHorizontal = abs(x) > abs(y)
        val dismissThresholdPx = width * config.swipeThreshold

        val direction: SwipeDirection? = if (isHorizontal) {
            val crossedThreshold = abs(x) > dismissThresholdPx
            val minFlingDistance = with(density) { 20.dp.toPx() }
            val isFling = abs(velocityX) > 1000f && (velocityX > 0f == x > 0f) && abs(x) > minFlingDistance
            if (crossedThreshold || isFling) {
                if (x > 0f) SwipeDirection.Right else SwipeDirection.Left
            } else {
                null
            }
        } else {
            val crossedDown = y > height * config.swipeThresholdDown
            val minFlingDistance = with(density) { 20.dp.toPx() }
            val isFlingDown = velocityY > 1000f && y > minFlingDistance
            if (crossedDown || isFlingDown) {
                SwipeDirection.Down
            } else {
                val crossedUp = y < -height * config.swipeThresholdUp
                val isFlingUp = velocityY < -1000f && y < -minFlingDistance
                if (crossedUp || isFlingUp) SwipeDirection.Up else null
            }
        }

        if (direction == null) {
            snapBack()
            return
        }

        if (canDismiss(direction)) {
            dismiss(direction, onSwipe)
        } else {
            onSwipeWithoutDismiss(direction)
            snapBack()
        }
    }

    fun onDragCancel() {
        if (isDismissing) return
        isDragging = false
        snapBack()
    }

    fun resetVisualState() {
        activeAnimJob?.cancel()
        isDragging = false
        isDismissing = false
        dismissDirection = null
        dragOffsetX = 0f
        dragOffsetY = 0f
        dragRotation = 0f
        topCardAlpha = 1f
        stackProgress = 0f
    }

    private fun snapBack() {
        activeAnimJob?.cancel()
        activeAnimJob = scope.launch {
            val startX = dragOffsetX
            val startY = dragOffsetY
            val startRot = dragRotation
            val startProgress = stackProgress
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = config.springDamping,
                    stiffness = config.springStiffness,
                ),
            ) {
                val t = value
                dragOffsetX = androidx.compose.ui.util.lerp(startX, 0f, t)
                dragOffsetY = androidx.compose.ui.util.lerp(startY, 0f, t)
                dragRotation = androidx.compose.ui.util.lerp(startRot, 0f, t)
                stackProgress = androidx.compose.ui.util.lerp(startProgress, 0f, t)
            }
            dragOffsetX = 0f
            dragOffsetY = 0f
            dragRotation = 0f
            stackProgress = 0f
            topCardAlpha = 1f
            isDismissing = false
            dismissDirection = null
        }
    }

    private fun dismiss(
        direction: SwipeDirection,
        onSwipe: (SwipeDirection) -> Unit,
    ) {
        activeAnimJob?.cancel()
        isDismissing = true
        dismissDirection = direction

        activeAnimJob = scope.launch {
            val startX = dragOffsetX
            val startY = dragOffsetY
            val startRot = dragRotation
            val startProgress = stackProgress

            val targetX = when (direction) {
                SwipeDirection.Left -> -config.dismissDistance
                SwipeDirection.Right -> config.dismissDistance
                else -> startX
            }
            val targetY = when (direction) {
                SwipeDirection.Up -> -config.dismissDistance
                SwipeDirection.Down -> config.dismissDistance
                else -> startY + 24f
            }
            val targetRot = startRot + when (direction) {
                SwipeDirection.Left -> -7f
                SwipeDirection.Right -> 7f
                else -> 0f
            }

            val duration = 280
            val anim = Animatable(0f)
            anim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing),
            ) {
                val t = value
                dragOffsetX = androidx.compose.ui.util.lerp(startX, targetX, t)
                dragOffsetY = androidx.compose.ui.util.lerp(startY, targetY, t)
                dragRotation = androidx.compose.ui.util.lerp(startRot, targetRot, t)
                stackProgress = androidx.compose.ui.util.lerp(startProgress, 1f, t)
                topCardAlpha = if (t < 0.4f) 1f else androidx.compose.ui.util.lerp(1f, 0f, (t - 0.4f) / 0.6f)
            }

            dragOffsetX = targetX
            dragOffsetY = targetY
            dragRotation = targetRot
            stackProgress = 1f
            topCardAlpha = 0f

            onSwipe(direction)

            delay(500)
            if (isDismissing) {
                resetVisualState()
            }
        }
    }

    fun currentDirection(): SwipeDirection? {
        if (isDismissing) return dismissDirection
        val x = dragOffsetX
        val y = dragOffsetY
        if (x == 0f && y == 0f) return null
        return if (abs(x) > abs(y)) {
            if (x >= 0f) SwipeDirection.Right else SwipeDirection.Left
        } else {
            if (y >= 0f) SwipeDirection.Down else SwipeDirection.Up
        }
    }
}

@Composable
internal fun rememberFeedSwipeCardState(config: FeedSwipeConfig = DefaultFeedSwipeConfig): FeedSwipeCardState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(config, scope, density) { FeedSwipeCardState(config, density, scope) }
}

@Composable
internal fun FeedSwipeDeck(
    items: List<SharedFeedItem>,
    undoAnimationSignal: Long,
    undoAnimationFrom: SharedUndoAnimationFrom,
    hapticFeedback: HapticFeedback,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onPostMenuClick: (Post) -> Unit,
    onOpenImageOverlay: (Post, SharedFeedOverlayLaunchData) -> Unit,
    onOverlaySourceSnapshot: (Post, SharedFeedOverlayLaunchData) -> Unit,
    isViewerActive: Boolean,
    activeOverlayPostId: String?,
    overlayDetachedCount: Int,
    modifier: Modifier = Modifier,
    config: FeedSwipeConfig = DefaultFeedSwipeConfig,
) {
    val undoTopOffsetX = remember { Animatable(0f) }
    val density = LocalDensity.current
    val undoStartOffsetPx = with(density) { 18.dp.toPx() }
    var nonSwipeBottomInsetPx by remember { mutableStateOf(0f) }

    LaunchedEffect(undoAnimationSignal) {
        if (undoAnimationSignal == 0L) return@LaunchedEffect
        val startOffset = if (undoAnimationFrom == SharedUndoAnimationFrom.LEFT) -undoStartOffsetPx else undoStartOffsetPx
        undoTopOffsetX.snapTo(startOffset)
        undoTopOffsetX.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
    }

    if (items.isEmpty()) return
    val state = rememberFeedSwipeCardState(config)
    val topItemKey = items.first().entryId
    val visibleItems = items.take(config.maxVisibleItems + 1).mapIndexed { index, item -> item to index }.reversed()
    val hasBackgroundCards = items.size > 1
    val stackBottomInset = remember(hasBackgroundCards, config) {
        if (!hasBackgroundCards) {
            0.dp
        } else {
            config.stackCardOffsets
                .take(config.maxVisibleItems - 1)
                .maxOrNull()
                ?: 0.dp
        }
    }

    var lastTopItemKey by remember { mutableStateOf(topItemKey) }
    if (lastTopItemKey != topItemKey) {
        lastTopItemKey = topItemKey
        state.resetVisualState()
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().onGloballyPositioned { state.containerSize = it.size },
        contentAlignment = Alignment.TopCenter,
    ) {
        val backgroundCardHeight = if (maxHeight > stackBottomInset) maxHeight - stackBottomInset + SharedFeedStackBottomBleed else 0.dp
        val topCardHeight = if (maxHeight > stackBottomInset) {
            maxHeight - (stackBottomInset - 16.dp - SharedFeedTopCardExtraHeight).coerceAtLeast(0.dp)
        } else {
            0.dp
        }

        visibleItems.forEach { (item, index) ->
            key(item.entryId) {
                val isTopCard = index == 0
                val isSecondCard = index == 1
                val isIncomingStackCard = index >= config.maxVisibleItems

                val idleScale = if (isIncomingStackCard) {
                    (config.stackCardScales.getOrNull(config.maxVisibleItems - 2) ?: 1f) - 0.05f
                } else if (isTopCard) {
                    1f
                } else {
                    config.stackCardScales.getOrNull(index - 1) ?: 1f
                }
                val promotedScale = if (isIncomingStackCard) {
                    config.stackCardScales.getOrNull(config.maxVisibleItems - 2) ?: idleScale
                } else if (index <= 1) {
                    1f
                } else {
                    config.stackCardScales.getOrNull(index - 2) ?: 1f
                }

                val thirdOffset = config.stackCardOffsets.getOrNull(config.maxVisibleItems - 2) ?: (config.stackSpacing * (config.maxVisibleItems - 1))
                val idleOffset = if (isIncomingStackCard) {
                    thirdOffset + config.stackSpacing
                } else if (isTopCard) {
                    0.dp
                } else {
                    config.stackCardOffsets.getOrNull(index - 1) ?: (config.stackSpacing * index)
                }
                val promotedOffset = if (isIncomingStackCard) {
                    thirdOffset
                } else if (index <= 1) {
                    0.dp
                } else {
                    config.stackCardOffsets.getOrNull(index - 2) ?: (config.stackSpacing * (index - 1))
                }

                val thirdStartInset = config.stackCardStartInsets.getOrNull(config.maxVisibleItems - 2) ?: 0.dp
                val idleStartInset = if (isIncomingStackCard) thirdStartInset else if (isTopCard) 0.dp else config.stackCardStartInsets.getOrNull(index - 1) ?: 0.dp
                val promotedStartInset = if (isIncomingStackCard) thirdStartInset else if (index <= 1) 0.dp else config.stackCardStartInsets.getOrNull(index - 2) ?: 0.dp

                val thirdEndInset = config.stackCardEndInsets.getOrNull(config.maxVisibleItems - 2) ?: 0.dp
                val idleEndInset = if (isIncomingStackCard) thirdEndInset else if (isTopCard) 0.dp else config.stackCardEndInsets.getOrNull(index - 1) ?: 0.dp
                val promotedEndInset = if (isIncomingStackCard) thirdEndInset else if (index <= 1) 0.dp else config.stackCardEndInsets.getOrNull(index - 2) ?: 0.dp

                val thirdRotation = config.stackCardRotations.getOrNull(config.maxVisibleItems - 2) ?: 0f
                val idleRotation = if (isIncomingStackCard) thirdRotation else if (isTopCard) 0f else config.stackCardRotations.getOrNull(index - 1) ?: 0f
                val promotedRotation = if (isIncomingStackCard) thirdRotation else if (index <= 1) 0f else config.stackCardRotations.getOrNull(index - 2) ?: 0f

                val baseOverlayAlpha = if (isTopCard) 0f else config.stackCardAlphas.getOrNull(index - 1) ?: 0f
                val promotedOverlayAlpha = when {
                    isTopCard -> 0f
                    isIncomingStackCard -> config.stackCardAlphas.getOrNull(config.maxVisibleItems - 2) ?: baseOverlayAlpha
                    index == 1 -> 0f
                    else -> config.stackCardAlphas.getOrNull(index - 2) ?: baseOverlayAlpha
                }

                val currentCardHeight = when (index) {
                    0 -> topCardHeight
                    1 -> lerp(backgroundCardHeight + SharedFeedSecondCardExtraHeight, topCardHeight, state.stackProgress)
                    2 -> lerp(backgroundCardHeight, backgroundCardHeight + SharedFeedSecondCardExtraHeight, state.stackProgress)
                    else -> backgroundCardHeight
                }

                val currentCardShape = if (index == 0) {
                    SharedPostCardShape
                } else if (index == 1) {
                    val bottomStartCorner = lerp(14.dp, 0.dp, state.stackProgress)
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomEnd = 14.dp,
                        bottomStart = bottomStartCorner,
                    )
                } else {
                    SharedStackCardShape
                }

                val cardZIndex = if (isTopCard) {
                    if (state.isDismissing) 10f else (config.maxVisibleItems + 1).toFloat()
                } else {
                    (config.maxVisibleItems - index).toFloat()
                }

                val gestureModifier = if (isTopCard) {
                    Modifier.pointerInput(item.entryId, nonSwipeBottomInsetPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cardHeight = size.height.toFloat()
                            val isBottomActionZone = down.position.y >= (cardHeight - nonSwipeBottomInsetPx)
                            if (isBottomActionZone || state.isDismissing) {
                                return@awaitEachGesture
                            }

                            val velocityTracker = VelocityTracker()
                            velocityTracker.addPosition(down.uptimeMillis, down.position)

                            var dragStarted = false
                            val dragChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                change.consume()
                                state.onDragStart()
                                state.onDrag(over)
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                dragStarted = true
                                if (config.hapticEnabled) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }

                            if (dragChange == null || !dragStarted) {
                                if (dragStarted) {
                                    state.onDragCancel()
                                }
                                return@awaitEachGesture
                            }

                            val completed = drag(dragChange.id) { change ->
                                val dragAmount = change.positionChange()
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                state.onDrag(dragAmount)
                            }

                            if (completed) {
                                val velocity = velocityTracker.calculateVelocity()
                                state.onDragEnd(
                                    velocityX = velocity.x,
                                    velocityY = velocity.y,
                                    onSwipe = { direction ->
                                        when (direction) {
                                            SwipeDirection.Left -> onSwipeLeft()
                                            SwipeDirection.Right -> onSwipeRight()
                                            else -> Unit
                                        }
                                    },
                                    canDismiss = { it == SwipeDirection.Left || it == SwipeDirection.Right },
                                    onSwipeWithoutDismiss = { direction ->
                                        if (direction == SwipeDirection.Down) {
                                            onPostMenuClick(item.post)
                                        }
                                    },
                                )
                                if (config.hapticEnabled && state.isDismissing) {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            } else {
                                state.onDragCancel()
                            }
                        }
                    }
                } else {
                    Modifier
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentCardHeight)
                        .zIndex(cardZIndex)
                        .graphicsLayer {
                            if (isTopCard) {
                                val dragOffsetX = state.dragOffsetX
                                val dragOffsetY = state.dragOffsetY
                                val dragRotation = state.dragRotation
                                val swipeProgress = state.stackProgress
                                val dragScale = 1f - (0.02f * swipeProgress)

                                translationX = dragOffsetX + undoTopOffsetX.value
                                translationY = dragOffsetY
                                rotationZ = dragRotation
                                transformOrigin = TransformOrigin(0.5f, 0f)
                                scaleX = dragScale
                                scaleY = dragScale
                                alpha = state.topCardAlpha
                            } else {
                                transformOrigin = TransformOrigin(0.5f, 0.5f)
                                val p = state.stackProgress
                                val currentScale = androidx.compose.ui.util.lerp(idleScale, promotedScale, p)
                                val currentOffsetY = lerp(idleOffset, promotedOffset, p).toPx()
                                val currentRotation = androidx.compose.ui.util.lerp(idleRotation, promotedRotation, p)
                                val currentStartInset = lerp(idleStartInset, promotedStartInset, p)
                                val currentEndInset = lerp(idleEndInset, promotedEndInset, p)
                                val currentTranslationX = ((currentStartInset - currentEndInset) / 2).toPx()

                                scaleX = currentScale
                                scaleY = 1f
                                translationX = currentTranslationX
                                translationY = currentOffsetY
                                rotationZ = currentRotation
                                alpha = if (isIncomingStackCard) p else 1f
                            }
                        }
                        .then(gestureModifier),
                ) {
                    CompositionLocalProvider(
                        LocalSharedSwipeItemIndex provides index,
                        LocalSharedSwipeCardShape provides currentCardShape,
                    ) {
                        if (isTopCard || isSecondCard) {
                            SharedFeedPostCard(
                                post = item.post,
                                recommendationReason = item.recommendationReason,
                                hapticFeedback = hapticFeedback,
                                onProfileClick = onProfileClick,
                                onProfileTagClick = onProfileTagClick,
                                onPostLinkClick = onPostLinkClick,
                                onCommentsClick = onCommentsClick,
                                onMoreClick = onPostMenuClick,
                                onOpenImageOverlay = { launchData -> onOpenImageOverlay(item.post, launchData) },
                                onOverlaySourceSnapshot = { launchData -> onOverlaySourceSnapshot(item.post, launchData) },
                                isViewerActive = isViewerActive,
                                isOverlayActive = activeOverlayPostId == item.post.id,
                                overlayDetachedCount = if (activeOverlayPostId == item.post.id) overlayDetachedCount else 0,
                                onNonSwipeBottomInsetChanged = { insetPx -> nonSwipeBottomInsetPx = insetPx.toFloat() },
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .clip(currentCardShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                    if (isTopCard) {
                        SharedSwipeOverlayLayer(state, config)
                    } else {
                        val p = state.stackProgress
                        val overlayAlpha = if (isIncomingStackCard) {
                            promotedOverlayAlpha * p
                        } else {
                            androidx.compose.ui.util.lerp(baseOverlayAlpha, promotedOverlayAlpha, p)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(currentCardShape)
                                .graphicsLayer {
                                    alpha = overlayAlpha
                                }
                                .background(Color.Black),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSwipeOverlayLayer(
    state: FeedSwipeCardState,
    config: FeedSwipeConfig,
) {
    if (!state.isDragging && !state.isDismissing) return
    val direction = state.currentDirection() ?: return
    val progressProvider = {
        val width = state.containerSize.width.toFloat().takeIf { it > 0f } ?: 1f
        val height = state.containerSize.height.toFloat().takeIf { it > 0f } ?: 1f
        if (state.isDismissing) {
            state.topCardAlpha
        } else {
            val rawProgress = when (direction) {
                SwipeDirection.Left, SwipeDirection.Right -> abs(state.dragOffsetX) / (width * config.swipeThreshold)
                SwipeDirection.Up -> abs(state.dragOffsetY) / (height * config.swipeThresholdUp)
                SwipeDirection.Down -> abs(state.dragOffsetY) / (height * config.swipeThresholdDown)
            }.coerceIn(0f, 1f)
            1f - (1f - rawProgress) * (1f - rawProgress)
        }
    }
    SharedSwipeOverlayContent(direction, progressProvider)
}

@Composable
internal fun SharedSwipeOverlayContent(direction: SwipeDirection, progressProvider: () -> Float) {
    Box(Modifier.fillMaxSize()) {
        when (direction) {
            SwipeDirection.Left -> SwipeIndicator(painterResource(Res.drawable.nav_back_icon), Color(0xFF6B7280), Alignment.CenterEnd, progressProvider, (-32).dp, 0.dp)
            SwipeDirection.Right -> SwipeIndicator(painterResource(Res.drawable.heart_icon), Color(0xFFFF2D55), Alignment.CenterStart, progressProvider, 32.dp, 0.dp)
            SwipeDirection.Up -> SwipeIndicator(painterResource(Res.drawable.plus_icon), Color(0xFF2563EB), Alignment.TopCenter, progressProvider, 0.dp, 60.dp)
            SwipeDirection.Down -> SwipeIndicator(painterResource(Res.drawable.reply_icon), Color(0xFF7C3AED), Alignment.BottomCenter, progressProvider, 0.dp, (-60).dp)
        }
    }
}

@Composable
private fun BoxScope.SwipeIndicator(
    painter: Painter,
    color: Color,
    alignment: Alignment,
    progressProvider: () -> Float,
    offsetX: Dp,
    offsetY: Dp,
) {
    Box(
        modifier = Modifier
            .size(86.dp)
            .align(alignment)
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer {
                val progress = progressProvider()
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
        Icon(painter = painter, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}

@Composable
internal fun SharedFeedPostCardSkeletonStack(
    modifier: Modifier = Modifier,
    config: FeedSwipeConfig = DefaultFeedSwipeConfig,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        val stackBottomInset = config.stackCardOffsets
            .take(config.maxVisibleItems - 1)
            .maxOrNull()
            ?: 0.dp

        val backgroundCardHeight = if (maxHeight > stackBottomInset) {
            maxHeight - stackBottomInset + SharedFeedStackBottomBleed
        } else {
            0.dp
        }
        val topCardHeight = if (maxHeight > stackBottomInset) {
            maxHeight - (stackBottomInset - 16.dp - SharedFeedTopCardExtraHeight).coerceAtLeast(0.dp)
        } else {
            0.dp
        }
        val density = LocalDensity.current
        val secondCardOffsetPx = with(density) { (config.stackCardOffsets.getOrNull(0) ?: 48.dp).toPx() }
        val thirdCardOffsetPx = with(density) { (config.stackCardOffsets.getOrNull(1) ?: 88.dp).toPx() }
        val secondCardRotation = config.stackCardRotations.getOrNull(0) ?: -1.1f
        val thirdCardRotation = config.stackCardRotations.getOrNull(1) ?: 0f
        val secondCardScale = config.stackCardScales.getOrNull(0) ?: 0.90f
        val thirdCardScale = config.stackCardScales.getOrNull(1) ?: 0.90f
        val secondCardStartInset = config.stackCardStartInsets.getOrNull(0) ?: 4.dp
        val secondCardEndInset = config.stackCardEndInsets.getOrNull(0) ?: 12.dp
        val thirdCardStartInset = config.stackCardStartInsets.getOrNull(1) ?: 4.dp
        val thirdCardEndInset = config.stackCardEndInsets.getOrNull(1) ?: 0.dp

        SharedFeedPostCardSkeletonShell(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = thirdCardStartInset, end = thirdCardEndInset)
                .height(backgroundCardHeight)
                .graphicsLayer {
                    translationY = thirdCardOffsetPx
                    scaleX = thirdCardScale
                    scaleY = 1f
                    rotationZ = thirdCardRotation
                },
            shape = SharedStackCardShape,
            overlayAlpha = config.stackCardAlphas.getOrNull(1) ?: 0.08f,
        )
        SharedFeedPostCardSkeletonShell(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = secondCardStartInset, end = secondCardEndInset)
                .height(backgroundCardHeight + SharedFeedSecondCardExtraHeight)
                .graphicsLayer {
                    translationY = secondCardOffsetPx
                    scaleX = secondCardScale
                    scaleY = 1f
                    rotationZ = secondCardRotation
                },
            shape = SharedStackCardShape,
            overlayAlpha = config.stackCardAlphas.getOrNull(0) ?: 0.12f,
        )
        SharedFeedPostCardSkeleton(
            modifier = Modifier
                .fillMaxWidth()
                .height(topCardHeight),
        )
    }
}

@Composable
private fun SharedFeedPostCardSkeletonShell(
    modifier: Modifier,
    shape: Shape,
    overlayAlpha: Float,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = overlayAlpha)))
    }
}

@Composable
private fun SharedFeedPostCardSkeleton(
    modifier: Modifier = Modifier,
) {
    val cardShape = SharedPostCardShape
    val colors = sharedFeedTopCardColors()

    Card(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val shimmerBrush = rememberSharedFeedPostSkeletonShimmerBrush(maxWidth, maxHeight)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(shimmerBrush),
            )
        }
    }
}

@Composable
private fun rememberSharedFeedPostSkeletonShimmerBrush(
    width: Dp,
    height: Dp,
): Brush {
    val transition = rememberInfiniteTransition(label = "shared_feed_post_skeleton_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
        ),
        label = "shared_feed_post_skeleton_shimmer_progress",
    )
    val density = LocalDensity.current
    val widthPx = with(density) { width.toPx() }.coerceAtLeast(1f)
    val heightPx = with(density) { height.toPx() }.coerceAtLeast(1f)
    val travel = widthPx + heightPx
    val offset = travel * progress
    val shimmer = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val shimmerTransparent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.01f)
    return Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            shimmerTransparent,
            shimmer,
            shimmerTransparent,
            Color.Transparent,
        ),
        start = Offset(x = offset - travel, y = 0f),
        end = Offset(x = offset, y = heightPx),
    )
}
