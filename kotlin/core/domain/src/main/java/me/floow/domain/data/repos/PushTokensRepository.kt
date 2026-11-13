package me.floow.domain.data.repos

import me.floow.domain.data.UpdateDataResponse

interface PushTokensRepository {
    suspend fun registerToken(
        token: String,
        appVersion: String,
        locale: String?,
        timezone: String?,
        platform: String = "android"
    ): UpdateDataResponse

    suspend fun revokeToken(token: String): UpdateDataResponse
}
