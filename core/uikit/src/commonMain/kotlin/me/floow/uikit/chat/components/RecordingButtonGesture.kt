package me.floow.uikit.chat.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

private enum class RecordingDragAxis {
	Horizontal,
	Vertical,
}

internal fun Modifier.recordingButtonGesture(
	cancelThresholdPx: Float,
	lockThresholdPx: Float,
	touchSlop: Float,
	onPress: () -> Unit,
	onRelease: () -> Unit,
	onSwipeUp: () -> Unit,
	onSwipeLeft: () -> Unit,
	onDrag: (Float, Float) -> Unit,
	onPressStateChanged: (Boolean) -> Unit,
): Modifier = pointerInput(cancelThresholdPx, lockThresholdPx, touchSlop) {
	awaitEachGesture {
		val down = awaitFirstDown(requireUnconsumed = false)
		down.consume()
		onPressStateChanged(true)
		onPress()

		var totalDragX = 0f
		var totalDragY = 0f
		var dragAxis: RecordingDragAxis? = null
		var cancelled = false
		var locked = false
		var currentEvent = awaitPointerEvent()

		while (currentEvent.changes.any { it.pressed }) {
			val change = currentEvent.changes.firstOrNull() ?: break
			val positionChange = change.positionChange()
			change.consume()
			totalDragX += positionChange.x
			totalDragY += positionChange.y

			if (!locked && !cancelled) {
				dragAxis = when {
					dragAxis == RecordingDragAxis.Vertical && abs(totalDragY) <= touchSlop -> null
					dragAxis == RecordingDragAxis.Horizontal && abs(totalDragX) <= touchSlop -> null
					else -> dragAxis
				}
			}

			if (dragAxis == null && (abs(totalDragX) > touchSlop || abs(totalDragY) > touchSlop)) {
				dragAxis = if (abs(totalDragX) >= abs(totalDragY)) {
					RecordingDragAxis.Horizontal
				} else {
					RecordingDragAxis.Vertical
				}
			}

			val effectiveDragX = if (dragAxis == RecordingDragAxis.Vertical) 0f else totalDragX
			val effectiveDragY = if (dragAxis == RecordingDragAxis.Horizontal) 0f else totalDragY
			onDrag(effectiveDragX, effectiveDragY)

			if (
				dragAxis == RecordingDragAxis.Horizontal &&
				totalDragX <= -cancelThresholdPx &&
				!cancelled
			) {
				cancelled = true
				onSwipeLeft()
			}
			if (
				dragAxis == RecordingDragAxis.Vertical &&
				totalDragY <= -lockThresholdPx &&
				!locked
			) {
				locked = true
				onSwipeUp()
			}
			currentEvent = awaitPointerEvent()
		}

		onPressStateChanged(false)
		if (!cancelled && !locked) onRelease()
	}
}
