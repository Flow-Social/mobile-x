package me.floow.shared.login.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.AuthenticationResult
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.login.uilogic.AuthRepositoryState

class CommonAuthRepository(
    private val authenticationManager: AuthenticationManager,
) : AuthRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(
        when {
            authenticationManager.hasPendingRegistration() -> AuthRepositoryState.PendingRegistration
            authenticationManager.isSignedIn() -> AuthRepositoryState.Authenticated
            else -> AuthRepositoryState.Idle
        }
    )
    override val state: StateFlow<AuthRepositoryState> = _state.asStateFlow()

    init {
        scope.launch {
            authenticationManager.authenticationStateFlow.collect { authState ->
                when (authState) {
                    AuthState.NoIdToken -> {
                        if (!authenticationManager.isSignedIn() && !authenticationManager.hasPendingRegistration()) {
                            _state.update { AuthRepositoryState.Idle }
                        }
                    }
                    AuthState.HandlingOAuth -> {
                        _state.update { AuthRepositoryState.Loading }
                    }
                    is AuthState.HasResult -> {
                        when (val authResult = authState.authenticationResult) {
                            is AuthenticationResult.Success -> {
                                if (authResult.isRegistration) {
                                    _state.update { AuthRepositoryState.PendingRegistration }
                                } else {
                                    _state.update { AuthRepositoryState.Authenticated }
                                }
                            }
                            AuthenticationResult.Failure -> {
                                _state.update { AuthRepositoryState.Error("Authentication failed") }
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun signInWithGoogle() {
        authenticationManager.startGoogleAuthentication()
    }

    override suspend fun resumeAuthorization() {
        val pending = authenticationManager.hasPendingRegistration()
        val signedIn = authenticationManager.isSignedIn()
        _state.update {
            when {
                pending -> AuthRepositoryState.PendingRegistration
                signedIn -> AuthRepositoryState.Authenticated
                else -> AuthRepositoryState.Idle
            }
        }
    }
}
