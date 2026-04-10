package me.floow.uikit.chat.states

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.uikit.components.misc.BlankContentBox
import me.floow.uikit.R

@Composable
fun NoMessagesState(modifier: Modifier = Modifier) {
	BlankContentBox(
		modifier = modifier,
		titleRes = R.string.no_messages_title,
		subtitleRes = R.string.no_messages_subtitle
	)
}
