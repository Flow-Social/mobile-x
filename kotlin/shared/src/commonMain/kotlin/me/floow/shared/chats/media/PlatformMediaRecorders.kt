package me.floow.shared.chats.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.shared.chats.camera.VideoCirclePreset
import me.floow.shared.chats.camera.VideoCircleSpec
import me.floow.shared.chats.uilogic.direct.RecordedVoiceClip
import me.floow.shared.chats.uilogic.direct.VideoRecorderBridge

interface PlatformVoiceRecorderBridge {
    val isRecording: Boolean
    fun startRecording(
        onAudioLevel: ((Float) -> Unit)? = null,
        onMaxDuration: (() -> Unit)? = null,
    ): Boolean
    fun stopRecording(): RecordedVoiceClip?
    fun cancelRecording()
}

@Composable
expect fun rememberPlatformVideoRecorder(
    spec: VideoCircleSpec = VideoCirclePreset.Standard,
): VideoRecorderBridge?

@Composable
expect fun rememberPlatformVoiceRecorder(): PlatformVoiceRecorderBridge?

@Composable
expect fun PlatformCameraPreview(
    recorder: VideoRecorderBridge?,
    modifier: Modifier = Modifier,
)
