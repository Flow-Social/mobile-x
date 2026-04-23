package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState

class VideoRecordingStateHolder(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val nowMs: () -> Long = { currentChatEpochMillis() },
) {
    private val _state = MutableStateFlow(VideoRecordingState())
    val state: StateFlow<VideoRecordingState> = _state.asStateFlow()
    val uiState: StateFlow<VideoCircleUiState> = _state
        .map(VideoRecordingState::toVideoCircleUiState)
        .stateIn(scope, SharingStarted.Lazily, VideoCircleUiState.Idle)

    private var timerJob: Job? = null
    private var machineState: MachineState = MachineState.Idle

    var recorder: VideoRecorderBridge? = null
    /** Called with the finalized clip + the circle's source bounds captured at finalize time. */
    var onVideoReadyForLocalInsertion: ((RecordedClip, androidx.compose.ui.geometry.Rect?) -> Unit)? = null

    fun onRecordButtonPress() {
        if (machineState != MachineState.Idle || _state.value.isActive) return

        machineState = MachineState.Recording
        _state.update {
            it.copy(
                mode = VideoRecordingMode.Recording,
                recordingStartedAtMs = nowMs(),
                elapsedMs = 0L,
                recordedDurationMs = 0L,
                error = null,
                isLocked = false,
                dragOffsetX = 0f,
                dragOffsetY = 0f,
                audioLevel = 0f,
            )
        }

        recorder?.startRecording(
            onAudioLevel = ::onAudioLevel,
            onComplete = { clip -> handleFinalize(clip) },
        )
        startRecordingTimer()
    }

    fun onSwipeLeft() {
        if (machineState != MachineState.Recording &&
            machineState != MachineState.LockedRecording &&
            machineState !is MachineState.Finalizing
        ) return
        recorder?.cancelRecording()
        resetToIdle()
    }

    /**
     * Feeds the raw drag delta from the record button gesture. Only meaningful while recording.
     * Horizontal drag is the stage 1/2 cancel gesture; vertical drag is preserved for stage 3 lock.
     */
    fun onRecordDrag(offsetX: Float, offsetY: Float) {
        if (machineState != MachineState.Recording) return
        val clamped = if (offsetX > 0f) 0f else offsetX
        _state.update {
            it.copy(
                dragOffsetX = clamped,
                dragOffsetY = offsetY,
            )
        }
        if (-clamped >= _state.value.cancelThresholdPx) {
            onSwipeLeft()
        }
    }

    fun onSwipeUpLock() {
        if (machineState != MachineState.Recording) return
        machineState = MachineState.LockedRecording
        _state.update {
            it.copy(
                isLocked = true,
                dragOffsetX = 0f,
                dragOffsetY = -it.lockThresholdPx,
            )
        }
    }

    fun onRecordButtonRelease() {
        if (machineState != MachineState.Recording) return
        if (_state.value.elapsedMs < MIN_RECORDING_DURATION_MS) {
            recorder?.cancelRecording()
            resetToIdle()
            return
        }
        finalizeRecording()
    }

    fun stopRecording() {
        if (machineState != MachineState.Recording && machineState != MachineState.LockedRecording) return
        if (_state.value.elapsedMs < MIN_RECORDING_DURATION_MS) {
            recorder?.cancelRecording()
            resetToIdle()
            return
        }
        finalizeRecording()
    }

    fun updateFlightSource(bounds: androidx.compose.ui.geometry.Rect) {
        _state.update { it.copy(flightSourceBounds = bounds) }
    }

    fun onDismissFailed() {
        resetToIdle()
    }

    private fun onAudioLevel(level: Float) {
        if (machineState != MachineState.Recording && machineState != MachineState.LockedRecording) return
        _state.update { state ->
            state.copy(audioLevel = level.coerceIn(0f, 1f))
        }
    }

    private fun finalizeRecording() {
        if (machineState is MachineState.Finalizing) return
        machineState = MachineState.Finalizing
        timerJob?.cancel()
        _state.update {
            it.copy(
                mode = VideoRecordingMode.Sending,
                recordedDurationMs = it.elapsedMs,
                audioLevel = 0f,
            )
        }
        recorder?.stopRecording()
    }

    private fun handleFinalize(clip: RecordedClip?) {
        if (machineState != MachineState.Finalizing) return
        if (clip == null) {
            if (_state.value.recordedDurationMs < MIN_RECORDING_DURATION_MS) {
                resetToIdle()
                return
            }
            machineState = MachineState.Failed
            _state.update { it.copy(mode = VideoRecordingMode.Failed, error = "Ошибка записи") }
            return
        }
        if (clip.durationMs > 0L && clip.durationMs < MIN_RECORDING_DURATION_MS) {
            recorder?.deleteTempFile(clip.path)
            resetToIdle()
            return
        }

        machineState = MachineState.Sending
        // Capture source bounds BEFORE resetting state so consumer sees them.
        val sourceBounds = _state.value.flightSourceBounds
        onVideoReadyForLocalInsertion?.invoke(clip, sourceBounds)
        resetToIdle()
    }

    private fun resetToIdle() {
        timerJob?.cancel()
        machineState = MachineState.Idle
        _state.value = VideoRecordingState(maxDurationMs = _state.value.maxDurationMs)
    }

    private fun startRecordingTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            val start = nowMs()
            while (true) {
                delay(TIMER_STEP_MS)
                val elapsed = nowMs() - start
                _state.update { it.copy(elapsedMs = elapsed) }
                if (elapsed >= _state.value.maxDurationMs) {
                    finalizeRecording()
                    break
                }
            }
        }
    }

    private sealed interface MachineState {
        data object Idle : MachineState
        data object Recording : MachineState
        data object LockedRecording : MachineState
        data object Finalizing : MachineState
        data object Sending : MachineState
        data object Failed : MachineState
    }

    private companion object {
        private const val MIN_RECORDING_DURATION_MS = 800L
        private const val TIMER_STEP_MS = 40L
    }
}

interface VideoRecorderBridge {
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
}
