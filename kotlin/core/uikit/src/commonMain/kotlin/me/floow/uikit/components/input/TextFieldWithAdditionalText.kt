package me.floow.uikit.components.input

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import me.floow.uikit.theme.LocalTypography

@Composable
fun TextFieldWithAdditionalText(
	title: String,
	additionalText: String,
	value: String,
	onValueChange: (String) -> Unit,
	minLines: Int = 1,
	maxLines: Int = 1,
	singleLine: Boolean = true,
	isError: Boolean = false,
	supportingText: String = "",
	placeholder: String = "",
	modifier: Modifier = Modifier
) {
	val outlineVariant = MaterialTheme.colorScheme.outlineVariant
	val outline = MaterialTheme.colorScheme.outline

	var highlightColor by remember { mutableStateOf(outlineVariant) }
	var borderWidth by remember { mutableStateOf(1.dp) }

	val isDarkTheme = isSystemInDarkTheme()

	Column(
		modifier = modifier
	) {
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			minLines = minLines,
			maxLines = maxLines,
			singleLine = singleLine,
			textStyle = LocalTypography.current.bodyMedium.copy(color = MaterialTheme.colorScheme.onBackground),
			cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
			decorationBox = { innerTextField ->
				Column(
					modifier = Modifier
						.border(
							width = borderWidth,
							color = borderColor(isError, highlightColor),
							shape = RoundedCornerShape(18.dp)
						)
						.padding(14.dp)
				) {
					Text(
						text = title,
						color = highlightColor,
						style = LocalTypography.current.labelMedium,
						modifier = Modifier
					)

					Spacer(Modifier.height(4.dp))

					Row(Modifier) {
						Text(
							text = additionalText,
							style = LocalTypography.current.bodyMedium,
						)

						Box {
							innerTextField()

							if (value.isEmpty()) {
								Text(
									text = placeholder,
									style = LocalTypography.current.bodyMedium,
									color = MaterialTheme.colorScheme.secondary
								)
							}
						}
					}
				}
			},
			modifier = Modifier
				.onFocusChanged {
					if (it.isFocused) {
						borderWidth = (1.5).dp
						highlightColor = if (isDarkTheme) {
							Color.White
						} else {
							outline
						}
					} else {
						borderWidth = 1.dp
						highlightColor = if (isDarkTheme) {
							outlineVariant
						} else {
							outlineVariant
						}
					}
				}
				.fillMaxWidth()
		)

		if (supportingText.isNotBlank()) {
			Spacer(Modifier.height(6.dp))

			Text(
				text = supportingText,
				style = LocalTypography.current.labelMedium,
				modifier = Modifier
					.padding(
						horizontal = 7.dp
					)
			)
		}
	}
}

@Composable
private fun borderColor(
	isError: Boolean,
	borderColor: Color,
): Color {
	return if (isError) MaterialTheme.colorScheme.error else borderColor
}
