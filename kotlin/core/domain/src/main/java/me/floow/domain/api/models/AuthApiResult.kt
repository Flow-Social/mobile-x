package me.floow.domain.api.models

import me.floow.domain.auth.models.PendingRegistrationInitialData

sealed interface AuthApiResult {
    data class Success(
        val token: String,
        val isRegistration: Boolean,
        val userId: String? = null,
        val pendingInitialData: PendingRegistrationInitialData? = null,
    ) : AuthApiResult

    data object Failure : AuthApiResult
}

sealed interface CompleteGoogleRegistrationResult {
    data class Success(
        val token: String,
        val userId: String? = null,
    ) : CompleteGoogleRegistrationResult

    data object UsernameAlreadyExists : CompleteGoogleRegistrationResult

    data object Failure : CompleteGoogleRegistrationResult
}
