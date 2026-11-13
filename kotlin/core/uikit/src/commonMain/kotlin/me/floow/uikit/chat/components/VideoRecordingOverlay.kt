package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

private val FigmaCircleSize = 340.dp
private const val FigmaOverlayHeightPx = 777f
private const val FigmaCircleTopPx = 215f
private val FigmaLockAnchorBottom = 108.dp
private val FigmaLockAnchorEnd = 23.dp
private val FigmaLockPillLowerOffset = 34.dp
private val FigmaLockPillWidth = 32.dp
private val FigmaLockPillHeight = 39.dp
private val FigmaSwitchCameraSize = 40.dp
private val FigmaSwitchCameraStart = 16.dp
private val FigmaSwitchCameraBottom = 82.dp
private val RecordingOverlayDimColor = Color(0x5B0F1015)
private val RecordingOverlayGradientTop = Color(0x48FFFFFF)
private val RecordingOverlayGradientMiddle = Color(0x4E121213)
private val RecordingOverlayGradientBottom = Color(0x7A121213)
private val FigmaOverlayPurple = Color(0xFFA259FF)

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
    onTogglePreviewPlayback: () -> Unit = {},
    cameraPreview: @Composable BoxScope.() -> Unit = {},
    videoPreview: @Composable (BoxScope.() -> Unit)? = null,
) {
    if (state.mode == VideoRecordingMode.Idle) return

    val contentAlpha by animateFloatAsState(
        targetValue = if (state.mode == VideoRecordingMode.Recording || state.mode == VideoRecordingMode.Preview || state.mode == VideoRecordingMode.Sending) 1f else 0.95f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "recording_overlay_alpha",
    )

    val a11yState = when (state.mode) {
        VideoRecordingMode.Recording -> "Recording"
        VideoRecordingMode.Preview -> "Preview"
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
            VideoRecordingMode.Recording,
            VideoRecordingMode.Preview,
            VideoRecordingMode.Sending -> {
                FigmaRecordingOverlay(
                    state = state,
                    bottomInsetPx = bottomInsetPx,
                    onSourceMeasured = onSourceMeasured,
                    onSwitchCameraClick = onSwitchCameraClick,
                    onTogglePreviewPlayback = onTogglePreviewPlayback,
                    onPauseClick = onStopClick,
                    cameraPreview = cameraPreview,
                    videoPreview = videoPreview,
                )
            }
            VideoRecordingMode.Failed -> {
                FailedOverlay(
                    error = state.error,
                    onDismiss = onDismissFailed,
                )
            }
            VideoRecordingMode.Idle -> Unit
        }
    }
}

@Composable
private fun FigmaRecordingOverlay(
    state: VideoRecordingState,
    bottomInsetPx: Int,
    onSourceMeasured: (Rect) -> Unit,
    onSwitchCameraClick: () -> Unit,
    onTogglePreviewPlayback: () -> Unit,
    onPauseClick: () -> Unit,
    cameraPreview: @Composable BoxScope.() -> Unit,
    videoPreview: @Composable (BoxScope.() -> Unit)?,
) {
    val circleIntroScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "figma_circle_intro_scale",
    )
    val density = LocalDensity.current
    val bottomInsetDp = with(density) { bottomInsetPx.toDp() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        val lockAnchorBottom = FigmaLockAnchorBottom
        val topMargin = 72.dp
        val bottomMargin = 82.dp + bottomInsetDp
        val availableHeight = (maxHeight - topMargin - bottomMargin).coerceAtLeast(100.dp)
        val centerOffset = (topMargin - bottomMargin) / 2

        val animatedCenterOffsetY by animateDpAsState(
            targetValue = centerOffset,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "recording_circle_center_offset",
        )
        val maxAllowedSize = minOf(availableHeight - 16.dp, maxWidth - 32.dp)
        val targetCircleSize = if (maxAllowedSize < FigmaCircleSize) {
            maxAllowedSize.coerceIn(180.dp, FigmaCircleSize)
        } else {
            FigmaCircleSize
        }
        val animatedCircleSize by animateDpAsState(
            targetValue = targetCircleSize,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "recording_circle_size",
        )
        val animatedLockBottom by animateDpAsState(
            targetValue = lockAnchorBottom + bottomInsetDp,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "recording_lock_bottom",
        )
        val animatedSwitchCameraBottom by animateDpAsState(
            targetValue = FigmaSwitchCameraBottom + bottomInsetDp,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "recording_switch_camera_bottom",
        )

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = animatedCenterOffsetY),
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
                circleSize = animatedCircleSize,
                onSourceMeasured = onSourceMeasured,
                onTogglePreviewPlayback = onTogglePreviewPlayback,
                cameraPreview = cameraPreview,
                videoPreview = videoPreview,
            )
        }

        if (state.mode != VideoRecordingMode.Preview && state.mode != VideoRecordingMode.Sending) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = FigmaLockAnchorEnd, bottom = animatedLockBottom)
                    .width(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                FigmaLockPill(
                    lockProgress = state.lockProgress,
                    isLocked = state.isLocked,
                    onPauseClick = onPauseClick,
                )
            }

            SwitchCameraButton(
                onClick = onSwitchCameraClick,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = FigmaSwitchCameraStart, bottom = animatedSwitchCameraBottom),
            )
        }
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
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = "switch camera",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RecordingCirclePreview(
    state: VideoRecordingState,
    circleIntroScale: Float,
    circleSize: Dp = FigmaCircleSize,
    onSourceMeasured: (Rect) -> Unit,
    onTogglePreviewPlayback: () -> Unit,
    cameraPreview: @Composable BoxScope.() -> Unit,
    videoPreview: @Composable (BoxScope.() -> Unit)?,
) {
    val dismissProgress by animateFloatAsState(
        targetValue = state.cancelProgress,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "recording_circle_dismiss_progress",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(circleSize)
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
                .background(FigmaOverlayPurple.copy(alpha = 0.3f))
                .then(
                    if (state.mode == VideoRecordingMode.Preview) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTogglePreviewPlayback,
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (state.mode == VideoRecordingMode.Preview && videoPreview != null) {
                videoPreview()
            } else {
                cameraPreview()
            }
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
                sweepAngle = if (state.mode == VideoRecordingMode.Preview) 360f else (360f * state.progress),
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
    onPauseClick: () -> Unit = {},
) {
    RecordingLockPill(
        isLocked = isLocked,
        lockProgress = lockProgress,
        onPauseClick = onPauseClick,
        modifier = modifier,
    )
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
