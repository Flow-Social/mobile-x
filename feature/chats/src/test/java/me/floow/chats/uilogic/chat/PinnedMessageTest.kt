package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PinnedMessageTest {

	private val baseTime = LocalDateTime.of(2026, 3, 1, 10, 0)

	private fun buildStateWithMessages(vararg messages: me.floow.uikit.chat.model.ChatMessage): ChatScreenVmState {
		val groups = listOf(DatedChatMessages(datetime = baseTime.toLocalDate(), messages = messages.toList()))
		val pinned = messages.filter { it.isPinned }
		return ChatScreenVmState(
			messages = groups,
			pinnedMessages = pinned,
			conversationId = 1L,
			chatInterlocutorId = "peer-1",
		)
	}

	// ─── applyPinnedFlagToState ───────────────────────────────────────────────

	@Test
	fun `applyPinnedFlagToState adds message to pinnedMessages when pinning`() {
		val msg = PrimaryInMessage(id = 10L, messageText = "hello", dateTime = baseTime)
		val state = buildStateWithMessages(msg)

		val result = applyPinnedFlagToState(state, messageId = 10L, isPinned = true)

		assertEquals(1, result.pinnedMessages.size)
		assertEquals(10L, result.pinnedMessages.first().id)
		assertTrue(result.pinnedMessages.first().isPinned)
	}

	@Test
	fun `applyPinnedFlagToState removes message from pinnedMessages when unpinning`() {
		val msg = PrimaryInMessage(id = 10L, messageText = "hello", dateTime = baseTime, isPinned = true)
		val state = buildStateWithMessages(msg)

		val result = applyPinnedFlagToState(state, messageId = 10L, isPinned = false)

		assertTrue(result.pinnedMessages.isEmpty())
	}

	@Test
	fun `applyPinnedFlagToState updates isPinned flag in timeline messages`() {
		val msg = PrimaryInMessage(id = 5L, messageText = "hi", dateTime = baseTime)
		val state = buildStateWithMessages(msg)

		val result = applyPinnedFlagToState(state, messageId = 5L, isPinned = true)

		val updatedMsg = flattenMessages(result.messages).firstOrNull { it.id == 5L }
		assertNotNull(updatedMsg)
		assertTrue(updatedMsg!!.isPinned)
	}

	@Test
	fun `applyPinnedFlagToState leaves unrelated messages unchanged`() {
		val msg1 = PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime)
		val msg2 = PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(1), isPinned = true)
		val state = buildStateWithMessages(msg1, msg2)

		val result = applyPinnedFlagToState(state, messageId = 1L, isPinned = true)

		assertEquals(2, result.pinnedMessages.size)
		assertTrue(result.pinnedMessages.any { it.id == 2L && it.isPinned })
		assertTrue(result.pinnedMessages.any { it.id == 1L && it.isPinned })
	}

	@Test
	fun `applyPinnedFlagToState no-op when messageId not in timeline`() {
		val msg = PrimaryInMessage(id = 3L, messageText = "c", dateTime = baseTime)
		val state = buildStateWithMessages(msg)

		val result = applyPinnedFlagToState(state, messageId = 999L, isPinned = true)

		assertTrue(result.pinnedMessages.isEmpty())
		assertFalse(flattenMessages(result.messages).any { it.id == 999L })
	}

	@Test
	fun `applyPinnedFlagToState does not duplicate pinned entries`() {
		val msg = PrimaryInMessage(id = 7L, messageText = "d", dateTime = baseTime, isPinned = true)
		val state = buildStateWithMessages(msg)

		val result = applyPinnedFlagToState(state, messageId = 7L, isPinned = true)

		assertEquals(1, result.pinnedMessages.size)
	}

	// ─── withPinned ───────────────────────────────────────────────────────────

	@Test
	fun `withPinned sets isPinned true on PrimaryOutMessage`() {
		val msg = PrimaryOutMessage(id = 1L, messageText = "out", dateTime = baseTime, isPinned = false)
		assertTrue(msg.withPinned(true).isPinned)
	}

	@Test
	fun `withPinned sets isPinned false on PrimaryInMessage`() {
		val msg = PrimaryInMessage(id = 2L, messageText = "in", dateTime = baseTime, isPinned = true)
		assertFalse(msg.withPinned(false).isPinned)
	}

	// ─── Optimistic unpin flow ────────────────────────────────────────────────

	@Test
	fun `optimistic unpin pinnedMessages is empty immediately after applyPinnedFlagToState with isPinned=false`() {
		val pinnedMsg = PrimaryInMessage(id = 42L, messageText = "pinned", dateTime = baseTime, isPinned = true)
		val otherMsg = PrimaryInMessage(id = 43L, messageText = "other", dateTime = baseTime.plusSeconds(1))
		val state = buildStateWithMessages(pinnedMsg, otherMsg)

		val afterUnpin = applyPinnedFlagToState(state, messageId = 42L, isPinned = false)

		assertTrue("pinnedMessages should be empty", afterUnpin.pinnedMessages.isEmpty())
		assertFalse("message in timeline should have isPinned=false",
			flattenMessages(afterUnpin.messages).first { it.id == 42L }.isPinned)
	}

	@Test
	fun `optimistic unpin rollback restores pinnedMessages on error`() {
		val pinnedMsg = PrimaryInMessage(id = 55L, messageText = "restore", dateTime = baseTime, isPinned = true)
		val state = buildStateWithMessages(pinnedMsg)

		val afterUnpin = applyPinnedFlagToState(state, messageId = 55L, isPinned = false)
		val afterRollback = applyPinnedFlagToState(afterUnpin, messageId = 55L, isPinned = true)

		assertEquals(1, afterRollback.pinnedMessages.size)
		assertTrue(afterRollback.pinnedMessages.first().isPinned)
	}

	@Test
	fun `optimistic pin message appears in pinnedMessages immediately`() {
		val msg = PrimaryInMessage(id = 77L, messageText = "to pin", dateTime = baseTime, isPinned = false)
		val state = buildStateWithMessages(msg)

		val afterPin = applyPinnedFlagToState(state, messageId = 77L, isPinned = true)

		assertEquals(1, afterPin.pinnedMessages.size)
		assertEquals(77L, afterPin.pinnedMessages.first().id)
	}

	@Test
	fun `optimistic pin rollback message removed from pinnedMessages on error`() {
		val msg = PrimaryInMessage(id = 88L, messageText = "rollback", dateTime = baseTime, isPinned = false)
		val state = buildStateWithMessages(msg)

		val afterPin = applyPinnedFlagToState(state, messageId = 88L, isPinned = true)
		val afterRollback = applyPinnedFlagToState(afterPin, messageId = 88L, isPinned = false)

		assertTrue(afterRollback.pinnedMessages.isEmpty())
		assertFalse(flattenMessages(afterRollback.messages).first { it.id == 88L }.isPinned)
	}

	// ─── Multiple pinned messages ─────────────────────────────────────────────

	@Test
	fun `unpinning one does not affect other pinned messages`() {
		val msg1 = PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime, isPinned = true)
		val msg2 = PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(1), isPinned = true)
		val msg3 = PrimaryInMessage(id = 3L, messageText = "c", dateTime = baseTime.plusSeconds(2), isPinned = true)
		val state = buildStateWithMessages(msg1, msg2, msg3)

		val result = applyPinnedFlagToState(state, messageId = 2L, isPinned = false)

		assertEquals(2, result.pinnedMessages.size)
		assertTrue(result.pinnedMessages.any { it.id == 1L && it.isPinned })
		assertTrue(result.pinnedMessages.any { it.id == 3L && it.isPinned })
		assertFalse(result.pinnedMessages.any { it.id == 2L })
	}

	// ─── extractPinnedMessages ────────────────────────────────────────────────

	@Test
	fun `extractPinnedMessages returns only pinned messages`() {
		val groups = listOf(
			DatedChatMessages(
				datetime = LocalDate.of(2026, 3, 1),
				messages = listOf(
					PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime, isPinned = false),
					PrimaryInMessage(id = 2L, messageText = "b", dateTime = baseTime.plusSeconds(1), isPinned = true),
					PrimaryInMessage(id = 3L, messageText = "c", dateTime = baseTime.plusSeconds(2), isPinned = true),
				)
			)
		)

		val result = extractPinnedMessages(groups)

		assertEquals(2, result.size)
		assertTrue(result.all { it.isPinned })
		assertTrue(result.any { it.id == 2L })
		assertTrue(result.any { it.id == 3L })
	}

	@Test
	fun `extractPinnedMessages returns empty for null groups`() {
		assertTrue(extractPinnedMessages(null).isEmpty())
	}

	@Test
	fun `extractPinnedMessages returns empty when no pinned messages`() {
		val groups = listOf(
			DatedChatMessages(
				datetime = LocalDate.of(2026, 3, 1),
				messages = listOf(
					PrimaryInMessage(id = 1L, messageText = "a", dateTime = baseTime, isPinned = false),
				)
			)
		)
		assertTrue(extractPinnedMessages(groups).isEmpty())
	}
}
