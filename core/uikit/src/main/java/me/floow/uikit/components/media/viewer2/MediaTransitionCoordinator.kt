package me.floow.uikit.components.media.viewer2

import kotlin.math.abs
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val DEFAULT_DISMISS_THRESHOLD_PX = 120f
private const val DEFAULT_DISMISS_VISUAL_DISTANCE_MULTIPLIER = 1.75f
private const val DEFAULT_HOST_SCALE_DELTA = 0.05f
private const val DEFAULT_HOST_TRANSLATION_PX = 40f
private const val DEFAULT_HOST_ALPHA_DROP = 0.08f
private const val DEFAULT_BAR_STATUS_ALPHA = 0.22f
private const val DEFAULT_BAR_NAV_ALPHA = 0.12f
private const val DEFAULT_BACKDROP_ALPHA = 1f
private val DEFAULT_DISMISS_THRESHOLD_DP = 120.dp

data class MediaTransitionScene(
    val phase: ViewerPhase,
    val dismissProgress: Float,
    val sceneProgress: Float,
    val backdropAlpha: Float,
    val statusBarAlpha: Float,
    val navigationBarAlpha: Float,
    val sourceRevealProgress: Float,
    val hostScaleX: Float,
    val hostScaleY: Float,
    val hostTranslationY: Float,
    val hostAlpha: Float
)

fun resolveMediaTransitionScene(
    phase: ViewerPhase,
    transitionProgress: Float,
    dismissOffsetY: Float,
    closeSceneStartProgress: Float,
    dismissThresholdPx: Float = DEFAULT_DISMISS_THRESHOLD_PX,
    hostScaleDelta: Float = DEFAULT_HOST_SCALE_DELTA,
    hostTranslationPx: Float = DEFAULT_HOST_TRANSLATION_PX,
    hostAlphaDrop: Float = DEFAULT_HOST_ALPHA_DROP,
    backdropAlpha: Float = DEFAULT_BACKDROP_ALPHA,
    statusBarAlpha: Float = DEFAULT_BAR_STATUS_ALPHA,
    navigationBarAlpha: Float = DEFAULT_BAR_NAV_ALPHA
): MediaTransitionScene {
    val dismissProgress = dismissProgress(phase, dismissOffsetY, dismissThresholdPx)
    val sceneProgress = viewerSceneProgress(
        phase = phase,
        transitionProgress = transitionProgress,
        dismissProgress = dismissProgress,
        closeSceneStartProgress = closeSceneStartProgress
    )
    val hostMotionProgress = easeOutCubic(sceneProgress)
    val hostScale = when (phase) {
        ViewerPhase.Opening -> 1f + (hostScaleDelta * hostMotionProgress)
        ViewerPhase.Opened -> 1f + (hostScaleDelta * hostMotionProgress)
        ViewerPhase.Closing -> 1f + (hostScaleDelta * hostMotionProgress)
        ViewerPhase.Closed -> 1f
    }
    val hostTranslation = when (phase) {
        ViewerPhase.Opening -> -hostTranslationPx * hostMotionProgress
        ViewerPhase.Opened -> -hostTranslationPx * hostMotionProgress
        ViewerPhase.Closing -> -hostTranslationPx * hostMotionProgress
        ViewerPhase.Closed -> 0f
    }
    val hostAlpha = (1f - (hostAlphaDrop * sceneProgress)).coerceIn(0f, 1f)
    return MediaTransitionScene(
        phase = phase,
        dismissProgress = dismissProgress,
        sceneProgress = sceneProgress,
        backdropAlpha = when (phase) {
            ViewerPhase.Opening -> backdropAlpha
            ViewerPhase.Opened -> (backdropAlpha * (1f - dismissProgress)).coerceIn(0f, backdropAlpha)
            ViewerPhase.Closing -> (backdropAlpha * sceneProgress).coerceIn(0f, backdropAlpha)
            ViewerPhase.Closed -> 0f
        },
        statusBarAlpha = when (phase) {
            ViewerPhase.Opening -> statusBarAlpha
            ViewerPhase.Opened -> (statusBarAlpha * (1f - dismissProgress)).coerceIn(0f, statusBarAlpha)
            ViewerPhase.Closing -> (statusBarAlpha * sceneProgress).coerceIn(0f, statusBarAlpha)
            ViewerPhase.Closed -> 0f
        },
        navigationBarAlpha = when (phase) {
            ViewerPhase.Opening -> navigationBarAlpha
            ViewerPhase.Opened -> (navigationBarAlpha * (1f - dismissProgress)).coerceIn(0f, navigationBarAlpha)
            ViewerPhase.Closing -> (navigationBarAlpha * sceneProgress).coerceIn(0f, navigationBarAlpha)
            ViewerPhase.Closed -> 0f
        },
        sourceRevealProgress = viewerSourceRevealProgress(
            phase = phase,
            sceneProgress = sceneProgress,
            transitionProgress = transitionProgress
        ),
        hostScaleX = hostScale,
        hostScaleY = hostScale,
        hostTranslationY = hostTranslation,
        hostAlpha = hostAlpha
    )
}

fun mediaTransitionDismissProgressForOffset(
    dismissOffsetY: Float,
    thresholdPx: Float = DEFAULT_DISMISS_THRESHOLD_PX
): Float {
    val visualThresholdPx = (thresholdPx * DEFAULT_DISMISS_VISUAL_DISTANCE_MULTIPLIER).coerceAtLeast(1f)
    return (abs(dismissOffsetY) / visualThresholdPx).coerceIn(0f, 1f)
}

fun mediaTransitionDismissThresholdPx(
    density: Density,
    thresholdDp: Dp = DEFAULT_DISMISS_THRESHOLD_DP
): Float {
    return thresholdDp.value * density.density
}

fun Modifier.mediaTransitionHostLayer(scene: MediaTransitionScene): Modifier {
    return graphicsLayer {
        scaleX = scene.hostScaleX
        scaleY = scene.hostScaleY
        translationY = scene.hostTranslationY
        alpha = scene.hostAlpha
    }
}

private fun easeOutQuad(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return 1f - (1f - clamped) * (1f - clamped)
}

private fun easeInQuad(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return clamped * clamped
}

private fun easeOutCubic(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    val inv = 1f - clamped
    return 1f - inv * inv * inv
}

private fun dismissProgress(
    phase: ViewerPhase,
    dismissOffsetY: Float,
    thresholdPx: Float
): Float {
    if (phase != ViewerPhase.Opened) return 0f
    return mediaTransitionDismissProgressForOffset(
        dismissOffsetY = dismissOffsetY,
        thresholdPx = thresholdPx
    )
}
