package me.floow.api

import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class CreateUploadPresignRequest(
	@SerialName("file_name")
	val fileName: String,
	@SerialName("content_type")
	val contentType: String,
	@SerialName("size_bytes")
	val sizeBytes: Long,
	val kind: String
)

@Serializable
private data class CreateUploadPresignApiResponse(
	@SerialName("upload_url")
	val uploadUrl: String,
	@SerialName("file_url")
	val fileUrl: String,
	@SerialName("object_key")
	val objectKey: String,
	@SerialName("expires_in_seconds")
	val expiresInSeconds: Long,
	@SerialName("max_size_bytes")
	val maxSizeBytes: Long
)

class UploadsApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : UploadsApi {
	private val httpClient = httpClientProvider.getClient()

	override suspend fun createPresign(data: CreateUploadPresignData): CreateUploadPresignResponse {
		return safeApiCall(errorResponse = CreateUploadPresignResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall CreateUploadPresignResponse.Error

			val requestBody = CreateUploadPresignRequest(
				fileName = data.fileName,
				contentType = data.contentType,
				sizeBytes = data.sizeBytes,
				kind = data.kind,
			)

			val response = httpClient.post("${config.apiUrl}/uploads/presign") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestBody))
			}

			logger.logKtorRequest("UploadsApiImpl createPresign", response.call.request)

			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("UploadsApiImpl createPresign", response.status, bodyText)
				return@safeApiCall CreateUploadPresignResponse.Error
			}

			val parsed = runCatching { JsonSerializer.decodeFromString<CreateUploadPresignApiResponse>(bodyText) }
				.getOrNull()
				?: return@safeApiCall CreateUploadPresignResponse.Error

			return@safeApiCall CreateUploadPresignResponse.Success(
				uploadUrl = parsed.uploadUrl,
				fileUrl = parsed.fileUrl,
				objectKey = parsed.objectKey,
				expiresInSeconds = parsed.expiresInSeconds,
				maxSizeBytes = parsed.maxSizeBytes,
			)
		}
	}

	override suspend fun uploadFile(uploadUrl: String, contentType: String, bytes: ByteArray): Boolean {
		return safeApiCall(errorResponse = false) {
			val parsedContentType = runCatching { ContentType.parse(contentType) }
				.getOrDefault(ContentType.Application.OctetStream)

			val response = httpClient.put(uploadUrl) {
				contentType(parsedContentType)
				headers {
					set(HttpHeaders.ContentType, parsedContentType.toString())
					set(HttpHeaders.ContentLength, bytes.size.toString())
				}
				setBody(bytes)
			}

			logger.logKtorRequest("UploadsApiImpl uploadFile", response.call.request)

			if (!response.status.isSuccess()) {
				val bodyText = response.bodyAsText()
				logger.logFailureResponse("UploadsApiImpl uploadFile", response.status, bodyText)
				return@safeApiCall false
			}

			return@safeApiCall true
		}
	}
}
