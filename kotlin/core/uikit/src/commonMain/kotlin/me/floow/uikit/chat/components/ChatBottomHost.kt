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
import androidx.compose.ui.layout.onSizeChanged
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun ChatBottomHost(
	controller: ChatInputController,
	actualImeHeightPx: Int,
	modifier: Modifier = Modifier,
	isRecording: Boolean = false,
	onComposerHeightChanged: (Int) -> Unit = {},
	composer: @Composable ColumnScope.() -> Unit,
	emojiPanel: @Composable BoxScope.() -> Unit,
) {
	val density = LocalDensity.current
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	val chatBg = if (isDark) Color(0xFF121217) else Color(0xFFF6F6F6)
	val recordingActive = isRecording || controller.inputMode == ChatInputMode.Recording

	val bottomSpacerHeightPx = when (controller.inputMode) {
		ChatInputMode.Keyboard -> if (controller.isSwitchingEmojiToKeyboard) {
			maxOf(actualImeHeightPx, controller.keyboardLayoutHeightPx)
		} else {
			actualImeHeightPx
		}
		ChatInputMode.Emoji -> controller.emojiPanelHeightPx
		ChatInputMode.None -> 0
		ChatInputMode.Recording -> if (controller.wasKeyboardOpenBeforeRecording) {
			actualImeHeightPx
		} else {
			0
		}
	}
	val bottomSpacerHeightDp = with(density) { bottomSpacerHeightPx.toDp() }

	val backgroundModifier = if (recordingActive) {
		Modifier
	} else {
		Modifier.background(
			Brush.verticalGradient(
				listOf(
					chatBg.copy(alpha = 0.0f),
					chatBg.copy(alpha = 0.45f),
					chatBg.copy(alpha = 0.90f),
					chatBg,
				),
			),
		)
	}

	Column(
		modifier = modifier
			.fillMaxWidth()
			.then(backgroundModifier)
			.navigationBarsPadding(),
	) {
		Column(modifier = Modifier.fillMaxWidth().onSizeChanged { onComposerHeightChanged(it.height) }) {
			composer()
		}

		Box(
			modifier = Modifier
				.testTag("chat_bottom_spacer")
				.fillMaxWidth()
				.height(bottomSpacerHeightDp)
				.background(if (recordingActive) Color.Transparent else chatBg),
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
