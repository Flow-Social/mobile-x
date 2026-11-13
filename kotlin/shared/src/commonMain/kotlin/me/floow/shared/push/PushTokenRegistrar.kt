package me.floow.shared.push

import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PushTokensRepository
import me.floow.domain.utils.Logger

interface PushTokenRegistrar {
    suspend fun register(
        token: String,
        platform: String,
        appVersion: String = "1.0.0",
        locale: String? = null,
        timezone: String? = null,
    ): Boolean

    suspend fun revoke(token: String = ""): Boolean
}

class PushTokenRegistrarImpl(
    private val repository: PushTokensRepository,
    private val logger: Logger,
) : PushTokenRegistrar {

    private var lastRegisteredToken: String? = null

    override suspend fun register(
        token: String,
        platform: String,
        appVersion: String,
        locale: String?,
        timezone: String?,
    ): Boolean {
        if (token.isBlank()) return false
        logger.d("PushTokenRegistrar", "Registering push token for platform=$platform")
        val result = repository.registerToken(
            token = token,
            appVersion = appVersion,
            locale = locale,
            timezone = timezone,
            platform = platform,
        )
        return when (result) {
            is UpdateDataResponse.Success -> {
                lastRegisteredToken = token
                logger.d("PushTokenRegistrar", "Successfully registered push token for platform=$platform")
                true
            }
            is UpdateDataResponse.Failure -> {
                logger.d("PushTokenRegistrar", "Failed to register push token for platform=$platform")
                false
            }
        }
    }

    override suspend fun revoke(token: String): Boolean {
        val targetToken = token.ifBlank { lastRegisteredToken.orEmpty() }
        if (targetToken.isBlank()) return true
        val result = repository.revokeToken(targetToken)
        return when (result) {
            is UpdateDataResponse.Success -> {
                if (lastRegisteredToken == targetToken) {
                    lastRegisteredToken = null
                }
                logger.d("PushTokenRegistrar", "Successfully revoked push token")
                true
            }
            is UpdateDataResponse.Failure -> {
                logger.d("PushTokenRegistrar", "Failed to revoke push token")
                false
            }
        }
    }
}
