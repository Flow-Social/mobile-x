package me.floow.domain.auth

import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.PendingRegistrationInitialData

interface AuthenticationManager {
	val authenticationStateFlow: StateFlow<AuthState>
	
	suspend fun handleGoogleOAuthCode(code: String)
	suspend fun getAuthTokenOrNull(): String?
	fun getSelfUserIdOrNull(): String?
	fun saveSelfUserId(userId: String)
	fun isSignedIn(): Boolean
	fun hasPendingRegistration(): Boolean
	fun getPendingRegistrationTokenOrNull(): String?
	fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData?
	suspend fun startGoogleAuthentication()
	suspend fun writeAuthToken(token: String)
	suspend fun clearPendingRegistration()
    
    // Метод для тестирования (очистка данных сессии)
    suspend fun clearAuth()
}
