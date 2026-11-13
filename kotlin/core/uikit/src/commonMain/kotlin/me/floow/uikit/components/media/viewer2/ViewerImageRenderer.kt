package me.floow.uikit.components.media.viewer2

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale

data class ViewerImageGeometry(
    val layoutRect: Rect,
    val drawRect: Rect,
    val clipRect: Rect,
    val visibleBitmapRect: Rect,
    val bitmapAspect: Float,
    val contentScale: ContentScale = ContentScale.Fit
)

@Composable
fun ViewerImageRenderer(
    painter: Painter,
    geometry: ViewerImageGeometry,
    cornerRadiusPx: Float = 0f,
    rotationZ: Float = 0f,
    alpha: Float = 1f,
    modifier: Modifier = Modifier
) {
    if (
        geometry.layoutRect.width <= 0f ||
        geometry.layoutRect.height <= 0f ||
        geometry.drawRect.width <= 0f ||
        geometry.drawRect.height <= 0f
    ) return

    val reusablePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha }
    ) {
        val layout = geometry.layoutRect
        val draw = geometry.drawRect

        val renderBlock = {
            if (cornerRadiusPx > 0.5f) {
                reusablePath.rewind()
                reusablePath.addRoundRect(
                    RoundRect(
                        rect = layout,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                )
                clipPath(reusablePath) {
                    translate(left = draw.left, top = draw.top) {
                        with(painter) {
                            draw(
                                size = Size(draw.width, draw.height),
                                alpha = 1f
                            )
                        }
                    }
                }
            } else {
                clipRect(
                    left = layout.left,
                    top = layout.top,
                    right = layout.right,
                    bottom = layout.bottom
                ) {
                    translate(left = draw.left, top = draw.top) {
                        with(painter) {
                            draw(
                                size = Size(draw.width, draw.height),
                                alpha = 1f
                            )
                        }
                    }
                }
            }
        }

        if (kotlin.math.abs(rotationZ) > 0.01f) {
            withTransform({
                rotate(degrees = rotationZ, pivot = layout.center)
            }) {
                renderBlock()
            }
        } else {
            renderBlock()
        }
    }
}

fun viewerImageGeometry(
    frameRect: Rect,
    bitmapAspect: Float,
    fitBlend: Float = 1f,
    contentScale: ContentScale = ContentScale.Fit
): ViewerImageGeometry {
    val safeAspect = bitmapAspect.coerceAtLeast(0.01f)
    val drawRect = resolveMorphContentRect(
        frameRect = frameRect,
        painterAspectRatio = safeAspect,
        fitBlend = fitBlend
    )
    return ViewerImageGeometry(
        layoutRect = frameRect,
        drawRect = drawRect,
        clipRect = frameRect,
        visibleBitmapRect = frameRect,
        bitmapAspect = safeAspect,
        contentScale = contentScale
    )
}

fun resolveMorphContentRect(
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
    return lerpRect(cropRect, fitRect, fitBlend.coerceIn(0f, 1f))
}

fun computeCropRectInBounds(
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
