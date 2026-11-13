package me.floow.shared.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.AuthApiResult
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.GoogleOAuth
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.AuthenticationResult
import me.floow.domain.auth.models.GoogleOAuthResult
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.utils.Logger

class CommonAuthenticationManager(
    private val tokenStorage: TokenStorage,
    private val logger: Logger,
    private val googleOAuth: GoogleOAuth?,
    private val authApi: AuthApi,
) : AuthenticationManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _authenticationStateFlow = MutableStateFlow<AuthState>(AuthState.NoIdToken)
    override val authenticationStateFlow: StateFlow<AuthState> = _authenticationStateFlow.asStateFlow()

    private var selfUserId: String? = null
    private var pendingToken: String? = null
    private var pendingInitialData: PendingRegistrationInitialData? = null

    init {
        val syncUserId = tokenStorage.getSyncUserIdOrNull()
        val syncToken = tokenStorage.getSyncAccessTokenOrNull()
        if (!syncUserId.isNullOrBlank()) {
            selfUserId = syncUserId
        } else if (!syncToken.isNullOrBlank()) {
            selfUserId = JwtPayloadParser.extractUserId(syncToken)
        }

        if (!syncToken.isNullOrBlank()) {
            _authenticationStateFlow.value = AuthState.HasResult(
                AuthenticationResult.Success(token = syncToken, isRegistration = false)
            )
        }

        scope.launch {
            val token = tokenStorage.getAccessToken()
            val savedUserId = tokenStorage.getUserId() ?: token?.let { JwtPayloadParser.extractUserId(it) }
            if (!savedUserId.isNullOrBlank()) {
                selfUserId = savedUserId
            }
            if (!token.isNullOrBlank()) {
                _authenticationStateFlow.value = AuthState.HasResult(
                    AuthenticationResult.Success(token = token, isRegistration = false)
                )
            } else if (syncToken.isNullOrBlank()) {
                _authenticationStateFlow.value = AuthState.NoIdToken
            }
        }
    }

    override suspend fun handleGoogleOAuthCode(code: String) {
        logger.d("CommonAuthenticationManager", "Start handling Google OAuth code")
        _authenticationStateFlow.update { AuthState.HandlingOAuth }

        val oauth = googleOAuth
        if (oauth == null) {
            logger.d("CommonAuthenticationManager", "GoogleOAuth is not configured")
            _authenticationStateFlow.update { AuthState.HasResult(AuthenticationResult.Failure) }
            return
        }

        when (val result = oauth.handleGoogleOAuthCode(code)) {
            is GoogleOAuthResult.Success -> {
                val apiResult = authApi.getAuthTokenByGoogleIdToken(result.idToken)
                if (apiResult is AuthApiResult.Success) {
                    if (apiResult.isRegistration) {
                        pendingToken = apiResult.token
                        pendingInitialData = apiResult.pendingInitialData
                        _authenticationStateFlow.update {
                            AuthState.HasResult(
                                AuthenticationResult.Success(
                                    token = apiResult.token,
                                    isRegistration = true
                                )
                            )
                        }
                    } else {
                        selfUserId = apiResult.userId
                        apiResult.userId?.let { uid -> scope.launch { tokenStorage.saveUserId(uid) } }
                        writeAuthToken(apiResult.token)
                        _authenticationStateFlow.update {
                            AuthState.HasResult(
                                AuthenticationResult.Success(
                                    token = apiResult.token,
                                    isRegistration = false
                                )
                            )
                        }
                    }
                } else {
                    _authenticationStateFlow.update { AuthState.HasResult(AuthenticationResult.Failure) }
                }
            }
            is GoogleOAuthResult.Failure -> {
                _authenticationStateFlow.update { AuthState.HasResult(AuthenticationResult.Failure) }
            }
        }
    }

    override suspend fun getAuthTokenOrNull(): String? = tokenStorage.getAccessToken()

    override fun getSelfUserIdOrNull(): String? {
        val cached = selfUserId
        if (!cached.isNullOrBlank()) return cached
        val syncUser = tokenStorage.getSyncUserIdOrNull()
        if (!syncUser.isNullOrBlank()) {
            selfUserId = syncUser
            return syncUser
        }
        val syncToken = tokenStorage.getSyncAccessTokenOrNull()
        if (!syncToken.isNullOrBlank()) {
            val extracted = JwtPayloadParser.extractUserId(syncToken)
            if (!extracted.isNullOrBlank()) {
                selfUserId = extracted
                return extracted
            }
        }
        return null
    }

    override fun saveSelfUserId(userId: String) {
        selfUserId = userId
        scope.launch { tokenStorage.saveUserId(userId) }
    }

    override fun isSignedIn(): Boolean {
        val current = _authenticationStateFlow.value
        val result = (current as? AuthState.HasResult)?.authenticationResult
        return result is AuthenticationResult.Success && !result.isRegistration
    }

    override fun hasPendingRegistration(): Boolean {
        val current = _authenticationStateFlow.value
        val result = (current as? AuthState.HasResult)?.authenticationResult
        return result is AuthenticationResult.Success && result.isRegistration
    }

    override fun getPendingRegistrationTokenOrNull(): String? = pendingToken

    override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? = pendingInitialData

    override suspend fun startGoogleAuthentication() {
        googleOAuth?.startSignIn()
    }

    override suspend fun writeAuthToken(token: String) {
        pendingToken = null
        pendingInitialData = null
        val extractedId = JwtPayloadParser.extractUserId(token)
        if (!extractedId.isNullOrBlank()) {
            selfUserId = extractedId
        }
        tokenStorage.saveTokens(accessToken = token)
        _authenticationStateFlow.value = AuthState.HasResult(
            AuthenticationResult.Success(token = token, isRegistration = false)
        )
    }

    override suspend fun clearPendingRegistration() {
        pendingToken = null
        pendingInitialData = null
        if (!isSignedIn()) {
            _authenticationStateFlow.value = AuthState.NoIdToken
        }
    }

    override suspend fun clearAuth() {
        selfUserId = null
        pendingToken = null
        pendingInitialData = null
        tokenStorage.clearTokens()
        _authenticationStateFlow.value = AuthState.NoIdToken
    }
}
