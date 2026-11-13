package me.floow.domain.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeViewEventReducerTest {

	@Test
	fun `non-resync event merges read cursor only`() {
		val decision = reduceRealtimeViewEvent(
			state = RealtimeViewCursorState(
				readCursor = 10L,
				lastHandledResyncCursor = 5L
			),
			input = RealtimeViewEventInput(
				incomingReadCursor = 14L,
				eventSeq = 20L,
				eventMaxCursor = 20L,
				isResyncEvent = false,
				isLoading = false
			)
		)

		assertFalse(decision.shouldHandleResync)
		assertEquals(14L, decision.nextState.readCursor)
		assertEquals(5L, decision.nextState.lastHandledResyncCursor)
	}

	@Test
	fun `resync event ignored while loading`() {
		val decision = reduceRealtimeViewEvent(
			state = RealtimeViewCursorState(
				readCursor = 10L,
				lastHandledResyncCursor = 30L
			),
			input = RealtimeViewEventInput(
				incomingReadCursor = null,
				eventSeq = 40L,
				eventMaxCursor = 40L,
				isResyncEvent = true,
				isLoading = true
			)
		)

		assertFalse(decision.shouldHandleResync)
		assertEquals(30L, decision.nextState.lastHandledResyncCursor)
	}

	@Test
	fun `resync event handled only when cursor is newer`() {
		val stale = reduceRealtimeViewEvent(
			state = RealtimeViewCursorState(
				readCursor = 1L,
				lastHandledResyncCursor = 50L
			),
			input = RealtimeViewEventInput(
				incomingReadCursor = null,
				eventSeq = 50L,
				eventMaxCursor = 50L,
				isResyncEvent = true,
				isLoading = false
			)
		)
		val fresh = reduceRealtimeViewEvent(
			state = RealtimeViewCursorState(
				readCursor = 1L,
				lastHandledResyncCursor = 50L
			),
			input = RealtimeViewEventInput(
				incomingReadCursor = null,
				eventSeq = 51L,
				eventMaxCursor = 51L,
				isResyncEvent = true,
				isLoading = false
			)
		)

		assertFalse(stale.shouldHandleResync)
		assertTrue(fresh.shouldHandleResync)
		assertEquals(51L, fresh.nextState.lastHandledResyncCursor)
	}
}
