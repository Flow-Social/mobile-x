package me.floow.uikit.chat.components.replyable

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun Modifier.replyDraggable(
	state: CustomReplyAnchoredDraggableState,
	coroutineScope: CoroutineScope,
	onReply: () -> Unit,
): Modifier {
	val haptics = LocalHapticFeedback.current
	var hasTriggeredHaptic by remember { mutableStateOf(false) }

	val springSpec = remember {
		spring<Float>(
			dampingRatio = Spring.DampingRatioMediumBouncy,
			stiffness = Spring.StiffnessMedium
		)
	}

	return this.then(
		Modifier.pointerInput(Unit) {
			detectHorizontalDragGestures(
				onDragStart = {
					state.isDragged = true
					hasTriggeredHaptic = false
				},
				onDragEnd = {
					state.isDragged = false
					hasTriggeredHaptic = false
					coroutineScope.launch {
						val threshold = state.replyOffset * 0.7f
						if (state.currentValue <= threshold && !state.isAnimated) {
							state.isAnimated = true
							onReply()
							state.animateTo(0f, 0f, springSpec)
						} else if (!state.isAnimated) {
							state.isAnimated = true
							state.animateTo(0f, 0f, springSpec)
						}
						state.isAnimated = false
					}
				},
				onHorizontalDrag = { change, dragAmount ->
					change.consume()
					val newValue = (state.currentValue + dragAmount).coerceIn(state.replyOffset, 0f)
					val threshold = state.replyOffset * 0.7f
					if (newValue <= threshold && !hasTriggeredHaptic) {
						haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
						hasTriggeredHaptic = true
					} else if (newValue > threshold && hasTriggeredHaptic) {
						hasTriggeredHaptic = false
					}
					state.currentValue = newValue
				}
			)
		}
	)
}
