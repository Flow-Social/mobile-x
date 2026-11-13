package me.floow.shared.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PushTokenRegistrationResult {
    data object Success : PushTokenRegistrationResult
    data class Failure(val error: String) : PushTokenRegistrationResult
}

interface PushNotificationContract {
    suspend fun registerDeviceToken(
        token: String,
        platform: String,
        appVersion: String = "1.0.0",
        locale: String? = null,
        timezone: String? = null,
    ): PushTokenRegistrationResult

    suspend fun revokeDeviceToken(
        token: String,
    ): PushTokenRegistrationResult
}

/**
 * Common Push Notification Manager coordinating device push tokens,
 * registration cycles, and token rotation.
 */
class PushNotificationManager(
    private val contract: PushNotificationContract,
    private val platform: String,
    private val appVersion: String = "1.0.0",
) {
    private val _deviceToken = MutableStateFlow<String?>(null)
    val deviceToken: StateFlow<String?> = _deviceToken.asStateFlow()

    private val _isRegistered = MutableStateFlow(false)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    suspend fun onNewTokenReceived(
        token: String,
        locale: String? = null,
        timezone: String? = null,
    ): PushTokenRegistrationResult {
        val sanitized = token.trim()
        if (sanitized.isEmpty()) {
            return PushTokenRegistrationResult.Failure("Empty push token")
        }
        _deviceToken.value = sanitized

        val result = contract.registerDeviceToken(
            token = sanitized,
            platform = platform,
            appVersion = appVersion,
            locale = locale,
            timezone = timezone,
        )

        if (result is PushTokenRegistrationResult.Success) {
            _isRegistered.value = true
        }
        return result
    }

    suspend fun revokeToken(): PushTokenRegistrationResult {
        val token = _deviceToken.value ?: return PushTokenRegistrationResult.Success
        val result = contract.revokeDeviceToken(token)
        if (result is PushTokenRegistrationResult.Success) {
            _deviceToken.value = null
            _isRegistered.value = false
        }
        return result
    }
}
