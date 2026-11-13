package me.floow.uikit.components.media.viewer2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter

@Composable
fun ViewerTransitionLayer(
    painter: Painter,
    animatedRect: Rect,
    painterAspectRatio: Float? = null,
    cornerRadiusPx: Float = 0f,
    rotationZ: Float = 0f,
    fitBlend: Float = 1f,
    mediaId: String = "unknown",
    phase: ViewerPhase? = null,
    progress: Float = fitBlend,
    sourceScaleMode: SourceImageScaleMode = SourceImageScaleMode.Fit,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (animatedRect.width <= 0f || animatedRect.height <= 0f) return

    val aspectRatio = (
        painterAspectRatio
            ?: resolvePainterAspectRatio(painter, animatedRect.width / animatedRect.height)
        ).coerceAtLeast(0.01f)
    val geometry = viewerImageGeometry(
        frameRect = animatedRect,
        bitmapAspect = aspectRatio,
        fitBlend = fitBlend
    )
    ViewerImageRenderer(
        painter = painter,
        geometry = geometry,
        cornerRadiusPx = cornerRadiusPx,
        rotationZ = rotationZ,
        modifier = modifier
    )
}

private fun resolvePainterAspectRatio(painter: Painter, fallback: Float): Float {
    val size: Size = painter.intrinsicSize
    val width = size.width
    val height = size.height
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        (width / height).coerceAtLeast(0.01f)
    } else {
        fallback.coerceAtLeast(0.01f)
    }
}
