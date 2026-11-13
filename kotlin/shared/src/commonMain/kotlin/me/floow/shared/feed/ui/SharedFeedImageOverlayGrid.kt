package me.floow.shared.feed.ui

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
import androidx.compose.ui.graphics.painter.Painter
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
import me.floow.shared.chats.ui.PlatformBackHandler
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.floow.domain.models.Post
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.resolvedImageVariants
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.SourceImageScaleMode
import kotlin.math.roundToInt

private const val MAX_CARDS = 4
private const val STAGGER_MS = 50L
private const val CLOSE_STAGGER_MS = 80L
private const val HINT_APPEAR_DELAY_MS = 600L

private class SharedFeedCardMotionState {
    val offsetX = Animatable(0f)
    val offsetY = Animatable(0f)
    val scaleX = Animatable(0.2f)
    val scaleY = Animatable(0.2f)
    val rotation = Animatable(0f)
    val alpha = Animatable(0f)
}

@Composable
internal fun SharedFeedImageOverlayGrid(
    post: Post,
    startRect: Rect?,
    launchData: SharedFeedOverlayLaunchData? = null,
    visible: Boolean,
    isViewerActive: Boolean = false,
    onDismiss: () -> Unit,
    onImageClick: (Int, SharedImageOrigin?, Painter?) -> Unit = { _, _, _ -> },
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit = { _, _ -> },
    onDetachedCountChange: (Int) -> Unit = {},
    onPhaseChanged: (SharedFeedOverlayPhase) -> Unit = {},
    hiddenSourceKey: String? = null,
    hiddenSourceRevealProgress: Float = 0f,
    onClosedAnimationEnd: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    PlatformBackHandler(enabled = visible && !isViewerActive) {
        onDismiss()
    }
    val haptic = LocalHapticFeedback.current
    val variants = remember(post) {
        post.content
            .resolvedImageVariants()
            .filter { variant ->
                !variant.thumbUrl.isNullOrBlank() ||
                    !variant.fullUrl.isNullOrBlank()
            }
            .take(MAX_CARDS)
    }
    if (variants.isEmpty()) return
    val activeCount = variants.size
    val isSingleImage = activeCount == 1

    val motions = remember(post.id) { List(MAX_CARDS) { SharedFeedCardMotionState() } }
    val backgroundAlpha = remember { Animatable(0f) }
    var detachedCount by remember(post.id) { mutableStateOf(0) }
    var sourceDetachCount by remember(post.id) { mutableStateOf(0) }
    var phase by remember(post.id) { mutableStateOf(SharedFeedOverlayPhase.Opening) }
    var showCloseHint by remember(post.id) { mutableStateOf(false) }

    fun publishPhase(newPhase: SharedFeedOverlayPhase) {
        phase = newPhase
        onPhaseChanged(newPhase)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LaunchedEffect(visible, phase) {
            if (visible && phase == SharedFeedOverlayPhase.Opening) {
                showCloseHint = false
                delay(HINT_APPEAR_DELAY_MS)
                if (visible && (phase == SharedFeedOverlayPhase.Opening || phase == SharedFeedOverlayPhase.Opened)) {
                    showCloseHint = true
                }
            } else if (!visible || phase == SharedFeedOverlayPhase.Closing || phase == SharedFeedOverlayPhase.Transfer) {
                showCloseHint = false
            }
        }

        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val gridGap = 10.dp
        val horizontalPadding = 22.dp
        val cardWidth = if (isSingleImage) {
            (maxWidth - horizontalPadding * 2).coerceIn(180.dp, 320.dp)
        } else {
            (((maxWidth - horizontalPadding * 2) - gridGap) / 2f).coerceIn(120.dp, 190.dp)
        }
        val cardHeight = cardWidth * (230f / 180f)
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = with(density) { cardHeight.toPx() }
        val gapPx = with(density) { gridGap.toPx() }

        val gridWidthPx = if (isSingleImage) cardWidthPx else cardWidthPx * 2f + gapPx
        val gridHeightPx = if (isSingleImage) cardHeightPx else cardHeightPx * 2f + gapPx
        val gridLeftPx = (screenWidthPx - gridWidthPx) / 2f
        val gridTopPx = ((screenHeightPx - gridHeightPx) / 2f - with(density) { 20.dp.toPx() })
            .coerceAtLeast(with(density) { 72.dp.toPx() })

        val targetOffsets = remember(
            isSingleImage,
            gridLeftPx,
            gridTopPx,
            gapPx,
            cardWidthPx,
            cardHeightPx,
        ) {
            if (isSingleImage) {
                listOf(Offset(gridLeftPx, gridTopPx))
            } else {
                listOf(
                    Offset(gridLeftPx, gridTopPx),
                    Offset(gridLeftPx + cardWidthPx + gapPx, gridTopPx),
                    Offset(gridLeftPx, gridTopPx + cardHeightPx + gapPx),
                    Offset(gridLeftPx + cardWidthPx + gapPx, gridTopPx + cardHeightPx + gapPx),
                )
            }
        }

        val validStartRect = startRect?.takeIf { it.width > 1f && it.height > 1f }
        val anchorCenter = validStartRect?.center ?: Offset(screenWidthPx / 2f, screenHeightPx * 0.78f)
        val anchorTopLeft = Offset(
            x = anchorCenter.x - cardWidthPx / 2f,
            y = anchorCenter.y - cardHeightPx / 2f,
        )
        val anchorScale = if (validStartRect != null && cardWidthPx > 0f) {
            (validStartRect.width / cardWidthPx).coerceIn(0.2f, 0.95f)
        } else {
            0.28f
        }
        val sourceRects = launchData?.cardRects.orEmpty()
        val sourcePoses = launchData?.cardPoses.orEmpty()
        val startScaleX = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null && cardWidthPx > 0f) {
                (pose.width / cardWidthPx).coerceIn(0.2f, 1.2f)
            } else {
                val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
                if (rect != null && cardWidthPx > 0f) (rect.width / cardWidthPx).coerceIn(0.2f, 1.1f) else anchorScale
            }
        }
        val startScaleY = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null && cardHeightPx > 0f) {
                (pose.height / cardHeightPx).coerceIn(0.2f, 1.2f)
            } else {
                val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
                if (rect != null && cardHeightPx > 0f) (rect.height / cardHeightPx).coerceIn(0.2f, 1.2f) else anchorScale
            }
        }
        val startOffsets = List(activeCount) { index ->
            val pose = sourcePoses.getOrNull(index)
            if (pose != null) {
                Offset(
                    x = pose.centerX - (cardWidthPx * startScaleX[index]) / 2f,
                    y = pose.centerY - (cardHeightPx * startScaleY[index]) / 2f,
                )
            } else {
                val rect = sourceRects.getOrNull(index)?.takeIf { it.width > 1f && it.height > 1f }
                if (rect != null) {
                    Offset(
                        x = rect.center.x - (cardWidthPx * startScaleX[index]) / 2f,
                        y = rect.center.y - (cardHeightPx * startScaleY[index]) / 2f,
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
                when (index) {
                    1 -> -10f
                    2 -> 10f
                    else -> 0f
                }
            }
        }

        LaunchedEffect(visible, post.id) {
            if (!visible && (phase == SharedFeedOverlayPhase.Closing || phase == SharedFeedOverlayPhase.Transfer)) {
                return@LaunchedEffect
            }

            val offsetSpec = spring<Float>(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            )
            val floatSpec = spring<Float>(
                stiffness = Spring.StiffnessLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            )
            val closeAlphaSpec = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
            val closeMoveSpec = tween<Float>(durationMillis = 170, easing = FastOutSlowInEasing)
            val alphaSpec = tween<Float>(durationMillis = 140, easing = FastOutSlowInEasing)
            val rotationTargets = floatArrayOf(0f, -4f, 4f, 0f)
            val visibleAlphas = floatArrayOf(1f, 1f, 1f, 1f)
            val scrimOpenSpec = tween<Float>(durationMillis = 320, easing = FastOutSlowInEasing)
            val scrimCloseSpec = tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing)

            if (visible) {
                publishPhase(SharedFeedOverlayPhase.Opening)
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
                    backgroundAlpha.animateTo(targetValue = 0.68f, animationSpec = scrimOpenSpec)
                }

                val openJobs = (0 until activeCount).map { index ->
                    launch {
                        delay(index * STAGGER_MS)
                        if (index > 0) {
                            detachedCount = (index + 1).coerceAtMost(activeCount)
                            sourceDetachCount = detachedCount
                            onDetachedCountChange(sourceDetachCount)
                        }
                        listOf(
                            launch { motions[index].offsetX.animateTo(targetOffsets[index].x, offsetSpec) },
                            launch { motions[index].offsetY.animateTo(targetOffsets[index].y, offsetSpec) },
                            launch { motions[index].scaleX.animateTo(1f, floatSpec) },
                            launch { motions[index].scaleY.animateTo(1f, floatSpec) },
                            launch { motions[index].rotation.animateTo(rotationTargets[index], floatSpec) },
                            launch { motions[index].alpha.animateTo(visibleAlphas[index], alphaSpec) },
                        ).joinAll()
                    }
                }
                openJobs.joinAll()
                publishPhase(SharedFeedOverlayPhase.Opened)
            } else {
                publishPhase(SharedFeedOverlayPhase.Closing)
                detachedCount = activeCount
                sourceDetachCount = activeCount
                onDetachedCountChange(sourceDetachCount)
                val scrimJob = launch {
                    backgroundAlpha.animateTo(0f, scrimCloseSpec)
                }

                val closeJobs = (0 until activeCount).map { reverseIndex ->
                    val index = activeCount - 1 - reverseIndex
                    launch {
                        delay(reverseIndex * CLOSE_STAGGER_MS)
                        sourceDetachCount = index
                        onDetachedCountChange(sourceDetachCount)
                        withFrameNanos { }
                        listOf(
                            launch { motions[index].offsetX.animateTo(startOffsets[index].x, closeMoveSpec) },
                            launch { motions[index].offsetY.animateTo(startOffsets[index].y, closeMoveSpec) },
                            launch { motions[index].scaleX.animateTo(startScaleX[index], closeMoveSpec) },
                            launch { motions[index].scaleY.animateTo(startScaleY[index], closeMoveSpec) },
                            launch { motions[index].rotation.animateTo(startRotations[index], closeMoveSpec) },
                            launch { motions[index].alpha.animateTo(0f, closeAlphaSpec) },
                        ).joinAll()
                        motions[index].offsetX.snapTo(startOffsets[index].x)
                        motions[index].offsetY.snapTo(startOffsets[index].y)
                        motions[index].scaleX.snapTo(startScaleX[index])
                        motions[index].scaleY.snapTo(startScaleY[index])
                        motions[index].rotation.snapTo(startRotations[index])
                        detachedCount = index
                    }
                }
                closeJobs.joinAll()
                scrimJob.join()
                publishPhase(SharedFeedOverlayPhase.Transfer)
                onClosedAnimationEnd()
            }
        }

        var containerBoundsInWindow by remember(post.id) { mutableStateOf(Rect.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { containerBoundsInWindow = it.boundsInWindow() }
                .zIndex(0f)
                .clickable(
                    enabled = visible &&
                        !isViewerActive &&
                        phase != SharedFeedOverlayPhase.Closing &&
                        phase != SharedFeedOverlayPhase.Transfer,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDismiss()
                }
                .background(Color.Black.copy(alpha = backgroundAlpha.value)),
        )

        repeat(activeCount) { index ->
            if (index >= detachedCount) return@repeat
            val state = motions[index]
            val variant = variants[index]
            val movableContent = launchData?.cardContents?.getOrNull(index)
            val fallbackPainter = launchData?.cardPainters?.getOrNull(index)
            SharedFeedOverlayCard(
                index = index,
                postId = post.id,
                variant = variant,
                movableContent = movableContent,
                fallbackPainter = fallbackPainter,
                state = state,
                cardWidth = cardWidth,
                cardHeight = cardHeight,
                clickable = visible &&
                    !isViewerActive &&
                    phase != SharedFeedOverlayPhase.Closing &&
                    phase != SharedFeedOverlayPhase.Transfer,
                onImageClick = onImageClick,
                onOriginChanged = onOriginChanged,
                hiddenSourceKey = hiddenSourceKey,
                hiddenSourceRevealProgress = hiddenSourceRevealProgress,
                containerBoundsInWindow = containerBoundsInWindow,
                z = (200 - index).toFloat(),
            )
        }

        if (showCloseHint && !isViewerActive) {
            val closeHintY = with(density) { (gridTopPx + gridHeightPx + 14.dp.toPx()).toInt() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(300f),
            ) {
                Text(
                    text = "нажмите чтобы закрыть",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, closeHintY) }
                        .padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SharedFeedOverlayCard(
    index: Int,
    postId: String,
    variant: PostImageVariant,
    movableContent: SharedFeedMovableCardContent? = null,
    fallbackPainter: Painter? = null,
    state: SharedFeedCardMotionState,
    cardWidth: Dp,
    cardHeight: Dp,
    clickable: Boolean,
    onImageClick: (Int, SharedImageOrigin?, Painter?) -> Unit,
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit,
    hiddenSourceKey: String?,
    hiddenSourceRevealProgress: Float,
    containerBoundsInWindow: Rect,
    z: Float,
) {
    var sourcePainter by remember(index) { mutableStateOf<Painter?>(fallbackPainter) }
    val interactionSource = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val sourceKey = remember(postId, index) { "feed_overlay_${postId}_$index" }
    val sourceVisibility = if (hiddenSourceKey == sourceKey) {
        hiddenSourceRevealProgress.coerceIn(0f, 1f)
    } else {
        1f
    }
    LaunchedEffect(fallbackPainter) {
        if (fallbackPainter != null) {
            sourcePainter = fallbackPainter
        }
    }
    val aspectRatio = remember(cardWidth, cardHeight, variant.width, variant.height) {
        val variantAspect = variant.width?.takeIf { it > 0 }?.let { width ->
            variant.height?.takeIf { it > 0 }?.let { height ->
                width.toFloat() / height.toFloat()
            }
        }
        variantAspect?.coerceAtLeast(0.01f) ?: (cardWidth.value / cardHeight.value.coerceAtLeast(0.01f))
    }
    val sourceAspectRatio = resolvePainterAspectRatioOrNull(sourcePainter ?: fallbackPainter)
        ?: aspectRatio
    val cardWidthPx = with(density) { cardWidth.toPx() }
    val cardHeightPx = with(density) { cardHeight.toPx() }
    val logicalRectInWindow = remember(
        containerBoundsInWindow,
        state.offsetX.value,
        state.offsetY.value,
        state.scaleX.value,
        state.scaleY.value,
        cardWidthPx,
        cardHeightPx,
    ) {
        val scaleX = state.scaleX.value.coerceAtLeast(0.01f)
        val scaleY = state.scaleY.value.coerceAtLeast(0.01f)
        val left = containerBoundsInWindow.left +
            state.offsetX.value +
            (cardWidthPx - cardWidthPx * scaleX) / 2f
        val top = containerBoundsInWindow.top +
            state.offsetY.value +
            (cardHeightPx - cardHeightPx * scaleY) / 2f
        Rect(
            left = left,
            top = top,
            right = left + cardWidthPx * scaleX,
            bottom = top + cardHeightPx * scaleY,
        )
    }
    val origin = remember(sourceKey, logicalRectInWindow, sourceAspectRatio, density) {
        SharedImageOrigin(
            sourceKey = sourceKey,
            rectInWindow = logicalRectInWindow,
            aspectRatio = sourceAspectRatio,
            contentRectInWindow = logicalRectInWindow,
            cornerRadiusPx = with(density) { 14.dp.toPx() },
            rotationZ = state.rotation.targetValue,
            sourceScaleMode = SourceImageScaleMode.Crop,
        )
    }

    LaunchedEffect(index, origin) {
        onOriginChanged(index, origin)
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(state.offsetX.value.roundToInt(), state.offsetY.value.roundToInt()) }
            .size(width = cardWidth, height = cardHeight)
            .zIndex(z)
            .graphicsLayer {
                scaleX = state.scaleX.value
                scaleY = state.scaleY.value
                alpha = state.alpha.value * sourceVisibility
                rotationZ = state.rotation.value
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                enabled = clickable && sourceVisibility > 0.99f,
                interactionSource = interactionSource,
                indication = null,
            ) {
                onImageClick(
                    index,
                    origin.copy(rotationZ = state.rotation.value),
                    sourcePainter ?: fallbackPainter,
                )
            },
    ) {
        if (movableContent != null) {
            movableContent()
        } else {
            ProgressiveImage(
                thumbUrl = variant.thumbUrl,
                targetUrl = variant.targetUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onPainterChanged = { sourcePainter = it },
            )
        }
    }
}

private fun resolvePainterAspectRatioOrNull(painter: Painter?): Float? {
    val size = painter?.intrinsicSize ?: return null
    val width = size.width
    val height = size.height
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        (width / height).coerceAtLeast(0.01f)
    } else {
        null
    }
}
