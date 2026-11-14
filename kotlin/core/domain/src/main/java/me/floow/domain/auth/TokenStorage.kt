package me.floow.domain.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String? = null,
)

sealed interface AuthTokenState {
    data object Loading : AuthTokenState
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
