package me.floow.domain.realtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCursorPolicyTest {

	@Test
	fun `event cursor and merge stay monotonic`() {
		assertEquals(12L, resolveRealtimeEventCursor(eventSeq = 10L, eventMaxCursor = 12L))
		assertEquals(
			12L,
			mergeRealtimeCursor(currentCursor = 9L, eventSeq = 10L, eventMaxCursor = 12L)
		)
		assertEquals(
			15L,
			mergeRealtimeCursor(currentCursor = 15L, eventSeq = 10L, eventMaxCursor = 12L)
		)
	}

	@Test
	fun `resync handling ignores stale duplicates but accepts newer cursor`() {
		assertFalse(
			shouldHandleRealtimeResync(
				isLoading = false,
				eventSeq = 70L,
				eventMaxCursor = 70L,
				lastHandledResyncCursor = 70L
			)
		)
		assertTrue(
			shouldHandleRealtimeResync(
				isLoading = false,
				eventSeq = 71L,
				eventMaxCursor = 71L,
				lastHandledResyncCursor = 70L
			)
		)
	}

	@Test
	fun `hello gap policy triggers on stale server max and large replay gaps`() {
		assertTrue(
			shouldReloadOnHelloRealtimeGap(
				localMaxCursor = 120L,
				eventMaxCursor = 100L,
				streamAfterCursor = 120L,
				maxReplayWindow = 500L
			)
		)
		assertTrue(
			shouldReloadOnHelloRealtimeGap(
				localMaxCursor = 200L,
				eventMaxCursor = 900L,
				streamAfterCursor = 200L,
				maxReplayWindow = 500L
			)
		)
		assertFalse(
			shouldReloadOnHelloRealtimeGap(
				localMaxCursor = 200L,
				eventMaxCursor = 450L,
				streamAfterCursor = 200L,
				maxReplayWindow = 500L
			)
		)
	}

	@Test
	fun `sequential gap policy only triggers on hole larger than one`() {
		assertFalse(shouldReloadOnSequentialGap(localMaxCursor = 10L, eventCursor = 11L))
		assertTrue(shouldReloadOnSequentialGap(localMaxCursor = 10L, eventCursor = 13L))
	}
}
