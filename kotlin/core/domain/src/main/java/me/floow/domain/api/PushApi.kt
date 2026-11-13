package me.floow.domain.api

import me.floow.domain.api.models.PushAckRequest
import me.floow.domain.api.models.PushAckResponse

interface PushApi {
	suspend fun ackPush(request: PushAckRequest): PushAckResponse
}
