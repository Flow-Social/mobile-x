package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
enum class VideoRecordingMode {
    Idle,
    Recording,
    Sending,
    Failed,
}

@Immutable
data class VideoRecordingState(
    val mode: VideoRecordingMode = VideoRecordingMode.Idle,
    val recordingStartedAtMs: Long? = null,
    val maxDurationMs: Long = 60_000L,
    val elapsedMs: Long = 0L,
    val recordedDurationMs: Long = 0L,
    val error: String? = null,
    val flightSourceBounds: androidx.compose.ui.geometry.Rect? = null,
    /** Recording is pinned; releasing the finger no longer finalizes it. */
    val isLocked: Boolean = false,
    /** Horizontal drag distance of the record button while held. Negative = swiping left toward cancel. */
    val dragOffsetX: Float = 0f,
    /** Vertical drag distance of the record button while held. Negative = swiping up. */
    val dragOffsetY: Float = 0f,
    /** Normalized microphone amplitude, 0..1. Drives recording control pulse. */
    val audioLevel: Float = 0f,
    /** Pixels of horizontal drag that fully cancels the recording. */
    val cancelThresholdPx: Float = 220f,
    /** Pixels of upward drag that locks the recording. */
    val lockThresholdPx: Float = 180f,
) {
    val isActive: Boolean get() = mode != VideoRecordingMode.Idle

    val isRecording: Boolean get() = mode == VideoRecordingMode.Recording

    /** 0..1 progress toward dismissal based on current horizontal drag (leftward). */
    val cancelProgress: Float get() = if (cancelThresholdPx <= 0f) 0f else
        ((-dragOffsetX) / cancelThresholdPx).coerceIn(0f, 1f)

    /** 0..1 progress toward lock based on upward drag. */
    val lockProgress: Float get() = if (lockThresholdPx <= 0f) 0f else
        ((-dragOffsetY) / lockThresholdPx).coerceIn(0f, 1f)

    val progress: Float get() = if (maxDurationMs <= 0L) 0f else (elapsedMs.toFloat() / maxDurationMs).coerceIn(0f, 1f)

    val formattedElapsed: String get() {
        val totalSec = (elapsedMs / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }

    val formattedMaxDuration: String get() {
        val totalSec = (maxDurationMs / 1000).toInt()
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }
}
