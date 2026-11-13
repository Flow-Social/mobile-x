package me.floow.uikit.components.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.floow.uikit.platform.PlatformBackHandler

interface FlowModalSheetScope {
    val dragHandleModifier: Modifier
    val expansionProgress: Float
}

@Composable
fun FlowModalSheetOverlay(
    entryId: Long,
    onClose: () -> Unit,
    onRevealChanged: (Float) -> Unit = {},
    canDragToDismiss: Boolean,
    shouldClose: () -> Boolean = { true },
    modifier: Modifier = Modifier,
    sheetHeightFraction: Float = 0.72f,
    minSheetHeight: Dp = 440.dp,
    scrimMaxAlpha: Float = 0.32f,
    containerColor: Color = MaterialTheme.colorScheme.background,
    sheetShape: Shape = RoundedCornerShape(28.dp),
    bottomPadding: Dp? = null,
    expandable: Boolean = false,
    expandedSheetHeightFraction: Float = 1f,
    expandRequestKey: Any? = null,
    respectStatusBar: Boolean = true,
    avoidIme: Boolean = false,
    imeAnchorOffsetFromSheetTop: Dp? = null,
    smoothScrollExpand: Boolean = true,
    twoStageDismiss: Boolean = true,
    content: @Composable FlowModalSheetScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var sheetOffsetY by remember(entryId) { mutableFloatStateOf(Float.NaN) }
    var sheetExpansionPx by remember(entryId) { mutableFloatStateOf(0f) }
    var sheetGestureImeDismissRequested by remember(entryId) { mutableStateOf(false) }

    fun requestImeDismissForSheetGesture() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        sheetGestureImeDismissRequested = true
    }

    DisposableEffect(entryId) {
        onDispose {
            onRevealChanged(0f)
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val statusBarTopPx = if (respectStatusBar) WindowInsets.statusBars.getTop(density).toFloat() else 0f
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val navBarBottomDp = with(density) {
            maxOf(
                WindowInsets.navigationBars.getBottom(density),
                WindowInsets.safeDrawing.getBottom(density)
            ).toDp()
        }
        val effectiveBottomPadding = bottomPadding ?: (navBarBottomDp + 8.dp)
        val effectiveBottomPaddingPx = with(density) { effectiveBottomPadding.toPx() }
        val maxSheetHeightPx = (maxHeightPx - statusBarTopPx - effectiveBottomPaddingPx).coerceAtLeast(0f)
        val maxSheetHeight = with(density) { maxSheetHeightPx.toDp() }
        val effectiveMinSheetHeight = minSheetHeight.coerceAtMost(maxSheetHeight)
        val collapsedCardHeight = (maxHeight * sheetHeightFraction).coerceIn(effectiveMinSheetHeight, maxSheetHeight)
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
        val actualImeHeightPx = (imeBottomPx - navigationBarBottomPx).coerceAtLeast(0).toFloat()
        val expandedCardHeight = if (expandable) {
            (maxHeight * expandedSheetHeightFraction).coerceIn(collapsedCardHeight, maxSheetHeight)
        } else {
            collapsedCardHeight
        }
        val collapsedHeightPx = with(density) { collapsedCardHeight.toPx() }
        val expandedHeightPx = with(density) { expandedCardHeight.toPx() }
        val maxExpansionPx = (expandedHeightPx - collapsedHeightPx).coerceAtLeast(0f)
        val currentContainerHeightPx = if (expandable) {
            (collapsedHeightPx + sheetExpansionPx).coerceIn(collapsedHeightPx, expandedHeightPx)
        } else {
            collapsedHeightPx
        }
        val currentContainerHeight = with(density) { currentContainerHeightPx.toDp() }
        val hiddenOffsetPx = currentContainerHeightPx + effectiveBottomPaddingPx
        val dismissThresholdPx = with(density) { 64.dp.toPx() }
        var isClosing by remember(entryId) { mutableStateOf(false) }
        var sheetOffsetJob: Job? by remember(entryId) { mutableStateOf(null) }
        var sheetExpansionJob: Job? by remember(entryId) { mutableStateOf(null) }

        val sheetSettleSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = 450f,
            visibilityThreshold = 0.5f,
        )
        val sheetJingleSpec = spring<Float>(
            dampingRatio = 0.60f,
            stiffness = 270f,
            visibilityThreshold = 0.5f,
        )
        val sheetEnterSpec = tween<Float>(
            durationMillis = 280,
            easing = FastOutSlowInEasing,
        )
        val isImeVisible = actualImeHeightPx > 0f
        val currentIsImeVisible by rememberUpdatedState(isImeVisible)

        fun currentRevealProgress(offset: Float = sheetOffsetY): Float {
            val actualOffset = if (offset.isNaN()) hiddenOffsetPx else offset
            return (1f - (actualOffset / hiddenOffsetPx).coerceIn(0f, 1f)).coerceIn(0f, 1f)
        }

        suspend fun animateSheetOffsetTo(
            targetValue: Float,
            animationSpec: AnimationSpec<Float>,
        ) {
            val startValue = if (sheetOffsetY.isNaN()) targetValue else sheetOffsetY
            val animation = Animatable(startValue)
            animation.animateTo(
                targetValue = targetValue,
                animationSpec = animationSpec,
            ) {
                sheetOffsetY = value
                onRevealChanged(currentRevealProgress(value))
            }
        }

        suspend fun animateSheetExpansionTo(
            targetValue: Float,
            animationSpec: AnimationSpec<Float>,
        ) {
            val animation = Animatable(sheetExpansionPx)
            animation.animateTo(
                targetValue = targetValue,
                animationSpec = animationSpec,
            ) {
                sheetExpansionPx = value
            }
        }

        fun closeWithAnimation() {
            if (isClosing) return
            if (!shouldClose()) {
                sheetOffsetJob?.cancel()
                sheetOffsetJob = scope.launch {
                    val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY
                    if (currentOffset > 0f) {
                        animateSheetOffsetTo(
                            targetValue = 0f,
                            animationSpec = sheetSettleSpec,
                        )
                    }
                }
                return
            }
            isClosing = true
            sheetOffsetJob?.cancel()
            sheetExpansionJob?.cancel()
            sheetOffsetJob = scope.launch {
                try {
                    animateSheetOffsetTo(
                        targetValue = hiddenOffsetPx,
                        animationSpec = sheetSettleSpec,
                    )
                    onRevealChanged(0f)
                    onClose()
                } finally {
                    isClosing = false
                }
            }
        }

        fun settleSheet(velocityY: Float = 0f) {
            if (isClosing) return
            val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY

            if (currentOffset > 0f) {
                if (sheetGestureImeDismissRequested || velocityY < -120f) {
                    sheetOffsetJob?.cancel()
                    sheetOffsetJob = scope.launch {
                        animateSheetOffsetTo(0f, sheetSettleSpec)
                    }
                } else if (currentOffset > dismissThresholdPx || velocityY > 400f) {
                    closeWithAnimation()
                } else {
                    sheetOffsetJob?.cancel()
                    sheetOffsetJob = scope.launch {
                        animateSheetOffsetTo(0f, sheetSettleSpec)
                    }
                }
                return
            }

            if (!expandable || maxExpansionPx <= 0f) {
                if (velocityY > 400f) {
                    closeWithAnimation()
                } else {
                    sheetOffsetJob?.cancel()
                    sheetOffsetJob = scope.launch {
                        animateSheetOffsetTo(0f, sheetSettleSpec)
                    }
                }
                return
            }

            sheetExpansionJob?.cancel()
            val targetExpansion = when {
                velocityY < -150f -> maxExpansionPx
                velocityY > 350f -> 0f
                sheetExpansionPx > maxExpansionPx * 0.38f -> maxExpansionPx
                else -> 0f
            }
            sheetExpansionJob = scope.launch {
                animateSheetExpansionTo(
                    targetValue = targetExpansion,
                    animationSpec = if (targetExpansion == 0f) sheetJingleSpec else sheetSettleSpec,
                )
            }
        }

        val sheetNestedScrollConnection = remember(
            entryId,
            canDragToDismiss,
            expandable,
            hiddenOffsetPx,
            maxExpansionPx,
        ) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (isClosing || !canDragToDismiss) return Offset.Zero
                    val deltaY = available.y
                    if (source == NestedScrollSource.UserInput && kotlin.math.abs(deltaY) > 0.5f) {
                        sheetOffsetJob?.cancel()
                        sheetExpansionJob?.cancel()
                    }
                    if (currentIsImeVisible && deltaY > 0f && !sheetGestureImeDismissRequested) {
                        requestImeDismissForSheetGesture()
                    }
                    val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY
                    if (deltaY < 0f) {
                        if (currentOffset > 0f) {
                            val restore = (-deltaY).coerceAtMost(currentOffset)
                            val next = currentOffset - restore
                            sheetOffsetY = next
                            onRevealChanged(currentRevealProgress(next))
                            val leftover = (-deltaY) - restore
                            if (leftover > 0f && expandable && sheetExpansionPx < maxExpansionPx) {
                                val expand = leftover.coerceAtMost(maxExpansionPx - sheetExpansionPx)
                                sheetExpansionPx += expand
                                return Offset(x = 0f, y = -(restore + expand))
                            }
                            return Offset(x = 0f, y = -restore)
                        } else if (expandable && sheetExpansionPx < maxExpansionPx) {
                            val consumed = (-deltaY).coerceAtMost(maxExpansionPx - sheetExpansionPx)
                            sheetExpansionPx += consumed
                            return Offset(x = 0f, y = -consumed)
                        }
                        return Offset.Zero
                    }
                    if (currentOffset <= 0f) return Offset.Zero
                    val previous = currentOffset
                    val next = (currentOffset + deltaY).coerceIn(0f, hiddenOffsetPx)
                    sheetOffsetY = next
                    onRevealChanged(currentRevealProgress(next))
                    return Offset(x = 0f, y = next - previous)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (isClosing || !canDragToDismiss) return Offset.Zero
                    val deltaY = available.y
                    if (deltaY <= 0f) return Offset.Zero
                    if (source == NestedScrollSource.UserInput && kotlin.math.abs(deltaY) > 0.5f) {
                        sheetOffsetJob?.cancel()
                        sheetExpansionJob?.cancel()
                    }
                    if (currentIsImeVisible && !sheetGestureImeDismissRequested) {
                        requestImeDismissForSheetGesture()
                    }
                    if (expandable && sheetExpansionPx > 0f) {
                        val collapse = deltaY.coerceAtMost(sheetExpansionPx)
                        sheetExpansionPx -= collapse
                        val leftover = deltaY - collapse
                        if (leftover <= 0f) return Offset(x = 0f, y = collapse)
                        val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY
                        val previous = currentOffset
                        val next = (currentOffset + leftover).coerceIn(0f, hiddenOffsetPx)
                        sheetOffsetY = next
                        onRevealChanged(currentRevealProgress(next))
                        return Offset(x = 0f, y = collapse + (next - previous))
                    }
                    val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY
                    val previous = currentOffset
                    val next = (currentOffset + deltaY).coerceIn(0f, hiddenOffsetPx)
                    sheetOffsetY = next
                    onRevealChanged(currentRevealProgress(next))
                    return Offset(x = 0f, y = next - previous)
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (isClosing || !canDragToDismiss) return Velocity.Zero
                    if (kotlin.math.abs(available.y) > 50f) {
                        sheetOffsetJob?.cancel()
                        sheetExpansionJob?.cancel()
                    }
                    if (currentIsImeVisible && available.y > 400f && !sheetGestureImeDismissRequested) {
                        requestImeDismissForSheetGesture()
                    }
                    val currentOffset = sheetOffsetY
                    if (!currentOffset.isNaN() && currentOffset > 0f) {
                        settleSheet(available.y)
                        return available
                    }
                    if (expandable && available.y < -150f && sheetExpansionPx < maxExpansionPx) {
                        settleSheet(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (isClosing || !canDragToDismiss) return Velocity.Zero
                    if (isClosing || !canDragToDismiss) return Velocity.Zero
                    settleSheet(available.y)
                    return available
                }
            }
        }

        PlatformBackHandler(onBack = ::closeWithAnimation)

        LaunchedEffect(entryId) {
            if (sheetOffsetY.isNaN()) {
                isClosing = false
                sheetExpansionPx = 0f
                sheetOffsetY = collapsedHeightPx
                onRevealChanged(0f)
                animateSheetOffsetTo(
                    targetValue = 0f,
                    animationSpec = sheetEnterSpec,
                )
                onRevealChanged(1f)
            }
        }

        var expandedByIme by remember(entryId) { mutableStateOf(false) }

        LaunchedEffect(expandable, actualImeHeightPx > 0f, sheetGestureImeDismissRequested) {
            val isImeOpen = actualImeHeightPx > 0f
            if (expandable && maxExpansionPx > 0f) {
                if (isImeOpen && !sheetGestureImeDismissRequested) {
                    if (sheetExpansionPx < maxExpansionPx * 0.9f) {
                        expandedByIme = true
                    }
                    sheetExpansionJob?.cancel()
                    sheetExpansionJob = scope.launch {
                        animateSheetExpansionTo(
                            targetValue = maxExpansionPx,
                            animationSpec = sheetSettleSpec,
                        )
                    }
                } else if (!isImeOpen && expandedByIme) {
                    expandedByIme = false
                    sheetExpansionJob?.cancel()
                    sheetExpansionJob = scope.launch {
                        animateSheetExpansionTo(
                            targetValue = 0f,
                            animationSpec = sheetJingleSpec,
                        )
                    }
                }
            }
        }

        LaunchedEffect(expandRequestKey, expandable, maxExpansionPx) {
            if (expandRequestKey != null && expandable && maxExpansionPx > 0f) {
                if (currentIsImeVisible && !sheetGestureImeDismissRequested) {
                    requestImeDismissForSheetGesture()
                }
                sheetExpansionJob?.cancel()
                sheetExpansionJob = scope.launch {
                    animateSheetExpansionTo(
                        targetValue = maxExpansionPx,
                        animationSpec = sheetSettleSpec,
                    )
                }
            }
        }

        LaunchedEffect(actualImeHeightPx > 0f) {
            if (actualImeHeightPx <= 0f) {
                sheetGestureImeDismissRequested = false
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val progress = currentRevealProgress()
                    if (progress > 0.001f) {
                        drawRect(Color.Black, alpha = scrimMaxAlpha * progress)
                    }
                }
                .pointerInput(entryId) {
                    detectTapGestures(onTap = {
                        val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY
                        if (!isClosing && currentOffset <= 5f && currentRevealProgress() > 0.01f) {
                            closeWithAnimation()
                        }
                    })
                }
        )

        val sheetScope = remember(
            entryId,
            canDragToDismiss,
            expandable,
            maxExpansionPx,
            hiddenOffsetPx,
        ) {
            object : FlowModalSheetScope {
                override val dragHandleModifier: Modifier = if (!canDragToDismiss) Modifier else Modifier.pointerInput(Unit) {
                    val velocityTracker = VelocityTracker()
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            if (isClosing) return@detectVerticalDragGestures
                            totalDragY = 0f
                            velocityTracker.resetTracking()
                            sheetOffsetJob?.cancel()
                            sheetExpansionJob?.cancel()
                        },
                        onDragEnd = {
                            if (isClosing) return@detectVerticalDragGestures
                            val velocityY = velocityTracker.calculateVelocity().y
                            settleSheet(velocityY)
                        },
                        onDragCancel = {
                            if (isClosing) return@detectVerticalDragGestures
                            settleSheet(0f)
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (isClosing) return@detectVerticalDragGestures
                            change.consume()
                            totalDragY += dragAmount
                            velocityTracker.addPosition(change.uptimeMillis, Offset(0f, totalDragY))

                            val currentOffset = if (sheetOffsetY.isNaN()) 0f else sheetOffsetY
                            if (dragAmount > 0f) { // dragging down
                                if (currentIsImeVisible && !sheetGestureImeDismissRequested) {
                                    requestImeDismissForSheetGesture()
                                }
                                if (expandable && sheetExpansionPx > 0f) {
                                    val collapse = dragAmount.coerceAtMost(sheetExpansionPx)
                                    sheetExpansionPx -= collapse
                                    val leftover = dragAmount - collapse
                                    if (leftover > 0f) {
                                        val next = (currentOffset + leftover).coerceIn(0f, hiddenOffsetPx)
                                        sheetOffsetY = next
                                        onRevealChanged(currentRevealProgress(next))
                                    }
                                } else {
                                    val next = (currentOffset + dragAmount).coerceIn(0f, hiddenOffsetPx)
                                    sheetOffsetY = next
                                    onRevealChanged(currentRevealProgress(next))
                                }
                            } else if (dragAmount < 0f) { // dragging up
                                if (currentOffset > 0f) {
                                    val restore = (-dragAmount).coerceAtMost(currentOffset)
                                    val next = currentOffset - restore
                                    sheetOffsetY = next
                                    onRevealChanged(currentRevealProgress(next))
                                    val leftover = (-dragAmount) - restore
                                    if (leftover > 0f && expandable && sheetExpansionPx < maxExpansionPx) {
                                        sheetExpansionPx = (sheetExpansionPx + leftover).coerceAtMost(maxExpansionPx)
                                    }
                                } else if (expandable && sheetExpansionPx < maxExpansionPx) {
                                    val expand = (-dragAmount).coerceAtMost(maxExpansionPx - sheetExpansionPx)
                                    sheetExpansionPx += expand
                                }
                            }
                        }
                    )
                }
                override val expansionProgress: Float
                    get() = if (maxExpansionPx > 0f) (sheetExpansionPx / maxExpansionPx).coerceIn(0f, 1f) else 0f
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = effectiveBottomPadding)
                .consumeWindowInsets(PaddingValues(bottom = effectiveBottomPadding))
                .heightIn(max = currentContainerHeight)
                .graphicsLayer {
                    val currentOffset = if (sheetOffsetY.isNaN()) hiddenOffsetPx else sheetOffsetY
                    val effectiveOffset = currentOffset.coerceAtLeast(-30f)
                    translationY = effectiveOffset
                    alpha = if (sheetOffsetY.isNaN()) 0f else 1f
                }
                .clip(sheetShape)
                .background(containerColor)
                .nestedScroll(sheetNestedScrollConnection)
        ) {
            sheetScope.content()
        }
    }
}
