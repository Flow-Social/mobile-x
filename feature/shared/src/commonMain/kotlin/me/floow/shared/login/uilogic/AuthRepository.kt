package me.floow.shared.login.uilogic

import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val state: StateFlow<AuthRepositoryState>

    suspend fun signInWithGoogle()

    suspend fun resumeAuthorization()
}

sealed interface AuthRepositoryState {
    data object Idle : AuthRepositoryState

    data object Loading : AuthRepositoryState

    data object Authenticated : AuthRepositoryState

    data object PendingRegistration : AuthRepositoryState

    data class Error(val message: String) : AuthRepositoryState
}
