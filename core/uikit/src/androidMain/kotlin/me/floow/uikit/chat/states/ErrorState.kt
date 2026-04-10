package me.floow.uikit.chat.states

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.floow.uikit.R
import me.floow.uikit.components.misc.ErrorWithButtonContentBox

@Composable
fun ErrorState(
	onRetryClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	ErrorWithButtonContentBox(
		title = stringResource(R.string.chat_error_generic),
		description = stringResource(R.string.chat_error_description),
		onButtonClick = onRetryClick,
		buttonContent = {
			Text(text = stringResource(R.string.chat_retry_action))
		},
		modifier = modifier
	)
}
