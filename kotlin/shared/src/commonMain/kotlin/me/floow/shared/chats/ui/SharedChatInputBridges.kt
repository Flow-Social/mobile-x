package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.floow.uikit.chat.input.ChatInputController

@Composable
internal fun rememberSharedChatInputLifecycleBridge(
	controller: ChatInputController,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller.onHostResumed()
                Lifecycle.Event.ON_PAUSE -> controller.onHostPaused()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
internal fun rememberSharedChatInputPersistenceBridge(
	controller: ChatInputController,
	persistenceKey: Any?,
) {
	var persistedKeyboardHeightPx by rememberSaveable(persistenceKey) { mutableStateOf(0) }
	val stableKeyboardHeightPx by remember(controller) {
		derivedStateOf { controller.stableKeyboardHeightPx }
	}

	LaunchedEffect(controller, persistedKeyboardHeightPx) {
		if (persistedKeyboardHeightPx > 0) {
			controller.syncPersistedKeyboardHeight(persistedKeyboardHeightPx)
		}
	}

	LaunchedEffect(stableKeyboardHeightPx, persistenceKey) {
		if (stableKeyboardHeightPx > 0) {
			persistedKeyboardHeightPx = stableKeyboardHeightPx
		}
	}
}
