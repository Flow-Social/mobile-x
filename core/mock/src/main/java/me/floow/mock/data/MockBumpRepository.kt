package me.floow.mock.data

import me.floow.domain.api.models.BumpCancelSessionRequest
import me.floow.domain.api.models.BumpCancelSessionResult
import me.floow.domain.api.models.BumpEventRequest
import me.floow.domain.api.models.BumpEventResult
import me.floow.domain.api.models.BumpSessionResult
import me.floow.domain.api.models.BumpSessionStatus
import me.floow.domain.api.models.BumpStartSessionRequest
import me.floow.domain.api.models.BumpStartSessionResponse
import me.floow.domain.api.models.CancelBumpSessionResponse
import me.floow.domain.api.models.GetBumpSessionResultResponse
import me.floow.domain.api.models.StartBumpSessionResponse
import me.floow.domain.api.models.SubmitBumpEventResponse
import me.floow.domain.data.repos.BumpRepository
import java.util.UUID

class MockBumpRepository : BumpRepository {
    override suspend fun startSession(request: BumpStartSessionRequest): StartBumpSessionResponse {
        val now = System.currentTimeMillis()
        return StartBumpSessionResponse.Success(
            BumpStartSessionResponse(
                sessionId = UUID.randomUUID().toString(),
                expiresAtMs = now + request.ttlSeconds * 1000L
            )
        )
    }

    override suspend fun submitEvent(request: BumpEventRequest): SubmitBumpEventResponse {
        return SubmitBumpEventResponse.Success(
            BumpEventResult(
                status = BumpSessionStatus.matching,
                sessionId = request.sessionId,
                matchedUserId = null
            )
        )
    }

    override suspend fun getSessionResult(sessionId: String): GetBumpSessionResultResponse {
        return GetBumpSessionResultResponse.Success(
            BumpSessionResult(
                status = BumpSessionStatus.waiting,
                sessionId = sessionId,
                matchedUserId = null
            )
        )
    }

    override suspend fun cancelSession(request: BumpCancelSessionRequest): CancelBumpSessionResponse {
        return CancelBumpSessionResponse.Success(
            BumpCancelSessionResult(
                ok = true,
                sessionId = request.sessionId
            )
        )
    }
}
