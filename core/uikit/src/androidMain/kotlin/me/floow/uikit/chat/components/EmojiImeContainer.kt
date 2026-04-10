package me.floow.uikit.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@Composable
internal fun EmojiImeContainer(
	isEmojiVisible: Boolean,
	cachedImeHeightPx: Int,
	onImeHeightMeasured: (Int) -> Unit = {},
	modifier: Modifier = Modifier,
	emojiPanel: @Composable () -> Unit
) {
	val density = LocalDensity.current
	val imeBottomPx = WindowInsets.ime.getBottom(density)
	val navigationBarsPx = WindowInsets.navigationBars.getBottom(density)
	val actualImeHeightPx = (imeBottomPx - navigationBarsPx).coerceAtLeast(0)
	val fallbackImeHeightPx = with(density) { 340.dp.roundToPx() }
	val minimumMeasuredImeHeightPx = with(density) { 180.dp.roundToPx() }
	val stableImeHeightPx = cachedImeHeightPx.takeIf { it > 0 } ?: fallbackImeHeightPx
	val targetHeightPx = if (isEmojiVisible) {
		maxOf(stableImeHeightPx, actualImeHeightPx, fallbackImeHeightPx)
	} else {
		actualImeHeightPx
	}

	SideEffect {
		if (actualImeHeightPx >= minimumMeasuredImeHeightPx) {
			onImeHeightMeasured(actualImeHeightPx)
		}
	}

	Box(
		modifier = modifier
			.fillMaxWidth()
			.height(with(density) { targetHeightPx.toDp() })
	) {
		if (isEmojiVisible) {
			Box(modifier = Modifier.fillMaxSize()) {
				emojiPanel()
				Spacer(
					modifier = Modifier
						.fillMaxWidth()
						.windowInsetsBottomHeight(WindowInsets.ime)
						.background(MaterialTheme.colorScheme.surface)
				)
			}
		}
	}
}
