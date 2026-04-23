package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState

private val FigmaCircleSize = 303.dp
private const val FigmaOverlayHeightPx = 777f
private const val FigmaCircleTopPx = 215f
private val FigmaBottomRowHeight = 54.dp
private val FigmaLockPillLowerOffset = 34.dp
private val FigmaLockPillWidth = 32.dp
private val FigmaLockPillHeight = 39.dp
private val FigmaSwitchCameraSize = 40.dp
private val FigmaSwitchCameraStart = 14.dp
private val FigmaSwitchCameraBottom = FigmaSwitchCameraStart
private val RecordingOverlayDimColor = Color(0x5B0F1015)
private val RecordingOverlayGradientTop = Color(0x48FFFFFF)
private val RecordingOverlayGradientMiddle = Color(0x4E121213)
private val RecordingOverlayGradientBottom = Color(0x7A121213)
private val FigmaOverlayPurple = Color(0xFFA259FF)
private val FigmaSwitchCameraColor = Color(0xFFA5A6FF)

private fun Modifier.consumeOverlayTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        }
    }
}

@Composable
fun VideoRecordingOverlay(
    state: VideoRecordingState,
    bottomInsetPx: Int = 0,
    recordingControlBounds: Rect? = null,
    modifier: Modifier = Modifier,
    onStopClick: () -> Unit = {},
    onSourceMeasured: (Rect) -> Unit = {},
    onDismissFailed: () -> Unit = {},
    onSwitchCameraClick: () -> Unit = {},
    cameraPreview: @Composable BoxScope.() -> Unit = {},
) {
    if (state.mode == VideoRecordingMode.Idle || state.mode == VideoRecordingMode.Sending) return

    val contentAlpha by animateFloatAsState(
        targetValue = if (state.mode == VideoRecordingMode.Recording) 1f else 0.95f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "recording_overlay_alpha",
    )

    val a11yState = when (state.mode) {
        VideoRecordingMode.Recording -> "Recording"
        VideoRecordingMode.Failed -> "Recording failed"
        VideoRecordingMode.Sending,
        VideoRecordingMode.Idle,
        -> "Idle"
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = contentAlpha }
            .semantics {
                stateDescription = a11yState
                liveRegion = LiveRegionMode.Polite
            }
    ) {
        val backdropAlpha by animateFloatAsState(
            targetValue = 1f - state.cancelProgress * 0.45f,
            animationSpec = tween(durationMillis = 90, easing = LinearEasing),
            label = "recording_overlay_backdrop_alpha",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeOverlayTouches()
                .blur(10.dp)
                .graphicsLayer { alpha = backdropAlpha }
                .background(RecordingOverlayDimColor),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeOverlayTouches()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            RecordingOverlayGradientTop,
                            RecordingOverlayGradientMiddle,
                            RecordingOverlayGradientBottom,
                        ),
                    ),
                ),
        )

        when (state.mode) {
            VideoRecordingMode.Recording -> {
                FigmaRecordingOverlay(
                    state = state,
                    bottomInsetPx = bottomInsetPx,
                    onSourceMeasured = onSourceMeasured,
                    onSwitchCameraClick = onSwitchCameraClick,
                    cameraPreview = cameraPreview,
                )
            }
            VideoRecordingMode.Failed -> {
                FailedOverlay(
                    error = state.error,
                    onDismiss = onDismissFailed,
                )
            }
            VideoRecordingMode.Idle,
            VideoRecordingMode.Sending,
            -> Unit
        }
    }
}

@Composable
private fun FigmaRecordingOverlay(
    state: VideoRecordingState,
    bottomInsetPx: Int,
    onSourceMeasured: (Rect) -> Unit,
    onSwitchCameraClick: () -> Unit,
    cameraPreview: @Composable BoxScope.() -> Unit,
) {
    val circleIntroScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "figma_circle_intro_scale",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val _unusedBottomInset = bottomInsetPx
        val circleTop = maxHeight * (FigmaCircleTopPx / FigmaOverlayHeightPx)
        val lockAnchorBottom = FigmaBottomRowHeight

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = circleTop),
            visible = true,
            enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                slideInVertically(
                    initialOffsetY = { it / 5 },
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            RecordingCirclePreview(
                state = state,
                circleIntroScale = circleIntroScale,
                onSourceMeasured = onSourceMeasured,
                cameraPreview = cameraPreview,
            )
        }

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = circleTop + FigmaCircleSize + 24.dp),
            visible = true,
            enter = fadeIn(animationSpec = tween(240, delayMillis = 40)),
            exit = fadeOut(animationSpec = tween(120)),
        ) {
            Text(
                text = "${state.formattedElapsed}/${state.formattedMaxDuration}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        FigmaLockPill(
            lockProgress = state.lockProgress,
            isLocked = state.isLocked,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = lockAnchorBottom),
        )

        SwitchCameraButton(
            onClick = onSwitchCameraClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = FigmaSwitchCameraStart, bottom = FigmaSwitchCameraBottom),
        )
    }
}

@Composable
private fun SwitchCameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(FigmaSwitchCameraSize)
            .clip(CircleShape)
            .background(FigmaSwitchCameraColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = "switch camera",
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun RecordingCirclePreview(
    state: VideoRecordingState,
    circleIntroScale: Float,
    onSourceMeasured: (Rect) -> Unit,
    cameraPreview: @Composable BoxScope.() -> Unit,
) {
    val dismissProgress by animateFloatAsState(
        targetValue = state.cancelProgress,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "recording_circle_dismiss_progress",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(FigmaCircleSize)
            .graphicsLayer {
                val lock = state.lockProgress
                translationX = 0f
                translationY = if (state.isLocked) 0f else state.dragOffsetY * 0.04f
                val dragScale = 1f - dismissProgress * 0.16f + lock * 0.03f
                scaleX = circleIntroScale * dragScale
                scaleY = circleIntroScale * dragScale
                alpha = 1f - dismissProgress * 0.18f
            }
            .onGloballyPositioned { onSourceMeasured(it.boundsInRoot()) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(FigmaOverlayPurple.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            cameraPreview()
        }

        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize(),
        ) {
            val strokeWidth = 5.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * state.progress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun FigmaLockPill(
    lockProgress: Float,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val lockedHop = remember { Animatable(0f) }
    LaunchedEffect(isLocked) {
        if (isLocked) {
            lockedHop.snapTo(0f)
            lockedHop.animateTo(
                targetValue = 1f,
                animationSpec = keyframes {
                    durationMillis = 360
                    0f at 0 using FastOutSlowInEasing
                    -18f at 110 using FastOutSlowInEasing
                    7f at 235 using FastOutSlowInEasing
                    0f at 360 using FastOutSlowInEasing
                },
            )
        } else {
            lockedHop.snapTo(0f)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 120, easing = LinearEasing),
        label = "figma_lock_alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isLocked) 1.08f else (0.92f + lockProgress * 0.08f),
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "figma_lock_scale",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isLocked) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "figma_lock_background",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isLocked) Color.White else Color.Black,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "figma_lock_icon",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isLocked) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        } else {
            Color.Black.copy(alpha = 0.06f)
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "figma_lock_border",
    )

    Box(
        modifier = modifier
            .size(width = FigmaLockPillWidth, height = FigmaLockPillHeight)
            .graphicsLayer {
                this.alpha = alpha
                translationY = if (isLocked) {
                    FigmaLockPillLowerOffset.toPx() + lockedHop.value
                } else {
                    (1f - lockProgress) * 12f
                }
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(41.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(41.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FailedOverlay(
    error: String?,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error ?: "Ошибка записи",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Закрыть",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onDismiss),
            )
        }
    }
}
