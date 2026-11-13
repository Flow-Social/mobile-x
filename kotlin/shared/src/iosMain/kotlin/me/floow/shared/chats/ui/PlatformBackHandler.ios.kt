package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun PlatformBackHandler(
	enabled: Boolean,
	onBack: () -> Unit,
) {
	// no-op on wasm
}
