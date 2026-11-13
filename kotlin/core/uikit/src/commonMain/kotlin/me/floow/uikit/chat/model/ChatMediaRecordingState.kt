package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
enum class ChatMediaRecordingType {
    Voice,
    Video
}

@Immutable
data class ChatMediaRecordingState(
    val type: ChatMediaRecordingType = ChatMediaRecordingType.Voice,
    val isRecording: Boolean = false,
    val isLocked: Boolean = false,
    val elapsedMs: Long = 0L,
    val audioLevel: Float = 0f,
    val dragOffsetX: Float = 0f,
    val dragOffsetY: Float = 0f,
    val cancelThresholdPx: Float = 220f,
    val lockThresholdPx: Float = 180f,
    val isPaused: Boolean = false,
) {
    val cancelProgress: Float get() = if (cancelThresholdPx <= 0f) 0f else
        ((-dragOffsetX) / cancelThresholdPx).coerceIn(0f, 1f)

    val lockProgress: Float get() = if (lockThresholdPx <= 0f) 0f else
        ((-dragOffsetY) / lockThresholdPx).coerceIn(0f, 1f)

    val formattedDuration: String get() {
        val totalSec = (elapsedMs / 1000).toInt()
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    }
}

fun VoiceRecordingInputState.toChatMediaRecordingState(): ChatMediaRecordingState =
    ChatMediaRecordingState(
        type = ChatMediaRecordingType.Voice,
        isRecording = isRecording,
        isLocked = isLocked,
        elapsedMs = durationMs,
        audioLevel = audioLevel,
        dragOffsetX = dragOffsetX,
        dragOffsetY = dragOffsetY,
    )

fun VideoRecordingState.toChatMediaRecordingState(): ChatMediaRecordingState =
    ChatMediaRecordingState(
        type = ChatMediaRecordingType.Video,
        isRecording = isRecording || isPreview,
        isLocked = isLocked,
        elapsedMs = if (isPreview && recordedDurationMs > 0L) recordedDurationMs else elapsedMs,
        audioLevel = if (isPreview) 0f else audioLevel,
        dragOffsetX = dragOffsetX,
        dragOffsetY = dragOffsetY,
        cancelThresholdPx = cancelThresholdPx,
        lockThresholdPx = lockThresholdPx,
        isPaused = isPreview,
    )
