package me.floow.uikit.components.media.viewer2

data class HostedFullscreenImageViewer(
    val modelProvider: () -> FullscreenImageViewerModel?,
    val state: FullscreenImageViewerState,
    val onAction: (FullscreenImageViewerAction) -> Unit,
)
