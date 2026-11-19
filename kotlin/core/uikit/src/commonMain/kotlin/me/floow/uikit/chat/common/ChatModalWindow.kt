package me.floow.uikit.chat.common

import androidx.compose.runtime.Composable

/** Hosts chat overlays above the system keyboard without moving focus from the composer. */
@Composable
expect fun ChatModalWindow(
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
)
