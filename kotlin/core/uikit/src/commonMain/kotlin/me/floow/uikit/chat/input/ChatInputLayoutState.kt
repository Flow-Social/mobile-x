package me.floow.uikit.chat.input
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity

@Composable
fun rememberChatInputLayoutState(
	controller: ChatInputController,
): Int {
	val density = LocalDensity.current
	val navigationBarBottomPx = WindowInsets.navigationBars.getBottom(density)
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	val actualImeHeightPx = (imeBottomPx - navigationBarBottomPx).coerceAtLeast(0)

	controller.onImeHeightChanged(actualImeHeightPx)

	return actualImeHeightPx
}
