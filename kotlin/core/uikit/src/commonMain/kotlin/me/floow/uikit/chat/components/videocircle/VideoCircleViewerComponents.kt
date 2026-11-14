package me.floow.uikit.chat.components.videocircle

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

val InactiveCircleSize: Dp = 228.dp
val ActiveCircleSize: Dp = 252.dp

private val SeekKnobDragHitSlop = 44.dp
private val SeekRingHitSlop = 18.dp

/** Telegram: 4dp inset when playing, 16dp extra inset when paused */
private const val PlayingInsetDp = 4f
private const val PauseExtraInsetDp = 16f

/** Telegram-style overshoot interpolation (tension = 1.15) */
fun overshootInterpolation(t: Float): Float {
    val s = 1.15f
    return if (t <= 0f) 0f else if (t >= 1f) 1f else {
        t - (s * (1f - t) * (1f - t) * t) + (s + 1f) * t * t * t - s * t * t
    }
}

/** Animate a single equalizer bar (oscillates 0..1) */
fun animateBar(current: Float, step: Float): Float {
    val direction = if (current >= 1f) -1 else if (current <= 0f) 1 else if (current > 0.5f) 1 else -1
    return (current + step * direction).coerceIn(0f, 1f)
}

@Composable
fun VideoCircleProgressOverlay(
    visible: Boolean,
    isPlaying: Boolean,
    isPaused: Boolean,
    pauseProgress: Float,
    overshootPauseProgress: Float,
    progress: Float,
    progressColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val density = this.density
        val baseInset = if (isPlaying && !isPaused) PlayingInsetDp * density else 0f
        val totalInset = baseInset + PauseExtraInsetDp * density * overshootPauseProgress
        val strokeWidth = (5f * density) + 5f * density * 0.5f * pauseProgress
        val diameter = size.minDimension - strokeWidth - totalInset * 2f
        val topLeft = Offset(
            (size.width - diameter) / 2f,
            (size.height - diameter) / 2f,
        )
        val arcSize = Size(diameter, diameter)
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = diameter / 2f

        if (pauseProgress > 0f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.3f * pauseProgress),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidth),
            )
        }

        drawArc(
            color = Color.White.copy(alpha = 0.15f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        if (progress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        if (overshootPauseProgress > 0f) {
            val knobOffset = progressToRingOffset(
                progress = progress,
                center = center,
                radius = radius,
            )
            val knobRadius = (3f + 5f * overshootPauseProgress) * density
            drawCircle(
                color = Color.White.copy(alpha = overshootPauseProgress.coerceIn(0f, 1f)),
                radius = knobRadius,
                center = knobOffset,
            )
        }
    }
}

@Composable
fun VideoCirclePlayingEqualizer(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val bar1Progress = remember { mutableFloatStateOf(0.47f) }
    val bar2Progress = remember { mutableFloatStateOf(0.0f) }
    val bar3Progress = remember { mutableFloatStateOf(0.32f) }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (true) {
            val now = delay(16).let { 16L }
            bar1Progress.floatValue = animateBar(bar1Progress.floatValue, 16f / 300f)
            bar2Progress.floatValue = animateBar(bar2Progress.floatValue, 16f / 310f)
            bar3Progress.floatValue = animateBar(bar3Progress.floatValue, 16f / 320f)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val density = this.density
        val barWidth = 2f * density
        val barMaxHeight = 8f * density
        val barSpacing = 3f * density
        val barsWidth = 3f * barWidth + 2f * barSpacing
        val startX = size.width - barsWidth - 8f * density
        val startY = size.height - 8f * density
        val playingColor = Color.White.copy(alpha = 0.75f)
        repeat(3) { index ->
            val barProgress = when (index) {
                0 -> bar1Progress.floatValue
                1 -> bar2Progress.floatValue
                else -> bar3Progress.floatValue
            }
            val barHeight = 2f * density + barMaxHeight * barProgress
            val x = startX + index * (barWidth + barSpacing)
            drawRect(
                color = playingColor,
                topLeft = Offset(x, startY - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

fun progressToRingOffset(
    progress: Float,
    center: Offset,
    radius: Float,
): Offset {
    val angleRad = (-90f + 360f * progress.coerceIn(0f, 1f)) * PI.toFloat() / 180f
    return Offset(
        x = center.x + cos(angleRad) * radius,
        y = center.y + sin(angleRad) * radius,
    )
}

fun progressFromTouch(
    position: Offset,
    size: IntSize,
): Float {
    if (size.width <= 0 || size.height <= 0) return 0f
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val angleDeg = atan2(position.y - centerY, position.x - centerX) * 180f / PI.toFloat()
    return ((angleDeg + 90f + 360f) % 360f) / 360f
}

fun isSeekRingTouch(
    position: Offset,
    size: IntSize,
    density: Float,
    isPaused: Boolean = true,
): Boolean {
    if (size.width <= 0 || size.height <= 0) return false
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = seekRingRadius(size, density, isPaused)
    val centerDistance = hypot(position.x - center.x, position.y - center.y)
    val ringHitSlopPx = SeekRingHitSlop.value * density
    return kotlin.math.abs(centerDistance - radius) <= ringHitSlopPx
}

fun isSeekKnobTouch(
    position: Offset,
    size: IntSize,
    progress: Float,
    density: Float,
    isPaused: Boolean = true,
): Boolean {
    if (size.width <= 0 || size.height <= 0) return false
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = seekRingRadius(size, density, isPaused)
    val knob = progressToRingOffset(
        progress = progress,
        center = center,
        radius = radius,
    )
    val knobDistance = hypot(position.x - knob.x, position.y - knob.y)
    return knobDistance <= SeekKnobDragHitSlop.value * density
}

fun seekRingRadius(
    size: IntSize,
    density: Float,
    isPaused: Boolean = true,
): Float {
    val strokeWidth = (5f * density) + (if (isPaused) 5f * density * 0.5f else 0f)
    val totalInset = (if (isPaused) PauseExtraInsetDp else PlayingInsetDp) * density
    val diameter = minOf(size.width, size.height) - strokeWidth - totalInset * 2f
    return (diameter / 2f).coerceAtLeast(1f)
}

fun Modifier.videoCircleSeekGesture(
    isProgressVisible: Boolean,
    isPaused: Boolean,
    progress: Float,
    onSeekingChange: (Boolean) -> Unit,
    consumeTapUp: Boolean = true,
    onTogglePlayback: () -> Unit,
    onLongPress: () -> Unit,
    onSeekProgress: (Float) -> Unit,
): Modifier = pointerInput(isProgressVisible, isPaused, consumeTapUp) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial,
        )

        if (!isProgressVisible) {
            awaitVideoCircleTapOrLongPress(
                down = down,
                consumeTapUp = consumeTapUp,
                onTap = onTogglePlayback,
                onLongPress = onLongPress,
            )
            return@awaitEachGesture
        }

        val startedOnKnob = isSeekKnobTouch(
            position = down.position,
            size = size,
            progress = progress,
            density = this@pointerInput.density,
            isPaused = isPaused,
        )
        val startedOnRing = !startedOnKnob && isSeekRingTouch(
            position = down.position,
            size = size,
            density = this@pointerInput.density,
            isPaused = isPaused,
        )

        if (!startedOnKnob && !startedOnRing) {
            awaitVideoCircleTapOrLongPress(
                down = down,
                consumeTapUp = consumeTapUp,
                onTap = onTogglePlayback,
                onLongPress = onLongPress,
            )
            return@awaitEachGesture
        }

        var seekStarted = false
        down.consume()
        onSeekingChange(true)
        try {
            if (startedOnRing) {
                seekStarted = true
                val nextProgress = progressFromTouch(down.position, size)
                onSeekProgress(nextProgress)
            }

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val dragDistance = hypot(
                    change.position.x - down.position.x,
                    change.position.y - down.position.y,
                )
                if (startedOnKnob && !seekStarted && dragDistance > viewConfiguration.touchSlop) {
                    seekStarted = true
                }
                if (startedOnKnob && seekStarted) {
                    change.consume()
                    val nextProgress = progressFromTouch(change.position, size)
                    onSeekProgress(nextProgress)
                } else if (startedOnRing) {
                    change.consume()
                    val nextProgress = progressFromTouch(change.position, size)
                    onSeekProgress(nextProgress)
                }
            }
        } finally {
            onSeekingChange(false)
        }
    }
}

private suspend fun AwaitPointerEventScope.awaitVideoCircleTapOrLongPress(
    down: PointerInputChange,
    consumeTapUp: Boolean = true,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    var upChange: PointerInputChange? = null
    val completedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
                ?: return@withTimeoutOrNull false
            if (!change.pressed) {
                upChange = change
                return@withTimeoutOrNull true
            }

            val dragDistance = hypot(
                change.position.x - down.position.x,
                change.position.y - down.position.y,
            )
            if (dragDistance > viewConfiguration.touchSlop) {
                return@withTimeoutOrNull false
            }
        }
    }

    when (completedBeforeLongPress) {
        null -> {
            onLongPress()
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return
                change.consume()
                if (!change.pressed) return
            }
        }
        true -> {
            if (consumeTapUp) {
                upChange?.consume()
            }
            onTap()
        }
        false -> Unit
    }
}
