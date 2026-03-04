package me.floow.profile.uilogic.bump

import me.floow.domain.api.models.BumpNearbyPeer

enum class ProfileBumpMode {
    Idle,
    Starting,
    Listening,
    Matching,
    Matched,
    Timeout,
    Error,
}

data class ProfileBumpUiState(
    val isSheetVisible: Boolean = false,
    val mode: ProfileBumpMode = ProfileBumpMode.Idle,
    val sessionId: String? = null,
    val bleToken: String? = null,
    val nearbyPeers: List<BumpNearbyPeer> = emptyList(),
    val expiresAtMs: Long = 0L,
    val matchedUserId: String? = null,
    val errorMessage: String? = null,
) {
    val isDetectorEnabled: Boolean
        get() = mode == ProfileBumpMode.Listening || mode == ProfileBumpMode.Matching

    val isBleProximityEnabled: Boolean
        get() = isDetectorEnabled && !bleToken.isNullOrBlank()
}
