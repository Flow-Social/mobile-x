package me.floow.domain.data.repos

import me.floow.domain.api.models.BumpCancelSessionRequest
import me.floow.domain.api.models.BumpEventRequest
import me.floow.domain.api.models.BumpStartSessionRequest
import me.floow.domain.api.models.CancelBumpSessionResponse
import me.floow.domain.api.models.GetBumpSessionResultResponse
import me.floow.domain.api.models.StartBumpSessionResponse
import me.floow.domain.api.models.SubmitBumpEventResponse

interface BumpRepository {
    suspend fun startSession(request: BumpStartSessionRequest): StartBumpSessionResponse

    suspend fun submitEvent(request: BumpEventRequest): SubmitBumpEventResponse

    suspend fun getSessionResult(sessionId: String): GetBumpSessionResultResponse

    suspend fun cancelSession(request: BumpCancelSessionRequest): CancelBumpSessionResponse
}
