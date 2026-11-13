package me.floow.domain.lifecycle

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

object AppLifecycleBridge {
	private val _isForeground = MutableStateFlow(true)
	val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

	private val _foregroundEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
	val foregroundEvents: SharedFlow<Unit> = _foregroundEvents.asSharedFlow()

	fun onAppForeground() {
		_isForeground.value = true
		_foregroundEvents.tryEmit(Unit)
	}

	fun onAppBackground() {
		_isForeground.value = false
	}

	/**
	 * Pauses for [delayMs] while respecting foreground state:
	 * - If app is in background, suspends until app returns to foreground.
	 * - If app transitions from background to foreground during delay, returns immediately.
	 */
	suspend fun delayWithForegroundWakeup(delayMs: Long) {
		if (!_isForeground.value) {
			_isForeground.first { it }
			return
		}
		if (delayMs <= 0L) return
		withTimeoutOrNull(delayMs) {
			_foregroundEvents.first()
		}
	}
}
