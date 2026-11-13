package me.floow.domain.api.models

data class RegisterPushTokenData(
    val token: String,
    val platform: String,
    val appVersion: String,
    val locale: String?,
    val timezone: String?
)

sealed interface RegisterPushTokenResponse {
    data object Success : RegisterPushTokenResponse
    data object Error : RegisterPushTokenResponse
}

sealed interface RevokePushTokenResponse {
    data object Success : RevokePushTokenResponse
    data object Error : RevokePushTokenResponse
}
