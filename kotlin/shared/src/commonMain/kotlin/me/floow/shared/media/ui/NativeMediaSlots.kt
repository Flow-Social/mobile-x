package me.floow.shared.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.uikit.chat.model.VideoCirclePlaybackUiState

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
expect fun NativeVideoCirclePlayer(
    messageKey: String,
    videoUrl: String,
    thumbnailUrl: String? = null,
    playbackUiState: VideoCirclePlaybackUiState = VideoCirclePlaybackUiState.IdlePreview,
    showMenuPreview: Boolean = false,
    onClick: (() -> Unit)? = null,
    onSeek: ((Float) -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
