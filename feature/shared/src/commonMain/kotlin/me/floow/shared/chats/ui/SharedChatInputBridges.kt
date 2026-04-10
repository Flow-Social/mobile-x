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
import me.floow.uikit.chat.input.ChatInputController

@Composable
internal fun rememberSharedChatInputLifecycleBridge(
	controller: ChatInputController,
) {
	LaunchedEffect(controller) {
		controller.onHostResumed()
	}

	DisposableEffect(controller) {
		onDispose {
			controller.onHostPaused()
		}
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
