package me.floow.profile.uilogic.bump

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.floow.domain.api.models.BumpCancelSessionRequest
import me.floow.domain.api.models.BumpEventRequest
import me.floow.domain.api.models.BumpNearbyPeer
import me.floow.domain.api.models.BumpSessionStatus
import me.floow.domain.api.models.BumpStartSessionRequest
import me.floow.domain.api.models.GetBumpSessionResultResponse
import me.floow.domain.api.models.StartBumpSessionResponse
import me.floow.domain.api.models.SubmitBumpEventResponse
import me.floow.domain.data.repos.BumpRepository

private const val MAX_NEARBY_PEERS = 8
private const val IMPACT_SUBMIT_COOLDOWN_MS = 1200L
private const val CANCEL_SESSION_TIMEOUT_MS = 2_000L

class ProfileBumpViewModel(
    private val bumpRepository: BumpRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileBumpUiState())
    val uiState: StateFlow<ProfileBumpUiState> = _uiState.asStateFlow()

    private val _openMatchedProfile = MutableSharedFlow<String>()
    val openMatchedProfile: SharedFlow<String> = _openMatchedProfile.asSharedFlow()

    private var pollJob: Job? = null
    private var timeoutJob: Job? = null
    private var lastImpactSubmittedAtMs: Long = 0L
    private var emittedMatchedForSessionId: String? = null

    fun showBlockingError(message: String) {
        val keepSheetVisible = _uiState.value.isSheetVisible
        cancelPolling()
        lastImpactSubmittedAtMs = 0L
        emittedMatchedForSessionId = null
        _uiState.value = ProfileBumpUiState(
            isSheetVisible = keepSheetVisible,
            mode = ProfileBumpMode.Error,
            errorMessage = message,
        )
    }

    fun showInlineError(message: String) {
        _uiState.update { current ->
            current.copy(errorMessage = message)
        }
    }

    fun openSheet() {
        cancelPolling()
        lastImpactSubmittedAtMs = 0L
        emittedMatchedForSessionId = null
        _uiState.value = ProfileBumpUiState(isSheetVisible = true)
    }

    fun hideSheet() {
        _uiState.update { current ->
            current.copy(isSheetVisible = false)
        }
    }

    fun startSession(ttlSeconds: Int = 10) {
        val current = _uiState.value
        if (current.mode == ProfileBumpMode.Listening || current.mode == ProfileBumpMode.Matching || current.mode == ProfileBumpMode.Starting) {
            return
        }

        cancelPolling()
        lastImpactSubmittedAtMs = 0L
        emittedMatchedForSessionId = null
        _uiState.update {
            it.copy(
                mode = ProfileBumpMode.Starting,
                sessionId = null,
                bleToken = null,
                nearbyPeers = emptyList(),
                matchedUserId = null,
                errorMessage = null,
                expiresAtMs = 0L,
            )
        }

        viewModelScope.launch {
            when (val response = bumpRepository.startSession(BumpStartSessionRequest(ttlSeconds = ttlSeconds))) {
                is StartBumpSessionResponse.Success -> {
                    val session = response.data
                    _uiState.update {
                        it.copy(
                            mode = ProfileBumpMode.Listening,
                            sessionId = session.sessionId,
                            bleToken = session.bleToken,
                            nearbyPeers = emptyList(),
                            expiresAtMs = session.expiresAtMs,
                            matchedUserId = null,
                            errorMessage = null,
                        )
                    }
                    scheduleTimeout()
                }

                is StartBumpSessionResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            mode = ProfileBumpMode.Error,
                            errorMessage = "Failed to start bump session",
                        )
                    }
                }
            }
        }
    }

    fun submitImpact(
        lat: Double,
        lon: Double,
        accuracyMeters: Float,
        accelPeak: Float,
        deviceNonce: String,
    ) {
        val current = _uiState.value
        if (current.mode != ProfileBumpMode.Listening && current.mode != ProfileBumpMode.Matching) return
        val sessionId = current.sessionId ?: return
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastImpactSubmittedAtMs < IMPACT_SUBMIT_COOLDOWN_MS) return
        lastImpactSubmittedAtMs = nowMs

        _uiState.update { it.copy(mode = ProfileBumpMode.Matching, errorMessage = null) }

        viewModelScope.launch {
            val nearbyPeers = current.nearbyPeers.sanitizeNearbyPeers()
            when (
                val response = bumpRepository.submitEvent(
                    BumpEventRequest(
                        sessionId = sessionId,
                        timestampMs = nowMs,
                        lat = lat,
                        lon = lon,
                        accuracyMeters = accuracyMeters,
                        accelPeak = accelPeak,
                        nearbyPeers = nearbyPeers,
                        deviceNonce = deviceNonce,
                    )
                )
            ) {
                is SubmitBumpEventResponse.Success -> {
                    applyStatus(response.data.status, response.data.matchedUserId)
                    if (response.data.status == BumpSessionStatus.matching || response.data.status == BumpSessionStatus.waiting) {
                        startPolling(sessionId)
                    }
                }

                is SubmitBumpEventResponse.Error -> {
                    _uiState.update {
                        it.copy(
                            mode = ProfileBumpMode.Error,
                            errorMessage = "Failed to submit bump event",
                        )
                    }
                    lastImpactSubmittedAtMs = 0L
                }
            }
        }
    }

    fun updateNearbyPeerToken(token: String, rssi: Int) {
        val normalizedToken = token.trim().lowercase()
        if (normalizedToken.isBlank()) return

        _uiState.update { current ->
            val updatedPeers = current.nearbyPeers
                .toMutableList()
                .apply {
                    val existingIndex = indexOfFirst { it.token == normalizedToken }
                    if (existingIndex >= 0) {
                        val existing = this[existingIndex]
                        this[existingIndex] = existing.copy(rssi = maxOf(existing.rssi, rssi))
                    } else {
                        add(BumpNearbyPeer(token = normalizedToken, rssi = rssi))
                    }
                }
                .sanitizeNearbyPeers()

            current.copy(nearbyPeers = updatedPeers)
        }
    }

    fun cancelSession() {
        val state = _uiState.value
        val sessionId = state.sessionId
        val expired = state.expiresAtMs > 0L && System.currentTimeMillis() >= state.expiresAtMs
        cancelPolling()
        lastImpactSubmittedAtMs = 0L
        emittedMatchedForSessionId = null
        // Close local bump state immediately so UI is responsive.
        _uiState.value = ProfileBumpUiState(isSheetVisible = state.isSheetVisible)
        if (sessionId == null || expired) return

        viewModelScope.launch {
            // Best-effort network cancel should never block UI lifecycle.
            withTimeoutOrNull(CANCEL_SESSION_TIMEOUT_MS) {
                bumpRepository.cancelSession(BumpCancelSessionRequest(sessionId = sessionId))
            }
        }
    }

    fun resetToIdle() {
        cancelPolling()
        lastImpactSubmittedAtMs = 0L
        emittedMatchedForSessionId = null
        _uiState.value = ProfileBumpUiState()
    }

    private fun startPolling(sessionId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(700)
                val current = _uiState.value
                if (current.sessionId != sessionId) break
                if (current.mode == ProfileBumpMode.Matched || current.mode == ProfileBumpMode.Timeout || current.mode == ProfileBumpMode.Error) {
                    break
                }

                when (val result = bumpRepository.getSessionResult(sessionId)) {
                    is GetBumpSessionResultResponse.Success -> {
                        applyStatus(result.data.status, result.data.matchedUserId)
                    }

                    is GetBumpSessionResultResponse.Error -> {
                        _uiState.update {
                            it.copy(
                                mode = ProfileBumpMode.Error,
                                errorMessage = "Failed to load bump status",
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    private fun scheduleTimeout() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            while (true) {
                delay(250)
                val state = _uiState.value
                val expiresAtMs = state.expiresAtMs
                if (expiresAtMs <= 0L) break
                if (state.mode == ProfileBumpMode.Matched || state.mode == ProfileBumpMode.Error || state.mode == ProfileBumpMode.Timeout || state.mode == ProfileBumpMode.Idle) {
                    break
                }
                if (System.currentTimeMillis() >= expiresAtMs) {
                    _uiState.update { current ->
                        current.copy(mode = ProfileBumpMode.Timeout)
                    }
                    break
                }
            }
        }
    }

    private fun applyStatus(status: BumpSessionStatus, matchedUserId: String?) {
        when (status) {
            BumpSessionStatus.matched -> {
                val matchedId = matchedUserId
                val current = _uiState.value
                val currentSessionId = current.sessionId
                if (current.mode != ProfileBumpMode.Matched || current.matchedUserId != matchedId) {
                    _uiState.update {
                        it.copy(
                            mode = ProfileBumpMode.Matched,
                            matchedUserId = matchedId,
                        )
                    }
                }
                cancelPolling()
                if (!matchedId.isNullOrBlank() && !currentSessionId.isNullOrBlank() && emittedMatchedForSessionId != currentSessionId) {
                    emittedMatchedForSessionId = currentSessionId
                    viewModelScope.launch {
                        _openMatchedProfile.emit(matchedId)
                    }
                }
            }

            BumpSessionStatus.matching,
            BumpSessionStatus.waiting -> {
                _uiState.update {
                    it.copy(
                        mode = ProfileBumpMode.Matching,
                        matchedUserId = null,
                    )
                }
            }

            BumpSessionStatus.timeout -> {
                _uiState.update { it.copy(mode = ProfileBumpMode.Timeout) }
            }

            BumpSessionStatus.cancelled -> {
                _uiState.update { it.copy(mode = ProfileBumpMode.Idle) }
            }

            BumpSessionStatus.error -> {
                _uiState.update {
                    it.copy(
                        mode = ProfileBumpMode.Error,
                        errorMessage = "Bump flow failed",
                    )
                }
            }
        }
    }

    private fun cancelPolling() {
        pollJob?.cancel()
        pollJob = null
        timeoutJob?.cancel()
        timeoutJob = null
    }

    override fun onCleared() {
        cancelPolling()
        super.onCleared()
    }
}

private fun List<BumpNearbyPeer>.sanitizeNearbyPeers(): List<BumpNearbyPeer> {
    return asSequence()
        .filter { it.token.isNotBlank() }
        .groupBy { it.token }
        .map { (token, peers) ->
            BumpNearbyPeer(
                token = token,
                rssi = peers.maxOf { it.rssi },
            )
        }
        .sortedByDescending { it.rssi }
        .take(MAX_NEARBY_PEERS)
        .toList()
}
