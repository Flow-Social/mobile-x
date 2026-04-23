package me.floow.chats.videocircle

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun FlyingCircleOverlay(
    sourceBounds: Rect,
    targetBounds: Rect,
    phase: FlightPhase,
    onFlightArrived: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val progressTarget = when (phase) {
        FlightPhase.Start -> 0f
        FlightPhase.InFlight,
        FlightPhase.Settled -> 1f
    }
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = VideoCircleMotion.Flight,
        label = "video_circle_flight_progress",
    )
    val settledAlpha by animateFloatAsState(
        targetValue = if (phase == FlightPhase.Settled) 0f else 1f,
        animationSpec = VideoCircleMotion.OverlayEntrance,
        label = "video_circle_flight_alpha",
    )
    var arrivalDispatched by remember(sourceBounds, targetBounds, phase) { mutableStateOf(false) }
    if (phase == FlightPhase.InFlight && progress >= 0.995f && !arrivalDispatched) {
        arrivalDispatched = true
        onFlightArrived()
    }

    val sourceCenter = sourceBounds.center
    val targetCenter = targetBounds.center
    val center = Offset(
        x = sourceCenter.x + (targetCenter.x - sourceCenter.x) * progress,
        y = sourceCenter.y + (targetCenter.y - sourceCenter.y) * progress,
    )

    val sourceSizePx = sourceBounds.width
    val targetSizePx = targetBounds.width
    val scale = if (sourceSizePx > 0f) {
        val targetScale = targetSizePx / sourceSizePx
        1f + (targetScale - 1f) * progress
    } else {
        1f
    }
    val easedScale = if (phase == FlightPhase.Settled) scale * 0.96f else scale
    val animatedShadowSize by animateDpAsState(
        targetValue = if (phase == FlightPhase.InFlight) 10.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = 240,
            easing = androidx.compose.animation.core.FastOutSlowInEasing,
        ),
        label = "video_circle_flight_shadow",
    )

    val baseSize: Dp = with(density) {
        if (sourceSizePx > 0f) sourceSizePx.toDp() else 192.dp
    }
    val baseHalfSizePx = with(density) { (baseSize / 2f).toPx() }

    Box(modifier = modifier.fillMaxSize()) {
        VideoCircleShell(
            size = baseSize,
            modifier = Modifier
                .graphicsLayer {
                    translationX = center.x - baseHalfSizePx
                    translationY = center.y - baseHalfSizePx
                    scaleX = easedScale
                    scaleY = easedScale
                    alpha = settledAlpha
                    shadowElevation = animatedShadowSize.toPx()
                }
                .size(baseSize),
        )
    }
}
