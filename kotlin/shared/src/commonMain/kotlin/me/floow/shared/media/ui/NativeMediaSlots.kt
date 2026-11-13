package me.floow.shared.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
 * Renders hardware-accelerated looping video in a 1:1 circular aspect ratio.
 */
@Composable
expect fun NativeVideoCirclePlayer(
    videoUrl: String,
    thumbnailUrl: String? = null,
    isPlaying: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
