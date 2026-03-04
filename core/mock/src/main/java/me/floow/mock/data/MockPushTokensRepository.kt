package me.floow.mock.data

import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PushTokensRepository

class MockPushTokensRepository : PushTokensRepository {
    override suspend fun registerToken(
        token: String,
        appVersion: String,
        locale: String?,
        timezone: String?
    ): UpdateDataResponse {
        return UpdateDataResponse.Success
    }

    override suspend fun revokeToken(token: String): UpdateDataResponse {
        return UpdateDataResponse.Success
    }
}
