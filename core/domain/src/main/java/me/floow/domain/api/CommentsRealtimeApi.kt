package me.floow.domain.api

import kotlinx.coroutines.flow.Flow
import me.floow.domain.api.models.CommentsRealtimeEvent

interface CommentsRealtimeApi {
	fun subscribePostComments(
		postId: Long,
		afterSeq: Long,
		replayLimit: Int = 200
	): Flow<CommentsRealtimeEvent>
}
