package me.floow.shared.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
)

sealed interface AuthTokenState {
    data object Unauthenticated : AuthTokenState
    data class Authenticated(val tokens: AuthTokens) : AuthTokenState
}

interface TokenStorage {
    val authState: StateFlow<AuthTokenState>

    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun saveTokens(accessToken: String, refreshToken: String? = null)
    suspend fun clearTokens()

    suspend fun getUserId(): String? = null
    suspend fun saveUserId(userId: String) {}

    fun getSyncUserIdOrNull(): String? = null
    fun getSyncAccessTokenOrNull(): String? = null
}

class InMemoryTokenStorage(
    initialTokens: AuthTokens? = null,
) : TokenStorage {
    private val _authState = MutableStateFlow<AuthTokenState>(
        if (initialTokens != null) AuthTokenState.Authenticated(initialTokens) else AuthTokenState.Unauthenticated
    )
    override val authState: StateFlow<AuthTokenState> = _authState.asStateFlow()

    private var currentTokens: AuthTokens? = initialTokens
    private var currentUserId: String? = initialTokens?.accessToken?.let { JwtPayloadParser.extractUserId(it) }

    override suspend fun getAccessToken(): String? = currentTokens?.accessToken
    override fun getSyncAccessTokenOrNull(): String? = currentTokens?.accessToken

    override suspend fun getRefreshToken(): String? = currentTokens?.refreshToken

    override suspend fun saveTokens(accessToken: String, refreshToken: String?) {
        val tokens = AuthTokens(accessToken = accessToken, refreshToken = refreshToken)
        currentTokens = tokens
        currentUserId = JwtPayloadParser.extractUserId(accessToken) ?: currentUserId
        _authState.value = AuthTokenState.Authenticated(tokens)
    }

    override suspend fun clearTokens() {
        currentTokens = null
        currentUserId = null
        _authState.value = AuthTokenState.Unauthenticated
    }

    override suspend fun getUserId(): String? = currentUserId ?: currentTokens?.accessToken?.let { JwtPayloadParser.extractUserId(it) }
    override fun getSyncUserIdOrNull(): String? = currentUserId ?: currentTokens?.accessToken?.let { JwtPayloadParser.extractUserId(it) }

    override suspend fun saveUserId(userId: String) {
        currentUserId = userId.takeIf { it.isNotBlank() }
    }
}

expect fun createTokenStorage(): TokenStorage
