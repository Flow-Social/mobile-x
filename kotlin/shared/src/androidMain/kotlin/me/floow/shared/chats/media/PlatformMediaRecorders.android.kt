package me.floow.shared.chats.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.floow.shared.chats.camera.VideoRecorder
import me.floow.shared.chats.uilogic.direct.RecordedVoiceClip
import me.floow.shared.chats.uilogic.direct.VideoRecorderBridge
import me.floow.shared.chats.voice.VoiceRecorder
import java.io.File

@Composable
actual fun rememberPlatformVideoRecorder(): VideoRecorderBridge? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val recorder = remember {
        VideoRecorder(context, lifecycleOwner).also {
            it.init(File(context.cacheDir, "video_circles"))
        }
    }
    DisposableEffect(recorder) {
        onDispose {
            recorder.release()
        }
    }
    return recorder
}

@Composable
actual fun rememberPlatformVoiceRecorder(): PlatformVoiceRecorderBridge? {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context) }
    return remember(recorder) {
        object : PlatformVoiceRecorderBridge {
            override val isRecording: Boolean get() = recorder.isCurrentlyRecording
            override fun startRecording(
                onAudioLevel: ((Float) -> Unit)?,
                onMaxDuration: (() -> Unit)?,
            ): Boolean = recorder.startRecording(onAudioLevel = onAudioLevel, onMaxDuration = onMaxDuration)
            override fun stopRecording(): RecordedVoiceClip? = recorder.stopRecording()
            override fun cancelRecording() = recorder.cancelRecording()
        }
    }
}

@Composable
actual fun PlatformCameraPreview(
    recorder: VideoRecorderBridge?,
    modifier: Modifier,
) {
    val videoRecorder = recorder as? VideoRecorder
    if (videoRecorder != null) {
        AndroidView(
            factory = { videoRecorder.previewViewChild },
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier)
    }
}
