package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class ChatTimelineMergeTest {

	private val baseTime = LocalDateTime.of(2026, 3, 1, 10, 0)

	@Test
	fun `mergeMessages deduplicates by clientMessageId keeping highest id`() {
		val optimistic = PrimaryOutMessage(
			id = -1L,
			clientMessageId = "key-1",
			messageText = "hello",
			createdAtMillis = baseTime.toEpochMillis(),
		)
		val confirmed = PrimaryOutMessage(
			id = 101L,
			clientMessageId = "key-1",
			messageText = "hello",
			createdAtMillis = baseTime.plusSeconds(1).toEpochMillis(),
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
			createdAtMillis = optimisticTime.toEpochMillis(),
		)
		val confirmed = PrimaryOutMessage(
			id = 200L,
			clientMessageId = "key-2",
			messageText = "hi",
			createdAtMillis = baseTime.plusSeconds(5).toEpochMillis(),
		)

		val result = mergeMessages(listOf(optimistic), listOf(confirmed))

		assertEquals(optimisticTime.toEpochMillis(), result.first().createdAtMillis)
	}

	@Test
	fun `mergeMessages keeps both messages when clientMessageIds differ`() {
		val msg1 = PrimaryOutMessage(id = -1L, clientMessageId = "k1", messageText = "a", createdAtMillis = baseTime.toEpochMillis())
		val msg2 = PrimaryOutMessage(id = -2L, clientMessageId = "k2", messageText = "b", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis())

		val result = mergeMessages(listOf(msg1), listOf(msg2))

		assertEquals(2, result.size)
	}

	@Test
	fun `mergeMessages deduplicates by id when no clientMessageId`() {
		val original = PrimaryInMessage(id = 55L, messageText = "original", createdAtMillis = baseTime.toEpochMillis())
		val duplicate = PrimaryInMessage(id = 55L, messageText = "duplicate", createdAtMillis = baseTime.plusSeconds(2).toEpochMillis())

		val result = mergeMessages(listOf(original), listOf(duplicate))

		assertEquals(1, result.size)
	}

	@Test
	fun `mergeMessages sorts by dateTime ascending`() {
		val later = PrimaryInMessage(id = 2L, messageText = "b", createdAtMillis = baseTime.plusSeconds(10).toEpochMillis())
		val earlier = PrimaryInMessage(id = 1L, messageText = "a", createdAtMillis = baseTime.toEpochMillis())

		val result = mergeMessages(listOf(later), listOf(earlier))

		assertEquals(1L, result.first().id)
		assertEquals(2L, result.last().id)
	}

	@Test
	fun `mergeObservedLatestMessages preserves older loaded messages`() {
		val older = PrimaryInMessage(id = 5L, messageText = "old", createdAtMillis = baseTime.toEpochMillis())
		val observed1 = PrimaryInMessage(id = 10L, messageText = "recent1", createdAtMillis = baseTime.plusMinutes(1).toEpochMillis())
		val observed2 = PrimaryInMessage(id = 11L, messageText = "recent2", createdAtMillis = baseTime.plusMinutes(2).toEpochMillis())

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
			createdAtMillis = baseTime.plusMinutes(5).toEpochMillis(),
		)
		val observed = PrimaryInMessage(id = 20L, messageText = "incoming", createdAtMillis = baseTime.plusMinutes(4).toEpochMillis())

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
			createdAtMillis = baseTime.toEpochMillis(),
		)
		val confirmedObserved = PrimaryOutMessage(
			id = 30L,
			clientMessageId = "confirmed-key",
			messageText = "hello",
			createdAtMillis = baseTime.plusSeconds(2).toEpochMillis(),
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
			PrimaryInMessage(id = 1L, messageText = "a", createdAtMillis = baseTime.toEpochMillis()),
			PrimaryInMessage(id = 2L, messageText = "b", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis()),
			PrimaryInMessage(id = 3L, messageText = "c", createdAtMillis = baseTime.plusSeconds(2).toEpochMillis()),
		)
		val replacement = PrimaryInMessage(id = 99L, messageText = "replaced", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis())

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
			PrimaryInMessage(id = 1L, messageText = "a", createdAtMillis = baseTime.toEpochMillis()),
		)
		val newMsg = PrimaryInMessage(id = 5L, messageText = "new", createdAtMillis = baseTime.plusSeconds(5).toEpochMillis())

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
			PrimaryInMessage(id = 1L, messageText = "a", createdAtMillis = day1.toEpochMillis()),
			PrimaryInMessage(id = 2L, messageText = "b", createdAtMillis = day2.toEpochMillis()),
			PrimaryInMessage(id = 3L, messageText = "c", createdAtMillis = day1.plusHours(1).toEpochMillis()),
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
			PrimaryOutMessage(id = 1L, messageText = "out", createdAtMillis = baseTime.toEpochMillis()),
			PrimaryInMessage(id = 2L, messageText = "in1", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis()),
			PrimaryInMessage(id = 3L, messageText = "in2", createdAtMillis = baseTime.plusSeconds(2).toEpochMillis()),
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
			PrimaryInMessage(id = 1L, messageText = "a", createdAtMillis = baseTime.toEpochMillis()),
			PrimaryInMessage(id = 2L, messageText = "b", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis()),
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
				PrimaryOutMessage(id = -3L, messageText = "optimistic", createdAtMillis = baseTime.toEpochMillis()),
				PrimaryInMessage(id = 10L, messageText = "server", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis()),
			)
		)

		assertEquals(10L, latestPersistableMessageId(groups))
	}

	@Test
	fun `latestRenderableMessageId includes optimistic ids`() {
		val groups = groupMessagesByDate(
			listOf(
				PrimaryOutMessage(id = -1L, messageText = "optimistic", createdAtMillis = baseTime.plusSeconds(2).toEpochMillis()),
				PrimaryInMessage(id = 10L, messageText = "server", createdAtMillis = baseTime.toEpochMillis()),
			)
		)

		assertEquals(10L, latestRenderableMessageId(groups))
	}

	@Test
	fun `flattenMessages returns all messages sorted by dateTime`() {
		val group1 = me.floow.uikit.chat.model.DatedChatMessages(
			dayStartMillis = baseTime.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
			messages = listOf(
				PrimaryInMessage(id = 2L, messageText = "b", createdAtMillis = baseTime.plusSeconds(1).toEpochMillis()),
				PrimaryInMessage(id = 1L, messageText = "a", createdAtMillis = baseTime.toEpochMillis()),
			),
		)

		val result = flattenMessages(listOf(group1))

		assertEquals(1L, result.first().id)
		assertEquals(2L, result.last().id)
	}
}

private fun LocalDateTime.toEpochMillis(): Long {
	return toInstant(ZoneOffset.UTC).toEpochMilli()
}
