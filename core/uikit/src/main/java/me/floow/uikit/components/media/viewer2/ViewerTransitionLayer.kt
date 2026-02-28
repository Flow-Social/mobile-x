package me.floow.uikit.components.media.viewer2

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity

@Composable
fun ViewerTransitionLayer(
    painter: Painter,
    animatedRect: Rect,
    modifier: Modifier = Modifier
) {
    if (animatedRect.width <= 0f || animatedRect.height <= 0f) return

    val density = LocalDensity.current
    val widthDp = with(density) { animatedRect.width.toDp() }
    val heightDp = with(density) { animatedRect.height.toDp() }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .requiredSize(widthDp, heightDp)
                .graphicsLayer {
                    translationX = animatedRect.left
                    translationY = animatedRect.top
                }
        )
    }
}
