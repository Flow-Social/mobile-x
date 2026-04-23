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

enum class FlightPhase {
    Start,
    InFlight,
    Settled,
}

data class FlightState(
    val messageUiKey: String,
    val sourceBounds: Rect? = null,
    val targetBounds: Rect? = null,
    val phase: FlightPhase = FlightPhase.Start,
)

internal fun VideoRecordingState.toVideoCircleUiState(): VideoCircleUiState {
    return when (mode) {
        VideoRecordingMode.Idle -> VideoCircleUiState.Idle
        VideoRecordingMode.Recording -> VideoCircleUiState.Recording(
            elapsedMs = elapsedMs,
            progress = progress,
        )
        VideoRecordingMode.Sending -> VideoCircleUiState.Sending(
            recordedDurationMs = recordedDurationMs,
        )
        VideoRecordingMode.Failed -> VideoCircleUiState.Failed(error ?: "Ошибка записи")
    }
}
