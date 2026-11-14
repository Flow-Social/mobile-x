package me.floow.shared.chats.uilogic.direct

import androidx.compose.ui.graphics.ImageBitmap
import me.floow.shared.chats.camera.VideoCirclePreset
import me.floow.shared.chats.camera.VideoCircleSpec

interface VideoRecorderBridge {
    val spec: VideoCircleSpec get() = VideoCirclePreset.Standard
    val currentRecordingFilePath: String? get() = null
    fun prepareCamera() {}
    fun stopCamera() {}
    fun startRecording(
        onAudioLevel: (Float) -> Unit = {},
        onComplete: (RecordedClip?) -> Unit,
    )
    fun stopRecording()
    fun cancelRecording()
    fun switchCamera(): Boolean
    fun zoomBy(scaleFactor: Float): Float?
    fun setZoomRatio(zoomRatio: Float): Float?
    fun deleteTempFile(path: String)
    fun capturePreviewBitmap(): ImageBitmap? = null
}
