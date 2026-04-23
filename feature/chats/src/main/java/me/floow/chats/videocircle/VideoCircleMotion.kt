package me.floow.chats.videocircle

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearOutSlowInEasing

object VideoCircleMotion {
    val HoldStart: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    val BubblePress: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    val OverlayEntrance = tween<Float>(
        durationMillis = 240,
        easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    )

    /** Closer to Telegram shared element settle than the previous dry 180ms hop. */
    val Flight = tween<Float>(
        durationMillis = 260,
        easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f),
    )

    /** Telegram: ~220ms for pause progress in (roundToPauseProgress += 16/220f) */
    const val PauseProgressInDurationMs = 220
    val PauseProgressInEasing = FastOutSlowInEasing

    /** Telegram: ~150ms for pause progress out (roundToPauseProgress -= 16/150f) */
    const val PauseProgressOutDurationMs = 150
    val PauseProgressOutEasing = LinearOutSlowInEasing

    val SwitchCamera: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
