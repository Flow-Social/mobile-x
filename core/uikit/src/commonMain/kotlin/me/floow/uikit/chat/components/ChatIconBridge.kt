package me.floow.uikit.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
internal expect fun ChatPinnedIcon(
	tint: Color,
	modifier: Modifier = Modifier
)

@Composable
internal expect fun ChatReplyIcon(
	tint: Color,
	mirrored: Boolean,
	modifier: Modifier = Modifier
)

@Composable
internal expect fun ChatScrollToBottomIcon(
	tint: Color,
	modifier: Modifier = Modifier
)
