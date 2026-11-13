package me.floow.domain.api

import me.floow.domain.api.models.RegisterPushTokenData
import me.floow.domain.api.models.RegisterPushTokenResponse
import me.floow.domain.api.models.RevokePushTokenResponse

interface PushTokensApi {
    suspend fun registerToken(data: RegisterPushTokenData): RegisterPushTokenResponse

    suspend fun revokeToken(token: String): RevokePushTokenResponse
}
