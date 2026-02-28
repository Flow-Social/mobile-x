package me.floow.feed.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.uikit.R
import me.floow.uikit.components.buttons.WideOutlinedIconButton

@Composable
internal fun SwipeButtons(
	onSkipClick: () -> Unit,
	onLikeClick: () -> Unit,
	enabled: Boolean = true,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically // Выравнивание по центру
	) {
		// Кнопка скип (1/3 ширины)
		WideOutlinedIconButton(
			onClick = onSkipClick,
			enabled = enabled,
			modifier = Modifier
				.weight(1f)
				.height(48.dp) // Фиксированная высота
		) {
			Icon(
				painter = painterResource(R.drawable.nav_back_icon),
				contentDescription = "Скип"
			)
		}
		
		// Кнопка лайк (2/3 ширины)
		Button(
			onClick = onLikeClick,
			enabled = enabled,
			modifier = Modifier
				.weight(2f)
				.height(48.dp) // Фиксированная высота
		) {
			Row(
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically // Выравнивание содержимого
			) {
				Icon(
					painter = painterResource(R.drawable.done_icon),
					contentDescription = null
				)
				Text("ЛАЙК")
			}
		}
	}
}

@Preview
@Composable
private fun SwipeButtonsPreview() {
	SwipeButtons(
		onSkipClick = {},
		onLikeClick = {}
	)
}

@Preview
@Composable
private fun SwipeButtonsPreview_Disabled() {
	SwipeButtons(
		onSkipClick = {},
		onLikeClick = {},
		enabled = false
	)
}