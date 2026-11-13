package me.floow.uikit.chat.components.videocircle

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween

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

    /**
     * Telegram/iOS fluid shared element settle duration (320ms).
     * Landing cross-fade is 80ms.
     */
    const val FlightDurationMs = 320
    const val LandingFadeDurationMs = 80

    /**
     * Unified organic fluid easing (0.20, 0.0, 0.0, 1.0).
     * Matches iOS/Telegram natural inertia deceleration.
     */
    val FlightEasing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

    val FlightSpec = tween<Float>(
        durationMillis = FlightDurationMs,
        easing = FlightEasing,
    )

    val Flight = FlightSpec
    val FlightLinearSpec = FlightSpec

    val FlightArcY: androidx.compose.animation.core.Easing = FlightEasing
    val FlightArcX: androidx.compose.animation.core.Easing = FlightEasing
    val FlightScaleEasing: androidx.compose.animation.core.Easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)
    val FlightFadeEasing: androidx.compose.animation.core.Easing = CubicBezierEasing(0.20f, 0.0f, 0.0f, 1.0f)

    const val PauseProgressInDurationMs = 220
    val PauseProgressInEasing = FastOutSlowInEasing

    const val PauseProgressOutDurationMs = 150
    val PauseProgressOutEasing = LinearOutSlowInEasing

    val SwitchCamera: SpringSpec<Float> = SpringSpec(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}
