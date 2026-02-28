package me.floow.uikit.components.buttons

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun BlurGlassButton(
    painter: Painter,
    modifier: Modifier = Modifier,
    showBackgroundImage: Boolean = true,
    blurRadius: Dp = 8.dp,
    backgroundBoundsInWindow: Rect? = null,
    backgroundContentScale: ContentScale = ContentScale.Crop,
    backgroundAlignment: Alignment = Alignment.Center,
    refractionScale: Float = 1.02f,
    overlayColor: Color = Color.Black.copy(alpha = 0.35f),
    containerColor: Color = Color.White.copy(alpha = 0.18f),
    strokeWidth: Dp = 1.dp,
    strokeColor: Color = Color.White.copy(alpha = 0.4f),
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var buttonBoundsInWindow by remember { mutableStateOf<Rect?>(null) }
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onGloballyPositioned { coordinates ->
                buttonBoundsInWindow = coordinates.boundsInWindow()
            }
            .clip(shape)
            .then(clickableModifier)
    ) {
        val imageModifier = Modifier
            .matchParentSize()
            .blur(blurRadius)
        if (showBackgroundImage) {
            if (backgroundBoundsInWindow != null && buttonBoundsInWindow != null) {
                Canvas(modifier = imageModifier) {
                    drawAlignedBackground(
                        painter = painter,
                        backgroundBoundsInWindow = backgroundBoundsInWindow,
                        buttonBoundsInWindow = buttonBoundsInWindow!!,
                        backgroundContentScale = backgroundContentScale,
                        backgroundAlignment = backgroundAlignment,
                        refractionScale = refractionScale
                    )
                }
            } else {
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = backgroundContentScale,
                    modifier = imageModifier
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(overlayColor)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(containerColor)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(BorderStroke(strokeWidth, strokeColor), shape)
        )
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAlignedBackground(
    painter: Painter,
    backgroundBoundsInWindow: Rect,
    buttonBoundsInWindow: Rect,
    backgroundContentScale: ContentScale,
    backgroundAlignment: Alignment,
    refractionScale: Float
) {
    val sourceSize = painter.intrinsicSize
    val destinationSize = backgroundBoundsInWindow.size
    if (
        sourceSize == Size.Unspecified ||
        sourceSize.width <= 0f ||
        sourceSize.height <= 0f ||
        destinationSize.width <= 0f ||
        destinationSize.height <= 0f
    ) {
        with(painter) {
            draw(size = size)
        }
        return
    }

    val scaleFactor = backgroundContentScale.computeScaleFactor(
        srcSize = sourceSize,
        dstSize = destinationSize
    )
    val mappedImageSize = Size(
        width = sourceSize.width * scaleFactor.scaleX,
        height = sourceSize.height * scaleFactor.scaleY
    )
    val imageOffsetInBackground = backgroundAlignment.align(
        size = IntSize(mappedImageSize.width.roundToInt(), mappedImageSize.height.roundToInt()),
        space = IntSize(destinationSize.width.roundToInt(), destinationSize.height.roundToInt()),
        layoutDirection = layoutDirection
    )
    val imageTopLeftInWindow = backgroundBoundsInWindow.topLeft + Offset(
        x = imageOffsetInBackground.x.toFloat(),
        y = imageOffsetInBackground.y.toFloat()
    )
    val imageTopLeftInButton = imageTopLeftInWindow - buttonBoundsInWindow.topLeft

    val normalizedRefraction = refractionScale.coerceAtLeast(1f)
    val refractedImageSize = Size(
        width = mappedImageSize.width * normalizedRefraction,
        height = mappedImageSize.height * normalizedRefraction
    )
    val buttonCenter = Offset(size.width / 2f, size.height / 2f)
    val refractedTopLeft = buttonCenter + (imageTopLeftInButton - buttonCenter) * normalizedRefraction

    withTransform({
        translate(left = refractedTopLeft.x, top = refractedTopLeft.y)
    }) {
        with(painter) {
            draw(size = refractedImageSize)
        }
    }
}
