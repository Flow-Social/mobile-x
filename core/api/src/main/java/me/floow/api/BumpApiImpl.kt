package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.BumpApi
import me.floow.domain.api.models.BumpCancelSessionRequest
import me.floow.domain.api.models.BumpCancelSessionResult
import me.floow.domain.api.models.BumpEventRequest
import me.floow.domain.api.models.BumpEventResult
import me.floow.domain.api.models.BumpSessionResult
import me.floow.domain.api.models.BumpStartSessionRequest
import me.floow.domain.api.models.BumpStartSessionResponse
import me.floow.domain.api.models.CancelBumpSessionResponse
import me.floow.domain.api.models.GetBumpSessionResultResponse
import me.floow.domain.api.models.StartBumpSessionResponse
import me.floow.domain.api.models.SubmitBumpEventResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

class BumpApiImpl(
    private val config: ApiConfig,
    private val logger: Logger,
    private val authenticationManager: AuthenticationManager,
    httpClientProvider: HttpClientProvider
) : BumpApi {
    private val httpClient = httpClientProvider.getClientWithoutHttpCache()

    override suspend fun startSession(request: BumpStartSessionRequest): StartBumpSessionResponse {
        return safeApiCall(errorResponse = StartBumpSessionResponse.Error) {
            val authToken = authenticationManager.getAuthTokenOrNull()
                ?: return@safeApiCall StartBumpSessionResponse.Error

            val response = httpClient.post("${config.apiUrl}/bump/session/start") {
                addAuthTokenHeader(authToken)
                contentType(ContentType.Application.Json)
                setBody(JsonSerializer.encodeToString(request))
            }

            logger.logKtorRequest("BumpApiImpl startSession", response.call.request)
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.logFailureResponse("BumpApiImpl startSession", response.status, bodyText)
                return@safeApiCall StartBumpSessionResponse.Error
            }

            val parsed = runCatching {
                JsonSerializer.decodeFromString<BumpStartSessionResponse>(bodyText)
            }.getOrNull() ?: return@safeApiCall StartBumpSessionResponse.Error

            StartBumpSessionResponse.Success(parsed)
        }
    }

    override suspend fun submitEvent(request: BumpEventRequest): SubmitBumpEventResponse {
        return safeApiCall(errorResponse = SubmitBumpEventResponse.Error) {
            val authToken = authenticationManager.getAuthTokenOrNull()
                ?: return@safeApiCall SubmitBumpEventResponse.Error

            val response = httpClient.post("${config.apiUrl}/bump/session/event") {
                addAuthTokenHeader(authToken)
                contentType(ContentType.Application.Json)
                setBody(JsonSerializer.encodeToString(request))
            }

            logger.logKtorRequest("BumpApiImpl submitEvent", response.call.request)
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.logFailureResponse("BumpApiImpl submitEvent", response.status, bodyText)
                return@safeApiCall SubmitBumpEventResponse.Error
            }

            val parsed = runCatching {
                JsonSerializer.decodeFromString<BumpEventResult>(bodyText)
            }.getOrNull() ?: return@safeApiCall SubmitBumpEventResponse.Error

            SubmitBumpEventResponse.Success(parsed)
        }
    }

    override suspend fun getSessionResult(sessionId: String): GetBumpSessionResultResponse {
        return safeApiCall(errorResponse = GetBumpSessionResultResponse.Error) {
            val authToken = authenticationManager.getAuthTokenOrNull()
                ?: return@safeApiCall GetBumpSessionResultResponse.Error

            val response = httpClient.get("${config.apiUrl}/bump/session/result") {
                addAuthTokenHeader(authToken)
                url { parameters.append("sessionId", sessionId) }
            }

            logger.logKtorRequest("BumpApiImpl getSessionResult", response.call.request)
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.logFailureResponse("BumpApiImpl getSessionResult", response.status, bodyText)
                return@safeApiCall GetBumpSessionResultResponse.Error
            }

            val parsed = runCatching {
                JsonSerializer.decodeFromString<BumpSessionResult>(bodyText)
            }.getOrNull() ?: return@safeApiCall GetBumpSessionResultResponse.Error

            GetBumpSessionResultResponse.Success(parsed)
        }
    }

    override suspend fun cancelSession(request: BumpCancelSessionRequest): CancelBumpSessionResponse {
        return safeApiCall(errorResponse = CancelBumpSessionResponse.Error) {
            val authToken = authenticationManager.getAuthTokenOrNull()
                ?: return@safeApiCall CancelBumpSessionResponse.Error

            val response = httpClient.post("${config.apiUrl}/bump/session/cancel") {
                addAuthTokenHeader(authToken)
                contentType(ContentType.Application.Json)
                setBody(JsonSerializer.encodeToString(request))
            }

            logger.logKtorRequest("BumpApiImpl cancelSession", response.call.request)
            val bodyText = response.bodyAsText()
            if (!response.status.isSuccess()) {
                logger.logFailureResponse("BumpApiImpl cancelSession", response.status, bodyText)
                return@safeApiCall CancelBumpSessionResponse.Error
            }

            val parsed = runCatching {
                JsonSerializer.decodeFromString<BumpCancelSessionResult>(bodyText)
            }.getOrNull() ?: return@safeApiCall CancelBumpSessionResponse.Error

            CancelBumpSessionResponse.Success(parsed)
        }
    }
}
