package me.floow.chats.uilogic.replies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepliesOverlayE2EIntegrationTest {

	@Test
	fun `10 replies flow keeps anchor and unread after partial read reopen`() {
		val seqByMessageId = (1L..15L).associateWith { seq -> seq }

		val firstOpen = projectRepliesReadModel(
			input = RepliesReadModelInput(
				seqByMessageId = seqByMessageId,
				serverReadUpToSeq = 5L,
				localReadUpToSeq = 5L,
				firstUnreadSeq = 6L,
				storedOpenAnchorSeq = null,
				resolvedOpenAnchorSeq = null,
				openMode = RepliesOverlayOpenMode.FROM_UNREAD,
				messageLinkOpenAnchorSeq = null
			)
		)

		assertEquals(6L, firstOpen.openAnchorMessageId)
		assertEquals(6L, firstOpen.unreadBoundaryMessageId)
		assertEquals(10, firstOpen.unreadMessageIds.size)
		assertEquals(5L, firstOpen.readUpToSeq)

		val reopen = projectRepliesReadModel(
			input = RepliesReadModelInput(
				seqByMessageId = seqByMessageId,
				serverReadUpToSeq = 8L,
				localReadUpToSeq = 8L,
				firstUnreadSeq = 9L,
				storedOpenAnchorSeq = 8L,
				resolvedOpenAnchorSeq = null,
				openMode = RepliesOverlayOpenMode.FROM_LAST_SEEN,
				messageLinkOpenAnchorSeq = null
			)
		)

		assertEquals(8L, reopen.openAnchorMessageId)
		assertEquals(9L, reopen.unreadBoundaryMessageId)
		assertEquals(7, reopen.unreadMessageIds.size)
		assertEquals(8L, reopen.readUpToSeq)
	}

	@Test
	fun `see all flow clears unread and boundary after reopen`() {
		val seqByMessageId = (1L..10L).associateWith { seq -> seq }

		val firstOpen = projectRepliesReadModel(
			input = RepliesReadModelInput(
				seqByMessageId = seqByMessageId,
				serverReadUpToSeq = 0L,
				localReadUpToSeq = 0L,
				firstUnreadSeq = 1L,
				storedOpenAnchorSeq = null,
				resolvedOpenAnchorSeq = null,
				openMode = RepliesOverlayOpenMode.FROM_UNREAD,
				messageLinkOpenAnchorSeq = null
			)
		)

		assertEquals(1L, firstOpen.openAnchorMessageId)
		assertEquals(1L, firstOpen.unreadBoundaryMessageId)
		assertEquals(10, firstOpen.unreadMessageIds.size)

		val reopen = projectRepliesReadModel(
			input = RepliesReadModelInput(
				seqByMessageId = seqByMessageId,
				serverReadUpToSeq = 10L,
				localReadUpToSeq = 10L,
				firstUnreadSeq = null,
				storedOpenAnchorSeq = 10L,
				resolvedOpenAnchorSeq = null,
				openMode = RepliesOverlayOpenMode.FROM_LAST_SEEN,
				messageLinkOpenAnchorSeq = null
			)
		)

		assertEquals(10L, reopen.openAnchorMessageId)
		assertNull(reopen.unreadBoundaryMessageId)
		assertEquals(0, reopen.unreadMessageIds.size)
		assertEquals(10L, reopen.readUpToSeq)
	}
}
