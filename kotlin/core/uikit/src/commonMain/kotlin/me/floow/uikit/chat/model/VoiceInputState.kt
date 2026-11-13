package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

enum class ChatRecordButtonMode {
    Video,
    Voice
}

@Immutable
data class VoiceDraftUiState(
    val localAudioPath: String = "",
    val durationMs: Long,
    val waveform: List<Int> = emptyList(),
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
)

@Immutable
data class VoiceRecordingInputState(
    val isRecording: Boolean = false,
    val durationMs: Long = 0L,
    val audioLevel: Float = 0f,
    val isLocked: Boolean = false,
    val dragOffsetX: Float = 0f,
    val dragOffsetY: Float = 0f,
)
