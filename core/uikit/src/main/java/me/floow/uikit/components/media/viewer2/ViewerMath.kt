package me.floow.uikit.components.media.viewer2

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.max

fun transformRect(
    baseRect: Rect,
    scale: Float,
    offset: Offset
): Rect {
    val safeScale = scale.coerceAtLeast(0.01f)
    val center = baseRect.center + offset
    val halfWidth = (baseRect.width * safeScale) / 2f
    val halfHeight = (baseRect.height * safeScale) / 2f
    return Rect(
        left = center.x - halfWidth,
        top = center.y - halfHeight,
        right = center.x + halfWidth,
        bottom = center.y + halfHeight
    )
}

fun computeFitRect(
    containerWidthPx: Float,
    containerHeightPx: Float,
    aspectRatio: Float
): Rect {
    val safeWidth = containerWidthPx.coerceAtLeast(1f)
    val safeHeight = containerHeightPx.coerceAtLeast(1f)
    val safeAspect = aspectRatio.coerceAtLeast(0.01f)
    val containerAspect = safeWidth / safeHeight

    return if (containerAspect > safeAspect) {
        val height = safeHeight
        val width = height * safeAspect
        val left = (safeWidth - width) / 2f
        Rect(left, 0f, left + width, height)
    } else {
        val width = safeWidth
        val height = width / safeAspect
        val top = (safeHeight - height) / 2f
        Rect(0f, top, width, top + height)
    }
}

fun fitRectInBounds(
    bounds: Rect,
    aspectRatio: Float
): Rect {
    val inner = computeFitRect(
        containerWidthPx = bounds.width,
        containerHeightPx = bounds.height,
        aspectRatio = aspectRatio
    )
    return Rect(
        left = bounds.left + inner.left,
        top = bounds.top + inner.top,
        right = bounds.left + inner.right,
        bottom = bounds.top + inner.bottom
    )
}

fun lerpRect(start: Rect, end: Rect, progress: Float): Rect {
    val p = progress.coerceIn(0f, 1f)
    return Rect(
        left = lerp(start.left, end.left, p),
        top = lerp(start.top, end.top, p),
        right = lerp(start.right, end.right, p),
        bottom = lerp(start.bottom, end.bottom, p)
    )
}

fun clampOffset(
    rawOffset: Offset,
    containerWidthPx: Float,
    containerHeightPx: Float,
    scale: Float
): Offset {
    val scaledWidth = containerWidthPx * scale
    val scaledHeight = containerHeightPx * scale
    val maxX = max(0f, (scaledWidth - containerWidthPx) / 2f)
    val maxY = max(0f, (scaledHeight - containerHeightPx) / 2f)
    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
}

private fun lerp(start: Float, stop: Float, progress: Float): Float {
    return start + (stop - start) * progress
}

fun viewerSceneProgressForOpened(dismissProgress: Float): Float {
    val clamped = dismissProgress.coerceIn(0f, 1f)
    return 1f - clamped
}

fun viewerSceneProgress(
    phase: ViewerPhase,
    transitionProgress: Float,
    dismissProgress: Float,
    closeSceneStartProgress: Float
): Float {
    val transition = transitionProgress.coerceIn(0f, 1f)
    return when (phase) {
        ViewerPhase.Closed -> 0f
        ViewerPhase.Opening -> transition
        ViewerPhase.Opened -> viewerSceneProgressForOpened(dismissProgress)
        ViewerPhase.Closing -> lerp(closeSceneStartProgress.coerceIn(0f, 1f), 0f, transition)
    }.coerceIn(0f, 1f)
}

fun viewerSourceRevealProgress(
    phase: ViewerPhase,
    sceneProgress: Float,
    transitionProgress: Float = sceneProgress
): Float {
    if (phase != ViewerPhase.Closing) return if (phase == ViewerPhase.Closed) 1f else 0f
    val normalized = ((transitionProgress.coerceIn(0f, 1f)) - 0.9f) / 0.1f
    val clamped = normalized.coerceIn(0f, 1f)
    return clamped * clamped
}
