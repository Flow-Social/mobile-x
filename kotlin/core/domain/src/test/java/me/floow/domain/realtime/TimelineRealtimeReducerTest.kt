package me.floow.domain.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineRealtimeReducerTest {

	@Test
	fun `hello updates read cursor and marks projection apply`() {
		val decision = reduceTimelineRealtimeEvent(
			state = RealtimeViewCursorState(readCursor = 5L, lastHandledResyncCursor = 0L),
			input = TimelineRealtimeEventInput(
				kind = TimelineRealtimeEventKind.HELLO,
				incomingReadCursor = 8L,
				eventSeq = 10L,
				eventMaxCursor = 10L,
				isLoading = false
			)
		)

		assertEquals(8L, decision.nextCursorState.readCursor)
		assertTrue(decision.shouldApplyReadProjection)
		assertFalse(decision.shouldHandleResync)
	}

	@Test
	fun `entity upsert updates cursor state but does not force read projection`() {
		val decision = reduceTimelineRealtimeEvent(
			state = RealtimeViewCursorState(readCursor = 8L, lastHandledResyncCursor = 0L),
			input = TimelineRealtimeEventInput(
				kind = TimelineRealtimeEventKind.ENTITY_UPSERTED,
				incomingReadCursor = 9L,
				eventSeq = 20L,
				eventMaxCursor = 20L,
				isLoading = false
			)
		)

		assertEquals(9L, decision.nextCursorState.readCursor)
		assertFalse(decision.shouldApplyReadProjection)
		assertFalse(decision.shouldHandleResync)
	}

	@Test
	fun `resync requires newer cursor and not loading`() {
		val stale = reduceTimelineRealtimeEvent(
			state = RealtimeViewCursorState(readCursor = 0L, lastHandledResyncCursor = 40L),
			input = TimelineRealtimeEventInput(
				kind = TimelineRealtimeEventKind.RESYNC_REQUIRED,
				incomingReadCursor = null,
				eventSeq = 40L,
				eventMaxCursor = 40L,
				isLoading = false
			)
		)
		val fresh = reduceTimelineRealtimeEvent(
			state = RealtimeViewCursorState(readCursor = 0L, lastHandledResyncCursor = 40L),
			input = TimelineRealtimeEventInput(
				kind = TimelineRealtimeEventKind.RESYNC_REQUIRED,
				incomingReadCursor = null,
				eventSeq = 41L,
				eventMaxCursor = 41L,
				isLoading = false
			)
		)

		assertFalse(stale.shouldHandleResync)
		assertTrue(fresh.shouldHandleResync)
		assertEquals(41L, fresh.nextCursorState.lastHandledResyncCursor)
	}

	@Test
	fun `read cursor update is ignored if the incoming readStateVersion is older`() {
		val decision = reduceTimelineRealtimeEvent(
			state = RealtimeViewCursorState(readCursor = 5L, lastHandledResyncCursor = 0L),
			input = TimelineRealtimeEventInput(
				kind = TimelineRealtimeEventKind.READ_CURSOR_UPDATED,
				incomingReadCursor = 8L,
				eventSeq = 10L,
				eventMaxCursor = 10L,
				isLoading = false
			)
		)

		assertEquals(8L, decision.nextCursorState.readCursor)
		assertTrue(decision.shouldApplyReadProjection)
	}
}
