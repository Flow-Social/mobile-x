package me.floow.uikit.components.topbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.nav_back_icon
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource

@Composable
fun SearchTopBar(
	onBackClick: () -> Unit,
	placeholder: String,
	searchFieldValue: String,
	onSearchFieldUpdate: (String) -> Unit,
	autoFocusOnStart: Boolean = false,
	modifier: Modifier = Modifier
) {
	val focusRequester = remember { FocusRequester() }
	val keyboardController = LocalSoftwareKeyboardController.current

	LaunchedEffect(autoFocusOnStart) {
		if (!autoFocusOnStart) return@LaunchedEffect
		focusRequester.requestFocus()
		keyboardController?.show()
	}

	Column(
		modifier = modifier
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier
				.height(80.dp)
				.padding(horizontal = 24.dp),
		) {
			IconButton(
				onClick = onBackClick,
				modifier = Modifier.size(24.dp)
			) {
				Icon(
					painter = painterResource(Res.drawable.nav_back_icon),
					contentDescription = null,
					modifier = Modifier.size(16.dp)
				)
			}

			Spacer(Modifier.width(10.dp))

			BasicTextField(
				value = searchFieldValue,
				onValueChange = onSearchFieldUpdate,
				singleLine = true,
				cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
				textStyle = LocalTypography.current.bodyMedium.copy(
					color = MaterialTheme.colorScheme.onBackground
				),
				decorationBox = { innerTextField ->
					Row(
						modifier = Modifier
							.padding(
								horizontal = 14.dp,
								vertical = 20.dp
							)
							.fillMaxWidth()
					) {
						Box(Modifier.fillMaxWidth()) {
							innerTextField()

							if (searchFieldValue.isEmpty()) {
								Text(
									text = placeholder,
									style = LocalTypography.current.bodyMedium,
									color = MaterialTheme.colorScheme.secondary,
									modifier = Modifier
								)
							}
						}
					}
				},
				modifier = Modifier
					.weight(1f)
					.focusRequester(focusRequester)
					.height(58.dp)
			)
		}

		HorizontalDivider()
	}
}
