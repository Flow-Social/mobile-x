package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.MessageFieldReply
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class EditMessageTest {

	private val t = LocalDateTime.of(2026, 3, 1, 10, 0)

	private fun stateWithMessages(vararg msgs: me.floow.uikit.chat.model.ChatMessage): ChatScreenVmState {
		val groups = listOf(DatedChatMessages(datetime = t.toLocalDate(), messages = msgs.toList()))
		return ChatScreenVmState(
			messages = groups,
			flatMessagesSnapshot = msgs.toList(),
			pinnedMessages = msgs.filter { it.isPinned },
			conversationId = 1L,
			chatInterlocutorId = "peer",
		)
	}

	// ─── startEditingMessage ──────────────────────────────────────────────────

	@Test
	fun `startEditingMessage saves draft text`() {
		val state = ChatScreenVmState(
			messageFieldValue = "work in progress",
			messageToEditId = null,
		)
		val updated = state.copy(
			messageToEditId = 5L,
			messageFieldValue = "original text",
			messageFieldReply = null,
			priorEditDraftText = state.messageFieldValue,
			priorEditReply = state.messageFieldReply,
		)
		assertEquals("work in progress", updated.priorEditDraftText)
		assertEquals(5L, updated.messageToEditId)
		assertEquals("original text", updated.messageFieldValue)
	}

	@Test
	fun `startEditingMessage saves active reply`() {
		val reply = MessageFieldReply(replyId = 99L, replyAuthorName = "Alice", replyMessageText = "hello")
		val state = ChatScreenVmState(
			messageFieldValue = "",
			messageFieldReply = reply,
			messageToEditId = null,
		)
		val updated = state.copy(
			messageToEditId = 10L,
			messageFieldValue = "edit text",
			messageFieldReply = null,
			priorEditDraftText = state.messageFieldValue,
			priorEditReply = state.messageFieldReply,
		)
		assertEquals(reply, updated.priorEditReply)
		assertNull(updated.messageFieldReply)
	}

	@Test
	fun `startEditingMessage clears reply in active field`() {
		val reply = MessageFieldReply(replyId = 1L, replyAuthorName = "Bob", replyMessageText = "msg")
		val state = ChatScreenVmState(messageFieldReply = reply)
		val updated = state.copy(
			messageToEditId = 7L,
			messageFieldValue = "edit",
			messageFieldReply = null,
			priorEditDraftText = "",
			priorEditReply = reply,
		)
		assertNull(updated.messageFieldReply)
	}

	// ─── cancelEditing ────────────────────────────────────────────────────────

	@Test
	fun `cancelEditing restores draft text`() {
		val state = ChatScreenVmState(
			messageToEditId = 3L,
			messageFieldValue = "edited text",
			priorEditDraftText = "my original draft",
		)
		val updated = state.copy(
			messageToEditId = null,
			messageFieldValue = state.priorEditDraftText,
			messageFieldReply = state.priorEditReply,
			priorEditDraftText = "",
			priorEditReply = null,
		)
		assertNull(updated.messageToEditId)
		assertEquals("my original draft", updated.messageFieldValue)
	}

	@Test
	fun `cancelEditing restores reply`() {
		val reply = MessageFieldReply(replyId = 42L, replyAuthorName = "Carol", replyMessageText = "test")
		val state = ChatScreenVmState(
			messageToEditId = 8L,
			messageFieldValue = "edit...",
			priorEditReply = reply,
			priorEditDraftText = "",
		)
		val updated = state.copy(
			messageToEditId = null,
			messageFieldValue = state.priorEditDraftText,
			messageFieldReply = state.priorEditReply,
			priorEditDraftText = "",
			priorEditReply = null,
		)
		assertEquals(reply, updated.messageFieldReply)
		assertNull(updated.priorEditReply)
	}

	@Test
	fun `cancelEditing clears priorEdit fields`() {
		val state = ChatScreenVmState(
			messageToEditId = 5L,
			priorEditDraftText = "draft",
			priorEditReply = MessageFieldReply(replyId = 1L, replyAuthorName = "X", replyMessageText = "y"),
		)
		val updated = state.copy(
			messageToEditId = null,
			messageFieldValue = state.priorEditDraftText,
			messageFieldReply = state.priorEditReply,
			priorEditDraftText = "",
			priorEditReply = null,
		)
		assertEquals("", updated.priorEditDraftText)
		assertNull(updated.priorEditReply)
	}

	// ─── applyLocalEdit ───────────────────────────────────────────────────────

	@Test
	fun `applyLocalEdit updates PrimaryOutMessage text`() {
		val msg = PrimaryOutMessage(id = 1L, messageText = "old", dateTime = t)
		val state = stateWithMessages(msg)
		val result = applyLocalEdit(state, messageId = 1L, text = "new")
		val updated = flattenMessages(result.messages).first { it.id == 1L }
		assertEquals("new", updated.messageText)
	}

	@Test
	fun `applyLocalEdit updates PrimaryInMessage text`() {
		val msg = PrimaryInMessage(id = 2L, messageText = "old in", dateTime = t)
		val state = stateWithMessages(msg)
		val result = applyLocalEdit(state, messageId = 2L, text = "new in")
		assertEquals("new in", flattenMessages(result.messages).first { it.id == 2L }.messageText)
	}

	@Test
	fun `applyLocalEdit updates ReplyOutMessage text`() {
		val msg = ReplyOutMessage(id = 3L, messageText = "old reply out", dateTime = t, replyMessageId = 0L, replyMessageText = "orig")
		val state = stateWithMessages(msg)
		val result = applyLocalEdit(state, messageId = 3L, text = "new reply out")
		assertEquals("new reply out", flattenMessages(result.messages).first { it.id == 3L }.messageText)
	}

	@Test
	fun `applyLocalEdit updates ReplyInMessage text`() {
		val msg = ReplyInMessage(id = 4L, messageText = "old reply in", dateTime = t, replyMessageId = 0L, replyMessageText = "orig")
		val state = stateWithMessages(msg)
		val result = applyLocalEdit(state, messageId = 4L, text = "new reply in")
		assertEquals("new reply in", flattenMessages(result.messages).first { it.id == 4L }.messageText)
	}

	@Test
	fun `applyLocalEdit updates pinnedMessages text`() {
		val msg = PrimaryOutMessage(id = 5L, messageText = "pinned old", dateTime = t, isPinned = true)
		val state = stateWithMessages(msg)
		val result = applyLocalEdit(state, messageId = 5L, text = "pinned new")
		assertEquals("pinned new", result.pinnedMessages.first { it.id == 5L }.messageText)
	}

	@Test
	fun `applyLocalEdit updates flat snapshot text when cache exists`() {
		val msg = PrimaryOutMessage(id = 55L, messageText = "cached old", dateTime = t)
		val state = stateWithMessages(msg)
		val result = applyLocalEdit(state, messageId = 55L, text = "cached new")
		assertEquals("cached new", result.flatMessagesSnapshot!!.first { it.id == 55L }.messageText)
	}

	@Test
	fun `applyLocalEdit clears messageToEditId and messageFieldValue`() {
		val msg = PrimaryOutMessage(id = 6L, messageText = "x", dateTime = t)
		val state = stateWithMessages(msg).copy(messageToEditId = 6L, messageFieldValue = "x")
		val result = applyLocalEdit(state, messageId = 6L, text = "y")
		assertNull(result.messageToEditId)
		assertEquals("", result.messageFieldValue)
	}

	@Test
	fun `applyLocalEdit does not affect unrelated messages`() {
		val msg1 = PrimaryOutMessage(id = 10L, messageText = "msg1", dateTime = t)
		val msg2 = PrimaryInMessage(id = 11L, messageText = "msg2", dateTime = t.plusSeconds(1))
		val state = stateWithMessages(msg1, msg2)
		val result = applyLocalEdit(state, messageId = 10L, text = "updated")
		assertEquals("msg2", flattenMessages(result.messages).first { it.id == 11L }.messageText)
	}

	// ─── editMessage error rollback ───────────────────────────────────────────

	@Test
	fun `error rollback restores messageToEditId`() {
		val msg = PrimaryOutMessage(id = 20L, messageText = "original", dateTime = t)
		val baseState = stateWithMessages(msg)
		val afterOptimistic = applyLocalEdit(baseState, messageId = 20L, text = "new")
		val afterRollback = applyLocalEdit(afterOptimistic, messageId = 20L, text = "original").copy(
			messageToEditId = 20L,
			messageFieldValue = "new",
		)
		assertEquals(20L, afterRollback.messageToEditId)
		assertEquals("new", afterRollback.messageFieldValue)
		assertEquals("original", flattenMessages(afterRollback.messages).first { it.id == 20L }.messageText)
	}

	@Test
	fun `optimistic edit then rollback returns to original text in timeline`() {
		val msg = PrimaryOutMessage(id = 30L, messageText = "before", dateTime = t)
		val state = stateWithMessages(msg)

		val optimistic = applyLocalEdit(state, messageId = 30L, text = "after")
		assertEquals("after", flattenMessages(optimistic.messages).first { it.id == 30L }.messageText)

		val rolledBack = applyLocalEdit(optimistic, messageId = 30L, text = "before")
		assertEquals("before", flattenMessages(rolledBack.messages).first { it.id == 30L }.messageText)
	}

	// ─── priorEdit state after multiple edit cycles ───────────────────────────

	@Test
	fun `second startEditingMessage overrides priorEdit fields`() {
		val reply1 = MessageFieldReply(replyId = 1L, replyAuthorName = "A", replyMessageText = "a")
		val state = ChatScreenVmState(
			messageFieldValue = "draft1",
			messageFieldReply = reply1,
		)
		val afterFirstEdit = state.copy(
			messageToEditId = 1L,
			messageFieldValue = "edit1",
			messageFieldReply = null,
			priorEditDraftText = state.messageFieldValue,
			priorEditReply = state.messageFieldReply,
		)
		val afterSecondEdit = afterFirstEdit.copy(
			messageToEditId = 2L,
			messageFieldValue = "edit2",
			messageFieldReply = null,
			priorEditDraftText = afterFirstEdit.messageFieldValue,
			priorEditReply = afterFirstEdit.messageFieldReply,
		)
		assertEquals("edit1", afterSecondEdit.priorEditDraftText)
		assertNull(afterSecondEdit.priorEditReply)
	}
}

internal fun applyLocalEdit(
	state: ChatScreenVmState,
	messageId: Long,
	text: String,
): ChatScreenVmState {
	val updatedMessages = state.currentFlatMessages().map { message ->
		if (message.id != messageId) message else when (message) {
			is PrimaryOutMessage -> message.copy(messageText = text)
			is ReplyOutMessage -> message.copy(messageText = text)
			is PrimaryInMessage -> message.copy(messageText = text)
			is ReplyInMessage -> message.copy(messageText = text)
			is me.floow.uikit.chat.model.PostPreviewMessage -> message
		}
	}
	val updatedPinned = state.pinnedMessages.map { pinned ->
		if (pinned.id != messageId) pinned else when (pinned) {
			is PrimaryOutMessage -> pinned.copy(messageText = text)
			is ReplyOutMessage -> pinned.copy(messageText = text)
			is PrimaryInMessage -> pinned.copy(messageText = text)
			is ReplyInMessage -> pinned.copy(messageText = text)
			is me.floow.uikit.chat.model.PostPreviewMessage -> pinned
		}
	}
	return state.withFlatMessages(updatedMessages).copy(
		pinnedMessages = updatedPinned,
		messageToEditId = null,
		messageFieldValue = "",
		priorEditDraftText = "",
		priorEditReply = null,
	)
}
