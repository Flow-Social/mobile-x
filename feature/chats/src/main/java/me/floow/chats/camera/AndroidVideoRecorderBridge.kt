package me.floow.chats.camera

import me.floow.shared.chats.uilogic.direct.RecordedClip
import me.floow.shared.chats.uilogic.direct.VideoRecorderBridge

class AndroidVideoRecorderBridge(
    private val recorder: VideoRecorder,
) : VideoRecorderBridge {
    override fun startRecording(
        onAudioLevel: (Float) -> Unit,
        onComplete: (RecordedClip?) -> Unit,
    ) {
        recorder.startRecording(
            onAudioLevel = onAudioLevel,
            onComplete = onComplete,
        )
    }

    override fun stopRecording() {
        recorder.stopRecording()
    }

    override fun cancelRecording() {
        recorder.cancelRecording()
    }

    override fun switchCamera(): Boolean {
        return recorder.switchCamera()
    }

    override fun zoomBy(scaleFactor: Float): Float? {
        return recorder.zoomBy(scaleFactor)
    }

    override fun setZoomRatio(zoomRatio: Float): Float? {
        return recorder.setZoomRatio(zoomRatio)
    }

    override fun deleteTempFile(path: String) {
        recorder.deleteTempFile(path)
    }
}
