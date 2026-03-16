package me.floow.domain.realtime

fun resolveRealtimeEventCursor(
	eventSeq: Long,
	eventMaxCursor: Long
): Long {
	return maxOf(eventSeq, eventMaxCursor).coerceAtLeast(0L)
}

fun mergeRealtimeCursor(
	currentCursor: Long,
	eventSeq: Long,
	eventMaxCursor: Long
): Long {
	return maxOf(
		currentCursor.coerceAtLeast(0L),
		resolveRealtimeEventCursor(eventSeq, eventMaxCursor)
	)
}

fun shouldHandleRealtimeResync(
	isLoading: Boolean,
	eventSeq: Long,
	eventMaxCursor: Long,
	lastHandledResyncCursor: Long
): Boolean {
	if (isLoading) return false
	val candidateCursor = resolveRealtimeEventCursor(eventSeq, eventMaxCursor)
	if (candidateCursor <= 0L) return true
	return candidateCursor > lastHandledResyncCursor.coerceAtLeast(0L)
}

fun shouldReloadOnHelloRealtimeGap(
	localMaxCursor: Long,
	eventMaxCursor: Long,
	streamAfterCursor: Long,
	maxReplayWindow: Long
): Boolean {
	val normalizedLocalMax = localMaxCursor.coerceAtLeast(0L)
	val normalizedEventMax = eventMaxCursor.coerceAtLeast(0L)
	val normalizedAfterCursor = streamAfterCursor.coerceAtLeast(0L)
	val normalizedReplayWindow = maxReplayWindow.coerceAtLeast(0L)

	val staleServerMax = normalizedLocalMax > 0L &&
		normalizedEventMax > 0L &&
		normalizedEventMax < normalizedLocalMax
	if (staleServerMax) return true

	val replayGap = (normalizedEventMax - normalizedAfterCursor).coerceAtLeast(0L)
	return normalizedAfterCursor > 0L && replayGap > normalizedReplayWindow
}

fun shouldReloadOnSequentialGap(
	localMaxCursor: Long,
	eventCursor: Long
): Boolean {
	val normalizedLocalMax = localMaxCursor.coerceAtLeast(0L)
	val normalizedEventCursor = eventCursor.coerceAtLeast(0L)
	if (normalizedLocalMax <= 0L || normalizedEventCursor <= 0L) return false
	return normalizedEventCursor > normalizedLocalMax + 1L
}
