package me.floow.chats.camera

import androidx.camera.video.Recording
import me.floow.shared.chats.uilogic.direct.RecordedClip

/**
 * Single active recording session.
 *
 * All mutable state that used to live as top-level fields on `VideoRecorder`
 * (output path, completion listener, finalizing flag) is now attached to a
 * session instance so that overlapping commands (`switch` during `finalize`,
 * fast `cancel`/`start` churn) cannot contaminate each other.
 */
internal data class RecorderSession(
    val id: String,
    val filePath: String,
    val onComplete: (RecordedClip?) -> Unit,
    var recording: Recording? = null,
    var state: State = State.Starting,
) {
    enum class State {
        /** Recorder.start has been issued, waiting for first `VideoRecordEvent.Start`. */
        Starting,
        /** Camera is actively capturing. Safe for switch / pause / resume. */
        Active,
        /** stop() called; finalize event pending. No switch allowed. */
        Finalizing,
        /** Terminal. */
        Closed,
    }
}
