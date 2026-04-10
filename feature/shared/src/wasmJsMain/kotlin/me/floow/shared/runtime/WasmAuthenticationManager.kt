package me.floow.shared.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.AuthenticationResult
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.login.auth.flowAuthWriteLocalStorage

class WasmAuthenticationManager : AuthenticationManager {
    override val authenticationStateFlow: StateFlow<AuthState>
        get() = _authenticationStateFlow

    private val _authenticationStateFlow = MutableStateFlow(readInitialState())

    override suspend fun handleGoogleOAuthCode(code: String) {
        error("WasmAuthenticationManager does not handle Google OAuth codes directly")
    }

    override suspend fun getAuthTokenOrNull(): String? {
        return readValue(SESSION_TOKEN_KEY)
    }

    override fun getSelfUserIdOrNull(): String? {
        return readValue(SELF_USER_ID_KEY)
    }

    override fun saveSelfUserId(userId: String) {
        val normalized = userId.trim().takeIf(String::isNotEmpty) ?: return
        flowAuthWriteLocalStorage(SELF_USER_ID_KEY, normalized)
    }

    override fun isSignedIn(): Boolean {
        return readValue(SESSION_TOKEN_KEY) != null
    }

    override fun hasPendingRegistration(): Boolean {
        return readValue(IS_REGISTRATION_KEY) == "true"
    }

    override fun getPendingRegistrationTokenOrNull(): String? {
        return if (hasPendingRegistration()) readValue(SESSION_TOKEN_KEY) else null
    }

    override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? {
        if (!hasPendingRegistration()) return null
        return PendingRegistrationInitialData(
            name = readValue(PENDING_NAME_KEY).orEmpty(),
            username = readValue(PENDING_USERNAME_KEY).orEmpty(),
            description = readValue(PENDING_DESCRIPTION_KEY).orEmpty(),
        )
    }

    override suspend fun startGoogleAuthentication() {
        error("WasmAuthenticationManager does not start Google authentication directly")
    }

    override suspend fun writeAuthToken(token: String) {
        val normalized = token.trim()
        if (normalized.isEmpty()) return
        flowAuthWriteLocalStorage(SESSION_TOKEN_KEY, normalized)
        flowAuthWriteLocalStorage(IS_REGISTRATION_KEY, "false")
        clearPendingInitialData()
        _authenticationStateFlow.update {
            AuthState.HasResult(
                AuthenticationResult.Success(
                    token = normalized,
                    isRegistration = false,
                )
            )
        }
    }

    override suspend fun clearPendingRegistration() {
        flowAuthWriteLocalStorage(IS_REGISTRATION_KEY, "")
        clearPendingInitialData()
        syncState()
    }

    override suspend fun clearAuth() {
        flowAuthWriteLocalStorage(SESSION_TOKEN_KEY, "")
        flowAuthWriteLocalStorage(IS_REGISTRATION_KEY, "")
        flowAuthWriteLocalStorage(SELF_USER_ID_KEY, "")
        clearPendingInitialData()
        _authenticationStateFlow.update { AuthState.NoIdToken }
    }

    private fun syncState() {
        _authenticationStateFlow.update { readInitialState() }
    }

    private fun readInitialState(): AuthState {
        val token = readValue(SESSION_TOKEN_KEY) ?: return AuthState.NoIdToken
        val isRegistration = readValue(IS_REGISTRATION_KEY) == "true"
        return AuthState.HasResult(
            AuthenticationResult.Success(
                token = token,
                isRegistration = isRegistration,
            )
        )
    }

    private fun readValue(key: String): String? {
        return flowAuthReadLocalStorage(key)?.trim()?.takeIf(String::isNotEmpty)
    }

    private fun clearPendingInitialData() {
        flowAuthWriteLocalStorage(PENDING_NAME_KEY, "")
        flowAuthWriteLocalStorage(PENDING_USERNAME_KEY, "")
        flowAuthWriteLocalStorage(PENDING_DESCRIPTION_KEY, "")
    }

    private companion object {
        const val SESSION_TOKEN_KEY = "flow.auth.session_token"
        const val IS_REGISTRATION_KEY = "flow.auth.is_registration"
        const val SELF_USER_ID_KEY = "flow.auth.user_id"
        const val PENDING_NAME_KEY = "flow.auth.pending.name"
        const val PENDING_USERNAME_KEY = "flow.auth.pending.username"
        const val PENDING_DESCRIPTION_KEY = "flow.auth.pending.description"
    }
}
