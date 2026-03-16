package me.floow.uikit.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode

@Composable
internal fun ChatBottomHost(
	controller: ChatInputController,
	actualImeHeightPx: Int,
	modifier: Modifier = Modifier,
	composer: @Composable ColumnScope.() -> Unit,
	emojiPanel: @Composable BoxScope.() -> Unit,
) {
	val density = LocalDensity.current

	val bottomSpacerHeightPx = when (controller.inputMode) {
		ChatInputMode.Keyboard -> maxOf(actualImeHeightPx, controller.keyboardLayoutHeightPx)
		ChatInputMode.Emoji -> controller.emojiPanelHeightPx
		ChatInputMode.None -> 0
	}
	val bottomSpacerHeightDp = with(density) { bottomSpacerHeightPx.toDp() }

	Column(
		modifier = modifier
			.fillMaxWidth()
			.background(MaterialTheme.colorScheme.surface)
			.navigationBarsPadding(),
	) {
		Column(modifier = Modifier.fillMaxWidth()) {
			composer()
		}

		Box(
			modifier = Modifier
				.testTag("chat_bottom_spacer")
				.fillMaxWidth()
				.height(bottomSpacerHeightDp),
		) {
			if (controller.inputMode == ChatInputMode.Emoji && controller.emojiPanelHeightPx > 0) {
				Box(
					modifier = Modifier
						.testTag("chat_emoji_panel")
						.matchParentSize(),
				) {
					emojiPanel()
				}
			}
		}
	}
}
