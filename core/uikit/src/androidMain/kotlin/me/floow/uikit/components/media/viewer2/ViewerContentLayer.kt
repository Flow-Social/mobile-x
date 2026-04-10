package me.floow.uikit.components.media.viewer2

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.abs
import me.floow.uikit.util.overlayHorizontalSwipeZone

@Composable
fun ViewerContentLayer(
    model: FullscreenImageViewerModel,
    state: FullscreenImageViewerState,
    pagerState: PagerState,
    sceneProgress: Float,
    dismissProgress: Float,
    onAction: (FullscreenImageViewerAction) -> Unit,
    onRequestClose: (Float?) -> Unit,
    onPagePainterReady: (Int, Painter) -> Unit,
    onPageFirstFrameReady: (Int) -> Unit = {},
    fallbackPainterForPage: (Int) -> Painter?,
    gesturesEnabled: Boolean = true,
    sharedTransitionSpec: FullscreenImageViewerSharedTransitionSpec? = null,
    modifier: Modifier = Modifier
) {
    val imageCount = model.images.size
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val dismissThresholdPx = mediaTransitionDismissThresholdPx(LocalDensity.current)
    val pagerZoneKey = remember { "fullscreen_viewer_pager_zone" }
    val chromeEnterProgress = ((sceneProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
    val chromeDismissProgress = ((dismissProgress - 0.03f) / 0.22f).coerceIn(0f, 1f)
    val chromeTargetAlpha = when {
        state.phase == ViewerPhase.Opened && state.chromeVisible ->
            (1f - chromeDismissProgress).coerceIn(0f, 1f)
        state.phase == ViewerPhase.Opening && state.chromeVisible -> chromeEnterProgress
        else -> 0f
    }
    val chromeAlpha by animateFloatAsState(
        targetValue = chromeTargetAlpha,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "viewer_chrome_alpha"
    )
    val chromeTranslationY by animateFloatAsState(
        targetValue = if (state.phase == ViewerPhase.Opening) {
            -18f * (1f - chromeEnterProgress)
        } else {
            -34f * chromeDismissProgress
        },
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "viewer_chrome_translation_y"
    )

    LaunchedEffect(state.page, state.visible) {
        if (state.visible && pagerState.currentPage != state.page) {
            pagerState.scrollToPage(state.page.coerceIn(0, imageCount - 1))
        }
    }

    LaunchedEffect(pagerState.currentPage, state.phase) {
        if (state.phase == ViewerPhase.Opened && pagerState.currentPage != state.page) {
            onAction(FullscreenImageViewerAction.PageChanged(pagerState.currentPage))
        }
    }

    val pagerEnabled by remember(gesturesEnabled, state.zoom) {
        androidx.compose.runtime.derivedStateOf {
            gesturesEnabled && state.zoom <= 1.01f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalDismissGestureV2(
                enabled = gesturesEnabled && state.zoom <= 1.01f,
                touchSlopPx = touchSlopPx,
                thresholdPx = dismissThresholdPx,
                onDragOffset = { onAction(FullscreenImageViewerAction.DismissDrag(it)) },
                onDismissRequest = onRequestClose,
                onCancel = { onAction(FullscreenImageViewerAction.DismissDrag(0f)) }
            )
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = pagerEnabled,
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_viewer_pager")
                .overlayHorizontalSwipeZone(
                    zoneKey = pagerZoneKey,
                    atStart = pagerState.currentPage == 0,
                    blockOverlay = true,
                    priority = 100
                )
        ) { page ->
            val fallbackPainter = fallbackPainterForPage(page)
            val pageSharedModifier = rememberViewerPageSharedModifier(
                sharedTransitionSpec = sharedTransitionSpec,
                page = page,
                currentPage = pagerState.currentPage,
                phase = state.phase
            )
            ZoomableViewerImageV2(
                imageUrl = model.images[page],
                resetKey = state.page,
                zoom = state.zoom,
                pan = state.pan,
                dismissOffsetYProvider = { state.dismissOffsetY },
                dismissThresholdPx = dismissThresholdPx,
                onAction = onAction,
                onPainterReady = { painter -> onPagePainterReady(page, painter) },
                onFirstFrameReady = { onPageFirstFrameReady(page) },
                fallbackPainter = fallbackPainter,
                gesturesEnabled = gesturesEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .then(pageSharedModifier)
            )
        }

        if (state.visible && chromeAlpha > 0.01f) {
            ViewerTopBar(
                title = model.title,
                subtitle = model.subtitleProvider(state.page.coerceIn(0, imageCount - 1)),
                countText = "${state.page + 1}/$imageCount",
                onBackClick = { onRequestClose(null) },
                onMenuClick = { onAction(FullscreenImageViewerAction.MenuClick) },
                backgroundColor = Color.Black.copy(alpha = 0.22f * sceneProgress),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = chromeTranslationY
                    }
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberViewerPageSharedModifier(
    sharedTransitionSpec: FullscreenImageViewerSharedTransitionSpec?,
    page: Int,
    currentPage: Int,
    phase: ViewerPhase
): Modifier {
    if (sharedTransitionSpec == null || page != currentPage) return Modifier
    val key = sharedTransitionSpec.keyForPage(page)
    val visible = phase == ViewerPhase.Opening
    val boundsTransform = remember {
        BoundsTransform { _: Rect, _: Rect ->
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        }
    }
    return with(sharedTransitionSpec.scope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = visible,
            boundsTransform = boundsTransform,
            renderInOverlayDuringTransition = true
        )
    }
}

@Composable
private fun ZoomableViewerImageV2(
    imageUrl: String,
    resetKey: Int,
    zoom: Float,
    pan: Offset,
    dismissOffsetYProvider: () -> Float,
    dismissThresholdPx: Float,
    onAction: (FullscreenImageViewerAction) -> Unit,
    onPainterReady: (Painter) -> Unit,
    onFirstFrameReady: () -> Unit,
    fallbackPainter: Painter?,
    gesturesEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentZoom by rememberUpdatedState(zoom)
    val currentPan by rememberUpdatedState(pan)
    val urls = remember(imageUrl) { resolveViewerImageUrls(imageUrl) }
    var lqPainter by remember(imageUrl, resetKey) { mutableStateOf<Painter?>(null) }
    var previewPainter by remember(imageUrl, resetKey) { mutableStateOf<Painter?>(null) }
    var fullPainter by remember(imageUrl, resetKey) { mutableStateOf<Painter?>(null) }
    var lqSettled by remember(imageUrl, resetKey) { mutableStateOf(false) }
    var previewSettled by remember(imageUrl, resetKey) { mutableStateOf(false) }
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
        animationSpec = tween(durationMillis = 140),
        label = "viewer_preview_alpha"
    )
    val fullAlpha by animateFloatAsState(
        targetValue = if (fullReady) 1f else 0f,
        animationSpec = tween(durationMillis = 140),
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

        val containerWidthPx = constraints.maxWidth.toFloat()
        val containerHeightPx = constraints.maxHeight.toFloat()

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

                            val newScale = (gestureScale * zoomChange).coerceIn(1f, 4f)
                            val rawOffset = if (newScale > 1f) gesturePan + panChange else Offset.Zero
                            val clampedOffset = clampOffset(
                                rawOffset = rawOffset,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx,
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
                            val targetScale = if (startScale > 1f) 1f else 2.5f
                            val focus = tap - Offset(containerWidthPx / 2f, containerHeightPx / 2f)
                            val nextOffset = if (targetScale > 1f) {
                                startPan - focus * (targetScale / startScale - 1f)
                            } else {
                                Offset.Zero
                            }
                            val clamped = clampOffset(nextOffset, containerWidthPx, containerHeightPx, targetScale)
                            scope.launch {
                                androidx.compose.animation.core.animate(
                                    initialValue = 0f,
                                    targetValue = 1f,
                                    animationSpec = spring(stiffness = 600f)
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
        } else Modifier

        // Shared graphics layer block to avoid duplication
        val graphicsBlock: GraphicsLayerScope.() -> Unit = {
            val offsetY = dismissOffsetYProvider()
            val dismissProgress = (abs(offsetY) / dismissThresholdPx).coerceIn(0f, 1f)
            val dismissScale = 1f - (dismissProgress * 0.08f)
            
            translationX = pan.x
            translationY = pan.y + offsetY
            scaleX = zoom * dismissScale
            scaleY = zoom * dismissScale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {

            if (fallbackPainter != null) {
                Image(
                    painter = fallbackPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            graphicsBlock()
                            // Keep fallback visible as a solid base until first stage is visible
                            alpha = if (lqReady || previewReady || fullReady) 0f else 1f
                        }
                )
            }

            Image(
                painter = lqPainter ?: lqAsyncPainter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        graphicsBlock()
                        alpha = lqAlpha
                    }
            )

            Image(
                painter = previewPainter ?: previewAsyncPainter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        graphicsBlock()
                        alpha = previewAlpha
                    }
            )

            Image(
                painter = fullPainter ?: fullAsyncPainter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        graphicsBlock()
                        alpha = fullAlpha
                    }
            )
        }
    }
}

private data class ViewerImageUrls(
    val lqUrl: String,
    val previewUrl: String,
    val fullUrl: String
)

private fun resolveViewerImageUrls(rawUrl: String): ViewerImageUrls {
    return when {
        rawUrl.endsWith("_full.jpg") -> {
            val stem = rawUrl.removeSuffix("_full.jpg")
            ViewerImageUrls(
                lqUrl = "${stem}_lq.jpg",
                previewUrl = "${stem}_preview.jpg",
                fullUrl = rawUrl
            )
        }

        rawUrl.endsWith("_preview.jpg") -> {
            val stem = rawUrl.removeSuffix("_preview.jpg")
            ViewerImageUrls(
                lqUrl = "${stem}_lq.jpg",
                previewUrl = rawUrl,
                fullUrl = "${stem}_full.jpg"
            )
        }

        rawUrl.endsWith("_lq.jpg") -> {
            val stem = rawUrl.removeSuffix("_lq.jpg")
            ViewerImageUrls(
                lqUrl = rawUrl,
                previewUrl = "${stem}_preview.jpg",
                fullUrl = "${stem}_full.jpg"
            )
        }

        else -> ViewerImageUrls(
            lqUrl = rawUrl,
            previewUrl = rawUrl,
            fullUrl = rawUrl
        )
    }
}

private fun Modifier.verticalDismissGestureV2(
    enabled: Boolean,
    touchSlopPx: Float,
    onDragOffset: (Float) -> Unit,
    onDismissRequest: (Float) -> Unit,
    onCancel: () -> Unit,
    thresholdPx: Float
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, touchSlopPx, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
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
                if (interruptedByMultiTouch) {
                    onCancel()
                } else if (abs(offsetY) >= thresholdPx) {
                    onDismissRequest(offsetY)
                } else {
                    onCancel()
                }
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, progress: Float): Float {
    return start + (stop - start) * progress
}
