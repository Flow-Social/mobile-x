package me.floow.mock.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.AuthenticationResult
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.mock.interfaces.MockStorage

class MockAuthenticationManager(
	private val mockStorage: MockStorage
) : AuthenticationManager {
	
	companion object {
		private const val AUTH_TOKEN_KEY = "mock_auth_token"
		private const val AUTH_USER_ID_KEY = "mock_auth_user_id"
		private const val PENDING_REG_TOKEN_KEY = "mock_pending_registration_token"
		private const val PENDING_REG_NAME_KEY = "mock_pending_registration_name"
		private const val PENDING_REG_USERNAME_KEY = "mock_pending_registration_username"
		private const val PENDING_REG_DESCRIPTION_KEY = "mock_pending_registration_description"

		private const val MOCK_TOKEN = "mock_auth_token_12345"
		private const val MOCK_PENDING_TOKEN = "mock_pending_registration_token_12345"
		private const val MOCK_USER_ID = "me"
	}
	
	private val _authenticationStateFlow = MutableStateFlow<AuthState>(
		when {
			isSignedIn() -> AuthState.HasResult(AuthenticationResult.Success(MOCK_TOKEN, false))
			hasPendingRegistration() -> AuthState.HasResult(AuthenticationResult.Success(MOCK_PENDING_TOKEN, true))
			else -> AuthState.NoIdToken
		}
	)
	override val authenticationStateFlow: StateFlow<AuthState> = _authenticationStateFlow
	
	override suspend fun handleGoogleOAuthCode(code: String) {
		delay(500) // имитация обработки
		
		_authenticationStateFlow.value = AuthState.HandlingOAuth
		
		delay(1000) // имитация запроса к серверу
		
		// Если профиля нет, предлагаем регистрацию (создание профиля)
		val hasProfile = mockStorage.getString("profile_name") != null
		
		if (hasProfile) {
			writeAuthToken(MOCK_TOKEN)
			saveSelfUserId(MOCK_USER_ID)
			_authenticationStateFlow.value =
				AuthState.HasResult(AuthenticationResult.Success(MOCK_TOKEN, false))
		} else {
			mockStorage.saveString(PENDING_REG_TOKEN_KEY, MOCK_PENDING_TOKEN)
			mockStorage.saveString(PENDING_REG_NAME_KEY, "Mock User")
			mockStorage.saveString(PENDING_REG_USERNAME_KEY, "mock_user")
			mockStorage.saveString(PENDING_REG_DESCRIPTION_KEY, "")

			_authenticationStateFlow.value =
				AuthState.HasResult(AuthenticationResult.Success(MOCK_PENDING_TOKEN, true))
		}
	}
	
	override suspend fun getAuthTokenOrNull(): String? {
		return mockStorage.getString(AUTH_TOKEN_KEY)
	}

	override fun getSelfUserIdOrNull(): String? {
		return mockStorage.getString(AUTH_USER_ID_KEY)
	}

	override fun saveSelfUserId(userId: String) {
		if (userId.isBlank()) return
		mockStorage.saveString(AUTH_USER_ID_KEY, userId)
	}
	
	override fun isSignedIn(): Boolean {
		return mockStorage.getString(AUTH_TOKEN_KEY) != null
	}

	override fun hasPendingRegistration(): Boolean {
		return mockStorage.getString(PENDING_REG_TOKEN_KEY) != null
	}

	override fun getPendingRegistrationTokenOrNull(): String? {
		return mockStorage.getString(PENDING_REG_TOKEN_KEY)
	}

	override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? {
		if (!hasPendingRegistration()) return null

		val name = mockStorage.getString(PENDING_REG_NAME_KEY) ?: return null
		val username = mockStorage.getString(PENDING_REG_USERNAME_KEY) ?: return null
		val description = mockStorage.getString(PENDING_REG_DESCRIPTION_KEY) ?: ""

		return PendingRegistrationInitialData(
			name = name,
			username = username,
			description = description
		)
	}
	
	override suspend fun startGoogleAuthentication() {
		delay(100) // имитация запуска
		// В моке сразу симулируем успешную авторизацию
		handleGoogleOAuthCode("mock_code")
	}

	override suspend fun writeAuthToken(token: String) {
		mockStorage.saveString(AUTH_TOKEN_KEY, token)
		clearPendingRegistration()
	}

	override suspend fun clearPendingRegistration() {
		mockStorage.removeString(PENDING_REG_TOKEN_KEY)
		mockStorage.removeString(PENDING_REG_NAME_KEY)
		mockStorage.removeString(PENDING_REG_USERNAME_KEY)
		mockStorage.removeString(PENDING_REG_DESCRIPTION_KEY)
	}
	
	// Метод для тестирования
	override suspend fun clearAuth() {
		mockStorage.removeString(AUTH_TOKEN_KEY)
		mockStorage.removeString(AUTH_USER_ID_KEY)
		clearPendingRegistration()
		_authenticationStateFlow.value = AuthState.NoIdToken
	}
}
