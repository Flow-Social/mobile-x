package me.floow.uikit.chat.model

enum class VideoCirclePlaybackUiState {
    Uploading,
    IdlePreview,
    ActivePlaying,
    ActivePaused;

    val isProgressVisible: Boolean
        get() = this == ActivePlaying || this == ActivePaused

    val isPaused: Boolean
        get() = this == ActivePaused

    val isPlaying: Boolean
        get() = this == ActivePlaying

    val isExpanded: Boolean
        get() = this == ActivePlaying || this == ActivePaused
}
