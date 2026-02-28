package me.floow.uikit.components.media.viewer2

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import coil.Coil
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import me.floow.uikit.util.SystemBarsScrim

private const val OPEN_DURATION_MS = 280
private const val CLOSE_DURATION_MS = 240
private const val CROSSFADE_MS = 180
private const val MAX_STATUS_BAR_ALPHA = 0.6f
private const val MAX_NAV_BAR_ALPHA = 0.35f

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

private data class BarScrimState(
    val statusAlpha: Float,
    val navigationAlpha: Float
)

@Composable
fun FullscreenImageViewerV2(
    model: FullscreenImageViewerModel,
    state: FullscreenImageViewerState,
    onAction: (FullscreenImageViewerAction) -> Unit,
    sharedTransitionSpec: FullscreenImageViewerSharedTransitionSpec? = null,
    modifier: Modifier = Modifier
) {
    if (state.phase == ViewerPhase.Closed || model.images.isEmpty()) return

    val context = LocalContext.current
    val imageCount = model.images.size
    val dismissThresholdPx = with(LocalDensity.current) { 120f * density }
    val dismissProgress = dismissProgress(
        phase = state.phase,
        dismissOffsetY = state.dismissOffsetY,
        thresholdPx = dismissThresholdPx
    )
    val openAnim = remember { Animatable(0f) }
    val closeAnim = remember { Animatable(0f) }
    val pagePainters = remember { mutableStateMapOf<Int, Painter>() }
    val pageFirstFrameReady = remember { mutableStateMapOf<Int, Boolean>() }
    val pagerState = rememberPagerState(
        initialPage = state.page.coerceIn(0, imageCount - 1),
        pageCount = { imageCount }
    )
    val useSharedTransition = sharedTransitionSpec != null

    val barScrim = resolveBarScrimState(
        phase = state.phase,
        openProgress = openAnim.value,
        closeProgress = closeAnim.value,
        dismissProgress = dismissProgress
    )
    SystemBarsScrim(
        visible = state.visible,
        scrim = Color.Black.copy(alpha = barScrim.statusAlpha),
        navigationScrim = Color.Black.copy(alpha = barScrim.navigationAlpha)
    )

    val requestClose: () -> Unit = {
        val activePage = resolveActivePageForClose(
            isScrollInProgress = pagerState.isScrollInProgress,
            currentPage = pagerState.currentPage,
            targetPage = pagerState.targetPage,
            maxIndex = imageCount - 1
        )
        onAction(
            FullscreenImageViewerAction.RequestClose(
                origin = model.originForPage(activePage),
                page = activePage
            )
        )
    }
    BackHandler(enabled = state.visible) { requestClose() }

    LaunchedEffect(state.phase) {
        when (state.phase) {
            ViewerPhase.Opening -> {
                openAnim.snapTo(0f)
                state.updateTransitionProgress(0f)
                openAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = OPEN_DURATION_MS, easing = FastOutSlowInEasing)
                )
                state.updateTransitionProgress(1f)
                onAction(FullscreenImageViewerAction.OpenAnimationFinished)
            }

            ViewerPhase.Closing -> {
                closeAnim.snapTo(0f)
                state.updateTransitionProgress(0f)
                closeAnim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = CLOSE_DURATION_MS, easing = FastOutSlowInEasing)
                )
                state.updateTransitionProgress(1f)
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
        val currentPage = pagerState.currentPage.coerceIn(0, imageCount - 1)
        val neighborPages = remember(currentPage, imageCount) {
            listOf(currentPage - 1, currentPage + 1)
                .filter { it in 0 until imageCount }
        }
        val asyncPainter = rememberAsyncImagePainter(model = model.images[currentPage])
        val cachedPainter = pagePainters[currentPage]
        val openingPainter = model.openingPainter
        val actorState = rememberActorState(
            phase = state.phase,
            useSharedTransition = useSharedTransition,
            openOrigin = state.openOrigin,
            closeOrigin = state.closeOrigin
        )

        LaunchedEffect(state.phase) {
            if (state.phase == ViewerPhase.Opening) {
                pageFirstFrameReady.clear()
            }
        }
        LaunchedEffect(neighborPages, model.images, containerWidthPx, containerHeightPx) {
            if (neighborPages.isEmpty()) return@LaunchedEffect
            val imageLoader = Coil.imageLoader(context)
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
                    openingPainter = openingPainter,
                    cachedPainter = cachedPainter,
                    asyncPainter = asyncPainter,
                    fallbackAspect = fallbackAspect
                )
            }
        } else if (state.phase == ViewerPhase.Closed) {
            persistentSnapshot = null
        }

        val transitionSnapshot = persistentSnapshot
        val canHoldSnapshot = transitionSnapshot?.page == currentPage
        val currentPageReady = when {
            state.phase == ViewerPhase.Opening -> false
            canHoldSnapshot -> pageFirstFrameReady[currentPage] == true
            else -> true
        }

        val transitionOrigin = when (state.phase) {
            ViewerPhase.Opening -> state.openOrigin
            ViewerPhase.Closing -> state.closeOrigin
            ViewerPhase.Opened, ViewerPhase.Closed -> state.openOrigin
        }
        val activeAspect = when {
            transitionSnapshot != null -> transitionSnapshot.aspectRatio
            transitionOrigin != null -> transitionOrigin.aspectRatio.coerceAtLeast(0.01f)
            else -> resolvePainterAspectRatio(cachedPainter ?: asyncPainter, fallbackAspect)
        }
        val endRect = computeFitRect(
            containerWidthPx = containerWidthPx,
            containerHeightPx = containerHeightPx,
            aspectRatio = activeAspect
        )
        val transitionRect = when (state.phase) {
            ViewerPhase.Opening -> {
                val start = transitionOrigin?.rectInWindow
                if (start != null) lerpRect(start, endRect, openAnim.value) else endRect
            }

            ViewerPhase.Closing -> {
                val end = transitionOrigin?.rectInWindow
                if (end != null) lerpRect(endRect, end, closeAnim.value) else endRect
            }

            ViewerPhase.Opened,
            ViewerPhase.Closed -> endRect
        }

        val contentTargetAlpha = resolveContentTargetAlpha(
            actorState = actorState,
            currentPageReady = currentPageReady
        )
        val contentAlpha by animateFloatAsState(
            targetValue = contentTargetAlpha,
            animationSpec = if (actorState == TransitionActorState.Opened) snap() else tween(durationMillis = CROSSFADE_MS),
            label = "viewer_content_alpha"
        )

        val snapshotTargetAlpha = resolveSnapshotTargetAlpha(
            actorState = actorState,
            hasSnapshot = transitionSnapshot != null,
            currentPageReady = currentPageReady
        )
        val snapshotAlpha by animateFloatAsState(
            targetValue = snapshotTargetAlpha,
            animationSpec = tween(durationMillis = CROSSFADE_MS),
            label = "viewer_snapshot_alpha"
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
                        val alpha = resolveBackdropAlpha(
                            phase = state.phase,
                            openProgress = openAnim.value,
                            closeProgress = closeAnim.value,
                            dismissProgress = dismissProgress
                        )
                        if (alpha > 0f) {
                            drawRect(Color.Black, alpha = alpha)
                        }
                    }
            )

            EdgeToEdgeStatusBarScrim(alpha = barScrim.statusAlpha)

            ViewerContentLayer(
                model = model,
                state = state,
                pagerState = pagerState,
                onAction = onAction,
                onRequestClose = requestClose,
                onPagePainterReady = { page, painter -> pagePainters[page] = painter },
                onPageFirstFrameReady = { page -> pageFirstFrameReady[page] = true },
                fallbackPainterForPage = { page ->
                    pagePainters[page]
                        ?: transitionSnapshot?.takeIf { it.page == page }?.painter
                        ?: if (page == state.page) openingPainter else null
                },
                gesturesEnabled = state.phase == ViewerPhase.Opened && contentAlpha > 0.99f,
                sharedTransitionSpec = sharedTransitionSpec,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
                    .testTag("viewer_content_layer")
                    .graphicsLayer {
                        alpha = contentAlpha * dismissContentAlpha(dismissProgress)
                    }
            )

            if (transitionSnapshot != null && snapshotAlpha > 0f) {
                ViewerTransitionLayer(
                    painter = transitionSnapshot.painter,
                    animatedRect = transitionRect,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .testTag("viewer_transition_layer")
                        .graphicsLayer { alpha = snapshotAlpha }
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

private fun resolveBarScrimState(
    phase: ViewerPhase,
    openProgress: Float,
    closeProgress: Float,
    dismissProgress: Float
): BarScrimState {
    val statusAlpha = when (phase) {
        ViewerPhase.Opening -> MAX_STATUS_BAR_ALPHA * openProgress
        ViewerPhase.Opened -> MAX_STATUS_BAR_ALPHA * (1f - dismissProgress)
        ViewerPhase.Closing -> MAX_STATUS_BAR_ALPHA * (1f - closeProgress)
        ViewerPhase.Closed -> 0f
    }.coerceIn(0f, MAX_STATUS_BAR_ALPHA)
    val navigationAlpha = when (phase) {
        ViewerPhase.Opening -> MAX_NAV_BAR_ALPHA * openProgress
        ViewerPhase.Opened -> MAX_NAV_BAR_ALPHA * (1f - dismissProgress)
        ViewerPhase.Closing -> MAX_NAV_BAR_ALPHA * (1f - closeProgress)
        ViewerPhase.Closed -> 0f
    }.coerceIn(0f, MAX_NAV_BAR_ALPHA)
    return BarScrimState(
        statusAlpha = statusAlpha,
        navigationAlpha = navigationAlpha
    )
}

private fun shouldCaptureSnapshot(
    phase: ViewerPhase,
    actorState: TransitionActorState
): Boolean {
    return when (phase) {
        ViewerPhase.Opening -> true
        ViewerPhase.Closing -> actorState == TransitionActorState.ClosingFallback
        ViewerPhase.Opened,
        ViewerPhase.Closed -> false
    }
}

private fun buildTransitionSnapshot(
    phase: ViewerPhase,
    currentPage: Int,
    openOrigin: SharedImageOrigin?,
    closeOrigin: SharedImageOrigin?,
    openingPainter: Painter?,
    cachedPainter: Painter?,
    asyncPainter: Painter,
    fallbackAspect: Float
): TransitionSnapshot {
    val snapshotOrigin = when (phase) {
        ViewerPhase.Opening -> openOrigin
        ViewerPhase.Closing -> closeOrigin
        ViewerPhase.Opened,
        ViewerPhase.Closed -> null
    }
    val painter = openingPainter ?: cachedPainter ?: asyncPainter
    val aspectRatio = (snapshotOrigin?.aspectRatio ?: resolvePainterAspectRatio(painter, fallbackAspect))
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
        TransitionActorState.OpeningShared -> 1f
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

private fun resolveBackdropAlpha(
    phase: ViewerPhase,
    openProgress: Float,
    closeProgress: Float,
    dismissProgress: Float
): Float {
    return when (phase) {
        ViewerPhase.Opening -> openProgress
        ViewerPhase.Opened -> dismissScrimAlpha(dismissProgress)
        ViewerPhase.Closing -> 1f - closeProgress
        ViewerPhase.Closed -> 0f
    }
}

@Composable
private fun BoxScope.EdgeToEdgeStatusBarScrim(alpha: Float, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    val density = LocalDensity.current
    val view = LocalView.current
    val statusBarInsetPx = ViewCompat.getRootWindowInsets(view)
        ?.getInsets(WindowInsetsCompat.Type.statusBars())
        ?.top
        ?: 0
    val statusBarInset = with(density) { statusBarInsetPx.toDp() }
    if (statusBarInset <= 0.dp) return
    Box(
        modifier = modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(statusBarInset)
            .zIndex(2.5f)
            .drawBehind {
                drawRect(Color.Black, alpha = alpha)
            }
    )
}

private fun dismissProgress(
    phase: ViewerPhase,
    dismissOffsetY: Float,
    thresholdPx: Float
): Float {
    if (phase != ViewerPhase.Opened) return 0f
    return (abs(dismissOffsetY) / thresholdPx).coerceIn(0f, 1f)
}

private fun dismissScrimAlpha(progress: Float): Float {
    return (1f - sqrt(progress.coerceIn(0f, 1f))).coerceIn(0f, 1f)
}

private fun dismissContentAlpha(progress: Float): Float {
    return (1f - (progress.coerceIn(0f, 1f) * 0.08f)).coerceIn(0f, 1f)
}

private fun prefetchViewerUrls(rawUrl: String): List<String> {
    if (rawUrl.isBlank()) return emptyList()
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
