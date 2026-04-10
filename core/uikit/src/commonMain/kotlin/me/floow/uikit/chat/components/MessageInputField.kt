package me.floow.uikit.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InsertEmoticon
import androidx.compose.material.icons.outlined.KeyboardAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.done_icon
import flow.core.uikit.generated.resources.emoji_picker_icon
import flow.core.uikit.generated.resources.send_icon
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource

private val ChatInputIconSize = 28.dp
private val ChatInputKeyboardGlyphSize = 24.dp
private val ChatInputGlyphSize = 28.dp
private val ChatInputSidePadding = 14.dp
private val ChatInputMinHeight = 54.dp
private val ChatInputIconBottomInset = 12.dp
private val ChatInputTextBottomInset = 15.dp
private val ChatInputTextStartInset = 38.dp
private val ChatInputTextEndInset = 38.dp
private val ChatInputTextSize = 16.sp

@Composable
fun MessageInputField(
	controller: ChatInputController,
	onSendClick: () -> Unit,
	sendButtonActive: Boolean,
	isEditMode: Boolean = false,
	showEmojiButton: Boolean = true,
	modifier: Modifier = Modifier,
) {
	val focusRequester = remember { FocusRequester() }
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val inputInteractionSource = remember { MutableInteractionSource() }
	var isFocused by remember { mutableStateOf(false) }

	LaunchedEffect(controller.keyboardRequestToken) {
		if (controller.keyboardRequestToken == 0) return@LaunchedEffect
		focusRequester.requestFocus()
		keyboardController?.show()
	}

	LaunchedEffect(controller.hideImeToken) {
		if (controller.hideImeToken == 0) return@LaunchedEffect
		keyboardController?.hide()
		focusManager.clearFocus(force = true)
	}

	LaunchedEffect(controller.inputMode) {
		if (controller.inputMode == ChatInputMode.Emoji) {
			keyboardController?.hide()
		}
	}

	Box(
		modifier = modifier
			.testTag("chat_input_row")
			.defaultMinSize(minHeight = ChatInputMinHeight)
			.padding(horizontal = ChatInputSidePadding)
			.clickable(
				interactionSource = inputInteractionSource,
				indication = null,
			) {
				controller.onInputTapped()
			},
	) {
		if (showEmojiButton) {
			val toggleStateDescription = if (controller.inputMode == ChatInputMode.Emoji) "keyboard" else "emoji"

			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.testTag("chat_input_toggle_button")
					.semantics { stateDescription = toggleStateDescription }
					.padding(bottom = ChatInputIconBottomInset)
					.size(ChatInputIconSize)
					.clip(CircleShape)
					.clickable { controller.toggleEmoji() },
			) {
				if (controller.inputMode == ChatInputMode.Emoji) {
					Icon(
						imageVector = Icons.Outlined.KeyboardAlt,
						contentDescription = toggleStateDescription,
						tint = Color(0xFF8E959B),
						modifier = Modifier.size(ChatInputKeyboardGlyphSize),
					)
				} else {
					Icon(
						painter = painterResource(Res.drawable.emoji_picker_icon),
						contentDescription = toggleStateDescription,
						tint = Color(0xFF8E959B),
						modifier = Modifier.size(ChatInputGlyphSize),
					)
				}
			}
		}

		Box(
			contentAlignment = Alignment.BottomStart,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.padding(
					start = if (showEmojiButton) ChatInputTextStartInset else 0.dp,
					end = ChatInputTextEndInset,
				)
				.padding(bottom = ChatInputTextBottomInset)
				.testTag("chat_input_text_field"),
		) {
			BasicTextField(
				value = controller.textFieldValue,
				onValueChange = controller::onTextFieldValueChange,
				textStyle = LocalTypography.current.titleMedium.copy(
					fontSize = ChatInputTextSize,
					fontWeight = FontWeight.Normal,
					color = MaterialTheme.colorScheme.onBackground,
				),
				maxLines = 8,
				cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 22.dp)
					.focusRequester(focusRequester)
					.onFocusChanged { focusState ->
						isFocused = focusState.isFocused
						controller.onTextFieldFocusChanged(focusState.isFocused)
					}
					.semantics {
						stateDescription = controller.inputMode.name
					},
				interactionSource = inputInteractionSource,
			) { innerTextField ->
				Box {
					if (controller.textFieldValue.text.isEmpty()) {
						Text(
							text = "Сообщение",
							color = Color.Gray,
							style = LocalTypography.current.titleMedium.copy(fontSize = ChatInputTextSize),
							fontWeight = FontWeight.Normal,
						)
					}
					innerTextField()
				}
			}
		}

		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.testTag("chat_input_send_button")
				.padding(bottom = ChatInputIconBottomInset)
				.size(ChatInputIconSize)
				.clip(CircleShape)
				.clickable {
					if (sendButtonActive) onSendClick()
				},
		) {
			Icon(
				painter = painterResource(
					if (isEditMode) Res.drawable.done_icon else Res.drawable.send_icon,
				),
				tint = sendButtonColor(sendButtonActive),
				contentDescription = null,
				modifier = Modifier.size(ChatInputGlyphSize),
			)
		}
	}
}

@Composable
private fun sendButtonColor(sendButtonActive: Boolean) =
	if (sendButtonActive) MaterialTheme.colorScheme.primary
	else Color(0xFF868686)
