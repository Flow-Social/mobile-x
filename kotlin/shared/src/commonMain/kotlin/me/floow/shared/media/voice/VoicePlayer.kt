package me.floow.shared.media.voice

import kotlinx.coroutines.flow.StateFlow

data class VoicePlaybackState(
    val activeMessageId: Long? = null,
    val activeUiKey: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f,
)

interface VoicePlayer {
    val state: StateFlow<VoicePlaybackState>

    fun play(
        messageId: Long?,
        urlOrPath: String,
        uiKey: String? = null,
        durationMs: Long = 0L,
    )

    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun toggleSpeed()
    fun stop()
    fun release()
}

expect fun createVoicePlayer(): VoicePlayer
