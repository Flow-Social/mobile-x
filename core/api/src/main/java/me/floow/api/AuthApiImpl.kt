package me.floow.api

import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.AuthApiResult
import me.floow.domain.api.models.CompleteGoogleRegistrationResult
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.utils.Logger

@Serializable
internal data class GoogleAuthorizeResponse(
	val token: String,
	val user: User? = null,
	@SerialName("is_new_user")
	val isNewUser: Boolean = false,
	@SerialName("pending_profile")
	val pendingProfile: PendingProfile? = null,
)

@Serializable
internal data class User(
	val id: String,
	val email: String,
	val username: String,
	val name: String,
	val avatar: String = "",
	val bio: String = "",
	val google_id: String = ""
)

@Serializable
internal data class PendingProfile(
	val name: String,
	val username: String,
	val description: String,
)

@Serializable
internal data class AuthTokenResponse(
	val token: String,
	val user: User? = null,
)

class AuthApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	httpClientProvider: HttpClientProvider
) : AuthApi {
	private val httpClient = httpClientProvider.getClient()

	override suspend fun getAuthTokenByGoogleIdToken(idToken: String): AuthApiResult =
		withContext(Dispatchers.Default) {
			safeApiCall(AuthApiResult.Failure) {
				val response = httpClient.post("${config.apiUrl}/auth/google") {
					contentType(ContentType.Application.FormUrlEncoded)

					setBody(
						FormDataContent(
							Parameters.build {
								append("type", "mobile")
								append("id_token", idToken)
							}
						)
					)
				}

				logger.logKtorRequest("getAuthTokenByGoogleIdToken", response.call.request)

				val bodyText = response.bodyAsText()

				logger.d("getAuthTokenByGoogleIdToken", "Body text: $bodyText")

				if (response.status.isSuccess()) {
					val json = JsonSerializer.parseToJsonElement(bodyText).jsonObject
					val token = json["token"]?.jsonPrimitive?.contentOrNull
						?: return@safeApiCall AuthApiResult.Failure
					val isNewUser = json["is_new_user"]?.jsonPrimitive?.booleanOrNull ?: false
					val userId = json["user"]?.jsonObject
						?.get("id")
						?.jsonPrimitive
						?.contentOrNull
					val pendingProfileJson = json["pending_profile"]?.jsonObject
					val pendingInitialData = pendingProfileJson?.let {
						PendingRegistrationInitialData(
							name = it["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
							username = it["username"]?.jsonPrimitive?.contentOrNull.orEmpty(),
							description = it["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
						)
					}

					return@safeApiCall AuthApiResult.Success(
						token = token,
						isRegistration = isNewUser,
						userId = userId,
						pendingInitialData = pendingInitialData
					)
				} else {
					logger.d("getAuthTokenByGoogleIdToken", "Failure: ${response.status}")
				}

				return@safeApiCall AuthApiResult.Failure
			}
		}

	override suspend fun completeGoogleRegistration(
		pendingToken: String,
		data: EditProfileData,
		avatarUrl: String?
	): CompleteGoogleRegistrationResult = withContext(Dispatchers.Default) {
		safeApiCall(CompleteGoogleRegistrationResult.Failure) {
			val response = httpClient.post("${config.apiUrl}/auth/google/complete") {
				contentType(ContentType.Application.FormUrlEncoded)

				setBody(
					FormDataContent(
						Parameters.build {
							append("pending_token", pendingToken)
							append("name", data.name.value)
							append("username", data.username.value)
							append("bio", data.description.value)
							append("avatar", avatarUrl.orEmpty())
						}
					)
				)
			}

			logger.logKtorRequest("completeGoogleRegistration", response.call.request)

			val bodyText = response.bodyAsText()
			logger.d("completeGoogleRegistration", "Body text: $bodyText")

			if (response.status.isSuccess()) {
				val parsed = JsonSerializer.decodeFromString<AuthTokenResponse>(bodyText)
				return@safeApiCall CompleteGoogleRegistrationResult.Success(
					token = parsed.token,
					userId = parsed.user?.id
				)
			}

			if (response.status.value == 409 && bodyText.contains("USERNAME_ALREADY_EXISTS")) {
				return@safeApiCall CompleteGoogleRegistrationResult.UsernameAlreadyExists
			}

			return@safeApiCall CompleteGoogleRegistrationResult.Failure
		}
	}
}
