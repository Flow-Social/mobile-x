package me.floow.data.repos

import me.floow.domain.api.PushTokensApi
import me.floow.domain.api.models.RegisterPushTokenData
import me.floow.domain.api.models.RegisterPushTokenResponse
import me.floow.domain.api.models.RevokePushTokenResponse
import me.floow.domain.data.FailureError
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PushTokensRepository
import me.floow.domain.utils.Logger

class PushTokensRepositoryImpl(
    private val logger: Logger,
    private val pushTokensApi: PushTokensApi
) : PushTokensRepository {
    override suspend fun registerToken(
        token: String,
        appVersion: String,
        locale: String?,
        timezone: String?
    ): UpdateDataResponse {
        val sanitizedToken = token.trim()
        if (sanitizedToken.isBlank()) {
            return UpdateDataResponse.Failure(FailureError.Other)
        }

        val response = pushTokensApi.registerToken(
            RegisterPushTokenData(
                token = sanitizedToken,
                platform = "android",
                appVersion = appVersion,
                locale = locale,
                timezone = timezone
            )
        )

        return when (response) {
            RegisterPushTokenResponse.Success -> UpdateDataResponse.Success
            RegisterPushTokenResponse.Error -> {
                logger.d("PushTokensRepositoryImpl.registerToken", "Failed to register push token")
                UpdateDataResponse.Failure(FailureError.Other)
            }
        }
    }

    override suspend fun revokeToken(token: String): UpdateDataResponse {
        val sanitizedToken = token.trim()
        if (sanitizedToken.isBlank()) {
            return UpdateDataResponse.Failure(FailureError.Other)
        }

        return when (pushTokensApi.revokeToken(sanitizedToken)) {
            RevokePushTokenResponse.Success -> UpdateDataResponse.Success
            RevokePushTokenResponse.Error -> {
                logger.d("PushTokensRepositoryImpl.revokeToken", "Failed to revoke push token")
                UpdateDataResponse.Failure(FailureError.Other)
            }
        }
    }
}
