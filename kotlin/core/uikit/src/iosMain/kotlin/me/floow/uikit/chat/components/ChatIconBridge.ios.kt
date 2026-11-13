package me.floow.uikit.chat.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.reply_out_icon
import org.jetbrains.compose.resources.painterResource

@Composable
internal actual fun ChatPinnedIcon(
	tint: Color,
	modifier: Modifier
) {
	Icon(
		imageVector = Icons.Filled.Notifications,
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
		painter = painterResource(Res.drawable.reply_out_icon),
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
		imageVector = Icons.Filled.ArrowDownward,
		contentDescription = null,
		tint = tint,
		modifier = modifier
	)
}
