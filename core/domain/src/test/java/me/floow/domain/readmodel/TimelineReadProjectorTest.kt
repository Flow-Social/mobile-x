package me.floow.domain.readmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineReadProjectorTest {

	@Test
	fun `projection keeps only incoming unread above effective read cursor`() {
		val projection = projectTimelineReadModel(
			input = TimelineReadProjectorInput(
				items = listOf(
					TimelineReadItem(messageId = 1L, cursor = 1L, isIncoming = true),
					TimelineReadItem(messageId = 2L, cursor = 2L, isIncoming = true),
					TimelineReadItem(messageId = 3L, cursor = 3L, isIncoming = false),
					TimelineReadItem(messageId = 4L, cursor = 4L, isIncoming = true)
				),
				serverReadUpToCursor = 1L,
				localReadUpToCursor = 2L,
				firstUnreadCursor = null,
				storedOpenAnchorCursor = null,
				resolvedOpenAnchorCursor = null,
				openMode = TimelineReadOpenMode.FROM_UNREAD,
				messageLinkAnchorCursor = null,
				preferCeilOpenAnchor = true,
				fallbackToOldestOpenAnchor = false
			)
		)

		assertEquals(setOf(4L), projection.unreadMessageIds)
		assertEquals(2L, projection.unreadBoundaryMessageId)
		assertEquals(2L, projection.readUpToCursor)
	}

	@Test
	fun `message link mode prioritizes link anchor`() {
		val projection = projectTimelineReadModel(
			input = TimelineReadProjectorInput(
				items = listOf(
					TimelineReadItem(messageId = 10L, cursor = 10L, isIncoming = true),
					TimelineReadItem(messageId = 20L, cursor = 20L, isIncoming = true),
					TimelineReadItem(messageId = 30L, cursor = 30L, isIncoming = true)
				),
				serverReadUpToCursor = 0L,
				localReadUpToCursor = 0L,
				firstUnreadCursor = 10L,
				storedOpenAnchorCursor = 20L,
				resolvedOpenAnchorCursor = null,
				openMode = TimelineReadOpenMode.FROM_MESSAGE_LINK,
				messageLinkAnchorCursor = 30L,
				preferCeilOpenAnchor = true,
				fallbackToOldestOpenAnchor = true
			)
		)

		assertEquals(30L, projection.openAnchorMessageId)
		assertEquals(30L, projection.openAnchorCursor)
	}

	@Test
	fun `resolve anchor chooses floor when prefer ceil is disabled`() {
		val messageId = resolveAnchorMessageIdByCursor(
			targetCursor = 25L,
			cursorByMessageId = linkedMapOf(
				100L to 10L,
				200L to 20L,
				300L to 30L
			),
			preferCeil = false,
			fallbackToOldest = false
		)

		assertEquals(200L, messageId)
	}

	@Test
	fun `resolve anchor returns oldest when target missing and fallback enabled`() {
		val messageId = resolveAnchorMessageIdByCursor(
			targetCursor = 1L,
			cursorByMessageId = linkedMapOf(
				100L to 10L,
				200L to 20L
			),
			preferCeil = false,
			fallbackToOldest = true
		)

		assertEquals(100L, messageId)
	}

	@Test
	fun `resolve max visible unread cursor ignores non-unread and unknown ids`() {
		val maxCursor = resolveMaxVisibleUnreadCursor(
			visibleMessageIds = linkedSetOf(1L, 2L, 4L, 9L),
			unreadMessageIds = linkedSetOf(2L, 3L, 4L),
			cursorByMessageId = mapOf(
				1L to 10L,
				2L to 20L,
				3L to 30L,
				4L to 40L
			)
		)

		assertEquals(40L, maxCursor)
	}

	@Test
	fun `read queue helpers keep monotonic cursor progression`() {
		assertEquals(true, shouldApplyVisibleReadCandidate(candidateCursor = 3L, lastAppliedVisibleCursor = 2L))
		assertEquals(false, shouldApplyVisibleReadCandidate(candidateCursor = 2L, lastAppliedVisibleCursor = 2L))
		assertEquals(true, shouldEnqueueReadCursor(readCursor = 7L, lastAppliedReadCursor = 6L))
		assertEquals(9L, mergePendingReadCursor(currentPendingCursor = 5L, newCursor = 9L))
		assertEquals(5L, mergePendingReadCursor(currentPendingCursor = 5L, newCursor = 4L))
		assertEquals(0L, mergePendingReadCursor(currentPendingCursor = null, newCursor = 0L))
	}

	@Test
	fun `projection keeps open anchor cursor when item list is empty`() {
		val projection = projectTimelineReadModel(
			input = TimelineReadProjectorInput(
				items = emptyList(),
				serverReadUpToCursor = 10L,
				localReadUpToCursor = 0L,
				firstUnreadCursor = null,
				storedOpenAnchorCursor = 10L,
				resolvedOpenAnchorCursor = null,
				openMode = TimelineReadOpenMode.FROM_LAST_SEEN,
				messageLinkAnchorCursor = null,
				preferCeilOpenAnchor = false,
				fallbackToOldestOpenAnchor = false
			)
		)

		assertNull(projection.openAnchorMessageId)
		assertEquals(10L, projection.openAnchorCursor)
		assertEquals(10L, projection.readUpToCursor)
	}
}
