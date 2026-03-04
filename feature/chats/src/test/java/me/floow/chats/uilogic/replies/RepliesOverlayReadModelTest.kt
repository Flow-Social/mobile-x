package me.floow.chats.uilogic.replies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepliesOverlayReadModelTest {
	@Test
	fun `resolveRepliesAnchorMessageId uses exact anchor seq when available`() {
		val anchor = resolveRepliesAnchorMessageId(
			anchorSeq = 42L,
			seqByMessageId = linkedMapOf(
				1001L to 41L,
				1002L to 42L,
				1003L to 43L
			)
		)

		assertEquals(1002L, anchor)
	}

	@Test
	fun `resolveRepliesAnchorMessageId uses nearest ceiling for missing anchor seq`() {
		val anchor = resolveRepliesAnchorMessageId(
			anchorSeq = 52L,
			seqByMessageId = linkedMapOf(
				2001L to 50L,
				2002L to 55L,
				2003L to 60L
			)
		)

		assertEquals(2002L, anchor)
	}

	@Test
	fun `resolveRepliesAnchorMessageId falls back to oldest item when anchor is absent`() {
		val anchor = resolveRepliesAnchorMessageId(
			anchorSeq = null,
			seqByMessageId = linkedMapOf(
				3001L to 70L,
				3002L to 75L,
				3003L to 76L
			)
		)

		assertEquals(3001L, anchor)
	}

	@Test
	fun `resolveRepliesAnchorMessageId returns null for empty map`() {
		val anchor = resolveRepliesAnchorMessageId(
			anchorSeq = 1L,
			seqByMessageId = emptyMap()
		)

		assertNull(anchor)
	}

	@Test
	fun `removeReadMessagesUpTo keeps only unread above cursor`() {
		val result = removeReadMessagesUpTo(
			messageIds = linkedSetOf(1L, 2L, 3L, 4L),
			seqByMessageId = linkedMapOf(
				1L to 101L,
				2L to 102L,
				3L to 103L,
				4L to 104L
			),
			readUpToSeq = 102L
		)

		assertEquals(linkedSetOf(3L, 4L), result)
	}
}
