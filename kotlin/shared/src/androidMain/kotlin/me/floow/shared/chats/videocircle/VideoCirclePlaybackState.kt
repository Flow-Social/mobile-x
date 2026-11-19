package me.floow.shared.chats.videocircle

enum class VideoCirclePlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
    Error,
}

data class VideoCirclePlaybackState(
    val activeUiKey: String? = null,
    val activeMessageId: Long? = null,
    val activeSource: String? = null,
    val isMuted: Boolean = true,
    val isUserActivated: Boolean = false,
    val status: VideoCirclePlaybackStatus = VideoCirclePlaybackStatus.Idle,
    val positionMs: Long = 0L,
)

data class VideoCirclePlaybackRequest(
    val uiKey: String,
    val messageId: Long,
    val source: String,
    val muted: Boolean,
    val userActivated: Boolean,
    val speed: Float = 1f,
)

data class VideoCirclePlaybackCandidate(
    val uiKey: String,
    val messageId: Long,
    val source: String,
    val visibleFraction: Float,
)
