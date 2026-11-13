package me.floow.domain.realtime

data class RealtimeViewCursorState(
	val readCursor: Long = 0L,
	val lastHandledResyncCursor: Long = 0L
)

data class RealtimeViewEventInput(
	val incomingReadCursor: Long?,
	val eventSeq: Long,
	val eventMaxCursor: Long,
	val isResyncEvent: Boolean,
	val isLoading: Boolean
)

data class RealtimeViewEventDecision(
	val nextState: RealtimeViewCursorState,
	val shouldHandleResync: Boolean
)

fun reduceRealtimeViewEvent(
	state: RealtimeViewCursorState,
	input: RealtimeViewEventInput
): RealtimeViewEventDecision {
	val nextReadCursor = input.incomingReadCursor
		?.coerceAtLeast(0L)
		?.let { incoming -> maxOf(state.readCursor.coerceAtLeast(0L), incoming) }
		?: state.readCursor.coerceAtLeast(0L)

	if (!input.isResyncEvent) {
		return RealtimeViewEventDecision(
			nextState = state.copy(readCursor = nextReadCursor),
			shouldHandleResync = false
		)
	}

	val shouldHandleResync = shouldHandleRealtimeResync(
		isLoading = input.isLoading,
		eventSeq = input.eventSeq,
		eventMaxCursor = input.eventMaxCursor,
		lastHandledResyncCursor = state.lastHandledResyncCursor
	)
	if (!shouldHandleResync) {
		return RealtimeViewEventDecision(
			nextState = state.copy(readCursor = nextReadCursor),
			shouldHandleResync = false
		)
	}
	val nextHandledResyncCursor = resolveRealtimeEventCursor(
		eventSeq = input.eventSeq,
		eventMaxCursor = input.eventMaxCursor
	)
	return RealtimeViewEventDecision(
		nextState = RealtimeViewCursorState(
			readCursor = nextReadCursor,
			lastHandledResyncCursor = nextHandledResyncCursor
		),
		shouldHandleResync = true
	)
}
