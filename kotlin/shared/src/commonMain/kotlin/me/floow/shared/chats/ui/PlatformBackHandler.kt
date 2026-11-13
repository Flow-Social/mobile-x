package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun PlatformBackHandler(
	enabled: Boolean,
	onBack: () -> Unit,
)
