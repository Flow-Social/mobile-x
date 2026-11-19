package me.floow.shared.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.uikit.chat.model.VideoCirclePlaybackUiState

internal expect class NativeVideoCirclePlayback {
    fun play(uiKey: String, messageId: Long, source: String, speed: Float)
    fun pause()
    fun stop()
    fun seekToProgress(progress: Float)
    fun progress(): Float
}

@Composable
internal expect fun rememberNativeVideoCirclePlayback(
    onPlaybackEnded: (String) -> Unit,
    onPlaybackPaused: (String) -> Unit,
): NativeVideoCirclePlayback

/**
 * Native camera preview slot for video circle recording.
 * On Android, renders CameraX TextureView/PreviewView.
 * On iOS, renders AVCaptureVideoPreviewLayer.
 */
@Composable
expect fun NativeCameraRecordingPreview(
    isRecording: Boolean,
    modifier: Modifier = Modifier
)

/**
 * Native video circle player slot for chat bubbles.
 * Renders hardware-accelerated video in a 1:1 circular aspect ratio.
 */
@Composable
internal expect fun NativeVideoCirclePlayer(
    playback: NativeVideoCirclePlayback,
    messageKey: String,
    videoUrl: String,
    thumbnailUrl: String? = null,
    playbackUiState: VideoCirclePlaybackUiState = VideoCirclePlaybackUiState.IdlePreview,
    showMenuPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
)
