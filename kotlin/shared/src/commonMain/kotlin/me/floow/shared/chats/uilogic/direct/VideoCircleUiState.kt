package me.floow.shared.chats.uilogic.direct

import androidx.compose.ui.geometry.Rect
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState

sealed interface VideoCircleUiState {
    data object Idle : VideoCircleUiState

    data class Recording(
        val elapsedMs: Long,
        val progress: Float,
    ) : VideoCircleUiState

    data class Sending(
        val recordedDurationMs: Long,
    ) : VideoCircleUiState

    data class Failed(
        val message: String,
    ) : VideoCircleUiState
}

typealias FlightPhase = me.floow.uikit.chat.model.FlightPhase
typealias FlightState = me.floow.uikit.chat.model.FlightState

internal fun VideoRecordingState.toVideoCircleUiState(): VideoCircleUiState {
    return when (mode) {
        VideoRecordingMode.Idle -> VideoCircleUiState.Idle
        VideoRecordingMode.Recording,
        VideoRecordingMode.Preview -> VideoCircleUiState.Recording(
            elapsedMs = if (mode == VideoRecordingMode.Preview && recordedDurationMs > 0L) recordedDurationMs else elapsedMs,
            progress = progress,
        )
        VideoRecordingMode.Sending -> VideoCircleUiState.Sending(
            recordedDurationMs = recordedDurationMs,
        )
        VideoRecordingMode.Failed -> VideoCircleUiState.Failed(error ?: "Ошибка записи")
    }
}
