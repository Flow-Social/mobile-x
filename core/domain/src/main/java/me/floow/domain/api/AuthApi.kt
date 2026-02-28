package me.floow.domain.api

import me.floow.domain.api.models.AuthApiResult
import me.floow.domain.api.models.CompleteGoogleRegistrationResult
import me.floow.domain.api.models.EditProfileData

interface AuthApi {
    suspend fun getAuthTokenByGoogleIdToken(idToken: String): AuthApiResult

    suspend fun completeGoogleRegistration(
        pendingToken: String,
        data: EditProfileData,
        avatarUrl: String? = null
    ): CompleteGoogleRegistrationResult
}
