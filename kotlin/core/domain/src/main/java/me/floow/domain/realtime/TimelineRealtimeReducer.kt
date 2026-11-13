package me.floow.domain.realtime

enum class TimelineRealtimeEventKind {
	HELLO,
	READ_CURSOR_UPDATED,
	ENTITY_UPSERTED,
	ENTITY_DELETED,
	TRANSIENT,
	RESYNC_REQUIRED
}

data class TimelineRealtimeEventInput(
	val kind: TimelineRealtimeEventKind,
	val incomingReadCursor: Long?,
	val eventSeq: Long,
	val eventMaxCursor: Long,
	val isLoading: Boolean
)

data class TimelineRealtimeEventDecision(
	val nextCursorState: RealtimeViewCursorState,
	val shouldApplyReadProjection: Boolean,
	val shouldHandleResync: Boolean
)

fun reduceTimelineRealtimeEvent(
	state: RealtimeViewCursorState,
	input: TimelineRealtimeEventInput
): TimelineRealtimeEventDecision {
	val baseDecision = reduceRealtimeViewEvent(
		state = state,
		input = RealtimeViewEventInput(
			incomingReadCursor = input.incomingReadCursor,
			eventSeq = input.eventSeq,
			eventMaxCursor = input.eventMaxCursor,
			isResyncEvent = input.kind == TimelineRealtimeEventKind.RESYNC_REQUIRED,
			isLoading = input.isLoading
		)
	)
	val shouldApplyReadProjection = when (input.kind) {
		TimelineRealtimeEventKind.HELLO,
		TimelineRealtimeEventKind.READ_CURSOR_UPDATED -> (input.incomingReadCursor ?: 0L) > 0L
		else -> false
	}
	return TimelineRealtimeEventDecision(
		nextCursorState = baseDecision.nextState,
		shouldApplyReadProjection = shouldApplyReadProjection,
		shouldHandleResync = baseDecision.shouldHandleResync
	)
}
