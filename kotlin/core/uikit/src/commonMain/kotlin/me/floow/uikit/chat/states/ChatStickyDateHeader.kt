package me.floow.uikit.chat.states

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.DateSeparator

/**
 * Renders the sticky/floating date separator at the top of the chat timeline,
 * appearing with fade animation when the user scrolls through different dates.
 */
@Composable
internal fun ChatStickyDateHeader(
	visible: Boolean,
	dateText: String,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier) {
		Spacer(Modifier.height(8.dp))

		Row(
			horizontalArrangement = Arrangement.Center,
			modifier = Modifier
				.fillMaxWidth()
				.height(32.dp),
		) {
			AnimatedVisibility(
				visible = visible && dateText.isNotBlank(),
				enter = fadeIn(),
				exit = fadeOut()
			) {
				DateSeparator(text = dateText)
			}
		}

		Spacer(Modifier.height(8.dp))
	}
}
