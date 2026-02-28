package me.floow.uikit.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.theme.LocalTypography
import me.floow.uikit.R

@Composable
fun MessageInputField(
	value: String,
	onValueChange: (String) -> Unit,
	onEmojiPickerClick: () -> Unit,
	onSendClick: () -> Unit,
	sendButtonActive: Boolean,
	isEditMode: Boolean = false,
	showEmojiButton: Boolean = true,
	maxLength: Int? = null,
	focusRequestKey: Long? = null,
	onFocusChanged: (Boolean) -> Unit = {},
	modifier: Modifier = Modifier
) {
	val focusRequester = remember { FocusRequester() }
	val keyboardController = LocalSoftwareKeyboardController.current

	LaunchedEffect(focusRequestKey) {
		if (focusRequestKey == null) return@LaunchedEffect
		focusRequester.requestFocus()
		keyboardController?.show()
	}

	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = modifier
			.padding(
				top = 13.dp,
				bottom = 16.dp,
			)
			.padding(horizontal = 16.dp)
	) {
		if (showEmojiButton) {
			Icon(
				painter = painterResource(
					me.floow.uikit.R.drawable.emoji_picker_icon,
				),
				contentDescription = null,
				tint = Color(0xFF8E959B),
				modifier = Modifier
					.clip(CircleShape)
					.clickable { onEmojiPickerClick() }
			)

			Spacer(
				Modifier
					.width(11.dp)
			)
		}

		BasicTextField(
			value = value,
			onValueChange = { newValue ->
				val trimmed = if (maxLength != null && newValue.length > maxLength) {
					newValue.take(maxLength)
				} else {
					newValue
				}
				onValueChange(trimmed)
			},
			textStyle = LocalTypography.current.titleMedium.copy(
				fontWeight = FontWeight.Normal,
				color = MaterialTheme.colorScheme.onBackground
			),
			maxLines = 4,
			cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
			modifier = Modifier
				.weight(1f)
				.focusRequester(focusRequester)
				.onFocusChanged { onFocusChanged(it.isFocused) }
		) { innerTextField ->
			Box {
				if (value.isEmpty()) {
					Text(
						text = stringResource(R.string.message_input_field_placeholder),
						color = Color.Gray,
						style = LocalTypography.current.titleMedium,
						fontWeight = FontWeight.Normal
					)
				}

				innerTextField()
			}
		}

		Spacer(
			Modifier
				.width(11.dp)
		)

		Icon(
			painter = painterResource(
				if (isEditMode) me.floow.uikit.R.drawable.done_icon else me.floow.uikit.R.drawable.send_icon,
			),
			tint = sendButtonColor(sendButtonActive),
			contentDescription = null,
			modifier = Modifier
				.clickable {
					if (sendButtonActive) onSendClick()
				}
		)
	}
}

@Composable
private fun sendButtonColor(sendButtonActive: Boolean) =
	if (sendButtonActive) MaterialTheme.colorScheme.primary
	else Color(0xFF868686)

@Preview
@Composable
private fun MessageInputFieldPreview() {
	var value by remember { mutableStateOf("") }

	MessageInputField(
		value = value,
		onValueChange = { value = it },
		onSendClick = {},
		onEmojiPickerClick = {},
		sendButtonActive = value.isNotBlank(),
		modifier = Modifier.fillMaxWidth()
	)
}
