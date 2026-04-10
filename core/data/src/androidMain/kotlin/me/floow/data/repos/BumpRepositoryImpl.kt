package me.floow.data.repos

import me.floow.domain.api.BumpApi
import me.floow.domain.api.models.BumpCancelSessionRequest
import me.floow.domain.api.models.BumpEventRequest
import me.floow.domain.api.models.BumpStartSessionRequest
import me.floow.domain.api.models.CancelBumpSessionResponse
import me.floow.domain.api.models.GetBumpSessionResultResponse
import me.floow.domain.api.models.StartBumpSessionResponse
import me.floow.domain.api.models.SubmitBumpEventResponse
import me.floow.domain.data.repos.BumpRepository

class BumpRepositoryImpl(
    private val bumpApi: BumpApi
) : BumpRepository {
    override suspend fun startSession(request: BumpStartSessionRequest): StartBumpSessionResponse {
        return bumpApi.startSession(request)
    }

    override suspend fun submitEvent(request: BumpEventRequest): SubmitBumpEventResponse {
        return bumpApi.submitEvent(request)
    }

    override suspend fun getSessionResult(sessionId: String): GetBumpSessionResultResponse {
        return bumpApi.getSessionResult(sessionId)
    }

    override suspend fun cancelSession(request: BumpCancelSessionRequest): CancelBumpSessionResponse {
        return bumpApi.cancelSession(request)
    }
}
