package me.floow.mock

import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.AuthApiResult
import me.floow.domain.api.models.CompleteGoogleRegistrationResult
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.models.PendingRegistrationInitialData

class AuthApiMock : AuthApi {
    override suspend fun getAuthTokenByGoogleIdToken(idToken: String): AuthApiResult {
        return AuthApiResult.Success(
            token = "mock_pending_or_auth_token",
            isRegistration = true,
            userId = "mock_user_id",
            pendingInitialData = PendingRegistrationInitialData(
                name = "Mock User",
                username = "mock_user",
                description = ""
            )
        )
    }

    override suspend fun completeGoogleRegistration(
        pendingToken: String,
        data: EditProfileData,
        avatarUrl: String?
    ): CompleteGoogleRegistrationResult {
        return if (data.username?.value == "taken") {
            CompleteGoogleRegistrationResult.UsernameAlreadyExists
        } else {
            CompleteGoogleRegistrationResult.Success(token = "mock_auth_token", userId = "mock_user_id")
        }
    }
}
