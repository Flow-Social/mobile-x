package me.floow.shared.chats.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
expect fun rememberPlatformVideoRecorder(): VideoRecorderBridge?

@Composable
expect fun rememberPlatformVoiceRecorder(): PlatformVoiceRecorderBridge?

@Composable
expect fun PlatformCameraPreview(
    recorder: VideoRecorderBridge?,
    modifier: Modifier = Modifier,
)
