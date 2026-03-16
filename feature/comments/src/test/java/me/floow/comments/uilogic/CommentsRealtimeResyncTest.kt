package me.floow.comments.uilogic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsRealtimeResyncTest {

	@Test
	fun `resync does not reload while initial loading is in progress`() {
		val shouldReload = shouldReloadCommentsOnResync(
			isLoading = true,
			eventSeq = 120L,
			eventMaxSeq = 140L,
			lastHandledResyncSeq = 0L
		)

		assertFalse(shouldReload)
	}

	@Test
	fun `resync reloads for first positive sequence`() {
		val shouldReload = shouldReloadCommentsOnResync(
			isLoading = false,
			eventSeq = 33L,
			eventMaxSeq = 33L,
			lastHandledResyncSeq = 0L
		)

		assertTrue(shouldReload)
	}

	@Test
	fun `resync ignores duplicate or stale sequence`() {
		val shouldReloadDuplicate = shouldReloadCommentsOnResync(
			isLoading = false,
			eventSeq = 70L,
			eventMaxSeq = 70L,
			lastHandledResyncSeq = 70L
		)
		val shouldReloadStale = shouldReloadCommentsOnResync(
			isLoading = false,
			eventSeq = 69L,
			eventMaxSeq = 69L,
			lastHandledResyncSeq = 70L
		)

		assertFalse(shouldReloadDuplicate)
		assertFalse(shouldReloadStale)
	}

	@Test
	fun `resync reloads when max sequence is newer than last handled`() {
		val shouldReload = shouldReloadCommentsOnResync(
			isLoading = false,
			eventSeq = 80L,
			eventMaxSeq = 95L,
			lastHandledResyncSeq = 90L
		)

		assertTrue(shouldReload)
	}
}
