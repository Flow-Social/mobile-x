package me.floow.data.repos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepliesRealtimeGapPolicyTest {

	@Test
	fun `hello forces snapshot when server max seq is behind local max`() {
		val shouldReload = shouldReloadRepliesSnapshotOnHello(
			localMaxSeq = 120L,
			eventMaxSeq = 100L,
			streamAfterSeq = 120L,
			maxReplayWindow = 500L
		)

		assertTrue(shouldReload)
	}

	@Test
	fun `hello forces snapshot when replay gap exceeds window`() {
		val shouldReload = shouldReloadRepliesSnapshotOnHello(
			localMaxSeq = 200L,
			eventMaxSeq = 900L,
			streamAfterSeq = 200L,
			maxReplayWindow = 500L
		)

		assertTrue(shouldReload)
	}

	@Test
	fun `hello does not force snapshot for normal replay window`() {
		val shouldReload = shouldReloadRepliesSnapshotOnHello(
			localMaxSeq = 200L,
			eventMaxSeq = 450L,
			streamAfterSeq = 200L,
			maxReplayWindow = 500L
		)

		assertFalse(shouldReload)
	}

	@Test
	fun `created event forces snapshot only for seq gaps greater than one`() {
		assertFalse(
			shouldReloadRepliesSnapshotOnCreated(
				localMaxSeq = 10L,
				eventSeq = 11L
			)
		)
		assertTrue(
			shouldReloadRepliesSnapshotOnCreated(
				localMaxSeq = 10L,
				eventSeq = 13L
			)
		)
	}
}
