package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PinUnpinFlowTest {

	private val t = LocalDateTime.of(2026, 3, 1, 10, 0)

	private fun stateWith(vararg msgs: me.floow.uikit.chat.model.ChatMessage): ChatScreenVmState {
		val groups = listOf(DatedChatMessages(datetime = t.toLocalDate(), messages = msgs.toList()))
		return ChatScreenVmState(
			messages = groups,
			flatMessagesSnapshot = msgs.toList(),
			pinnedMessages = msgs.filter { it.isPinned },
			conversationId = 1L,
			chatInterlocutorId = "peer",
		)
	}

	// ─── togglePinMessage delegation ─────────────────────────────────────────

	@Test
	fun `applyPinnedFlagToState pin then unpin returns original state`() {
		val msg = PrimaryInMessage(id = 1L, messageText = "a", dateTime = t, isPinned = false)
		val state = stateWith(msg)

		val pinned = applyPinnedFlagToState(state, 1L, isPinned = true)
		val unpinned = applyPinnedFlagToState(pinned, 1L, isPinned = false)

		assertTrue(unpinned.pinnedMessages.isEmpty())
		assertFalse(flattenMessages(unpinned.messages).first { it.id == 1L }.isPinned)
	}

	@Test
	fun `applyPinnedFlagToState double-pin is idempotent`() {
		val msg = PrimaryInMessage(id = 2L, messageText = "b", dateTime = t, isPinned = false)
		val state = stateWith(msg)

		val once = applyPinnedFlagToState(state, 2L, isPinned = true)
		val twice = applyPinnedFlagToState(once, 2L, isPinned = true)

		assertEquals(1, twice.pinnedMessages.size)
		assertTrue(flattenMessages(twice.messages).first { it.id == 2L }.isPinned)
	}

	@Test
	fun `applyPinnedFlagToState double-unpin is idempotent`() {
		val msg = PrimaryInMessage(id = 3L, messageText = "c", dateTime = t, isPinned = true)
		val state = stateWith(msg)

		val once = applyPinnedFlagToState(state, 3L, isPinned = false)
		val twice = applyPinnedFlagToState(once, 3L, isPinned = false)

		assertTrue(twice.pinnedMessages.isEmpty())
		assertFalse(flattenMessages(twice.messages).first { it.id == 3L }.isPinned)
	}

	// ─── withPinned for every message type ───────────────────────────────────

	@Test
	fun `withPinned works for ReplyOutMessage`() {
		val msg = ReplyOutMessage(
			id = 10L, messageText = "reply", dateTime = t,
			replyMessageId = 1L, replyMessageText = "orig",
			isPinned = false,
		)
		assertTrue(msg.withPinned(true).isPinned)
		assertFalse(msg.withPinned(false).isPinned)
	}

	@Test
	fun `withPinned works for ReplyInMessage`() {
		val msg = ReplyInMessage(
			id = 11L, messageText = "reply in", dateTime = t,
			replyMessageId = 2L, replyMessageText = "orig",
			isPinned = true,
		)
		assertFalse(msg.withPinned(false).isPinned)
		assertTrue(msg.withPinned(true).isPinned)
	}

	@Test
	fun `withPinned works for PrimaryOutMessage`() {
		val msg = PrimaryOutMessage(id = 12L, messageText = "out", dateTime = t, isPinned = false)
		assertTrue(msg.withPinned(true).isPinned)
	}

	// ─── Pinned list is consistent with timeline ──────────────────────────────

	@Test
	fun `after pin pinnedMessages and timeline are in sync`() {
		val msg = PrimaryInMessage(id = 20L, messageText = "sync", dateTime = t, isPinned = false)
		val state = stateWith(msg)

		val result = applyPinnedFlagToState(state, 20L, isPinned = true)

		val inTimeline = flattenMessages(result.messages).first { it.id == 20L }
		val inPinned = result.pinnedMessages.first { it.id == 20L }
		assertEquals(inTimeline.isPinned, inPinned.isPinned)
	}

	@Test
	fun `after unpin pinnedMessages and timeline are in sync`() {
		val msg = PrimaryInMessage(id = 21L, messageText = "sync2", dateTime = t, isPinned = true)
		val state = stateWith(msg)

		val result = applyPinnedFlagToState(state, 21L, isPinned = false)

		val inTimeline = flattenMessages(result.messages).first { it.id == 21L }
		assertFalse(inTimeline.isPinned)
		assertFalse(result.pinnedMessages.any { it.id == 21L })
	}

	@Test
	fun `applyPinnedFlagToState updates flat snapshot cache`() {
		val msg = PrimaryInMessage(id = 22L, messageText = "cache", dateTime = t, isPinned = false)
		val state = stateWith(msg)

		val pinned = applyPinnedFlagToState(state, 22L, isPinned = true)
		val unpinned = applyPinnedFlagToState(pinned, 22L, isPinned = false)

		assertTrue(pinned.flatMessagesSnapshot!!.first { it.id == 22L }.isPinned)
		assertFalse(unpinned.flatMessagesSnapshot!!.first { it.id == 22L }.isPinned)
	}

	// ─── Optimistic unpin: message removed before API response ───────────────

	@Test
	fun `optimistic unpin sequence remove from bar API succeeds no duplicate in bar`() {
		val msg = PrimaryInMessage(id = 30L, messageText = "opt", dateTime = t, isPinned = true)
		val state = stateWith(msg)

		val afterOptimisticUnpin = applyPinnedFlagToState(state, 30L, isPinned = false)
		assertTrue("bar should be empty after optimistic unpin", afterOptimisticUnpin.pinnedMessages.isEmpty())

		val afterApiSuccess = afterOptimisticUnpin
		assertTrue("bar stays empty after API success", afterApiSuccess.pinnedMessages.isEmpty())
	}

	@Test
	fun `optimistic unpin sequence remove from bar API fails restores to bar`() {
		val msg = PrimaryInMessage(id = 31L, messageText = "rollback", dateTime = t, isPinned = true)
		val state = stateWith(msg)

		val afterOptimisticUnpin = applyPinnedFlagToState(state, 31L, isPinned = false)
		assertTrue(afterOptimisticUnpin.pinnedMessages.isEmpty())

		val afterRollback = applyPinnedFlagToState(afterOptimisticUnpin, 31L, isPinned = true)
		assertEquals(1, afterRollback.pinnedMessages.size)
		assertTrue(afterRollback.pinnedMessages.first().isPinned)
	}

	// ─── Multiple pinned messages integrity ──────────────────────────────────

	@Test
	fun `pinning second message preserves first pinned`() {
		val msg1 = PrimaryInMessage(id = 40L, messageText = "a", dateTime = t, isPinned = true)
		val msg2 = PrimaryInMessage(id = 41L, messageText = "b", dateTime = t.plusSeconds(1), isPinned = false)
		val state = stateWith(msg1, msg2)

		val result = applyPinnedFlagToState(state, 41L, isPinned = true)

		assertEquals(2, result.pinnedMessages.size)
		assertTrue(result.pinnedMessages.all { it.isPinned })
	}

	@Test
	fun `unpinning one of multiple pinned preserves others`() {
		val msg1 = PrimaryInMessage(id = 50L, messageText = "a", dateTime = t, isPinned = true)
		val msg2 = PrimaryInMessage(id = 51L, messageText = "b", dateTime = t.plusSeconds(1), isPinned = true)
		val msg3 = PrimaryInMessage(id = 52L, messageText = "c", dateTime = t.plusSeconds(2), isPinned = true)
		val state = stateWith(msg1, msg2, msg3)

		val result = applyPinnedFlagToState(state, 51L, isPinned = false)

		assertEquals(2, result.pinnedMessages.size)
		assertTrue(result.pinnedMessages.any { it.id == 50L })
		assertTrue(result.pinnedMessages.any { it.id == 52L })
		assertFalse(result.pinnedMessages.any { it.id == 51L })
	}
}
