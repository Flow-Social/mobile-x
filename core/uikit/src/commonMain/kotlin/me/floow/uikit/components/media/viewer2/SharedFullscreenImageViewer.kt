package me.floow.uikit.components.media.viewer2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.max

private const val COMMON_VIEWER_OPEN_DURATION_MS = 280
private const val COMMON_VIEWER_CLOSE_DURATION_MS = 240
private const val COMMON_VIEWER_DISMISS_THRESHOLD_PX = 140f
private const val COMMON_VIEWER_MAX_CHROME_BG_ALPHA = 0.22f
private const val COMMON_VIEWER_MAX_SCENE_SCRIM_ALPHA = 0.12f

@Composable
fun SharedFullscreenImageViewer(
    model: FullscreenImageViewerModel,
    state: FullscreenImageViewerState,
    onAction: (FullscreenImageViewerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.phase == ViewerPhase.Closed || model.images.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = state.page.coerceIn(0, model.images.lastIndex.coerceAtLeast(0)),
        pageCount = { model.images.size }
    )
    val dismissOffset = remember { Animatable(0f) }
    val dismissProgressTarget = sharedDismissProgressForOffset(state.dismissOffsetY)
    val dismissProgress by animateFloatAsState(
        targetValue = dismissProgressTarget,
        animationSpec = tween(durationMillis = 120),
        label = "shared_viewer_dismiss_progress"
    )
    val sceneProgress = sharedViewerSceneProgress(
        phase = state.phase,
        transitionProgress = state.transitionProgress,
        dismissProgress = dismissProgress,
        closeSceneStartProgress = state.closeSceneStartProgress
    )
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
        label = "shared_viewer_chrome_alpha"
    )
    val chromeTranslationY by animateFloatAsState(
        targetValue = if (state.phase == ViewerPhase.Opening) {
            -18f * (1f - chromeEnterProgress)
        } else {
            -34f * chromeDismissProgress
        },
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "shared_viewer_chrome_translation_y"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = when (state.phase) {
            ViewerPhase.Opening -> state.transitionProgress.coerceIn(0f, 1f)
            ViewerPhase.Closing -> (1f - state.transitionProgress).coerceIn(0f, 1f)
            ViewerPhase.Opened -> 1f
            ViewerPhase.Closed -> 0f
        },
        animationSpec = tween(durationMillis = 140),
        label = "shared_viewer_content_alpha"
    )

    LaunchedEffect(state.phase) {
        when (state.phase) {
            ViewerPhase.Opening -> {
                state.updateTransitionProgress(0f)
                dismissOffset.snapTo(0f)
                val progress = Animatable(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = COMMON_VIEWER_OPEN_DURATION_MS, easing = FastOutSlowInEasing)
                ) {
                    state.updateTransitionProgress(value)
                }
                onAction(FullscreenImageViewerAction.OpenAnimationFinished)
            }

            ViewerPhase.Closing -> {
                val progress = Animatable(0f)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = COMMON_VIEWER_CLOSE_DURATION_MS, easing = FastOutSlowInEasing)
                ) {
                    state.updateTransitionProgress(value)
                }
                onAction(FullscreenImageViewerAction.CloseAnimationFinished)
            }

            ViewerPhase.Opened,
            ViewerPhase.Closed -> Unit
        }
    }

    LaunchedEffect(state.page, state.visible) {
        if (state.visible && pagerState.currentPage != state.page) {
            pagerState.scrollToPage(state.page.coerceIn(0, model.images.lastIndex))
        }
    }

    LaunchedEffect(pagerState.currentPage, state.phase) {
        if (state.phase == ViewerPhase.Opened && pagerState.currentPage != state.page) {
            onAction(FullscreenImageViewerAction.PageChanged(pagerState.currentPage))
        }
    }

    LaunchedEffect(state.dismissOffsetY) {
        dismissOffset.animateTo(
            targetValue = state.dismissOffsetY,
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = sceneProgress))
            .pointerInput(state.phase, state.zoom) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var totalY = 0f
                    var verticalLocked = false
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        totalY += delta.y
                        if (!verticalLocked) {
                            verticalLocked = totalY.absoluteValue > 6f && totalY.absoluteValue > delta.x.absoluteValue
                        }
                        if (verticalLocked && state.zoom <= 1.01f && state.phase == ViewerPhase.Opened) {
                            onAction(FullscreenImageViewerAction.DismissDrag(totalY))
                            change.consume()
                        }
                    }
                    if (state.dismissOffsetY.absoluteValue >= COMMON_VIEWER_DISMISS_THRESHOLD_PX) {
                        onAction(
                            FullscreenImageViewerAction.RequestClose(
                                page = pagerState.currentPage,
                                dismissProgressAtClose = dismissProgressTarget
                            )
                        )
                    } else {
                        onAction(FullscreenImageViewerAction.DismissDrag(0f))
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = COMMON_VIEWER_MAX_SCENE_SCRIM_ALPHA * sceneProgress))
        )

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = state.zoom <= 1.01f,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                }
        ) { page ->
            SharedZoomableViewerImage(
                imageUrl = model.images[page],
                dismissOffsetYProvider = { dismissOffset.value },
                dismissProgressProvider = { dismissProgress },
                zoom = state.zoom,
                pan = state.pan,
                onTransform = { nextZoom, nextPan ->
                    onAction(FullscreenImageViewerAction.ZoomChanged(nextZoom, nextPan))
                },
                onTap = { onAction(FullscreenImageViewerAction.ToggleChrome) },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (state.visible && chromeAlpha > 0.01f) {
            SharedViewerTopBar(
                title = model.title,
                subtitle = model.subtitleProvider(pagerState.currentPage),
                countText = "${pagerState.currentPage + 1}/${model.images.size}",
                onBackClick = {
                    onAction(FullscreenImageViewerAction.RequestClose(page = pagerState.currentPage))
                },
                onMenuClick = { onAction(FullscreenImageViewerAction.MenuClick) },
                backgroundColor = Color.Black.copy(alpha = COMMON_VIEWER_MAX_CHROME_BG_ALPHA * sceneProgress),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = chromeAlpha
                        translationY = chromeTranslationY
                    }
            )
        }
    }
}

@Composable
private fun SharedViewerTopBar(
    title: String,
    subtitle: String,
    countText: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = countText,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Меню",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun SharedZoomableViewerImage(
    imageUrl: String,
    dismissOffsetYProvider: () -> Float,
    dismissProgressProvider: () -> Float,
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val scope = rememberCoroutineScope()
        val currentZoom by rememberUpdatedState(zoom)
        val currentPan by rememberUpdatedState(pan)
        val urls = remember(imageUrl) { sharedResolveViewerImageUrls(imageUrl) }
        var lqSettled by remember(imageUrl) { mutableStateOf(false) }
        var previewSettled by remember(imageUrl) { mutableStateOf(false) }
        var lqReady by remember(imageUrl) { mutableStateOf(false) }
        var previewReady by remember(imageUrl) { mutableStateOf(false) }
        var fullReady by remember(imageUrl) { mutableStateOf(false) }

        val lqAlpha by animateFloatAsState(
            targetValue = if (lqReady && !previewReady) 1f else 0f,
            animationSpec = tween(durationMillis = 120),
            label = "shared_viewer_lq_alpha"
        )
        val previewAlpha by animateFloatAsState(
            targetValue = if (previewReady && !fullReady) 1f else 0f,
            animationSpec = tween(durationMillis = 140),
            label = "shared_viewer_preview_alpha"
        )
        val fullAlpha by animateFloatAsState(
            targetValue = if (fullReady) 1f else 0f,
            animationSpec = tween(durationMillis = 140),
            label = "shared_viewer_full_alpha"
        )

        val lqAsyncPainter = rememberAsyncImagePainter(
            model = urls.lqUrl,
            onState = { painterState ->
                when (painterState) {
                    is AsyncImagePainter.State.Success -> {
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
            model = if (lqSettled) urls.previewUrl else null,
            onState = { painterState ->
                when (painterState) {
                    is AsyncImagePainter.State.Success -> {
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
            model = if (previewSettled) urls.fullUrl else null,
            onState = { painterState ->
                if (painterState is AsyncImagePainter.State.Success) {
                    fullReady = true
                }
            }
        )

        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val gestureModifier = Modifier
            .pointerInput(imageUrl) {
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
                        if (pressedCount <= 1 && gestureScale == 1f && zoomChange == 1f) continue
                        event.changes.forEach { if (it.pressed) it.consume() }
                        val newScale = (gestureScale * zoomChange).coerceIn(1f, 4f)
                        val rawOffset = if (newScale > 1f) gesturePan + panChange else Offset.Zero
                        val clamped = sharedClampOffset(rawOffset, containerWidthPx, containerHeightPx, newScale)
                        gestureScale = newScale
                        gesturePan = clamped
                        onTransform(newScale, clamped)
                    }
                }
            }
            .pointerInput(imageUrl) {
                detectTapGestures(
                    onTap = { onTap() },
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
                        val clamped = sharedClampOffset(nextOffset, containerWidthPx, containerHeightPx, targetScale)
                        scope.launch {
                            animate(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = spring(stiffness = 600f)
                            ) { value, _ ->
                                onTransform(
                                    sharedLerp(startScale, targetScale, value),
                                    Offset(
                                        sharedLerp(startPan.x, clamped.x, value),
                                        sharedLerp(startPan.y, clamped.y, value)
                                    )
                                )
                            }
                        }
                    }
                )
            }

        val graphicsBlock: GraphicsLayerScope.() -> Unit = {
            val dismissProgress = dismissProgressProvider()
            val dismissScale = 1f - (dismissProgress * 0.08f)
            translationX = pan.x
            translationY = pan.y + dismissOffsetYProvider()
            scaleX = zoom * dismissScale
            scaleY = zoom * dismissScale
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
        ) {
            if (!lqReady && !previewReady && !fullReady) {
                Image(
                    painter = lqAsyncPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            graphicsBlock()
                            alpha = if (lqReady || previewReady || fullReady) 0f else 1f
                        }
                )
            }

            Image(
                painter = lqAsyncPainter,
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
                painter = previewAsyncPainter,
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
                painter = fullAsyncPainter,
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

private data class SharedViewerImageUrls(
    val lqUrl: String,
    val previewUrl: String,
    val fullUrl: String
)

private fun sharedResolveViewerImageUrls(rawUrl: String): SharedViewerImageUrls {
    return when {
        rawUrl.endsWith("_full.jpg") -> {
            val stem = rawUrl.removeSuffix("_full.jpg")
            SharedViewerImageUrls(
                lqUrl = "${stem}_lq.jpg",
                previewUrl = "${stem}_preview.jpg",
                fullUrl = rawUrl
            )
        }

        rawUrl.endsWith("_preview.jpg") -> {
            val stem = rawUrl.removeSuffix("_preview.jpg")
            SharedViewerImageUrls(
                lqUrl = "${stem}_lq.jpg",
                previewUrl = rawUrl,
                fullUrl = "${stem}_full.jpg"
            )
        }

        rawUrl.endsWith("_lq.jpg") -> {
            val stem = rawUrl.removeSuffix("_lq.jpg")
            SharedViewerImageUrls(
                lqUrl = rawUrl,
                previewUrl = "${stem}_preview.jpg",
                fullUrl = "${stem}_full.jpg"
            )
        }

        else -> SharedViewerImageUrls(
            lqUrl = rawUrl,
            previewUrl = rawUrl,
            fullUrl = rawUrl
        )
    }
}

private fun sharedClampOffset(
    rawOffset: Offset,
    containerWidthPx: Float,
    containerHeightPx: Float,
    scale: Float
): Offset {
    val scaledWidth = containerWidthPx * scale
    val scaledHeight = containerHeightPx * scale
    val maxX = max(0f, (scaledWidth - containerWidthPx) / 2f)
    val maxY = max(0f, (scaledHeight - containerHeightPx) / 2f)
    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
}

private fun sharedLerp(start: Float, stop: Float, progress: Float): Float {
    return start + (stop - start) * progress
}

private fun sharedDismissProgressForOffset(offsetY: Float): Float {
    return (abs(offsetY) / COMMON_VIEWER_DISMISS_THRESHOLD_PX).coerceIn(0f, 1f)
}

private fun sharedViewerSceneProgress(
    phase: ViewerPhase,
    transitionProgress: Float,
    dismissProgress: Float,
    closeSceneStartProgress: Float
): Float {
    val transition = transitionProgress.coerceIn(0f, 1f)
    return when (phase) {
        ViewerPhase.Closed -> 0f
        ViewerPhase.Opening -> transition
        ViewerPhase.Opened -> 1f - dismissProgress.coerceIn(0f, 1f)
        ViewerPhase.Closing -> sharedLerp(closeSceneStartProgress.coerceIn(0f, 1f), 0f, transition)
    }.coerceIn(0f, 1f)
}
