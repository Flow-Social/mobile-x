package me.floow.domain.api.models

import kotlinx.serialization.Serializable

@Serializable
data class BumpStartSessionRequest(
    val ttlSeconds: Int = 10
)

@Serializable
data class BumpStartSessionResponse(
    val sessionId: String,
    val expiresAtMs: Long,
    val bleToken: String? = null,
)

@Serializable
data class BumpNearbyPeer(
    val token: String,
    val rssi: Int,
)

@Serializable
data class BumpEventRequest(
    val sessionId: String,
    val timestampMs: Long,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val accelPeak: Float,
    val nearbyPeers: List<BumpNearbyPeer> = emptyList(),
    val deviceNonce: String
)

@Serializable
data class BumpEventResult(
    val status: BumpSessionStatus,
    val matchedUserId: String? = null,
    val sessionId: String
)

@Serializable
data class BumpSessionResult(
    val status: BumpSessionStatus,
    val matchedUserId: String? = null,
    val sessionId: String
)

@Serializable
data class BumpCancelSessionRequest(
    val sessionId: String
)

@Serializable
data class BumpCancelSessionResult(
    val ok: Boolean,
    val sessionId: String
)

@Serializable
enum class BumpSessionStatus {
    waiting,
    matching,
    matched,
    timeout,
    cancelled,
    error
}

sealed interface StartBumpSessionResponse {
    data class Success(val data: BumpStartSessionResponse) : StartBumpSessionResponse
    data object Error : StartBumpSessionResponse
}

sealed interface SubmitBumpEventResponse {
    data class Success(val data: BumpEventResult) : SubmitBumpEventResponse
    data object Error : SubmitBumpEventResponse
}

sealed interface GetBumpSessionResultResponse {
    data class Success(val data: BumpSessionResult) : GetBumpSessionResultResponse
    data object Error : GetBumpSessionResultResponse
}

sealed interface CancelBumpSessionResponse {
    data class Success(val data: BumpCancelSessionResult) : CancelBumpSessionResponse
    data object Error : CancelBumpSessionResponse
}
