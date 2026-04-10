package me.floow.uikit.chat.input

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import me.floow.uikit.chat.EmojiKeyboardHeightStore
import me.floow.uikit.chat.rememberEmojiKeyboardHeightStore
import me.floow.uikit.chat.rememberIsLandscapeKeyboardContext
import me.floow.uikit.chat.rememberPersistedKeyboardHeightPx

private const val CHAT_INPUT_PERSISTENCE_DEBUG_TAG = "ChatScreenImeDebug"

@Composable
internal fun rememberChatInputPersistenceBridge(
	controller: ChatInputController,
	keyboardHeightStore: EmojiKeyboardHeightStore = rememberEmojiKeyboardHeightStore(),
) {
	val isLandscapeKeyboardContext = rememberIsLandscapeKeyboardContext()
	val persistedKeyboardHeightPx = rememberPersistedKeyboardHeightPx(keyboardHeightStore)
	val stableKeyboardHeightPx by remember(controller) {
		derivedStateOf { controller.stableKeyboardHeightPx }
	}

	LaunchedEffect(stableKeyboardHeightPx, isLandscapeKeyboardContext, keyboardHeightStore) {
		if (stableKeyboardHeightPx > 0) {
			keyboardHeightStore.persistCachedImeHeightPx(
				heightPx = stableKeyboardHeightPx,
				isLandscape = isLandscapeKeyboardContext,
			)
		}
	}

	Log.d(CHAT_INPUT_PERSISTENCE_DEBUG_TAG, "persistedCachedImeHeightPx=$persistedKeyboardHeightPx")
	controller.syncPersistedKeyboardHeight(persistedKeyboardHeightPx)
}
