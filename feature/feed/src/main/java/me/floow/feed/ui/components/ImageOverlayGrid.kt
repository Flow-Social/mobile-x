package me.floow.feed.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.floow.domain.models.Post
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.resolvedImageVariants
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import kotlin.math.roundToInt

private const val MAX_CARDS = 4
private const val STAGGER_MS = 50L
private const val CLOSE_STAGGER_MS = 80L
private const val HINT_APPEAR_DELAY_MS = 600L

private class CardMotionState {
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)
    val scaleX = Animatable(0.2f)
    val scaleY = Animatable(0.2f)
    val alpha = Animatable(0f)
    val rotation = Animatable(0f)
}

private enum class OverlayScenePhase {
    Opening,
    Opened,
    Closing,
    Transfer
}

@Composable
internal fun ImageOverlayGrid(
    post: Post,
    startRect: Rect?,
    launchData: OverlayLaunchData? = null,
    visible: Boolean,
    onDismiss: () -> Unit,
    onImageClick: (Int, SharedImageOrigin?) -> Unit = { _, _ -> },
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit = { _, _ -> },
    onDetachedCountChange: (Int) -> Unit = {},
    onClosedAnimationEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val variants = remember(post) {
        post.content
            .resolvedImageVariants()
            .filter { variant ->
                !variant.previewUrl.isNullOrBlank() ||
                    !variant.lqUrl.isNullOrBlank() ||
                    !variant.fullUrl.isNullOrBlank()
            }
            .take(MAX_CARDS)
    }
    if (variants.isEmpty()) return

    val motions = remember(post.id) { List(MAX_CARDS) { CardMotionState() } }
    val backgroundAlpha = remember { Animatable(0f) }
    var detachedCount by remember(post.id) { mutableStateOf(0) }
    var sourceDetachCount by remember(post.id) { mutableStateOf(0) }
    var phase by remember(post.id) { mutableStateOf(OverlayScenePhase.Opening) }
    var showCloseHint by remember(post.id) { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        LaunchedEffect(visible, phase) {
            if (visible && phase == OverlayScenePhase.Opening) {
                showCloseHint = false
                delay(HINT_APPEAR_DELAY_MS)
                if (visible && (phase == OverlayScenePhase.Opening || phase == OverlayScenePhase.Opened)) {
                    showCloseHint = true
                }
            } else if (!visible || phase == OverlayScenePhase.Closing || phase == OverlayScenePhase.Transfer) {
                showCloseHint = false
            }
        }

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val gridGap = 10.dp
        val horizontalPadding = 22.dp
        val cardWidth = (((maxWidth - horizontalPadding * 2) - gridGap) / 2f)
            .coerceIn(120.dp, 190.dp)
        val cardHeight = cardWidth * (230f / 180f)
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val gapPx = with(density) { gridGap.toPx() }

        val gridWidthPx = cardWidthPx * 2f + gapPx
        val gridHeightPx = cardHeightPx * 2f + gapPx
        val gridLeftPx = (screenWidthPx - gridWidthPx) / 2f
        val gridTopPx = ((screenHeightPx - gridHeightPx) / 2f - with(density) { 20.dp.toPx() })
            .coerceAtLeast(with(density) { 72.dp.toPx() })

        val targetOffsets = remember(gridLeftPx, gridTopPx, gapPx, cardWidthPx, cardHeightPx) {
            listOf(
                Offset(gridLeftPx, gridTopPx),
                Offset(gridLeftPx + cardWidthPx + gapPx, gridTopPx),
                Offset(gridLeftPx, gridTopPx + cardHeightPx + gapPx),
                Offset(gridLeftPx + cardWidthPx + gapPx, gridTopPx + cardHeightPx + gapPx)
            )
        }

        val validStartRect = startRect?.takeIf { it.width > 1f && it.height > 1f }
        val anchorCenter = validStartRect?.center
            ?: Offset(screenWidthPx / 2f, screenHeightPx * 0.78f)
        val anchorTopLeft = Offset(
            x = anchorCenter.x - cardWidthPx / 2f,
            y = anchorCenter.y - cardHeightPx / 2f
        )
        val anchorScale = if (validStartRect != null && cardWidthPx > 0f) {
            (validStartRect.width / cardWidthPx).coerceIn(0.2f, 0.95f)
        } else {
            0.28f
        }
        val activeCount = variants.size
        val sourceRects = launchData?.cardRects ?: emptyList()
        val sourcePoses = launchData?.cardPoses ?: emptyList()
        val startScaleX = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null && cardWidthPx > 0f) {
                (pose.width / cardWidthPx).coerceIn(0.2f, 1.2f)
            } else {
            val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
            if (rect != null && cardWidthPx > 0f) {
                (rect.width / cardWidthPx).coerceIn(0.2f, 1.1f)
            } else {
                anchorScale
            }
            }
        }
        val startScaleY = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null && cardHeightPx > 0f) {
                (pose.height / cardHeightPx).coerceIn(0.2f, 1.2f)
            } else {
            val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
            if (rect != null && cardHeightPx > 0f) {
                (rect.height / cardHeightPx).coerceIn(0.2f, 1.2f)
            } else {
                anchorScale
            }
            }
        }
        val startOffsets = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null) {
                Offset(
                    x = pose.centerX - (cardWidthPx * startScaleX[index]) / 2f,
                    y = pose.centerY - (cardHeightPx * startScaleY[index]) / 2f
                )
            } else {
            val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
            if (rect != null) {
                Offset(
                    x = rect.center.x - (cardWidthPx * startScaleX[index]) / 2f,
                    y = rect.center.y - (cardHeightPx * startScaleY[index]) / 2f
                )
            } else {
                anchorTopLeft
            }
            }
        }
        val startRotations = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null) {
                pose.rotation
            } else {
                val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
                if (rect != null) {
                when (index) {
                    1 -> -10f
                    2 -> 10f
                    else -> 0f
                }
            } else {
                0f
            }
            }
        }

        LaunchedEffect(visible, post.id) {
            if (!visible && (phase == OverlayScenePhase.Closing || phase == OverlayScenePhase.Transfer)) {
                return@LaunchedEffect
            }

            val offsetSpec = spring<Float>(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
            val floatSpec = spring<Float>(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
            val closeAlphaSpec = tween<Float>(
                durationMillis = 180,
                easing = FastOutSlowInEasing
            )
            val closeMoveSpec = tween<Float>(
                durationMillis = 170,
                easing = FastOutSlowInEasing
            )
            val alphaSpec = tween<Float>(
                durationMillis = 140,
                easing = FastOutSlowInEasing
            )
            val rotationTargets = floatArrayOf(0f, -4f, 4f, 0f)
            val visibleAlphas = floatArrayOf(1f, 1f, 1f, 1f)
            val scrimSpec = tween<Float>(
                durationMillis = 140,
                easing = FastOutSlowInEasing
            )

            if (visible) {
                phase = OverlayScenePhase.Opening
                detachedCount = if (activeCount > 0) 1 else 0
                sourceDetachCount = detachedCount
                onDetachedCountChange(sourceDetachCount)
                backgroundAlpha.snapTo(0f)

                repeat(activeCount) { index ->
                    val state = motions[index]
                    state.offsetX.snapTo(startOffsets[index].x)
                    state.offsetY.snapTo(startOffsets[index].y)
                    state.scaleX.snapTo(startScaleX[index])
                    state.scaleY.snapTo(startScaleY[index])
                    state.alpha.snapTo(if (index == 0) 1f else 0f)
                    state.rotation.snapTo(startRotations[index])
                }

                launch {
                    backgroundAlpha.animateTo(
                        targetValue = 0.68f,
                        animationSpec = scrimSpec
                    )
                }

                val openJobs = (0 until activeCount).map { index ->
                    launch {
                        delay(index * STAGGER_MS)
                        if (index > 0) {
                            detachedCount = (index + 1).coerceAtMost(activeCount)
                            sourceDetachCount = detachedCount
                            onDetachedCountChange(sourceDetachCount)
                        }
                        val cardJobs = listOf(
                            launch { motions[index].offsetX.animateTo(targetOffsets[index].x, offsetSpec) },
                            launch { motions[index].offsetY.animateTo(targetOffsets[index].y, offsetSpec) },
                            launch { motions[index].scaleX.animateTo(1f, floatSpec) },
                            launch { motions[index].scaleY.animateTo(1f, floatSpec) },
                            launch { motions[index].rotation.animateTo(rotationTargets[index], floatSpec) },
                            launch { motions[index].alpha.animateTo(visibleAlphas[index], alphaSpec) }
                        )
                        cardJobs.joinAll()
                    }
                }
                openJobs.joinAll()
                phase = OverlayScenePhase.Opened
            } else {
                phase = OverlayScenePhase.Closing
                detachedCount = activeCount
                sourceDetachCount = activeCount
                onDetachedCountChange(sourceDetachCount)
                val scrimJob = launch {
                    backgroundAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
                    )
                }

                val closeJobs = (0 until activeCount).map { reverseIndex ->
                    val index = activeCount - 1 - reverseIndex
                    launch {
                        delay(reverseIndex * CLOSE_STAGGER_MS)
                        sourceDetachCount = index
                        onDetachedCountChange(sourceDetachCount)
                        withFrameNanos { }
                        val cardJobs = listOf(
                            launch { motions[index].offsetX.animateTo(startOffsets[index].x, closeMoveSpec) },
                            launch { motions[index].offsetY.animateTo(startOffsets[index].y, closeMoveSpec) },
                            launch { motions[index].scaleX.animateTo(startScaleX[index], closeMoveSpec) },
                            launch { motions[index].scaleY.animateTo(startScaleY[index], closeMoveSpec) },
                            launch { motions[index].rotation.animateTo(startRotations[index], closeMoveSpec) },
                            launch { motions[index].alpha.animateTo(0f, closeAlphaSpec) }
                        )
                        cardJobs.joinAll()
                        motions[index].offsetX.snapTo(startOffsets[index].x)
                        motions[index].offsetY.snapTo(startOffsets[index].y)
                        motions[index].scaleX.snapTo(startScaleX[index])
                        motions[index].scaleY.snapTo(startScaleY[index])
                        motions[index].rotation.snapTo(startRotations[index])
                        detachedCount = index
                    }
                }
                closeJobs.joinAll()
                repeat(activeCount) { index ->
                    motions[index].offsetX.snapTo(startOffsets[index].x)
                    motions[index].offsetY.snapTo(startOffsets[index].y)
                    motions[index].scaleX.snapTo(startScaleX[index])
                    motions[index].scaleY.snapTo(startScaleY[index])
                    motions[index].rotation.snapTo(startRotations[index])
                }
                phase = OverlayScenePhase.Transfer
                onClosedAnimationEnd()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .clickable(
                    enabled = visible &&
                        phase != OverlayScenePhase.Closing &&
                        phase != OverlayScenePhase.Transfer,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
                .background(Color.Black.copy(alpha = backgroundAlpha.value))
        )
        repeat(activeCount) { index ->
            if (index >= detachedCount) return@repeat
            val state = motions[index]
            val variant = variants[index]
            val movableContent = launchData?.cardContents?.getOrNull(index)
            OverlayCard(
                index = index,
                postId = post.id,
                variant = variant,
                movableContent = movableContent,
                state = state,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                clickable = visible &&
                    phase != OverlayScenePhase.Closing &&
                    phase != OverlayScenePhase.Transfer,
                onImageClick = onImageClick,
                onOriginChanged = onOriginChanged,
                z = (200 - index).toFloat()
            )
        }

        if (showCloseHint) {
            val closeHintY = with(density) { (gridTopPx + gridHeightPx + 14.dp.toPx()).toInt() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(300f)
            ) {
                Text(
                    text = "нажмите чтобы закрыть",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, closeHintY) }
                        .padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayCard(
    index: Int,
    postId: String,
    variant: PostImageVariant,
    movableContent: MovableCardContent? = null,
    state: CardMotionState,
    cardWidth: Dp,
    cardHeight: Dp,
    clickable: Boolean,
    onImageClick: (Int, SharedImageOrigin?) -> Unit,
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit,
    z: Float
) {
    var rectInWindow by remember(index) { mutableStateOf<Rect?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    val sourceKey = remember(postId, index) { "feed_overlay_${postId}_$index" }
    val aspectRatio = remember(cardWidth, cardHeight) {
        val h = cardHeight.value.coerceAtLeast(0.01f)
        cardWidth.value / h
    }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = state.offsetX.value.roundToInt(),
                    y = state.offsetY.value.roundToInt()
                )
            }
            .size(width = cardWidth, height = cardHeight)
            .zIndex(z)
            .graphicsLayer {
                scaleX = state.scaleX.value
                scaleY = state.scaleY.value
                alpha = state.alpha.value
                rotationZ = state.rotation.value
            }
            .clip(RoundedCornerShape(14.dp))
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                rectInWindow = rect
                onOriginChanged(
                    index,
                    SharedImageOrigin(
                        sourceKey = sourceKey,
                        rectInWindow = rect,
                        aspectRatio = aspectRatio
                    )
                )
            }
            .clickable(
                enabled = clickable,
                interactionSource = interactionSource,
                indication = null
            ) {
                onImageClick(
                    index,
                    rectInWindow?.let { rect ->
                        SharedImageOrigin(
                            sourceKey = sourceKey,
                            rectInWindow = rect,
                            aspectRatio = aspectRatio
                        )
                    }
                )
            }
    ) {
        if (movableContent != null) {
            movableContent()
        } else {
            ProgressiveImage(
                lqUrl = variant.lqUrl,
                previewUrl = variant.previewUrl,
                fullUrl = variant.fullUrl,
                mode = ProgressiveImageMode.LIST,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
