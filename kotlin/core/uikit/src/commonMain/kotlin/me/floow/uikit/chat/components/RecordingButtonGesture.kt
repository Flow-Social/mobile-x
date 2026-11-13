package me.floow.uikit.chat.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

private enum class RecordingDragAxis {
	Horizontal,
	Vertical,
}

@Composable
internal fun Modifier.recordingButtonGesture(
	cancelThresholdPx: Float,
	lockThresholdPx: Float,
	touchSlop: Float,
	onTap: () -> Unit = {},
	onPress: () -> Unit,
	onRelease: () -> Unit,
	onSwipeUp: () -> Unit,
	onSwipeLeft: () -> Unit,
	onDrag: (Float, Float) -> Unit,
	onPressStateChanged: (Boolean) -> Unit,
): Modifier {
	val currentOnTap by rememberUpdatedState(onTap)
	val currentOnPress by rememberUpdatedState(onPress)
	val currentOnRelease by rememberUpdatedState(onRelease)
	val currentOnSwipeUp by rememberUpdatedState(onSwipeUp)
	val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
	val currentOnDrag by rememberUpdatedState(onDrag)
	val currentOnPressStateChanged by rememberUpdatedState(onPressStateChanged)

	return pointerInput(cancelThresholdPx, lockThresholdPx, touchSlop) {
		awaitEachGesture {
			val down = awaitFirstDown(requireUnconsumed = false)
			val downUptime = down.uptimeMillis
			var lastUptime = downUptime
			down.consume()
			currentOnPressStateChanged(true)

		var totalDragX = 0f
		var totalDragY = 0f
		var dragAxis: RecordingDragAxis? = null
		var cancelled = false
		var locked = false
		var recordingStarted = false

		// 1. Ожидаем порог удержания (300 мс) до запуска записи
		while (!cancelled && !locked) {
			val elapsed = (lastUptime - downUptime).coerceAtLeast(0L)
			val remaining = 300L - elapsed
			if (remaining <= 0L) {
				recordingStarted = true
				currentOnPress()
				break
			}

			val event = withTimeoutOrNull(remaining) {
				awaitPointerEvent()
			}
			if (event == null) {
				// Таймаут истек: палец удерживается на месте >= 300 мс
				recordingStarted = true
				currentOnPress()
				break
			}

			val change = event.changes.firstOrNull() ?: break
			lastUptime = change.uptimeMillis
			if (!change.pressed) {
				// Палец отпущен до истечения 300 мс
				change.consume()
				break
			}

			val positionChange = change.positionChange()
			change.consume()
			totalDragX += positionChange.x
			totalDragY += positionChange.y
		}

		// 2. Если запись началась — обрабатываем жесты отмены/блокировки и драга
		if (recordingStarted) {
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
				currentOnDrag(effectiveDragX, effectiveDragY)

				if (
					dragAxis == RecordingDragAxis.Horizontal &&
					totalDragX <= -cancelThresholdPx &&
					!cancelled
				) {
					cancelled = true
					currentOnSwipeLeft()
				}
				if (
					dragAxis == RecordingDragAxis.Vertical &&
					totalDragY <= -lockThresholdPx &&
					!locked
				) {
					locked = true
					currentOnSwipeUp()
				}
				currentEvent = awaitPointerEvent()
			}
		}

		currentOnPressStateChanged(false)

		if (!recordingStarted) {
			// Палец был отпущен до порога старта записи
			if (abs(totalDragX) <= touchSlop && abs(totalDragY) <= touchSlop && !cancelled && !locked) {
				currentOnTap()
			}
		} else {
			// Запись была начата — отправляем, если не была отменена или заблокирована
			if (!cancelled && !locked) {
				currentOnRelease()
			}
		}
	}
}
}
