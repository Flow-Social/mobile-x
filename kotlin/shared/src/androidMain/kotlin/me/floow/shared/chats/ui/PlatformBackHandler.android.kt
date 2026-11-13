package me.floow.shared.chats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformBackHandler(
	enabled: Boolean,
	onBack: () -> Unit,
) {
	BackHandler(enabled = enabled, onBack = onBack)
}
