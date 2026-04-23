package me.floow.chats.videocircle

import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.uikit.util.overlayHorizontalSwipeZone
import java.io.File
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

private val InactiveCircleSize = 228.dp
private val ActiveCircleSize = 252.dp
private val SeekKnobDragHitSlop = 44.dp
private val SeekRingHitSlop = 18.dp
private const val SeekDebugTag = "VideoCircleSeek"
/** Telegram: 4dp inset when playing, 16dp extra inset when paused */
private val PlayingInsetDp = 4f
private val PauseExtraInsetDp = 16f

@Composable
fun MessageCirclePlayer(
    videoPath: String,
    player: ExoPlayer?,
    isPlaying: Boolean,
    isPaused: Boolean = false,
    autoplayMutedLoop: Boolean = false,
    onTogglePlayback: () -> Unit,
    onSeekProgress: (Float) -> Unit = {},
    onSeekInteractionChange: (Boolean) -> Unit = {},
    onPlaybackEnded: () -> Unit = {},
    onBoundsMeasured: (Rect) -> Unit = {},
    alpha: Float = 1f,
    growFromEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val progress = remember { mutableFloatStateOf(0f) }
    val progressColor = MaterialTheme.colorScheme.primary
    var showThumbnailFallback by remember(videoPath) { mutableStateOf(false) }
    var isSeeking by remember(videoPath) { mutableStateOf(false) }
    val knobSwipeZoneKey = remember(videoPath) { Any() }
    val currentOnTogglePlayback by rememberUpdatedState(onTogglePlayback)
    val currentOnSeekProgress by rememberUpdatedState(onSeekProgress)

    val isActive = isPlaying || isPaused || autoplayMutedLoop

    // Telegram: pause progress animates 220ms in (overshoot), 150ms out
    val pauseProgress by animateFloatAsState(
        targetValue = if (isPaused) 1f else 0f,
        animationSpec = if (isPaused) {
            tween(durationMillis = VideoCircleMotion.PauseProgressInDurationMs, easing = VideoCircleMotion.PauseProgressInEasing)
        } else {
            tween(durationMillis = VideoCircleMotion.PauseProgressOutDurationMs, easing = VideoCircleMotion.PauseProgressOutEasing)
        },
        label = "pause_progress",
    )
    val animatedAlpha by animateFloatAsState(
        targetValue = alpha,
        animationSpec = VideoCircleMotion.BubblePress,
        label = "message_circle_alpha",
    )

    // One-shot spring entrance for a freshly-appeared bubble (e.g. just-recorded clip).
    val entranceProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        entranceProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = 0.62f,
                stiffness = 380f,
            ),
        )
    }
    val entranceScale = 0.55f + entranceProgress.value * 0.45f
    val entranceAlpha = entranceProgress.value

    // Overshoot interpolation for pause entrance (Telegram: overshootInterpolator)
    val overshootPauseProgress = if (isPaused) {
        overshootInterpolation(pauseProgress)
    } else {
        pauseProgress
    }

    // Equalizer bars state for playing indicator
    val bar1Progress = remember { mutableFloatStateOf(0.47f) }
    val bar2Progress = remember { mutableFloatStateOf(0.0f) }
    val bar3Progress = remember { mutableFloatStateOf(0.32f) }

    LaunchedEffect(isPlaying, isPaused, autoplayMutedLoop, player, isSeeking) {
        val currentPlayer = player
        if (!isActive || currentPlayer == null) {
            progress.floatValue = 0f
            return@LaunchedEffect
        }
        while (true) {
            if (!isSeeking) {
                val duration = currentPlayer.duration.takeIf { it > 0L } ?: 0L
                val position = currentPlayer.currentPosition.coerceAtLeast(0L)
                progress.floatValue = if (duration > 0L) {
                    (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
            delay(32)
        }
    }

    // Animate equalizer bars when playing (not paused)
    LaunchedEffect(isActive, isPaused) {
        if (!isActive || isPaused) return@LaunchedEffect
        var lastTime = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            var dt = (now - lastTime).coerceAtMost(50)
            lastTime = now
            bar1Progress.floatValue = animateBar(bar1Progress.floatValue, dt / 300f)
            bar2Progress.floatValue = animateBar(bar2Progress.floatValue, dt / 310f)
            bar3Progress.floatValue = animateBar(bar3Progress.floatValue, dt / 320f)
            delay(16)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(if (growFromEnd) Alignment.End else Alignment.Start),
        contentAlignment = if (growFromEnd) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        val activeCircleSize = maxWidth.takeIf { it > 0.dp } ?: ActiveCircleSize
        val circleSize by animateDpAsState(
            targetValue = if (isActive) activeCircleSize else InactiveCircleSize,
            animationSpec = tween(durationMillis = 180),
            label = "video_circle_size",
        )

        Box(
            modifier = Modifier
                .size(circleSize)
                .onGloballyPositioned { onBoundsMeasured(it.boundsInRoot()) }
                .graphicsLayer {
                    scaleX = entranceScale
                    scaleY = entranceScale
                    this.alpha = animatedAlpha * entranceAlpha
                    transformOrigin = if (growFromEnd) {
                        androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                    } else {
                        androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                }
                .semantics {
                    stateDescription = if (isPlaying) "Video circle playing" else "Video circle paused"
                }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            VideoCircleThumbnail(
                videoPath = videoPath,
                shellSize = circleSize,
                modifier = Modifier.fillMaxSize(),
            )

            if (player != null && !showThumbnailFallback) {
                CircleVideoSurface(
                    player = player,
                    onSourceError = { showThumbnailFallback = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Progress ring + pause overlay (Telegram: drawRoundProgress)
            if (isActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val density = this.density
                    val baseInset = if (isPlaying && !isPaused) PlayingInsetDp * density else 0f
                    // Telegram: inset += dp(16) * pauseProgress (ring shrinks when paused)
                    val totalInset = baseInset + PauseExtraInsetDp * density * overshootPauseProgress
                    val strokeWidth = (5f * density) + 5f * density * 0.5f * pauseProgress
                    val diameter = size.minDimension - strokeWidth - totalInset * 2f
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f,
                    )
                    val arcSize = Size(diameter, diameter)
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val radius = diameter / 2f

                    // Telegram: dim background circle when paused (30% alpha)
                    if (pauseProgress > 0f) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f * pauseProgress),
                            radius = radius,
                            center = Offset(centerX, centerY),
                            style = Stroke(width = strokeWidth),
                        )
                    }

                    // Background ring (full circle, dim)
                    drawArc(
                        color = Color.White.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )

                    // Progress arc
                    if (progress.floatValue > 0f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress.floatValue,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }

                    // Telegram: seek knob when paused (white dot at progress position on ring)
                    if (overshootPauseProgress > 0f) {
                        val knobOffset = progressToRingOffset(
                            progress = progress.floatValue,
                            center = Offset(centerX, centerY),
                            radius = radius,
                        )
                        val knobRadius = (3f + 5f * overshootPauseProgress) * density
                        drawCircle(
                            color = Color.White.copy(alpha = overshootPauseProgress),
                            radius = knobRadius,
                            center = knobOffset,
                        )
                    }
                }
            }

            // Playing indicator: 3 animated equalizer bars (Telegram: RoundVideoPlayingDrawable)
            if (isActive && !isPaused) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    val density = this.density
                    val barWidth = 2f * density
                    val barMaxHeight = 8f * density
                    val barSpacing = 3f * density
                    val barsWidth = 3f * barWidth + 2f * barSpacing
                    // Bottom-right corner, offset inward
                    val startX = size.width - barsWidth - 6f * density
                    val startY = size.height - 6f * density

                    val playingColor = Color.White.copy(alpha = 0.7f)
                    for (i in 0..2) {
                        val barProgress = when (i) {
                            0 -> bar1Progress.floatValue
                            1 -> bar2Progress.floatValue
                            else -> bar3Progress.floatValue
                        }
                        val barHeight = 2f * density + barMaxHeight * barProgress
                        val x = startX + i * (barWidth + barSpacing)
                        drawRect(
                            color = playingColor,
                            topLeft = Offset(x, startY - barHeight),
                            size = Size(barWidth, barHeight),
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isPaused) {
                            Modifier.overlayHorizontalSwipeZone(
                                zoneKey = knobSwipeZoneKey,
                                atStart = true,
                                blockOverlay = true,
                                priority = 200,
                            )
                        } else {
                            Modifier
                        }
                    )
                    .pointerInput(isPaused, player) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            val currentPlayer = player
                            if (currentPlayer == null) {
                                Log.d(SeekDebugTag, "down ignored: player=null position=${down.position}")
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    up.consume()
                                    currentOnTogglePlayback()
                                }
                                return@awaitEachGesture
                            }

                            if (!isPaused) {
                                Log.d(SeekDebugTag, "down ignored: not paused position=${down.position}")
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    up.consume()
                                    currentOnTogglePlayback()
                                }
                                return@awaitEachGesture
                            }

                            val startedOnKnob = isSeekKnobTouch(
                                position = down.position,
                                size = size,
                                progress = progress.floatValue,
                                density = this@pointerInput.density,
                            )
                            val startedOnRing = !startedOnKnob && isSeekRingTouch(
                                position = down.position,
                                size = size,
                                density = this@pointerInput.density,
                            )
                            Log.d(
                                SeekDebugTag,
                                "down position=${down.position} size=$size startedOnKnob=$startedOnKnob startedOnRing=$startedOnRing progress=${progress.floatValue}",
                            )

                            if (!startedOnKnob && !startedOnRing) {
                                val up = waitForUpOrCancellation()
                                if (up != null) {
                                    up.consume()
                                    currentOnTogglePlayback()
                                }
                                return@awaitEachGesture
                            }

                            var seekStarted = false
                            down.consume()
                            isSeeking = true
                            try {
                                if (startedOnRing) {
                                    seekStarted = true
                                    seekToTouchProgress(currentPlayer, down.position, size, progress, currentOnSeekProgress)
                                }

                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (!change.pressed) {
                                        Log.d(
                                            SeekDebugTag,
                                            "up seekStarted=$seekStarted lastProgress=${progress.floatValue}",
                                        )
                                        break
                                    }

                                    val dragDistance = hypot(
                                        change.position.x - down.position.x,
                                        change.position.y - down.position.y,
                                    )
                                    Log.d(
                                        SeekDebugTag,
                                        "move position=${change.position} dragDistance=$dragDistance seekStarted=$seekStarted consumed=${change.isConsumed}",
                                    )
                                    if (startedOnKnob && !seekStarted && dragDistance > viewConfiguration.touchSlop) {
                                        seekStarted = true
                                        Log.d(SeekDebugTag, "seek started by drag touchSlop=${viewConfiguration.touchSlop}")
                                    }
                                    if (startedOnKnob && seekStarted) {
                                        change.consume()
                                        seekToTouchProgress(currentPlayer, change.position, size, progress, currentOnSeekProgress)
                                    } else if (startedOnRing) {
                                        change.consume()
                                    }
                                }
                            } finally {
                                Log.d(
                                    SeekDebugTag,
                                    "finish seekStarted=$seekStarted progress=${progress.floatValue}",
                                )
                                isSeeking = false
                            }
                        }
                    },
            )
        }
    }
}

@Composable
private fun VideoCircleThumbnail(
    videoPath: String,
    shellSize: Dp,
    modifier: Modifier = Modifier,
) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, videoPath) {
        value = withContext(Dispatchers.IO) {
            extractFirstFrame(videoPath)
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        VideoCircleShell(size = shellSize)
    }
}

private fun progressToRingOffset(
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

private fun progressFromTouch(
    position: Offset,
    size: IntSize,
): Float {
    if (size.width <= 0 || size.height <= 0) return 0f
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val angleDeg = atan2(position.y - centerY, position.x - centerX) * 180f / PI.toFloat()
    return ((angleDeg + 90f + 360f) % 360f) / 360f
}

private fun isSeekRingTouch(
    position: Offset,
    size: IntSize,
    density: Float,
): Boolean {
    if (size.width <= 0 || size.height <= 0) return false
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = seekRingRadius(size, density)
    val centerDistance = hypot(position.x - center.x, position.y - center.y)
    val ringHitSlopPx = SeekRingHitSlop.value * density
    return kotlin.math.abs(centerDistance - radius) <= ringHitSlopPx
}

private fun isSeekKnobTouch(
    position: Offset,
    size: IntSize,
    progress: Float,
    density: Float,
): Boolean {
    if (size.width <= 0 || size.height <= 0) return false
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = seekRingRadius(size, density)
    val knob = progressToRingOffset(
        progress = progress,
        center = center,
        radius = radius,
    )
    val knobDistance = hypot(position.x - knob.x, position.y - knob.y)
    return knobDistance <= SeekKnobDragHitSlop.value * density
}

private fun seekRingRadius(
    size: IntSize,
    density: Float,
): Float {
    val pausedStrokeWidth = (5f * density) + 5f * density * 0.5f
    val pausedInset = PauseExtraInsetDp * density
    return ((minOf(size.width, size.height) - pausedStrokeWidth - pausedInset * 2f) / 2f)
        .coerceAtLeast(1f)
}

private fun seekToTouchProgress(
    player: ExoPlayer,
    position: Offset,
    size: IntSize,
    progress: androidx.compose.runtime.MutableFloatState,
    onSeekProgress: (Float) -> Unit,
) {
    player.duration.takeIf { it > 0L } ?: return
    val nextProgress = progressFromTouch(position, size)
    progress.floatValue = nextProgress
    Log.d(
        SeekDebugTag,
        "seek position=$position size=$size nextProgress=$nextProgress duration=${player.duration}",
    )
    onSeekProgress(nextProgress)
}

private fun extractFirstFrame(videoPath: String): ImageBitmap? {
    val mediaSource = resolveLocalMediaSource(videoPath) ?: return null
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(mediaSource)
            retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.asImageBitmap()
        }
    }.getOrNull()
}

private fun resolveLocalMediaSource(videoPath: String): String? {
    return when {
        videoPath.startsWith("content://", ignoreCase = true) ||
            videoPath.startsWith("file://", ignoreCase = true) ||
            videoPath.startsWith("http://", ignoreCase = true) ||
            videoPath.startsWith("https://", ignoreCase = true) -> videoPath

        else -> File(videoPath)
            .takeIf { it.exists() && it.length() > 0L }
            ?.absolutePath
    }
}

/** Telegram-style overshoot interpolation (tension = 2.0) */
private fun overshootInterpolation(t: Float): Float {
    val s = 1.15f
    return if (t == 0f) 0f else if (t >= 1f) 1f else {
        t - (s * (1f - t) * (1f - t) * t) + (s + 1f) * t * t * t - s * t * t
    }
}

/** Animate a single equalizer bar (oscillates 0..1) */
private fun animateBar(current: Float, step: Float): Float {
    val direction = if (current >= 1f) -1 else if (current <= 0f) 1 else if (current > 0.5f) 1 else -1
    return (current + step * direction).coerceIn(0f, 1f)
}
