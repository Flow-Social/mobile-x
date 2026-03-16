package me.floow.uikit.chat.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun rememberChatInputLifecycleBridge(
	controller: ChatInputController,
) {
	val lifecycleOwner = LocalLifecycleOwner.current

	DisposableEffect(lifecycleOwner, controller) {
		val observer = LifecycleEventObserver { _, event ->
			when (event) {
				Lifecycle.Event.ON_PAUSE,
				Lifecycle.Event.ON_STOP -> controller.onHostPaused()
				Lifecycle.Event.ON_RESUME -> controller.onHostResumed()
				else -> Unit
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}
}
