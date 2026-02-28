package me.floow.api

import io.ktor.client.request.*
import io.ktor.client.request.patch
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.network.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.ProfileApi
import me.floow.domain.api.models.*
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class UserProfileResponse(
	val id: String,
	val email: String = "",
	val username: String,
	val name: String,
	val avatar: String = "",
	val background: String = "",
	@SerialName("background_updated_at")
	val backgroundUpdatedAt: Long? = null,
	val bio: String = "",
	@SerialName("total_likes_received")
	val totalLikesReceived: Int = 0,
	@SerialName("google_id")
	val googleId: String = ""
)

@Serializable
private data class EditProfileRequest(
	val name: String,
	val username: String,
	val bio: String,
	val avatar: String = ""
)

@Serializable
private data class UpdateMediaRequest(
	val kind: String,
	@SerialName("media_url")
	val mediaUrl: String
)

class ProfileApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : ProfileApi {
	private val httpClient = httpClientProvider.getClient()

	override suspend fun getSelf(): GetSelfResponse {
		return safeApiCall(errorResponse = GetSelfResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetSelfResponse.Error

			val response = httpClient.get("${config.apiUrl}/profile") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ProfileApiImpl getSelf", response.call.request)

			val bodyText = response.bodyAsText()

			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ProfileApiImpl getSelf", response.status, bodyText)

				return@safeApiCall GetSelfResponse.Error
			}

			logger.d("ProfileApiImpl getSelf", "Body text: $bodyText")

			val parsed = runCatching {
				JsonSerializer.decodeFromString<UserProfileResponse>(bodyText)
			}.getOrNull() ?: return@safeApiCall GetSelfResponse.Error

			return@safeApiCall GetSelfResponse.Success(
				name = parsed.name,
				username = parsed.username,
				avatarUrl = parsed.avatar.ifBlank { null },
				backgroundUrl = parsed.background.ifBlank { null },
				backgroundUpdatedAt = parsed.backgroundUpdatedAt,
				biography = parsed.bio,
				totalLikesReceived = parsed.totalLikesReceived,
			)
		}
	}

	override suspend fun getUserProfile(userId: String): GetSelfResponse {
		return safeApiCall(errorResponse = GetSelfResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetSelfResponse.Error

			val response = httpClient.get("${config.apiUrl}/users/$userId") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ProfileApiImpl getUserProfile", response.call.request)

			val bodyText = response.bodyAsText()

			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ProfileApiImpl getUserProfile", response.status, bodyText)

				return@safeApiCall GetSelfResponse.Error
			}

			logger.d("ProfileApiImpl getUserProfile", "Body text: $bodyText")

			val parsed = runCatching {
				JsonSerializer.decodeFromString<UserProfileResponse>(bodyText)
			}.getOrNull() ?: return@safeApiCall GetSelfResponse.Error

			return@safeApiCall GetSelfResponse.Success(
				name = parsed.name,
				username = parsed.username,
				avatarUrl = parsed.avatar.ifBlank { null },
				backgroundUrl = parsed.background.ifBlank { null },
				backgroundUpdatedAt = parsed.backgroundUpdatedAt,
				biography = parsed.bio,
				totalLikesReceived = parsed.totalLikesReceived,
			)
		}
	}

	override suspend fun edit(data: EditProfileData): EditProfileResponse {
		return safeApiCall(errorResponse = EditProfileResponse(status = EditProfileResponseStatus.ERROR)) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall EditProfileResponse(EditProfileResponseStatus.ERROR)

			val requestBody = EditProfileRequest(
				name = data.name.value,
				username = data.username.value,
				bio = data.description.value,
				avatar = "" // Пока не обновляем аватарку через этот метод
			)
			
			val response = httpClient.put("${config.apiUrl}/profile") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("ProfileApiImpl edit", response.call.request)

			val bodyText = response.bodyAsText()

			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ProfileApiImpl edit", response.status, bodyText)

				if (response.status == HttpStatusCode.Conflict) {
					return@safeApiCall EditProfileResponse(EditProfileResponseStatus.USERNAME_ALREADY_EXISTS)
				}

				return@safeApiCall EditProfileResponse(EditProfileResponseStatus.ERROR)
			}

			return@safeApiCall EditProfileResponse(EditProfileResponseStatus.SUCCESS)
		}
	}

	override suspend fun updateProfileMedia(kind: String, mediaUrl: String): Boolean {
		return safeApiCall(errorResponse = false) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall false

			val response = httpClient.patch("${config.apiUrl}/profile/media") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						UpdateMediaRequest(
							kind = kind,
							mediaUrl = mediaUrl
						)
					)
				)
			}

			logger.logKtorRequest("ProfileApiImpl updateProfileMedia", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ProfileApiImpl updateProfileMedia", response.status, bodyText)
				return@safeApiCall false
			}

			return@safeApiCall true
		}
	}

	override suspend fun updateAvatarUrl(avatarUrl: String): Boolean {
		return updateProfileMedia(kind = "avatar", mediaUrl = avatarUrl)
	}

	override suspend fun updateBackgroundUrl(backgroundUrl: String): Boolean {
		return updateProfileMedia(kind = "background", mediaUrl = backgroundUrl)
	}

	@Serializable
	private data class CheckUsernameResponse(
		val available: Boolean
	)

	override suspend fun checkUsername(username: String): Boolean {
		return safeApiCall(errorResponse = false) {
			val response = httpClient.get("${config.apiUrl}/auth/check-username") {
				parameter("username", username)
			}

			if (!response.status.isSuccess()) return@safeApiCall false

			val bodyText = response.bodyAsText()
			val parsed = runCatching {
				JsonSerializer.decodeFromString<CheckUsernameResponse>(bodyText)
			}.getOrNull() ?: return@safeApiCall false

			return@safeApiCall parsed.available
		}
	}

	override suspend fun deleteProfile(): DeleteProfileResponse {
		return safeApiCall(errorResponse = DeleteProfileResponse(DeleteProfileResponseStatus.SUCCESS)) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall DeleteProfileResponse(DeleteProfileResponseStatus.ERROR)

			val response = httpClient.post("${config.apiUrl}/user/get/personal/delete") {
				addAuthTokenHeader(authToken)
			}

			if (!response.status.isSuccess()) return@safeApiCall DeleteProfileResponse(
				DeleteProfileResponseStatus.ERROR
			)

			return@safeApiCall DeleteProfileResponse(DeleteProfileResponseStatus.SUCCESS)
		}
	}
}
