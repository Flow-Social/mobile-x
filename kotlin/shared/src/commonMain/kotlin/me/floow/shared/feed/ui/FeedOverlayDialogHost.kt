package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable

@Composable
internal expect fun FeedOverlayDialogHost(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
)
