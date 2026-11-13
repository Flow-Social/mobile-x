package me.floow.uikit.chat.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import me.floow.uikit.R

@Composable
internal actual fun ChatPinnedIcon(
	tint: Color,
	modifier: Modifier
) {
	Icon(
		painter = painterResource(R.drawable.notification_bell),
		contentDescription = null,
		tint = tint,
		modifier = modifier
	)
}

@Composable
internal actual fun ChatReplyIcon(
	tint: Color,
	mirrored: Boolean,
	modifier: Modifier
) {
	Icon(
		painter = painterResource(R.drawable.reply_out_icon),
		contentDescription = null,
		tint = tint,
		modifier = modifier.graphicsLayer(scaleX = if (mirrored) -1f else 1f)
	)
}

@Composable
internal actual fun ChatScrollToBottomIcon(
	tint: Color,
	modifier: Modifier
) {
	Icon(
		painter = painterResource(R.drawable.dropdown_icon),
		contentDescription = null,
		tint = tint,
		modifier = modifier
	)
}
