package me.floow.shared.login.auth

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.AuthenticationResult
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.login.uilogic.AuthRepositoryState

private val Context.loginAuthDataStore by preferencesDataStore(name = "flow_login_auth")

class AndroidAuthRepository(
    private val context: Context,
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
    override val state: StateFlow<AuthRepositoryState> = _state

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
                        handleAuthenticationResult(authState.authenticationResult)
                    }
                }
            }
        }
    }

    override suspend fun signInWithGoogle() {
        _state.update { AuthRepositoryState.Loading }
        authenticationManager.startGoogleAuthentication()
    }

    override suspend fun resumeAuthorization() = Unit

    private suspend fun handleAuthenticationResult(result: AuthenticationResult) {
        when (result) {
            is AuthenticationResult.Success -> {
                persistSession(
                    token = result.token,
                    userId = authenticationManager.getSelfUserIdOrNull(),
                    isRegistration = result.isRegistration
                )
                _state.update {
                    if (result.isRegistration) {
                        AuthRepositoryState.PendingRegistration
                    } else {
                        AuthRepositoryState.Authenticated
                    }
                }
            }

            AuthenticationResult.Failure -> {
                _state.update { AuthRepositoryState.Error("Login failed") }
            }
        }
    }

    private suspend fun persistSession(
        token: String,
        userId: String?,
        isRegistration: Boolean,
    ) {
        context.applicationContext.loginAuthDataStore.edit { preferences: MutablePreferences ->
            preferences[SESSION_TOKEN_KEY] = token
            preferences[IS_REGISTRATION_KEY] = isRegistration
            if (!userId.isNullOrBlank()) {
                preferences[USER_ID_KEY] = userId
            }
        }
    }

    private companion object {
        val SESSION_TOKEN_KEY = stringPreferencesKey("session_token")
        val USER_ID_KEY = stringPreferencesKey("user_id")
        val IS_REGISTRATION_KEY = booleanPreferencesKey("is_registration")
    }
}
