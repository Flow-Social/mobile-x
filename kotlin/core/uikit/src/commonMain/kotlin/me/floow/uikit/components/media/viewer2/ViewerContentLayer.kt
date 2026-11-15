package me.floow.uikit.components.media.viewer2

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import me.floow.uikit.util.overlayHorizontalPagerSwipeZone

@Composable
fun ViewerContentLayer(
    model: FullscreenImageViewerModel,
    state: FullscreenImageViewerState,
    pagerState: PagerState,
    sceneProgress: Float,
    dismissProgress: Float,
    onAction: (FullscreenImageViewerAction) -> Unit,
    onRequestClose: (offsetOverride: Float?, velocityOverride: Float?) -> Unit,
    onPagePainterReady: (page: Int, painter: Painter) -> Unit,
    onPageFirstFrameReady: (page: Int) -> Unit,
    onPageDisplaySnapshot: (page: Int, painter: Painter, geometry: ViewerImageGeometry, aspectRatio: Float) -> Unit,
    fallbackPainterForPage: (Int) -> Painter?,
    targetAspectRatioForPage: (Int) -> Float?,
    gesturesEnabled: Boolean,
    sharedTransitionSpec: FullscreenImageViewerSharedTransitionSpec?,
    dismissOffsetYProvider: () -> Float,
    onDismissDrag: (Float) -> Unit,
    contentAlpha: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val scope = rememberCoroutineScope()
    val touchSlopPx = viewConfig.touchSlop
    val dismissThresholdPx = mediaTransitionDismissThresholdPx(density)
    val isClosing = state.phase == ViewerPhase.Closing
    var dismissReturnJob by remember { mutableStateOf<Job?>(null) }
    val pagerEnabled = gesturesEnabled && state.zoom <= 1.05f
    val pagerZoneKey = remember(state.page, model.images.size) {
        "viewer_pager_${state.page}_${model.images.size}"
    }

    val chromeTargetAlpha = when {
        state.phase == ViewerPhase.Opened && state.chromeVisible -> (1f - (dismissProgress / 0.25f)).coerceIn(0f, 1f)
        state.phase == ViewerPhase.Opening && state.chromeVisible -> ((sceneProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
        else -> 0f
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = chromeTargetAlpha,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "viewer_chrome_alpha"
    )
    val chromeTranslationY by animateFloatAsState(
        targetValue = if (state.phase == ViewerPhase.Opening) {
            -18f * (1f - ((sceneProgress - 0.72f) / 0.28f).coerceIn(0f, 1f))
        } else {
            -34f * (dismissProgress / 0.25f).coerceIn(0f, 1f)
        },
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "viewer_chrome_translation_y"
    )

    Box(
        modifier = modifier
            .verticalDismissGestureV2(
                enabled = gesturesEnabled && state.zoom <= 1.02f,
                touchSlopPx = touchSlopPx,
                thresholdPx = dismissThresholdPx,
                onDragOffset = { offset ->
                    dismissReturnJob?.cancel()
                    dismissReturnJob = null
                    onDismissDrag(offset)
                },
                onDismissRequest = { offset, velocity ->
                    dismissReturnJob?.cancel()
                    dismissReturnJob = null
                    onRequestClose(offset, velocity)
                },
                onCancel = { startOffset ->
                    dismissReturnJob?.cancel()
                    dismissReturnJob = scope.launch {
                        animate(
                            initialValue = startOffset,
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        ) { value, _ ->
                            onDismissDrag(value)
                        }
                        onDismissDrag(0f)
                        dismissReturnJob = null
                    }
                }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = pagerEnabled,
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_viewer_pager")
                .graphicsLayer {
                    alpha = contentAlpha
                }
                .overlayHorizontalPagerSwipeZone(
                    zoneKey = pagerZoneKey,
                    currentPage = pagerState.currentPage,
                    blockOverlayAtStart = true,
                )
        ) { page ->
            val fallbackPainter = fallbackPainterForPage(page)
            ZoomableViewerImageV2(
                imageUrl = model.images[page],
                phase = state.phase,
                resetKey = page,
                zoom = state.zoom,
                pan = state.pan,
                dismissOffsetYProvider = dismissOffsetYProvider,
                dismissThresholdPx = dismissThresholdPx,
                onAction = onAction,
                onPainterReady = { painter ->
                    if (abs(dismissOffsetYProvider()) <= 0.5f) {
                        onPagePainterReady(page, painter)
                    }
                },
                onFirstFrameReady = {
                    onPageFirstFrameReady(page)
                },
                onDisplaySnapshot = { painter, geometry, aspectRatio ->
                    onPageDisplaySnapshot(page, painter, geometry, aspectRatio)
                },
                fallbackPainter = fallbackPainter,
                targetAspectRatio = targetAspectRatioForPage(page) ?: model.aspectRatioForPage(page),
                gesturesEnabled = gesturesEnabled,
                modifier = Modifier.fillMaxSize()
            )
        }

        val isClosing = state.phase == ViewerPhase.Closing
        val imageCount = model.images.size
        if ((state.phase == ViewerPhase.Opened || state.phase == ViewerPhase.Opening || isClosing) && state.visible && chromeAlpha > 0.01f) {
            ViewerTopBar(
                title = model.title,
                subtitle = model.subtitleProvider(state.page.coerceIn(0, imageCount - 1)),
                countText = "${state.page + 1}/$imageCount",
                onBackClick = { onRequestClose(null, null) },
                onMenuClick = { onAction(FullscreenImageViewerAction.MenuClick) },
                backgroundColor = Color.Black.copy(alpha = 0.22f * sceneProgress),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val currentDismissOffsetY = dismissOffsetYProvider()
                        val dismissFade = (1f - (abs(currentDismissOffsetY) / (dismissThresholdPx * 0.45f))).coerceIn(0f, 1f)
                        val closeFade = if (isClosing) (1f - state.transitionProgress).coerceIn(0f, 1f) else 1f
                        alpha = chromeAlpha * dismissFade * closeFade
                        translationY = chromeTranslationY - (abs(currentDismissOffsetY) * 0.45f)
                    }
            )
        }
    }
}

@Composable
private fun ZoomableViewerImageV2(
    imageUrl: String,
    phase: ViewerPhase,
    resetKey: Int,
    zoom: Float,
    pan: Offset,
    dismissOffsetYProvider: () -> Float,
    dismissThresholdPx: Float,
    onAction: (FullscreenImageViewerAction) -> Unit,
    onPainterReady: (Painter) -> Unit,
    onFirstFrameReady: () -> Unit,
    onDisplaySnapshot: (Painter, ViewerImageGeometry, Float) -> Unit,
    fallbackPainter: Painter?,
    targetAspectRatio: Float?,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    val scope = rememberCoroutineScope()
    var doubleTapJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val urls = remember(imageUrl) { resolveViewerImageUrls(imageUrl) }
    var lqPainter by remember(imageUrl, resetKey) { mutableStateOf<Painter?>(null) }
    var previewPainter by remember(imageUrl, resetKey) { mutableStateOf<Painter?>(null) }
    var fullPainter by remember(imageUrl, resetKey) { mutableStateOf<Painter?>(null) }
    var lqSettled by remember(imageUrl, resetKey) { mutableStateOf(urls.lqUrl.isNullOrBlank()) }
    var previewSettled by remember(imageUrl, resetKey) { mutableStateOf(urls.previewUrl.isNullOrBlank()) }
    var lqReady by remember(imageUrl, resetKey) { mutableStateOf(false) }
    var previewReady by remember(imageUrl, resetKey) { mutableStateOf(false) }
    var fullReady by remember(imageUrl, resetKey) { mutableStateOf(false) }
    var firstFrameNotified by remember(imageUrl, resetKey) { mutableStateOf(false) }

    val lqAlpha by animateFloatAsState(
        targetValue = if (lqReady && !previewReady) 1f else 0f,
        animationSpec = if (fallbackPainter != null) snap() else tween(durationMillis = 120),
        label = "viewer_lq_alpha"
    )
    val previewAlpha by animateFloatAsState(
        targetValue = if (previewReady && !fullReady) 1f else 0f,
        animationSpec = if (fallbackPainter != null) snap() else tween(durationMillis = 140),
        label = "viewer_preview_alpha"
    )
    val fullAlpha by animateFloatAsState(
        targetValue = if (fullReady) 1f else 0f,
        animationSpec = if (fallbackPainter != null) snap() else tween(durationMillis = 140),
        label = "viewer_full_alpha"
    )

    LaunchedEffect(lqReady, previewReady, fullReady) {
        if ((lqReady || previewReady || fullReady) && !firstFrameNotified) {
            firstFrameNotified = true
            onFirstFrameReady()
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val targetWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val targetHeightPx = constraints.maxHeight.coerceAtLeast(1)
        fun buildSizedRequest(url: String?): ImageRequest? {
            if (url.isNullOrBlank()) return null
            return ImageRequest.Builder(context)
                .data(url)
                .size(targetWidthPx, targetHeightPx)
                .build()
        }

        val lqRequest = remember(urls.lqUrl, targetWidthPx, targetHeightPx) {
            buildSizedRequest(urls.lqUrl)
        }
        val previewRequest = remember(lqSettled, urls.previewUrl, targetWidthPx, targetHeightPx) {
            if (!lqSettled) return@remember null
            buildSizedRequest(urls.previewUrl)
        }
        val fullRequest = remember(previewSettled, urls.fullUrl, targetWidthPx, targetHeightPx) {
            if (!previewSettled) return@remember null
            buildSizedRequest(urls.fullUrl)
        }

        val lqAsyncPainter = rememberAsyncImagePainter(
            model = lqRequest,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        if (lqPainter != state.painter) {
                            lqPainter = state.painter
                            onPainterReady(state.painter)
                        }
                        lqReady = true
                        lqSettled = true
                    }

                    is AsyncImagePainter.State.Error -> {
                        lqSettled = true
                    }

                    else -> Unit
                }
            }
        )
        val previewAsyncPainter = rememberAsyncImagePainter(
            model = previewRequest,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Success -> {
                        if (previewPainter != state.painter) {
                            previewPainter = state.painter
                            onPainterReady(state.painter)
                        }
                        previewReady = true
                        previewSettled = true
                    }

                    is AsyncImagePainter.State.Error -> {
                        previewSettled = true
                    }

                    else -> Unit
                }
            }
        )
        val fullAsyncPainter = rememberAsyncImagePainter(
            model = fullRequest,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    if (fullPainter != state.painter) {
                        fullPainter = state.painter
                        onPainterReady(state.painter)
                    }
                    fullReady = true
                }
            }
        )
        val visiblePainter = when {
            fullReady -> fullPainter ?: fullAsyncPainter
            previewReady -> previewPainter ?: previewAsyncPainter
            lqReady -> lqPainter ?: lqAsyncPainter
            fallbackPainter != null -> fallbackPainter
            else -> null
        }
        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val displayPainter = visiblePainter
        val painterAspect = displayPainter?.let { painter ->
            val size = painter.intrinsicSize
            if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
                (size.width / size.height).coerceAtLeast(0.01f)
            } else null
        }
        val displayAspectRatio = painterAspect
            ?: targetAspectRatio?.coerceAtLeast(0.01f)
            ?: (containerWidthPx / containerHeightPx).coerceAtLeast(0.01f)
        val frozenTargetRect = computeFitRect(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            aspectRatio = displayAspectRatio
        )

        val gestureModifier = if (gesturesEnabled) {
            Modifier
                .pointerInput(resetKey) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var gestureScale = currentZoom
                        var gesturePan = currentPan
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) break
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            
                            // Only consume if we are actually zooming or already zoomed
                            if (pressedCount <= 1 && gestureScale == 1f && zoomChange == 1f) continue
                            
                            event.changes.forEach { if (it.pressed) it.consume() }

                            val newScale = (gestureScale * zoomChange).coerceIn(1f, 5f)
                            val rawOffset = if (newScale > 1f) gesturePan + panChange else Offset.Zero
                            val clampedOffset = clampOffset(
                                rawOffset = rawOffset,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx,
                                contentWidthPx = frozenTargetRect.width,
                                contentHeightPx = frozenTargetRect.height,
                                scale = newScale
                            )
                            gestureScale = newScale
                            gesturePan = clampedOffset
                            onAction(FullscreenImageViewerAction.ZoomChanged(newScale, clampedOffset))
                        }
                    }
                }
                .pointerInput(resetKey) {
                    detectTapGestures(
                        onTap = { onAction(FullscreenImageViewerAction.ToggleChrome) },
                        onDoubleTap = { tap ->
                            val startScale = currentZoom
                            val startPan = currentPan
                            val targetScale = if (startScale > 1.05f) 1f else 3.0f
                            val focus = tap - Offset(containerWidthPx / 2f, containerHeightPx / 2f)
                            val nextOffset = if (targetScale > 1f) {
                                startPan - focus * (targetScale / startScale - 1f)
                            } else {
                                Offset.Zero
                            }
                            val clamped = clampOffset(
                                rawOffset = nextOffset,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx,
                                contentWidthPx = frozenTargetRect.width,
                                contentHeightPx = frozenTargetRect.height,
                                scale = targetScale
                            )
                            doubleTapJob?.cancel()
                            doubleTapJob = scope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                ) { value, _ ->
                                    onAction(FullscreenImageViewerAction.ZoomChanged(
                                        scale = lerp(startScale, targetScale, value),
                                        pan = Offset(
                                            lerp(startPan.x, clamped.x, value),
                                            lerp(startPan.y, clamped.y, value)
                                        )
                                    ))
                                }
                            }
                        }
                    )
                }
        } else {
            Modifier
        }

        val settledGeometry = remember(
            frozenTargetRect,
            currentZoom,
            currentPan,
            dismissOffsetYProvider(),
            displayAspectRatio
        ) {
            val dismissOffsetY = dismissOffsetYProvider()
            val dismissProgress = (abs(dismissOffsetY) / dismissThresholdPx).coerceIn(0f, 1f)
            val dismissScale = 1f - (dismissProgress * 0.08f)
            val effectiveZoom = currentZoom * dismissScale
            val effectivePan = Offset(
                x = currentPan.x,
                y = currentPan.y + dismissOffsetY
            )
            val currentRect = transformRect(
                baseRect = frozenTargetRect,
                scale = effectiveZoom,
                offset = effectivePan
            )
            viewerImageGeometry(
                frameRect = currentRect,
                bitmapAspect = displayAspectRatio,
                fitBlend = 1f
            )
        }

        SideEffect {
            val painterForSnapshot = fullPainter ?: fullAsyncPainter
                ?: previewPainter ?: previewAsyncPainter
                ?: lqPainter ?: lqAsyncPainter
                ?: fallbackPainter
            if (painterForSnapshot != null) {
                onDisplaySnapshot(painterForSnapshot, settledGeometry, displayAspectRatio)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            ViewerImageRenderer(
                painter = lqPainter ?: lqAsyncPainter,
                geometry = settledGeometry,
                alpha = lqAlpha
            )

            ViewerImageRenderer(
                painter = previewPainter ?: previewAsyncPainter,
                geometry = settledGeometry,
                alpha = previewAlpha
            )

            ViewerImageRenderer(
                painter = fullPainter ?: fullAsyncPainter,
                geometry = settledGeometry,
                alpha = fullAlpha
            )
        }
    }
}

private fun resolveViewerPainterAspectRatio(painter: Painter, fallback: Float): Float {
    val size = painter.intrinsicSize
    val width = size.width
    val height = size.height
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        (width / height).coerceAtLeast(0.01f)
    } else {
        fallback.coerceAtLeast(0.01f)
    }
}

private data class ViewerImageUrls(
    val lqUrl: String? = null,
    val previewUrl: String? = null,
    val fullUrl: String
)

private fun resolveViewerImageUrls(rawUrl: String): ViewerImageUrls {
    return ViewerImageUrls(
        lqUrl = null,
        previewUrl = null,
        fullUrl = rawUrl
    )
}

private fun Modifier.verticalDismissGestureV2(
    enabled: Boolean,
    touchSlopPx: Float,
    onDragOffset: (Float) -> Unit,
    onDismissRequest: (offsetY: Float, velocityY: Float) -> Unit,
    onCancel: (Float) -> Unit,
    thresholdPx: Float
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, touchSlopPx, thresholdPx) {
        val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            velocityTracker.resetTracking()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var totalDx = 0f
            var totalDy = 0f
            var isDragging = false
            var offsetY = 0f
            var interruptedByMultiTouch = false

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val pressedCount = event.changes.count { it.pressed }
                if (pressedCount > 1) {
                    interruptedByMultiTouch = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val dx = change.position.x - change.previousPosition.x
                val dy = change.position.y - change.previousPosition.y
                totalDx += dx
                totalDy += dy

                if (!isDragging && abs(totalDy) > touchSlopPx && abs(totalDy) > abs(totalDx)) {
                    isDragging = true
                }

                if (isDragging) {
                    change.consume()
                    offsetY += dy
                    onDragOffset(offsetY)
                }
            }

            if (isDragging) {
                val velocityY = velocityTracker.calculateVelocity().y
                val fastFling = abs(velocityY) > 1000f && ((velocityY > 0 && offsetY > 0) || (velocityY < 0 && offsetY < 0))
                if (interruptedByMultiTouch) {
                    onCancel(offsetY)
                } else if (abs(offsetY) >= thresholdPx || fastFling) {
                    onDismissRequest(offsetY, velocityY)
                } else {
                    onCancel(offsetY)
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, progress: Float): Float {
    return start + (stop - start) * progress
}
