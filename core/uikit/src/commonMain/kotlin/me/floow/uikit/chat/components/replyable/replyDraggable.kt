package me.floow.uikit.chat.components.replyable

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun Modifier.replyDraggable(
	state: CustomReplyAnchoredDraggableState,
	coroutineScope: CoroutineScope,
	onReply: () -> Unit,
): Modifier {
	return this.then(
		Modifier.pointerInput(Unit) {
			detectHorizontalDragGestures(
				onDragStart = { state.isDragged = true },
				onDragEnd = {
					state.isDragged = false
					coroutineScope.launch {
						if (state.currentValue < state.replyOffset * 0.7 && !state.isAnimated) {
							state.isAnimated = true
							state.animateTo(state.replyOffset, 3f, tween(50))
							onReply()
							state.animateTo(0f, 3f, tween(150))
						} else if (!state.isAnimated) {
							state.isAnimated = true
							state.animateTo(0f, 3f, tween(200))
						}
						state.isAnimated = false
					}
				},
				onHorizontalDrag = { change, dragAmount ->
					change.consume()
					state.currentValue = (state.currentValue + dragAmount).coerceIn(state.replyOffset, 0f)
				}
			)
		}
	)
}
