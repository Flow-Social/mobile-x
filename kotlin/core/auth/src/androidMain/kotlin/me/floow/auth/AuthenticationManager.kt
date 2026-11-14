package me.floow.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.AuthApiResult
import me.floow.domain.auth.AuthTokenState
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.GoogleOAuth
import me.floow.domain.auth.TokenStorage
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.AuthenticationResult
import me.floow.domain.auth.models.GoogleOAuthResult
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.utils.Logger

class AuthenticationManagerImpl(
	private val context: Context,
	private val logger: Logger,
	private val googleOAuth: GoogleOAuth,
	private val authApi: AuthApi,
	private val tokenStorage: TokenStorage,
) : AuthenticationManager {
	companion object {
		private const val AUTH_SHARED_PREF = "flowme.auth"
		private const val PENDING_REG_TOKEN_PREF_KEY = "pendingRegistrationToken"
		private const val PENDING_REG_NAME_PREF_KEY = "pendingRegistrationName"
		private const val PENDING_REG_USERNAME_PREF_KEY = "pendingRegistrationUsername"
		private const val PENDING_REG_DESCRIPTION_PREF_KEY = "pendingRegistrationDescription"
		private const val RESCUE_PREFS = "flowme.rescue_sync"
		private const val KEY_MIGRATION_COMPLETED = "is_migration_completed_v2"
	}

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private val _authenticationStateFlow: MutableStateFlow<AuthState> =
		MutableStateFlow(AuthState.NoIdToken)
	override val authenticationStateFlow: StateFlow<AuthState> = _authenticationStateFlow

	private val sharedPreferences: SharedPreferences = context
		.getSharedPreferences(
			AUTH_SHARED_PREF,
			Context.MODE_PRIVATE
		)

	init {
		val pendingToken = sharedPreferences.getString(PENDING_REG_TOKEN_PREF_KEY, null)
		if (pendingToken != null) {
			_authenticationStateFlow.update {
				AuthState.HasResult(
					AuthenticationResult.Success(
						token = pendingToken,
						isRegistration = true
					)
				)
			}
		}

		scope.launch {
			tokenStorage.authState.collect { tokenState ->
				when (tokenState) {
					is AuthTokenState.Authenticated -> {
						_authenticationStateFlow.update {
							AuthState.HasResult(
								AuthenticationResult.Success(
									token = tokenState.tokens.accessToken,
									isRegistration = false
								)
							)
						}
					}
					is AuthTokenState.Unauthenticated -> {
						if (!hasPendingRegistration()) {
							_authenticationStateFlow.update { AuthState.NoIdToken }
						}
					}
					AuthTokenState.Loading -> {
						// Ожидаем загрузки токена
					}
				}
			}
		}
	}

	override suspend fun handleGoogleOAuthCode(code: String) {
		logger.d(
			"AuthenticationManagerImpl.handleGoogleOAuthCode",
			"Start handling Google OAuth code"
		)

		_authenticationStateFlow.update {
			AuthState.HandlingOAuth
		}

		when (val googleOAuthResult = googleOAuth.handleGoogleOAuthCode(code)) {
			is GoogleOAuthResult.Success -> {
				val authApiResult = authApi.getAuthTokenByGoogleIdToken(googleOAuthResult.idToken)

				if (authApiResult is AuthApiResult.Success) {
					logger.d(
						"AuthenticationManagerImpl.handleGoogleOAuthCode",
						"Success auth api result. isRegistration: ${authApiResult.isRegistration}"
					)

					if (authApiResult.isRegistration) {
						_authenticationStateFlow.update {
							AuthState.HasResult(
								AuthenticationResult.Success(
									token = authApiResult.token,
									isRegistration = true
								)
							)
						}
						writePendingRegistration(
							token = authApiResult.token,
							initialData = authApiResult.pendingInitialData
						)
					} else {
						writeAuthToken(authApiResult.token, authApiResult.userId)
						_authenticationStateFlow.update {
							AuthState.HasResult(
								AuthenticationResult.Success(
									token = authApiResult.token,
									isRegistration = false
								)
							)
						}
					}
				} else {
					logger.d(
						"AuthenticationManagerImpl.handleGoogleOAuthCode",
						"Failure auth api result"
					)

					_authenticationStateFlow.update {
						AuthState.HasResult(AuthenticationResult.Failure)
					}
				}
			}

			is GoogleOAuthResult.Failure -> {
				logger.d(
					"AuthenticationManagerImpl.handleGoogleOAuthCode",
					"Failed to get Google OAuth ID token"
				)

				_authenticationStateFlow.update {
					AuthState.HasResult(AuthenticationResult.Failure)
				}
			}
		}
	}

	private fun writePendingRegistration(token: String, initialData: PendingRegistrationInitialData?) {
		sharedPreferences.edit().apply {
			putString(PENDING_REG_TOKEN_PREF_KEY, token)
			putString(PENDING_REG_NAME_PREF_KEY, initialData?.name)
			putString(PENDING_REG_USERNAME_PREF_KEY, initialData?.username)
			putString(PENDING_REG_DESCRIPTION_PREF_KEY, initialData?.description)
			apply()
		}

		logger.d("AuthenticationManagerImpl.writePendingRegistration", "Wrote pending registration token")
	}

	override suspend fun writeAuthToken(token: String) {
		writeAuthToken(token, getSelfUserIdOrNull())
	}

	private suspend fun writeAuthToken(token: String, userId: String?) {
		tokenStorage.saveTokens(token)
		if (!userId.isNullOrBlank()) {
			tokenStorage.saveUserId(userId)
		}

		clearPendingRegistration()
		context.getSharedPreferences(RESCUE_PREFS, Context.MODE_PRIVATE)
			.edit()
			.putBoolean(KEY_MIGRATION_COMPLETED, true)
			.apply()

		logger.d("AuthenticationManagerImpl.writeAuthToken", "Wrote authentication token and marked migration completed")
	}

	override suspend fun getAuthTokenOrNull(): String? {
		return tokenStorage.getAccessToken()
	}

	override fun getSelfUserIdOrNull(): String? {
		return tokenStorage.getSyncUserIdOrNull()
	}

	override fun saveSelfUserId(userId: String) {
		if (userId.isBlank()) return
		scope.launch {
			tokenStorage.saveUserId(userId)
		}
	}

	override fun isSignedIn(): Boolean {
		val state = tokenStorage.authState.value
		return state is AuthTokenState.Authenticated || !tokenStorage.getSyncAccessTokenOrNull().isNullOrBlank()
	}

	override fun hasPendingRegistration(): Boolean {
		return sharedPreferences.getString(PENDING_REG_TOKEN_PREF_KEY, null) != null
	}

	override fun getPendingRegistrationTokenOrNull(): String? {
		return sharedPreferences.getString(PENDING_REG_TOKEN_PREF_KEY, null)
	}

	override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? {
		if (!hasPendingRegistration()) return null

		val name = sharedPreferences.getString(PENDING_REG_NAME_PREF_KEY, null) ?: return null
		val username = sharedPreferences.getString(PENDING_REG_USERNAME_PREF_KEY, null) ?: return null
		val description = sharedPreferences.getString(PENDING_REG_DESCRIPTION_PREF_KEY, null) ?: ""

		return PendingRegistrationInitialData(
			name = name,
			username = username,
			description = description
		)
	}

	override suspend fun startGoogleAuthentication() {
		logger.d(
			"AuthenticationManagerImpl.startGoogleAuthentication",
			"Started google authentication"
		)

		googleOAuth.startSignIn()
	}

	override suspend fun clearPendingRegistration() {
		sharedPreferences.edit().apply {
			remove(PENDING_REG_TOKEN_PREF_KEY)
			remove(PENDING_REG_NAME_PREF_KEY)
			remove(PENDING_REG_USERNAME_PREF_KEY)
			remove(PENDING_REG_DESCRIPTION_PREF_KEY)
			apply()
		}
	}

	override suspend fun clearAuth() {
		tokenStorage.clearTokens()
		clearPendingRegistration()
		_authenticationStateFlow.update { AuthState.NoIdToken }
	}
}
