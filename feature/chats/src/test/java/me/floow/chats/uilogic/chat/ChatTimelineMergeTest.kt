package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ChatTimelineMergeTest {

	private val baseTime = LocalDateTime.of(2026, 3, 1, 10, 0)

	@Test
	fun `mergeMessages deduplicates by clientMessageId keeping highest id`() {
		val optimistic = PrimaryOutMessage(
			id = -1L,
			clientMessageId = "key-1",
			messageText = "hello",
			dateTime = baseTime,
		)
		val confirmed = PrimaryOutMessage(
			id = 101L,
			clientMessageId = "key-1",
			messageText = "hello",
			dateTime = baseTime.plusSeconds(1),
		)

		val result = mergeMessages(listOf(optimistic), listOf(confirmed))

		assertEquals(1, result.size)
		assertEquals(101L, result.first().id)
	}

	@Test
	fun `mergeMessages preserves optimistic datetime for confirmed message`() {
		val optimisticTime = baseTime
		val optimistic = PrimaryOutMessage(
			id = -1L,
			clientMessageId = "key-2",
			messageText = "hi",
			dateTime = optimisticTime,
		)
		val confirmed = PrimaryOutMessage(
			id = 200L,
			clientMessageId = "key-2",
			messageText = "hi",
			dateTime = baseTime.plusSeconds(5),
		)

		val result = mergeMessages(listOf(optimistic), listOf(confirmed))

		assertEquals(optimisticTime, result.first().dateTime)
	}

	@Test
	fun `mergeMessages keeps both messages when clientMessageIds differ`() {
		val msg1 = PrimaryOutMessage(id = -1L, clientMessageId = "k1", messageText = "a", dateTime = baseTime)
		val msg2 = PrimaryOutMessage(id = -2L, clientMessageId = "k2", messageText = "b", dateTime = baseTime.plusSeconds(1))

		val result = mergeMessages(listOf(msg1), listOf(msg2))

		assertEquals(2, result.size)
	}

	@Test
	fun `mergeMessages deduplicates by id when no clientMessageId`() {
		val original = PrimaryInMessage(id = 55L, messageText = "original", dateTime = baseTime)
		val duplicate = PrimaryInMessage(id = 55L, messageText = "duplicate", dateTime = baseTime.plusSeconds(2))

		val result = mergeMessages(listOf(original), listOf(duplicate))

		assertEquals(1, result.size)
	}

	@Test
	fun `mergeMessages sorts by dateTime ascending`() {
		val later = PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(10))
		val earlier = PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime)

		val result = mergeMessages(listOf(later), listOf(earlier))

		assertEquals(1L, result.first().id)
		assertEquals(2L, result.last().id)
	}

	@Test
	fun `mergeObservedLatestMessages preserves older loaded messages`() {
		val older = PrimaryInMessage(id = 5L, messageText = "old", dateTime = baseTime)
		val observed1 = PrimaryInMessage(id = 10L, messageText = "recent1", dateTime = baseTime.plusMinutes(1))
		val observed2 = PrimaryInMessage(id = 11L, messageText = "recent2", dateTime = baseTime.plusMinutes(2))

		val result = mergeObservedLatestMessages(
			currentMessages = listOf(older, observed1),
			observedMessages = listOf(observed1, observed2),
			pendingOutgoingOptimisticIds = emptySet(),
		)

		assertTrue("should contain older message", result.any { it.id == 5L })
		assertTrue("should contain observed2", result.any { it.id == 11L })
	}

	@Test
	fun `mergeObservedLatestMessages keeps pending optimistic not in observed`() {
		val optimistic = PrimaryOutMessage(
			id = -1L,
			clientMessageId = "pending-key",
			messageText = "sending",
			dateTime = baseTime.plusMinutes(5),
		)
		val observed = PrimaryInMessage(id = 20L, messageText = "incoming", dateTime = baseTime.plusMinutes(4))

		val result = mergeObservedLatestMessages(
			currentMessages = listOf(optimistic),
			observedMessages = listOf(observed),
			pendingOutgoingOptimisticIds = setOf(-1L),
		)

		assertTrue("should still contain optimistic", result.any { it.id == -1L })
		assertTrue("should contain observed", result.any { it.id == 20L })
	}

	@Test
	fun `mergeObservedLatestMessages removes optimistic if clientMessageId confirmed`() {
		val optimistic = PrimaryOutMessage(
			id = -1L,
			clientMessageId = "confirmed-key",
			messageText = "hello",
			dateTime = baseTime,
		)
		val confirmedObserved = PrimaryOutMessage(
			id = 30L,
			clientMessageId = "confirmed-key",
			messageText = "hello",
			dateTime = baseTime.plusSeconds(2),
		)

		val result = mergeObservedLatestMessages(
			currentMessages = listOf(optimistic),
			observedMessages = listOf(confirmedObserved),
			pendingOutgoingOptimisticIds = setOf(-1L),
		)

		assertFalse("optimistic should be gone", result.any { it.id == -1L })
		assertTrue("confirmed should be present", result.any { it.id == 30L })
	}

	@Test
	fun `replaceOrMergeMessage replaces at correct index`() {
		val messages = listOf(
			PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime),
			PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(1)),
			PrimaryInMessage(id = 3L, messageText = "c", dateTime = baseTime.plusSeconds(2)),
		)
		val replacement = PrimaryInMessage(id = 99L, messageText = "replaced", dateTime = baseTime.plusSeconds(1))

		val result = replaceOrMergeMessage(
			base = messages,
			replaceId = 2L,
			confirmed = replacement,
		)

		assertEquals(3, result.size)
		assertFalse("id=2 should be gone", result.any { it.id == 2L })
		assertTrue("replacement should be present", result.any { it.id == 99L })
	}

	@Test
	fun `replaceOrMergeMessage merges when replaceId not found`() {
		val messages = listOf(
			PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime),
		)
		val newMsg = PrimaryInMessage(id = 5L, messageText = "new", dateTime = baseTime.plusSeconds(5))

		val result = replaceOrMergeMessage(
			base = messages,
			replaceId = 999L,
			confirmed = newMsg,
		)

		assertEquals(2, result.size)
		assertTrue(result.any { it.id == 5L })
	}

	@Test
	fun `groupMessagesByDate groups correctly`() {
		val day1 = LocalDateTime.of(2026, 3, 1, 10, 0)
		val day2 = LocalDateTime.of(2026, 3, 2, 10, 0)
		val messages = listOf(
			PrimaryInMessage(id = 1L, messageText = "a", dateTime = day1),
			PrimaryInMessage(id = 2L, messageText = "b", dateTime = day2),
			PrimaryInMessage(id = 3L, messageText = "c", dateTime = day1.plusHours(1)),
		)

		val grouped = groupMessagesByDate(messages)

		assertEquals(2, grouped.size)
		assertEquals(2, grouped.first().messages.size)
		assertEquals(1, grouped.last().messages.size)
	}

	@Test
	fun `groupMessagesByDate returns empty for empty input`() {
		assertTrue(groupMessagesByDate(emptyList()).isEmpty())
	}

	@Test
	fun `inferUnreadBoundaryMessageId returns first incoming after readUpTo`() {
		val messages = listOf(
			PrimaryOutMessage(id = 1L, messageText = "out", dateTime = baseTime),
			PrimaryInMessage(id = 2L, messageText = "in1", dateTime = baseTime.plusSeconds(1)),
			PrimaryInMessage(id = 3L, messageText = "in2", dateTime = baseTime.plusSeconds(2)),
		)

		val boundary = inferUnreadBoundaryMessageId(
			messages = messages,
			lastReadMessageId = 1L,
			peerUserId = "peer-id",
		)

		assertEquals(2L, boundary)
	}

	@Test
	fun `inferUnreadBoundaryMessageId returns null when all read`() {
		val messages = listOf(
			PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime),
			PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(1)),
		)

		val boundary = inferUnreadBoundaryMessageId(
			messages = messages,
			lastReadMessageId = 5L,
			peerUserId = "peer-id",
		)

		assertNull(boundary)
	}

	@Test
	fun `latestPersistableMessageId ignores optimistic negative ids`() {
		val groups = groupMessagesByDate(
			listOf(
				PrimaryOutMessage(id = -3L, messageText = "optimistic", dateTime = baseTime),
				PrimaryInMessage(id = 10L, messageText = "server", dateTime = baseTime.plusSeconds(1)),
			)
		)

		assertEquals(10L, latestPersistableMessageId(groups))
	}

	@Test
	fun `latestRenderableMessageId includes optimistic ids`() {
		val groups = groupMessagesByDate(
			listOf(
				PrimaryOutMessage(id = -1L, messageText = "optimistic", dateTime = baseTime.plusSeconds(2)),
				PrimaryInMessage(id = 10L, messageText = "server", dateTime = baseTime),
			)
		)

		assertEquals(10L, latestRenderableMessageId(groups))
	}

	@Test
	fun `flattenMessages returns all messages sorted by dateTime`() {
		val group1 = me.floow.uikit.chat.model.DatedChatMessages(
			datetime = baseTime.toLocalDate(),
			messages = listOf(
				PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(1)),
				PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime),
			),
		)

		val result = flattenMessages(listOf(group1))

		assertEquals(1L, result.first().id)
		assertEquals(2L, result.last().id)
	}
}
