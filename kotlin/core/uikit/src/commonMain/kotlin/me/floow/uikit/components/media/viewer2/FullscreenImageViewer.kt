package me.floow.uikit.components.media.viewer2

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.abs
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import me.floow.uikit.platform.PlatformBackHandler

private const val OPEN_DURATION_MS = 280
private const val CLOSE_DURATION_MS = 220
private val TelegramCloseEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private const val CROSSFADE_MS = 60
private const val FINISH_HOLD_FRAMES = 0
private const val MAX_STATUS_BAR_ALPHA = 0.22f
private const val MAX_NAV_BAR_ALPHA = 0.12f

private enum class TransitionActorState {
    OpeningShared,
    OpeningFallback,
    Opened,
    ClosingShared,
    ClosingFallback
}

private data class TransitionSnapshot(
    val painter: Painter,
    val aspectRatio: Float,
    val page: Int,
    val phase: ViewerPhase
)

private data class DisplaySnapshot(
    val painter: Painter,
    val geometry: ViewerImageGeometry,
    val aspectRatio: Float,
    val page: Int
)

@Composable
fun FullscreenImageViewer(
    model: FullscreenImageViewerModel,
    state: FullscreenImageViewerState,
    onAction: (FullscreenImageViewerAction) -> Unit,
    sharedTransitionSpec: FullscreenImageViewerSharedTransitionSpec? = null,
    modifier: Modifier = Modifier
) {
    if (state.phase == ViewerPhase.Closed || model.images.isEmpty()) return

    val context = LocalPlatformContext.current
    val imageCount = model.images.size
    val dismissThresholdPx = mediaTransitionDismissThresholdPx(LocalDensity.current)
    val sceneAnim = remember { Animatable(0f) }
    val transitionScene = resolveMediaTransitionScene(
        phase = state.phase,
        transitionProgress = state.transitionProgress,
        dismissOffsetY = state.dismissOffsetY,
        closeSceneStartProgress = state.closeSceneStartProgress,
        dismissThresholdPx = dismissThresholdPx
    )
    val sceneProgress = transitionScene.sceneProgress
    val actorProgress = when (state.phase) {
        ViewerPhase.Closing -> state.transitionProgress.coerceIn(0f, 1f)
        else -> sceneProgress
    }
    val pagePainters = remember { mutableStateMapOf<Int, Painter>() }
    val pageFirstFrameReady = remember { mutableStateMapOf<Int, Boolean>() }
    val frozenPageAspects = remember(model.images) { mutableStateMapOf<Int, Float>() }
    val pagerState = rememberPagerState(
        initialPage = state.page.coerceIn(0, imageCount - 1),
        pageCount = { imageCount }
    )
    val useSharedTransition = false

    ViewerSystemBarsLayer(visible = state.visible)

    var initialCloseVelocity by remember { mutableStateOf(0f) }
    var requestClose by remember { mutableStateOf<(Float?, Float?) -> Unit>({ _, _ -> }) }
    PlatformBackHandler(enabled = state.visible) { requestClose(null, null) }

    LaunchedEffect(state.phase) {
        if (state.phase == ViewerPhase.Closed) {
            state.dismissOffsetY = 0f
        }
        when (state.phase) {
            ViewerPhase.Opening -> {
                sceneAnim.snapTo(0f)
                state.updateTransitionProgress(0f)
                sceneAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = OPEN_DURATION_MS, easing = FastOutSlowInEasing)
                ) {
                    state.updateTransitionProgress(value)
                }
                state.updateTransitionProgress(1f)
                repeat(FINISH_HOLD_FRAMES) {
                    withFrameNanos { }
                }
                onAction(FullscreenImageViewerAction.OpenAnimationFinished)
            }

            ViewerPhase.Closing -> {
                sceneAnim.snapTo(0f)
                state.updateTransitionProgress(0f)
                val normVelocity = (kotlin.math.abs(initialCloseVelocity) / 1000f).coerceIn(0f, 5f)
                if (normVelocity > 0.1f) {
                    sceneAnim.animateTo(
                        targetValue = 1f,
                        initialVelocity = normVelocity,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = 500f
                        )
                    ) {
                        state.updateTransitionProgress(value.coerceIn(0f, 1f))
                    }
                } else {
                    sceneAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = 220,
                            easing = FastOutSlowInEasing
                        )
                    ) {
                        state.updateTransitionProgress(value.coerceIn(0f, 1f))
                    }
                }
                state.updateTransitionProgress(1f)
                repeat(FINISH_HOLD_FRAMES) {
                    withFrameNanos { }
                }
                initialCloseVelocity = 0f
                onAction(FullscreenImageViewerAction.CloseAnimationFinished)
            }

            ViewerPhase.Opened, ViewerPhase.Closed -> Unit
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .zIndex(2f)
    ) {
        val containerWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val containerHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val fallbackAspect = containerWidthPx / containerHeightPx
        val currentPage = when (state.phase) {
            ViewerPhase.Closing -> state.page
            ViewerPhase.Opening,
            ViewerPhase.Opened,
            ViewerPhase.Closed -> pagerState.currentPage
        }.coerceIn(0, imageCount - 1)
        val currentPageAspect = remember(model, currentPage) {
            model.aspectRatioForPage(currentPage)?.coerceAtLeast(0.01f)
        }
        val neighborPages = remember(currentPage, imageCount) {
            listOf(currentPage - 1, currentPage + 1)
                .filter { it in 0 until imageCount }
        }
        val currentImageRaw = model.images[currentPage]
        val currentImageModel = currentImageRaw
        val asyncPainter = rememberAsyncImagePainter(model = currentImageModel)
        val cachedPainter = pagePainters[currentPage]
        val openingPainter = model.openingPainter
        val displaySnapshots = remember { mutableStateMapOf<Int, DisplaySnapshot>() }
        var currentAnimatedRect by remember { mutableStateOf<Rect?>(null) }
        val closeDisplaySnapshot = displaySnapshots[currentPage]
        val actorState = rememberActorState(
            phase = state.phase,
            useSharedTransition = useSharedTransition,
            openOrigin = state.openOrigin,
            closeOrigin = state.closeOrigin
        )
        requestClose = { dismissOffsetOverride, dismissVelocityOverride ->
            initialCloseVelocity = dismissVelocityOverride ?: 0f
            val activePage = resolveActivePageForClose(
                isScrollInProgress = pagerState.isScrollInProgress,
                currentPage = pagerState.currentPage,
                targetPage = pagerState.targetPage,
                maxIndex = imageCount - 1
            )
            val origin = model.originForPage(activePage)
            val closeDismissOffset = dismissOffsetOverride ?: state.dismissOffsetY
            val closeDismissProgress = mediaTransitionDismissProgressForOffset(closeDismissOffset, dismissThresholdPx)
            val activeDisplaySnapshot = displaySnapshots[activePage]
            val aspect = (
                activeDisplaySnapshot?.aspectRatio?.coerceAtLeast(0.01f)
                    ?: frozenPageAspects[activePage]?.coerceAtLeast(0.01f)
                    ?: model.aspectRatioForPage(activePage)?.coerceAtLeast(0.01f)
                    ?: origin?.aspectRatio?.coerceAtLeast(0.01f)
                    ?: resolvePainterAspectRatio(pagePainters[activePage] ?: asyncPainter, fallbackAspect)
                ).coerceAtLeast(0.01f)
            val closeStart = if (state.phase == ViewerPhase.Opening) {
                currentAnimatedRect
                    ?: resolveCloseStartRect(
                        state = state,
                        containerWidthPx = containerWidthPx,
                        containerHeightPx = containerHeightPx,
                        aspectRatio = aspect,
                        dismissProgress = closeDismissProgress,
                        dismissOffsetY = closeDismissOffset
                    )
            } else {
                if (kotlin.math.abs(closeDismissOffset) > 1f) {
                    resolveCloseStartRect(
                        state = state,
                        containerWidthPx = containerWidthPx,
                        containerHeightPx = containerHeightPx,
                        aspectRatio = aspect,
                        dismissProgress = closeDismissProgress,
                        dismissOffsetY = closeDismissOffset
                    )
                } else {
                    activeDisplaySnapshot?.geometry?.layoutRect
                        ?: resolveCloseStartRect(
                            state = state,
                            containerWidthPx = containerWidthPx,
                            containerHeightPx = containerHeightPx,
                            aspectRatio = aspect,
                            dismissProgress = closeDismissProgress,
                            dismissOffsetY = closeDismissOffset
                        )
                }
            }
            onAction(
                FullscreenImageViewerAction.RequestClose(
                    origin = origin,
                    page = activePage,
                    dismissProgressAtClose = closeDismissProgress,
                    closeStartRect = closeStart
                )
            )
        }

        LaunchedEffect(state.phase) {
            if (state.phase == ViewerPhase.Opening) {
                pageFirstFrameReady.clear()
            }
        }
        LaunchedEffect(neighborPages, model.images, containerWidthPx, containerHeightPx) {
            if (neighborPages.isEmpty()) return@LaunchedEffect
            val imageLoader = SingletonImageLoader.get(context)
            val width = containerWidthPx.roundToInt().coerceAtLeast(1)
            val height = containerHeightPx.roundToInt().coerceAtLeast(1)
            neighborPages
                .flatMap { page -> prefetchViewerUrls(model.images[page]) }
                .distinct()
                .forEach { url ->
                    val request = ImageRequest.Builder(context)
                        .data(url)
                        .size(width, height)
                        .build()
                    imageLoader.enqueue(request)
                }
        }

        var persistentSnapshot by remember { mutableStateOf<TransitionSnapshot?>(null) }
        val shouldCaptureSnapshot = shouldCaptureSnapshot(
            phase = state.phase,
            actorState = actorState
        )
        if (shouldCaptureSnapshot) {
            if (persistentSnapshot?.phase != state.phase || persistentSnapshot?.page != currentPage) {
                persistentSnapshot = buildTransitionSnapshot(
                    phase = state.phase,
                    currentPage = currentPage,
                    openOrigin = state.openOrigin,
                    closeOrigin = state.closeOrigin,
                    displaySnapshot = closeDisplaySnapshot,
                    openingPainter = openingPainter,
                    cachedPainter = cachedPainter,
                    asyncPainter = asyncPainter,
                    preferredAspectRatio = currentPageAspect,
                    fallbackAspect = fallbackAspect
                )
            }
        } else if (state.phase == ViewerPhase.Closed) {
            persistentSnapshot = null
        }

        val transitionSnapshot = persistentSnapshot
        val currentPageReady = state.phase != ViewerPhase.Opening

        val transitionOrigin = when (state.phase) {
            ViewerPhase.Opening -> state.openOrigin
            ViewerPhase.Closing -> state.closeOrigin ?: if (state.page == state.openPage) state.openOrigin else null
            ViewerPhase.Opened, ViewerPhase.Closed -> state.openOrigin
        }
        val transitionOriginRect = transitionOrigin?.contentRectInWindow ?: transitionOrigin?.rectInWindow
        val painterForAspect = cachedPainter ?: openingPainter ?: asyncPainter
        val painterAspect = painterForAspect?.let { p ->
            val size = p.intrinsicSize
            if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
                (size.width / size.height).coerceAtLeast(0.01f)
            } else null
        }
        val activeAspect = when {
            transitionSnapshot != null -> transitionSnapshot.aspectRatio
            painterAspect != null -> painterAspect
            currentPageAspect != null -> currentPageAspect
            transitionOrigin != null && transitionOrigin.sourceScaleMode != SourceImageScaleMode.Crop -> transitionOrigin.aspectRatio.coerceAtLeast(0.01f)
            else -> transitionOrigin?.aspectRatio?.coerceAtLeast(0.01f)
                ?: resolvePainterAspectRatio(cachedPainter ?: asyncPainter, fallbackAspect)
        }
        if (state.visible) {
            val currentFrozen = frozenPageAspects[currentPage]
            if (currentFrozen == null) {
                frozenPageAspects[currentPage] = activeAspect
            } else if (painterAspect != null && kotlin.math.abs(currentFrozen - painterAspect) > 0.02f) {
                frozenPageAspects[currentPage] = painterAspect
            }
        }
        if (state.phase == ViewerPhase.Closed) {
            frozenPageAspects.clear()
            displaySnapshots.clear()
        }
        val endRect = computeFitRect(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            aspectRatio = activeAspect
        )
        val closeTargetRect = transitionOriginRect
        val isCloseOriginVisible = closeTargetRect != null &&
            closeTargetRect.bottom > 0f && closeTargetRect.top < containerHeightPx &&
            closeTargetRect.right > 0f && closeTargetRect.left < containerWidthPx
        val transitionRect = when (state.phase) {
            ViewerPhase.Opening -> {
                val start = transitionOriginRect
                if (start != null) lerpRect(start, endRect, actorProgress) else endRect
            }

            ViewerPhase.Closing -> {
                val start = state.closeStartRect ?: endRect
                if (isCloseOriginVisible) {
                    lerpRect(start, closeTargetRect, actorProgress)
                } else {
                    val centerEnd = transformRect(start, scale = 0.72f, offset = Offset.Zero)
                    lerpRect(start, centerEnd, actorProgress)
                }
            }

            ViewerPhase.Opened,
            ViewerPhase.Closed -> endRect
        }
        if (state.phase == ViewerPhase.Opening) {
            currentAnimatedRect = transitionRect
        }
        val transitionCornerRadiusPx = when (state.phase) {
            ViewerPhase.Opening -> lerpFloat(
                start = transitionOrigin?.cornerRadiusPx ?: 0f,
                end = 0f,
                progress = actorProgress
            )
            ViewerPhase.Closing -> lerpFloat(
                start = 0f,
                end = transitionOrigin?.cornerRadiusPx ?: 0f,
                progress = actorProgress
            )
            ViewerPhase.Opened,
            ViewerPhase.Closed -> 0f
        }
        val transitionRotationZ = when (state.phase) {
            ViewerPhase.Opening -> lerpFloat(
                start = transitionOrigin?.rotationZ ?: 0f,
                end = 0f,
                progress = actorProgress
            )
            ViewerPhase.Closing -> lerpFloat(
                start = 0f,
                end = transitionOrigin?.rotationZ ?: 0f,
                progress = actorProgress
            )
            ViewerPhase.Opened,
            ViewerPhase.Closed -> 0f
        }
        val transitionFitBlend = when (state.phase) {
            ViewerPhase.Opening -> resolveTransitionFitBlend(
                sourceScaleMode = transitionOrigin?.sourceScaleMode ?: SourceImageScaleMode.Fit,
                progress = actorProgress
            )
            ViewerPhase.Closing -> resolveTransitionFitBlend(
                sourceScaleMode = transitionOrigin?.sourceScaleMode ?: SourceImageScaleMode.Fit,
                progress = 1f - actorProgress
            )
            ViewerPhase.Opened,
            ViewerPhase.Closed -> 1f
        }

        val contentAlpha = resolveContentTargetAlpha(
            actorState = actorState,
            currentPageReady = currentPageReady
        )
        val snapshotAlpha = resolveSnapshotTargetAlpha(
            actorState = actorState,
            hasSnapshot = transitionSnapshot != null,
            currentPageReady = currentPageReady
        )

        LaunchedEffect(actorState, currentPageReady, snapshotAlpha, transitionSnapshot) {
            if (
                actorState == TransitionActorState.Opened &&
                transitionSnapshot != null &&
                currentPageReady &&
                snapshotAlpha <= 0.01f
            ) {
                persistentSnapshot = null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val alpha = when (state.phase) {
                            ViewerPhase.Opening -> actorProgress.coerceIn(0f, 1f)
                            ViewerPhase.Closing -> {
                                val startAlpha = state.closeSceneStartProgress.coerceIn(0f, 1f)
                                lerpFloat(startAlpha, 0f, actorProgress)
                            }
                            ViewerPhase.Closed -> 0f
                            else -> {
                                val offset = state.dismissOffsetY
                                val progress = mediaTransitionDismissProgressForOffset(offset, dismissThresholdPx)
                                (1f - progress).coerceIn(0f, 1f)
                            }
                        }
                        if (alpha > 0f) {
                            drawRect(Color.Black, alpha = alpha)
                        }
                    }
            )

            val showContentLayer = state.phase == ViewerPhase.Opening ||
                state.phase == ViewerPhase.Opened ||
                (state.phase == ViewerPhase.Closing && (transitionSnapshot == null || state.transitionProgress < 0.95f))
            if (showContentLayer) {
                ViewerContentLayer(
                    model = model,
                    state = state,
                    pagerState = pagerState,
                    sceneProgress = transitionScene.sceneProgress,
                    dismissProgress = transitionScene.dismissProgress,
                    onAction = onAction,
                    onRequestClose = requestClose,
                    onPagePainterReady = { page, painter -> pagePainters[page] = painter },
                    onPageFirstFrameReady = { page -> pageFirstFrameReady[page] = true },
                    onPageDisplaySnapshot = { page, painter, geometry, aspectRatio ->
                        displaySnapshots[page] = DisplaySnapshot(
                            painter = painter,
                            geometry = geometry,
                            aspectRatio = aspectRatio,
                            page = page
                        )
                        frozenPageAspects[page] = aspectRatio
                    },
                    fallbackPainterForPage = { page ->
                        pagePainters[page]
                            ?: transitionSnapshot?.takeIf { it.page == page }?.painter
                            ?: if (page == state.page) openingPainter else null
                    },
                    targetAspectRatioForPage = { page ->
                        frozenPageAspects[page]
                            ?: model.aspectRatioForPage(page)?.coerceAtLeast(0.01f)
                    },
                    gesturesEnabled = state.phase == ViewerPhase.Opened,
                    sharedTransitionSpec = null,
                    dismissOffsetYProvider = { state.dismissOffsetY },
                    onDismissDrag = { state.dismissOffsetY = it },
                    contentAlpha = contentAlpha,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .testTag("viewer_content_layer")
                )
            }

            EdgeToEdgeStatusBarScrim(alphaProvider = { transitionScene.statusBarAlpha })

            if (transitionSnapshot != null && snapshotAlpha > 0f) {
                ViewerTransitionLayer(
                    painter = transitionSnapshot.painter,
                    animatedRect = transitionRect,
                    painterAspectRatio = transitionSnapshot.aspectRatio,
                    cornerRadiusPx = transitionCornerRadiusPx,
                    rotationZ = transitionRotationZ,
                    fitBlend = transitionFitBlend,
                    mediaId = model.images.getOrNull(currentPage)
                        ?: transitionOrigin?.sourceKey
                        ?: "page:$currentPage",
                    phase = state.phase,
                    progress = actorProgress,
                    sourceScaleMode = transitionOrigin?.sourceScaleMode ?: SourceImageScaleMode.Fit,
                    isVisible = snapshotAlpha > 0f,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .testTag("viewer_transition_layer")
                        .graphicsLayer {
                            val centerFade = if (state.phase == ViewerPhase.Closing && !isCloseOriginVisible) {
                                (1f - actorProgress).coerceIn(0f, 1f)
                            } else {
                                1f
                            }
                            alpha = snapshotAlpha * centerFade
                        }
                )
            }
        }
    }
}

private fun rememberActorState(
    phase: ViewerPhase,
    useSharedTransition: Boolean,
    openOrigin: SharedImageOrigin?,
    closeOrigin: SharedImageOrigin?
): TransitionActorState {
    return when (phase) {
        ViewerPhase.Opening -> {
            if (useSharedTransition && openOrigin != null) {
                TransitionActorState.OpeningShared
            } else {
                TransitionActorState.OpeningFallback
            }
        }

        ViewerPhase.Opened -> TransitionActorState.Opened

        ViewerPhase.Closing -> {
            if (useSharedTransition && closeOrigin != null) {
                TransitionActorState.ClosingShared
            } else {
                TransitionActorState.ClosingFallback
            }
        }

        ViewerPhase.Closed -> TransitionActorState.ClosingFallback
    }
}

private fun resolveActivePageForClose(
    isScrollInProgress: Boolean,
    currentPage: Int,
    targetPage: Int,
    maxIndex: Int
): Int {
    return if (isScrollInProgress) {
        targetPage.coerceIn(0, maxIndex)
    } else {
        currentPage.coerceIn(0, maxIndex)
    }
}

private fun shouldCaptureSnapshot(
    phase: ViewerPhase,
    actorState: TransitionActorState
): Boolean {
    return when (phase) {
        ViewerPhase.Opening -> true
        ViewerPhase.Closing -> true
        ViewerPhase.Opened,
        ViewerPhase.Closed -> false
    }
}

private fun buildTransitionSnapshot(
    phase: ViewerPhase,
    currentPage: Int,
    openOrigin: SharedImageOrigin?,
    closeOrigin: SharedImageOrigin?,
    displaySnapshot: DisplaySnapshot?,
    openingPainter: Painter?,
    cachedPainter: Painter?,
    asyncPainter: Painter,
    preferredAspectRatio: Float?,
    fallbackAspect: Float
): TransitionSnapshot {
    val snapshotOrigin = when (phase) {
        ViewerPhase.Opening -> openOrigin
        ViewerPhase.Closing -> closeOrigin
        ViewerPhase.Opened,
        ViewerPhase.Closed -> null
    }
    val closingDisplaySnapshot = displaySnapshot?.takeIf {
        phase == ViewerPhase.Closing && it.page == currentPage
    }
    val painter = closingDisplaySnapshot?.painter ?: openingPainter ?: cachedPainter ?: asyncPainter
    val painterAspect = painter?.let { p ->
        val size = p.intrinsicSize
        if (size.width.isFinite() && size.height.isFinite() && size.width > 0f && size.height > 0f) {
            (size.width / size.height).coerceAtLeast(0.01f)
        } else null
    }
    val aspectRatio = closingDisplaySnapshot?.aspectRatio
        ?: painterAspect
        ?: preferredAspectRatio
        ?: snapshotOrigin?.aspectRatio
        ?: fallbackAspect
        .coerceAtLeast(0.01f)
    return TransitionSnapshot(
        painter = painter,
        aspectRatio = aspectRatio,
        page = currentPage,
        phase = phase
    )
}

private fun resolveContentTargetAlpha(
    actorState: TransitionActorState,
    currentPageReady: Boolean
): Float {
    return when (actorState) {
        TransitionActorState.OpeningFallback -> 0f
        TransitionActorState.OpeningShared -> 0f
        TransitionActorState.Opened -> if (currentPageReady) 1f else 0f
        TransitionActorState.ClosingFallback,
        TransitionActorState.ClosingShared -> 0f
    }
}

private fun resolveSnapshotTargetAlpha(
    actorState: TransitionActorState,
    hasSnapshot: Boolean,
    currentPageReady: Boolean
): Float {
    return when (actorState) {
        TransitionActorState.OpeningFallback,
        TransitionActorState.ClosingFallback -> 1f
        TransitionActorState.Opened -> if (!hasSnapshot || currentPageReady) 0f else 1f
        TransitionActorState.OpeningShared,
        TransitionActorState.ClosingShared -> 1f
    }
}

@Composable
private fun BoxScope.EdgeToEdgeStatusBarScrim(
    alphaProvider: () -> Float,
    modifier: Modifier = Modifier
) {
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (statusBarInset <= 0.dp) return
    Box(
        modifier = modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(statusBarInset)
            .zIndex(1.5f)
            .drawBehind {
                val alpha = alphaProvider()
                if (alpha > 0f) {
                    drawRect(Color.Black, alpha = alpha)
                }
            }
    )
}

private fun lerpFloat(start: Float, end: Float, progress: Float): Float {
    val p = progress.coerceIn(0f, 1f)
    return start + (end - start) * p
}

private fun resolveTransitionFitBlend(
    sourceScaleMode: SourceImageScaleMode,
    progress: Float
): Float {
    if (sourceScaleMode == SourceImageScaleMode.Fit) return 1f
    return progress.coerceIn(0f, 1f)
}

private fun resolveCloseStartRect(
    state: FullscreenImageViewerState,
    containerWidthPx: Float,
    containerHeightPx: Float,
    aspectRatio: Float,
    dismissProgress: Float,
    dismissOffsetY: Float
): Rect {
    val baseRect = computeFitRect(
        containerWidthPx = containerWidthPx,
        containerHeightPx = containerHeightPx,
        aspectRatio = aspectRatio
    )
    val dismissScale = 1f - (dismissProgress * 0.08f)
    return transformRect(
        baseRect = baseRect,
        scale = state.zoom * dismissScale,
        offset = Offset(
            x = state.pan.x,
            y = state.pan.y + dismissOffsetY
        )
    )
}

private fun prefetchViewerUrls(rawUrl: String): List<String> {
    if (rawUrl.isBlank() || rawUrl.startsWith("/")) return emptyList()
    return when {
        rawUrl.endsWith("_full.jpg") -> {
            val stem = rawUrl.removeSuffix("_full.jpg")
            listOf("${stem}_lq.jpg", "${stem}_preview.jpg")
        }

        rawUrl.endsWith("_preview.jpg") -> {
            val stem = rawUrl.removeSuffix("_preview.jpg")
            listOf("${stem}_lq.jpg", rawUrl)
        }

        rawUrl.endsWith("_lq.jpg") -> {
            val stem = rawUrl.removeSuffix("_lq.jpg")
            listOf(rawUrl, "${stem}_preview.jpg")
        }

        else -> listOf(rawUrl)
    }
}

private fun resolvePainterAspectRatio(painter: Painter, fallback: Float): Float {
    val size: Size = painter.intrinsicSize
    val width = size.width
    val height = size.height
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        (width / height).coerceAtLeast(0.01f)
    } else {
        fallback.coerceAtLeast(0.01f)
    }
}
