package me.floow.uikit.components.media.viewer2

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
fun ViewerTransitionLayer(
    painter: Painter,
    animatedRect: Rect,
    painterAspectRatio: Float? = null,
    cornerRadiusPx: Float = 0f,
    fitBlend: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (animatedRect.width <= 0f || animatedRect.height <= 0f) return

    val density = LocalDensity.current
    val widthDp = with(density) { animatedRect.width.toDp() }
    val heightDp = with(density) { animatedRect.height.toDp() }
    val cornerRadiusDp = with(density) { cornerRadiusPx.toDp() }
    val blend = fitBlend.coerceIn(0f, 1f)
    val intrinsicSize = painter.intrinsicSize
    val intrinsicAspectRatio = if (
        intrinsicSize.width.isFinite() &&
        intrinsicSize.height.isFinite() &&
        intrinsicSize.width > 0f &&
        intrinsicSize.height > 0f
    ) {
        intrinsicSize.width / intrinsicSize.height
    } else {
        null
    }
    val aspectRatio = (
        painterAspectRatio
            ?: intrinsicAspectRatio
            ?: (animatedRect.width / animatedRect.height)
        ).coerceAtLeast(0.01f)
    val contentRect = resolveMorphContentRect(
        frameRect = animatedRect,
        painterAspectRatio = aspectRatio,
        fitBlend = blend
    )
    val contentWidthDp = with(density) { contentRect.width.toDp() }
    val contentHeightDp = with(density) { contentRect.height.toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .requiredSize(widthDp, heightDp)
                .graphicsLayer {
                    translationX = animatedRect.left
                    translationY = animatedRect.top
                }
                .clip(RoundedCornerShape(cornerRadiusDp))
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .requiredSize(contentWidthDp, contentHeightDp)
                    .graphicsLayer {
                        translationX = contentRect.left - animatedRect.left
                        translationY = contentRect.top - animatedRect.top
                    }
            )
        }
    }
}

private fun resolveMorphContentRect(
    frameRect: Rect,
    painterAspectRatio: Float,
    fitBlend: Float
): Rect {
    val fitRect = fitRectInBounds(
        bounds = frameRect,
        aspectRatio = painterAspectRatio
    )
    val cropRect = computeCropRectInBounds(
        bounds = frameRect,
        aspectRatio = painterAspectRatio
    )
    return lerpRect(cropRect, fitRect, fitBlend)
}

private fun computeCropRectInBounds(
    bounds: Rect,
    aspectRatio: Float
): Rect {
    val safeAspect = aspectRatio.coerceAtLeast(0.01f)
    val boundsAspect = (bounds.width / bounds.height).coerceAtLeast(0.01f)
    return if (boundsAspect > safeAspect) {
        val width = bounds.width
        val height = width / safeAspect
        val top = bounds.top - (height - bounds.height) / 2f
        Rect(
            left = bounds.left,
            top = top,
            right = bounds.right,
            bottom = top + height
        )
    } else {
        val height = bounds.height
        val width = height * safeAspect
        val left = bounds.left - (width - bounds.width) / 2f
        Rect(
            left = left,
            top = bounds.top,
            right = left + width,
            bottom = bounds.bottom
        )
    }
}
