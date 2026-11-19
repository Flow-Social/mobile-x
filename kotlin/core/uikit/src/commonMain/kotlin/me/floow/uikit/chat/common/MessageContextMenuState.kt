package me.floow.uikit.chat.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize

data class ElevatedMessageData(
	val messageId: Long,
	val sourceBounds: Rect,
	val progress: Animatable<Float, AnimationVector1D>,
	val tapOffset: Offset?,
	val foregroundLayer: GraphicsLayer?,
) {
	internal var foregroundEnabled by mutableStateOf(false)
	private val foregroundVisible by derivedStateOf(structuralEqualityPolicy()) {
		foregroundEnabled && progress.value > 0f
	}
	internal val isInForeground: Boolean
		get() = foregroundVisible &&
			foregroundLayer?.size?.let { it != IntSize.Zero } == true
}

internal fun fitMessageMenuForeground(
	source: Rect,
	viewport: Rect,
	liftPx: Float,
	marginPx: Float,
): Rect? {
	if (source.isEmpty) return null
	val usableWidth = viewport.width - marginPx * 2
	val usableHeight = viewport.height - marginPx * 2
	if (usableWidth <= 0 || usableHeight <= 0) return null
	val scale = minOf(1.025f, usableWidth / source.width, usableHeight / source.height)
	val width = (source.width * scale).coerceAtMost(usableWidth)
	val height = (source.height * scale).coerceAtMost(usableHeight)
	val minX = viewport.left + marginPx
	val minY = viewport.top + marginPx
	return Rect(
		offset = Offset(
			x = (source.center.x - width / 2).coerceIn(minX, (viewport.right - marginPx - width).coerceAtLeast(minX)),
			y = (source.center.y - height / 2 - liftPx).coerceIn(minY, (viewport.bottom - marginPx - height).coerceAtLeast(minY)),
		),
		size = Size(width, height),
	)
}
