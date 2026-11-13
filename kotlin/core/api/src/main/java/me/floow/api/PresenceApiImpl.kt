package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.PresenceApi
import me.floow.domain.api.models.PresenceGetResponse
import me.floow.domain.api.models.PresenceItem
import me.floow.domain.api.models.PresenceUpdateResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class PresenceItemDto(
	@SerialName("user_id") val userId: Long,
	@SerialName("is_online") val isOnline: Boolean,
	@SerialName("last_seen_at") val lastSeenAt: Long? = null,
	@SerialName("server_timestamp") val serverTimestamp: Long = 0L,
	@SerialName("expires_at") val expiresAt: Long? = null
)

@Serializable
private data class PresenceListResponse(
	val items: List<PresenceItemDto> = emptyList()
)

@Serializable
private data class PresenceSessionRequest(
	@SerialName("session_id") val sessionId: String? = null
)

class PresenceApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : PresenceApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val baseUrl = config.apiUrl.trimEnd('/')

	override suspend fun getPresence(userIds: List<String>): PresenceGetResponse {
		return safeApiCall(errorResponse = PresenceGetResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall PresenceGetResponse.Error
			if (userIds.isEmpty()) return@safeApiCall PresenceGetResponse.Success(emptyList())
			val response = httpClient.get("$baseUrl/presence") {
				addAuthTokenHeader(authToken)
				url {
					parameters.append("user_ids", userIds.joinToString(","))
				}
			}
			logger.logKtorRequest("PresenceApiImpl.getPresence", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("PresenceApiImpl.getPresence", response.status, bodyText)
				return@safeApiCall PresenceGetResponse.Error
			}
			val parsed = runCatching { JsonSerializer.decodeFromString<PresenceListResponse>(bodyText) }
				.getOrNull()
				?: return@safeApiCall PresenceGetResponse.Error
			val items = parsed.items.map { dto ->
				PresenceItem(
					userId = dto.userId.toString(),
					isOnline = dto.isOnline,
					lastSeenAtMillis = dto.lastSeenAt,
					serverTimestampMillis = dto.serverTimestamp,
					expiresAtMillis = dto.expiresAt
				)
			}
			PresenceGetResponse.Success(items)
		}
	}

	override suspend fun heartbeat(sessionId: String?): PresenceUpdateResponse {
		return updatePresence("PresenceApiImpl.heartbeat", "$baseUrl/presence/heartbeat", sessionId)
	}

	override suspend fun offline(sessionId: String?): PresenceUpdateResponse {
		return updatePresence("PresenceApiImpl.offline", "$baseUrl/presence/offline", sessionId)
	}

	private suspend fun updatePresence(tag: String, url: String, sessionId: String?): PresenceUpdateResponse {
		return safeApiCall(errorResponse = PresenceUpdateResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall PresenceUpdateResponse.Error
			val response = httpClient.post(url) {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(PresenceSessionRequest(sessionId)))
			}
			logger.logKtorRequest(tag, response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse(tag, response.status, bodyText)
				return@safeApiCall PresenceUpdateResponse.Error
			}
			val parsed = runCatching { JsonSerializer.decodeFromString<PresenceItemDto>(bodyText) }
				.getOrNull()
				?: return@safeApiCall PresenceUpdateResponse.Error
			PresenceUpdateResponse.Success(
				PresenceItem(
					userId = parsed.userId.toString(),
					isOnline = parsed.isOnline,
					lastSeenAtMillis = parsed.lastSeenAt,
					serverTimestampMillis = parsed.serverTimestamp,
					expiresAtMillis = parsed.expiresAt
				)
			)
		}
	}
}
