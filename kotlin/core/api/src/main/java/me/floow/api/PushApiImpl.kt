package me.floow.api

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
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.PushApi
import me.floow.domain.api.models.PushAckRequest
import me.floow.domain.api.models.PushAckResponse
import me.floow.domain.utils.Logger

@Serializable
private data class PushAckRequestBody(
	@SerialName("notification_id") val notificationId: String,
	@SerialName("conversation_id") val conversationId: String,
	@SerialName("message_id") val messageId: String,
	@SerialName("received_at_ms") val receivedAtMs: Long,
	@SerialName("device_id") val deviceId: String? = null
)

class PushApiImpl(
	private val config: ApiConfig,
	private val httpClientProvider: HttpClientProvider,
	private val logger: Logger
) : PushApi {
	private val baseUrl = buildPushApiBaseUrl(config.apiUrl)

	override suspend fun ackPush(request: PushAckRequest): PushAckResponse {
		return safeApiCall(errorResponse = PushAckResponse.Error) {
			val httpClient = httpClientProvider.getClientWithoutHttpCache()
			val response = httpClient.post("$baseUrl/push/ack") {
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						PushAckRequestBody(
							notificationId = request.notificationId,
							conversationId = request.conversationId.toString(),
							messageId = request.messageId.toString(),
							receivedAtMs = request.receivedAtMs,
							deviceId = request.deviceId
						)
					)
				)
			}
			logger.logKtorRequest("PushApiImpl.ackPush", response.call.request)
			if (!response.status.isSuccess()) {
				val bodyText = response.bodyAsText()
				logger.logFailureResponse("PushApiImpl.ackPush", response.status, bodyText)
				return@safeApiCall PushAckResponse.Error
			}
			PushAckResponse.Success
		}
	}
}

private fun buildPushApiBaseUrl(apiUrl: String): String {
	val normalized = apiUrl.trimEnd('/')
	return when {
		normalized.endsWith("/api/v1") -> normalized.removeSuffix("/api/v1") + "/api/v2"
		normalized.endsWith("/api") -> "$normalized/v2"
		normalized.endsWith("/v1") -> normalized.removeSuffix("/v1") + "/v2"
		else -> "$normalized/api/v2"
	}
}
