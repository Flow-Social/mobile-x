package me.floow.domain.auth.models

sealed interface AuthState {
    data object NoIdToken : AuthState

    data object HandlingOAuth : AuthState

    data class HasResult(val authenticationResult: AuthenticationResult) : AuthState
}

data class PendingRegistrationInitialData(
    val name: String,
    val username: String,
    val description: String,
)
