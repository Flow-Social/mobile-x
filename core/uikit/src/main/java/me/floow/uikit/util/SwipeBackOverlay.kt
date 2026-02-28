package me.floow.uikit.util

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class HorizontalSwipeZone(
    val boundsInRoot: Rect,
    val atStart: Boolean,
    val blockOverlay: Boolean,
    val priority: Int
)

private class HorizontalSwipeZoneRegistry {
    private val zones = mutableStateMapOf<Any, HorizontalSwipeZone>()

    fun update(
        key: Any,
        boundsInRoot: Rect,
        atStart: Boolean,
        blockOverlay: Boolean,
        priority: Int
    ) {
        zones[key] = HorizontalSwipeZone(
            boundsInRoot = boundsInRoot,
            atStart = atStart,
            blockOverlay = blockOverlay,
            priority = priority
        )
    }

    fun remove(key: Any) {
        zones.remove(key)
    }

    fun findZone(pointInRoot: Offset): HorizontalSwipeZone? {
        val matchingZones = zones.values.filter { it.boundsInRoot.contains(pointInRoot) }
        return matchingZones
            .maxByOrNull { it.priority }
            ?.let { maxPriorityZone ->
                matchingZones
                    .filter { it.priority == maxPriorityZone.priority }
                    .minByOrNull { it.boundsInRoot.width * it.boundsInRoot.height }
            }
    }
}

private val LocalHorizontalSwipeZoneRegistry = staticCompositionLocalOf<HorizontalSwipeZoneRegistry?> {
    null
}

fun Modifier.overlayHorizontalSwipeZone(
    zoneKey: Any,
    atStart: Boolean,
    blockOverlay: Boolean = false,
    priority: Int = 0
): Modifier = composed {
    val registry = LocalHorizontalSwipeZoneRegistry.current ?: return@composed this
    var boundsInRoot by remember(zoneKey) { mutableStateOf<Rect?>(null) }

    DisposableEffect(registry, zoneKey) {
        onDispose {
            registry.remove(zoneKey)
        }
    }

    SideEffect {
        val bounds = boundsInRoot
        if (bounds != null) {
            registry.update(
                key = zoneKey,
                boundsInRoot = bounds,
                atStart = atStart,
                blockOverlay = blockOverlay,
                priority = priority
            )
        }
    }

    this.onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        boundsInRoot = bounds
        registry.update(
            key = zoneKey,
            boundsInRoot = bounds,
            atStart = atStart,
            blockOverlay = blockOverlay,
            priority = priority
        )
    }
}

@Composable
fun SwipeBackOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    edgeWidth: Dp = 0.dp,
    threshold: Dp = 72.dp,
    scrimMaxAlpha: Float = 0.25f,
    onProgressChange: (Float) -> Unit = {},
    canClose: (() -> Boolean)? = null,
    entryKey: Any? = null,
    animateIn: Boolean = true,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val viewConfig = LocalViewConfiguration.current
    val horizontalZoneRegistry = remember(entryKey) { HorizontalSwipeZoneRegistry() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val thresholdPx = with(density) { threshold.toPx() }
        val edgeWidthPx = with(density) { edgeWidth.toPx() }
        val touchSlop = viewConfig.touchSlop
        // "animateIn" is derived from parent state and can flip after first composition (e.g. when
        // FlowNavHost marks the entry as "entered"). Freeze it for this overlay instance to avoid
        // interrupting/restarting the entry animation mid-flight.
        val animateInForInstance = remember(entryKey) { animateIn }

        // Important for smooth entry: when animateIn=true we must start offscreen on the first frame,
        // otherwise the content briefly flashes at x=0 and then jumps behind before animating in.
        val initialOffsetX = if (animateInForInstance && widthPx > 0f) widthPx else 0f
        val offsetX = remember(entryKey, widthPx) { mutableFloatStateOf(initialOffsetX) }
        val animationJob = remember(entryKey) { mutableStateOf<Job?>(null) }
        var isClosing by remember(entryKey) { mutableStateOf(false) }
        var gestureLayerOriginInRoot by remember(entryKey) { mutableStateOf(Offset.Zero) }

        fun animateTo(target: Float, spec: androidx.compose.animation.core.AnimationSpec<Float>) {
            animationJob.value?.cancel()
            isClosing = false
            animationJob.value = coroutineScope.launch {
                animate(
                    initialValue = offsetX.floatValue,
                    targetValue = target,
                    animationSpec = spec
                ) { value, _ ->
                    offsetX.floatValue = value
                }
            }
        }

        fun animateClose() {
            animationJob.value?.cancel()
            isClosing = true
            animationJob.value = coroutineScope.launch {
                animate(
                    initialValue = offsetX.floatValue,
                    targetValue = widthPx,
                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    offsetX.floatValue = value
                }
                if (isClosing) {
                    onClose()
                }
            }
        }

        BackHandler {
            if (canClose?.invoke() != false) {
                animateClose()
            }
        }

        LaunchedEffect(widthPx, entryKey, animateInForInstance) {
            if (widthPx <= 0f) return@LaunchedEffect
            if (animateInForInstance) {
                offsetX.floatValue = widthPx
                animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            } else {
                offsetX.floatValue = 0f
            }
        }

        val progress = if (widthPx == 0f) 0f else (offsetX.floatValue / widthPx).coerceIn(0f, 1f)
        onProgressChange(progress)
        val scrimAlpha = (1f - progress) * scrimMaxAlpha

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.floatValue.roundToInt(), 0) }
                .onGloballyPositioned { coordinates ->
                    gestureLayerOriginInRoot = coordinates.boundsInRoot().topLeft
                }
                .pointerInput(widthPx, thresholdPx, edgeWidthPx, horizontalZoneRegistry) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (isClosing) {
                            animationJob.value?.cancel()
                            isClosing = false
                        }
                        if (edgeWidthPx > 0f && down.position.x > edgeWidthPx) {
                            // Ignore drags that start outside the edge zone.
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                            }
                            return@awaitEachGesture
                        }
                        val downInRoot = gestureLayerOriginInRoot + down.position
                        fun shouldLeaveToChild(): Boolean {
                            val hitZone = horizontalZoneRegistry.findZone(downInRoot)
                            return hitZone?.blockOverlay == true ||
                                (hitZone != null && !hitZone.atStart)
                        }
                        if (shouldLeaveToChild()) {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                            }
                            return@awaitEachGesture
                        }

                        var totalDx = 0f
                        var totalDy = 0f
                        var isSwiping = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            if (shouldLeaveToChild()) {
                                if (isSwiping) {
                                    animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                                }
                                while (true) {
                                    val blockEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val blockChange = blockEvent.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!blockChange.pressed) break
                                }
                                return@awaitEachGesture
                            }

                            val dx = change.position.x - change.previousPosition.x
                            val dy = change.position.y - change.previousPosition.y
                            totalDx += dx
                            totalDy += dy

                            if (!isSwiping) {
                                val absDx = kotlin.math.abs(totalDx)
                                val absDy = kotlin.math.abs(totalDy)
                                val passedSlop = absDx > touchSlop || absDy > touchSlop

                                if (passedSlop) {
                                    if (absDy > absDx) {
                                        return@awaitEachGesture
                                    }
                                    if (totalDx < 0f) {
                                        // Leave left swipes to child (reply).
                                        return@awaitEachGesture
                                    }
                                    isSwiping = true
                                }
                            }

                            if (isSwiping) {
                                change.consume()
                                animationJob.value?.cancel()
                                offsetX.floatValue =
                                    (offsetX.floatValue + dx).coerceIn(0f, widthPx)
                            }
                        }

                        if (isSwiping) {
                            val shouldClose = offsetX.floatValue >= thresholdPx
                            if (shouldClose) {
                                if (canClose?.invoke() != false) {
                                    animateClose()
                                } else {
                                    animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                                }
                            } else {
                                animateTo(0f, spring(stiffness = Spring.StiffnessLow))
                            }
                        }
                    }
                }
        ) {
            CompositionLocalProvider(
                LocalHorizontalSwipeZoneRegistry provides horizontalZoneRegistry
            ) {
                content()
            }
        }
    }
}
