package me.floow.shared.login.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    init {
        scope.launch {
            authRepository.state.collectLatest { authState ->
                _state.value = when (authState) {
                    AuthRepositoryState.Idle -> LoginState.Idle
                    AuthRepositoryState.Loading -> LoginState.Loading
                    AuthRepositoryState.Authenticated -> LoginState.Authenticated
                    AuthRepositoryState.PendingRegistration -> LoginState.PendingRegistration
                    is AuthRepositoryState.Error -> LoginState.Error(authState.message)
                }
            }
        }
    }

    fun signInWithGoogle() {
        scope.launch {
            authRepository.signInWithGoogle()
        }
    }

    fun resumeAuthorization() {
        scope.launch {
            authRepository.resumeAuthorization()
        }
    }

    fun consumeError() {
        val currentState = _state.value
        if (currentState is LoginState.Error) {
            _state.value = LoginState.Idle
        }
    }
}

sealed interface LoginState {
    data object Idle : LoginState

    data object Loading : LoginState

    data object Authenticated : LoginState

    data object PendingRegistration : LoginState

    data class Error(val message: String) : LoginState
}
