package me.floow.shared.profile.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.util.SetStatusBarStyle
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class ProfilePage { Header, Posts }

private val ProfilePageSnap = tween<Float>(
    durationMillis = 340,
    easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfilePager(
    postsGridState: LazyGridState,
    hasPostsGrid: Boolean,
    controlsStatusBar: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable (height: Dp) -> Unit,
    posts: @Composable (scrollEnabled: Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier.fillMaxSize().clip(MainShellDefaults.contentShape).background(Color.Black)
    ) {
        val viewportHeight = maxHeight
        if (viewportHeight <= 0.dp) return@BoxWithConstraints

        val peekHeight = (viewportHeight * 0.25f).coerceIn(160.dp, 210.dp)
            .coerceAtMost(viewportHeight * 0.5f)
        val headerHeight = viewportHeight - peekHeight
        val travelPx = with(LocalDensity.current) { headerHeight.roundToPx() }
        val anchors = remember(travelPx) {
            DraggableAnchors {
                ProfilePage.Header at 0f
                ProfilePage.Posts at -travelPx.toFloat()
            }
        }
        val state = rememberSaveable(
            saver = Saver(
                save = { it.settledValue.name },
                restore = { AnchoredDraggableState(ProfilePage.valueOf(it), anchors) },
            ),
        ) {
            AnchoredDraggableState(ProfilePage.Header, anchors)
        }
        SideEffect { state.updateAnchors(anchors, newTarget = state.targetValue) }

        val maxThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
        val positionalThreshold = remember(maxThresholdPx) {
            { distance: Float -> minOf(maxThresholdPx, distance * 0.23f) }
        }
        val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
            state = state,
            positionalThreshold = positionalThreshold,
            animationSpec = ProfilePageSnap,
        )
        val drag = Modifier.anchoredDraggable(
            state = state,
            orientation = Orientation.Vertical,
            flingBehavior = flingBehavior,
        )
        val postsOpen = state.settledValue == ProfilePage.Posts
        val gridHandlesSwipe = postsOpen && hasPostsGrid
        val gridHandoff = remember(state, postsGridState, flingBehavior) {
            object : NestedScrollConnection {
                private var draggingFromGrid = false

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f ||
                        state.settledValue != ProfilePage.Posts || postsGridState.canScrollBackward ||
                        state.isAnimationRunning
                    ) return Offset.Zero

                    val moved = state.dispatchRawDelta(available.y)
                    if (moved > 0f) draggingFromGrid = true
                    return Offset(0f, moved)
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!draggingFromGrid || source != NestedScrollSource.UserInput ||
                        available.y >= 0f
                    ) return Offset.Zero

                    // A reversal moves the pager back first; only the remainder scrolls the grid.
                    val moved = state.dispatchRawDelta(available.y)
                    draggingFromGrid = state.requireOffset() > state.anchors.minPosition()
                    return Offset(0f, moved)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!draggingFromGrid) return Velocity.Zero
                    draggingFromGrid = false
                    state.anchoredDrag { latestAnchors ->
                        val scrollScope = object : ScrollScope {
                            override fun scrollBy(pixels: Float): Float {
                                val offset = state.requireOffset()
                                val next = (offset + pixels).coerceIn(
                                    latestAnchors.minPosition(),
                                    latestAnchors.maxPosition(),
                                )
                                dragTo(next)
                                return next - offset
                            }
                        }
                        with(flingBehavior) { scrollScope.performFling(available.y) }
                    }
                    return Velocity(0f, available.y)
                }
            }
        }
        val scope = rememberCoroutineScope()
        val radius by animateDpAsState(if (postsOpen) 0.dp else 24.dp, label = "profilePostsRadius")
        val handleAlpha by animateFloatAsState(if (postsOpen) 0f else 1f, label = "profilePostsHandle")
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val postsColor = MaterialTheme.colorScheme.surface
        if (controlsStatusBar) {
            val statusBarMidpointPx = with(LocalDensity.current) { statusBarHeight.toPx() / 2f }
            val darkIcons by remember(state, travelPx, statusBarMidpointPx, postsColor) {
                derivedStateOf {
                    // The hero image is darkened; use the surface contrast once posts cover the icons.
                    travelPx + state.requireOffset() <= statusBarMidpointPx &&
                        postsColor.luminance() > 0.179f
                }
            }
            SetStatusBarStyle(color = Color.Transparent, darkIcons = darkIcons)
        }

        Box(
            Modifier.fillMaxSize()
                .profilePagerWheel(state, postsGridState, hasPostsGrid) { page ->
                    scope.launch { state.animateTo(page, ProfilePageSnap) }
                }
                .then(if (gridHandlesSwipe) Modifier else drag),
        ) {
            // Use one offset for both placements so neither block can slide over the other.
            Box(
                Modifier.offset { IntOffset(0, state.requireOffset().roundToInt()) }
                    .fillMaxWidth()
                    .height(headerHeight),
            ) {
                header(headerHeight)
            }
            Column(
                Modifier.offset { IntOffset(0, travelPx + state.requireOffset().roundToInt()) }
                    .fillMaxWidth()
                    .height(viewportHeight)
                    .background(
                        postsColor,
                        RoundedCornerShape(topStart = radius, topEnd = radius),
                    )
                    .nestedScroll(gridHandoff),
            ) {
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(min = statusBarHeight)
                        .then(if (gridHandlesSwipe) drag else Modifier)
                        .semantics {
                            if (postsOpen) {
                                collapse {
                                    scope.launch { state.animateTo(ProfilePage.Header, ProfilePageSnap) }
                                    true
                                }
                            } else {
                                expand {
                                    scope.launch { state.animateTo(ProfilePage.Posts, ProfilePageSnap) }
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BottomSheetDefaults.DragHandle(Modifier.graphicsLayer { alpha = handleAlpha })
                }
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    posts(postsOpen)
                }
            }
        }
    }
}

private fun Modifier.profilePagerWheel(
    state: AnchoredDraggableState<ProfilePage>,
    postsGridState: LazyGridState,
    hasPostsGrid: Boolean,
    onPage: (ProfilePage) -> Unit,
): Modifier = pointerInput(state, postsGridState, hasPostsGrid) {
    awaitPointerEventScope {
        var previousScrollTime: Long? = null
        var consumeWheelGesture = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val pointer = event.changes.firstOrNull() ?: continue
            val delta = pointer.scrollDelta
            if (abs(delta.y) <= abs(delta.x)) continue

            if (previousScrollTime?.let { pointer.uptimeMillis - it > 160L } != false) {
                val target = when {
                    state.settledValue == ProfilePage.Header && delta.y > 0f -> ProfilePage.Posts
                    state.settledValue == ProfilePage.Posts && delta.y < 0f &&
                        (!hasPostsGrid || !postsGridState.canScrollBackward) -> ProfilePage.Header
                    else -> null
                }
                consumeWheelGesture = target != null || state.isAnimationRunning
                if (target != null) onPage(target)
            }
            previousScrollTime = pointer.uptimeMillis
            if (consumeWheelGesture) event.changes.forEach { it.consume() }
        }
    }
}
