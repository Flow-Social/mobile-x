package me.floow.shared.login.auth

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.floow.domain.auth.AuthenticationManager
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.login.uilogic.AuthRepositoryState
import me.floow.shared.profile.auth.flowWasmApiRequest
import kotlin.js.JsName
import kotlin.coroutines.resume

@JsName("flowAuthSignInWithGoogle")
external fun flowAuthSignInWithGoogle(
    clientId: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

@JsName("flowAuthExchangeIdToken")
external fun flowAuthExchangeIdToken(
    idToken: String,
    apiUrl: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit,
)

class WasmAuthRepository(
    private val authenticationManager: AuthenticationManager,
) : AuthRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(initialState())

    override val state: StateFlow<AuthRepositoryState> = _state

    override suspend fun signInWithGoogle() {
        _state.update { AuthRepositoryState.Loading }
        val idToken = suspendCancellableCoroutine<String?> { continuation ->
            flowAuthSignInWithGoogle(
                clientId = GOOGLE_WEB_CLIENT_ID,
                onSuccess = { credential ->
                    if (continuation.isActive) {
                        continuation.resume(credential)
                    }
                },
                onError = { message ->
                    if (continuation.isActive) {
                        continuation.resume("""{"error":"$message"}""")
                    }
                },
            )
        } ?: run {
            _state.update { AuthRepositoryState.Idle }
            return
        }

        if (idToken.startsWith("{")) {
            val error = runCatching {
                json.decodeFromString(WasmAuthResponse.serializer(), idToken).error
            }.getOrNull()
            _state.update { AuthRepositoryState.Error(error ?: "Google sign-in failed") }
            return
        }

        exchangeGoogleIdToken(idToken)
    }

    override suspend fun resumeAuthorization() {
        restoreSelfUserIdIfNeeded()
        _state.update { readPersistedState() }
    }

    private suspend fun exchangeGoogleIdToken(idToken: String) {
        val payload = suspendCancellableCoroutine<String?> { continuation ->
            flowAuthExchangeIdToken(
                idToken = idToken,
                apiUrl = FLOW_API_URL,
                onSuccess = { raw ->
                    if (continuation.isActive) {
                        continuation.resume(raw)
                    }
                },
                onError = { message ->
                    if (continuation.isActive) {
                        continuation.resume("""{"error":"$message"}""")
                    }
                },
            )
        } ?: run {
            _state.update { AuthRepositoryState.Error("Empty backend response") }
            return
        }

        val parsed = runCatching { json.decodeFromString(WasmAuthResponse.serializer(), payload) }
            .getOrNull()
            ?: run {
                _state.update { AuthRepositoryState.Error("Invalid auth response") }
                return
            }

        if (!parsed.error.isNullOrBlank()) {
            _state.update { AuthRepositoryState.Error(parsed.error) }
            return
        }

        val token = parsed.token
        if (token.isNullOrBlank()) {
            _state.update { AuthRepositoryState.Error("Missing session token") }
            return
        }

        val selfUserId = if (parsed.isNewUser) {
            null
        } else {
            parsed.user?.id?.trim()?.takeIf(String::isNotEmpty)
                ?: fetchSelfUserId(token)
        }

        persistSession(
            token = token,
            isRegistration = parsed.isNewUser,
            pendingProfile = parsed.pendingProfile,
            selfUserId = selfUserId,
        )
        _state.update { readPersistedState() }
    }

    private fun initialState(): AuthRepositoryState {
        return if (requiresSelfUserIdRestore()) {
            AuthRepositoryState.Loading
        } else {
            readPersistedState()
        }
    }

    private fun readPersistedState(): AuthRepositoryState {
        return when {
            hasPersistedSession() && hasPendingRegistration() -> AuthRepositoryState.PendingRegistration
            hasPersistedSession() -> AuthRepositoryState.Authenticated
            else -> AuthRepositoryState.Idle
        }
    }

    private fun requiresSelfUserIdRestore(): Boolean {
        return hasPersistedSession() &&
            !hasPendingRegistration() &&
            authenticationManager.getSelfUserIdOrNull().isNullOrBlank()
    }

    private fun hasPersistedSession(): Boolean =
        flowAuthReadLocalStorage(SESSION_TOKEN_KEY)?.isNotBlank() == true

    private fun hasPendingRegistration(): Boolean =
        flowAuthReadLocalStorage(IS_REGISTRATION_KEY) == "true"

    private suspend fun restoreSelfUserIdIfNeeded() {
        if (!requiresSelfUserIdRestore()) return
        _state.update { AuthRepositoryState.Loading }
        val token = flowAuthReadLocalStorage(SESSION_TOKEN_KEY)?.trim()?.takeIf(String::isNotEmpty) ?: return
        fetchSelfUserId(token)?.let(authenticationManager::saveSelfUserId)
    }

    private suspend fun fetchSelfUserId(token: String): String? {
        val payload = suspendCancellableCoroutine<String?> { continuation ->
            flowWasmApiRequest(
                method = "GET",
                path = "/profile",
                apiUrl = FLOW_API_URL,
                authToken = token,
                contentType = null,
                body = null,
                onSuccess = { raw ->
                    if (continuation.isActive) {
                        continuation.resume(raw)
                    }
                },
                onError = { message ->
                    if (continuation.isActive) {
                        continuation.resume("""{"error":"$message"}""")
                    }
                },
            )
        } ?: return null

        return runCatching {
            json.decodeFromString(WasmProfileResponse.serializer(), payload)
                .id
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }.getOrNull()
    }

    private fun persistSession(
        token: String,
        isRegistration: Boolean,
        pendingProfile: WasmPendingProfile?,
        selfUserId: String?,
    ) {
        flowAuthWriteLocalStorage(SESSION_TOKEN_KEY, token)
        flowAuthWriteLocalStorage(IS_REGISTRATION_KEY, isRegistration.toString())
        if (isRegistration) {
            flowAuthWriteLocalStorage(SELF_USER_ID_KEY, "")
        } else {
            flowAuthWriteLocalStorage(SELF_USER_ID_KEY, selfUserId.orEmpty())
        }
        flowAuthWriteLocalStorage(PENDING_NAME_KEY, pendingProfile?.name.orEmpty())
        flowAuthWriteLocalStorage(PENDING_USERNAME_KEY, pendingProfile?.username.orEmpty())
        flowAuthWriteLocalStorage(PENDING_DESCRIPTION_KEY, pendingProfile?.description.orEmpty())
    }

    private companion object {
        const val GOOGLE_WEB_CLIENT_ID =
            "291755427997-o08h65qcr40sq8nv1vo064d9p3ch17o8.apps.googleusercontent.com"
        const val FLOW_API_URL = "https://45.66.228.158.nip.io/api/v1"
        const val SESSION_TOKEN_KEY = "flow.auth.session_token"
        const val IS_REGISTRATION_KEY = "flow.auth.is_registration"
        const val SELF_USER_ID_KEY = "flow.auth.user_id"
        const val PENDING_NAME_KEY = "flow.auth.pending.name"
        const val PENDING_USERNAME_KEY = "flow.auth.pending.username"
        const val PENDING_DESCRIPTION_KEY = "flow.auth.pending.description"
    }
}

@JsName("flowAuthWriteLocalStorage")
external fun flowAuthWriteLocalStorage(key: String, value: String)

@JsName("flowAuthReadLocalStorage")
external fun flowAuthReadLocalStorage(key: String): String?

@Serializable
private data class WasmAuthResponse(
    val token: String? = null,
    val user: WasmAuthUser? = null,
    @SerialName("is_new_user")
    val isNewUser: Boolean = false,
    @SerialName("pending_profile")
    val pendingProfile: WasmPendingProfile? = null,
    val error: String? = null,
)

@Serializable
private data class WasmAuthUser(
    val id: String? = null,
)

@Serializable
private data class WasmPendingProfile(
    val name: String = "",
    val username: String = "",
    val description: String = "",
)

@Serializable
private data class WasmProfileResponse(
    val id: String? = null,
    val error: String? = null,
)
