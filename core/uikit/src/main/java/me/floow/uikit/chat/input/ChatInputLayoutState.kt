package me.floow.uikit.chat.input

import android.util.Log
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalDensity

private const val CHAT_INPUT_LAYOUT_DEBUG_TAG = "ChatScreenImeDebug"

@Composable
internal fun rememberChatInputLayoutState(
	controller: ChatInputController,
): Int {
	val density = LocalDensity.current
	val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	val actualImeHeightPx = (imeBottomPx - navigationBarBottomPx).coerceAtLeast(0)

	SideEffect {
		Log.d(
			CHAT_INPUT_LAYOUT_DEBUG_TAG,
			"actualImeHeightPx=$actualImeHeightPx imeBottomPx=$imeBottomPx navigationBarBottomPx=$navigationBarBottomPx",
		)
		controller.onImeHeightChanged(actualImeHeightPx)
	}

	return actualImeHeightPx
}
