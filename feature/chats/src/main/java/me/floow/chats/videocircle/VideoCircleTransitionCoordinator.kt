package me.floow.chats.videocircle

import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VideoCircleTransitionCoordinator {
    private val _flightState = MutableStateFlow<FlightState?>(null)
    val flightState: StateFlow<FlightState?> = _flightState.asStateFlow()

    fun start(messageUiKey: String, sourceBounds: Rect?) {
        _flightState.value = FlightState(
            messageUiKey = messageUiKey,
            sourceBounds = sourceBounds,
            targetBounds = null,
            phase = FlightPhase.Start,
        )
    }

    fun onTargetMeasured(messageUiKey: String, bounds: Rect) {
        val current = _flightState.value ?: return
        if (current.messageUiKey != messageUiKey) return
        if (current.phase == FlightPhase.Settled) return

        val previousTarget = current.targetBounds
        if (previousTarget != null && previousTarget.isApproximatelySame(bounds)) return

        _flightState.value = current.copy(
            targetBounds = bounds,
            phase = FlightPhase.InFlight,
        )
    }

    fun settle() {
        val current = _flightState.value ?: return
        _flightState.value = current.copy(phase = FlightPhase.Settled)
    }

    fun cleanup() {
        _flightState.value = null
    }
}

private fun Rect.isApproximatelySame(other: Rect, epsilon: Float = 1.5f): Boolean {
    return kotlin.math.abs(left - other.left) <= epsilon &&
        kotlin.math.abs(top - other.top) <= epsilon &&
        kotlin.math.abs(right - other.right) <= epsilon &&
        kotlin.math.abs(bottom - other.bottom) <= epsilon
}

enum class FlightPhase {
    Start,
    InFlight,
    Settled,
}

data class FlightState(
    val messageUiKey: String,
    val sourceBounds: Rect?,
    val targetBounds: Rect?,
    val phase: FlightPhase,
)
