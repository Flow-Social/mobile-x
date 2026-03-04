package me.floow.api

import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.PushTokensApi
import me.floow.domain.api.models.RegisterPushTokenData
import me.floow.domain.api.models.RegisterPushTokenResponse
import me.floow.domain.api.models.RevokePushTokenResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class RegisterPushTokenRequest(
    val token: String,
    val platform: String,
    @SerialName("app_version") val appVersion: String,
    val locale: String? = null,
    val timezone: String? = null
)

@Serializable
private data class RevokePushTokenRequest(
    val token: String
)

@Serializable
private data class ApiPushResponse(
    val ok: Boolean = false
)

class PushTokensApiImpl(
    private val config: ApiConfig,
    private val logger: Logger,
    private val authenticationManager: AuthenticationManager,
    httpClientProvider: HttpClientProvider
) : PushTokensApi {
    private val httpClient = httpClientProvider.getClientWithoutHttpCache()

    override suspend fun registerToken(data: RegisterPushTokenData): RegisterPushTokenResponse {
        return safeApiCall(errorResponse = RegisterPushTokenResponse.Error) {
            val authToken = authenticationManager.getAuthTokenOrNull()
                ?: return@safeApiCall RegisterPushTokenResponse.Error

            val requestBody = RegisterPushTokenRequest(
                token = data.token,
                platform = data.platform,
                appVersion = data.appVersion,
                locale = data.locale,
                timezone = data.timezone
            )

            val response = httpClient.post("${config.apiUrl}/devices/tokens") {
                addAuthTokenHeader(authToken)
                contentType(ContentType.Application.Json)
                setBody(JsonSerializer.encodeToString(requestBody))
            }

            logger.logKtorRequest("PushTokensApiImpl registerToken", response.call.request)
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.logFailureResponse("PushTokensApiImpl registerToken", response.status, bodyText)
                return@safeApiCall RegisterPushTokenResponse.Error
            }

            val parsed = runCatching { JsonSerializer.decodeFromString<ApiPushResponse>(bodyText) }.getOrNull()
                ?: return@safeApiCall RegisterPushTokenResponse.Error

            if (!parsed.ok) return@safeApiCall RegisterPushTokenResponse.Error
            RegisterPushTokenResponse.Success
        }
    }

    override suspend fun revokeToken(token: String): RevokePushTokenResponse {
        return safeApiCall(errorResponse = RevokePushTokenResponse.Error) {
            val authToken = authenticationManager.getAuthTokenOrNull()
                ?: return@safeApiCall RevokePushTokenResponse.Error

            val response = httpClient.delete("${config.apiUrl}/devices/tokens") {
                addAuthTokenHeader(authToken)
                contentType(ContentType.Application.Json)
                setBody(JsonSerializer.encodeToString(RevokePushTokenRequest(token = token)))
            }

            logger.logKtorRequest("PushTokensApiImpl revokeToken", response.call.request)
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.logFailureResponse("PushTokensApiImpl revokeToken", response.status, bodyText)
                return@safeApiCall RevokePushTokenResponse.Error
            }

            val parsed = runCatching { JsonSerializer.decodeFromString<ApiPushResponse>(bodyText) }.getOrNull()
                ?: return@safeApiCall RevokePushTokenResponse.Error

            if (!parsed.ok) return@safeApiCall RevokePushTokenResponse.Error
            RevokePushTokenResponse.Success
        }
    }
}
