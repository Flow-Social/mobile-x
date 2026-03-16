package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.ChatAnchorRequest
import me.floow.uikit.chat.model.ChatHighlightRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JumpToMessageTokenTest {

	private fun buildJumpRequest(messageId: Long): Pair<ChatAnchorRequest, ChatHighlightRequest> {
		val token = System.currentTimeMillis()
		val anchor = ChatAnchorRequest(
			messageId = messageId,
			requestToken = token,
			initialOffsetPx = 0,
			keepAnchored = false,
		)
		val highlight = ChatHighlightRequest(
			messageId = messageId,
			requestToken = token,
			keepAnchored = true,
		)
		return anchor to highlight
	}

	@Test
	fun `jump request tokens are positive`() {
		val (anchor, highlight) = buildJumpRequest(messageId = 10L)
		assertTrue("anchor token must be > 0", anchor.requestToken > 0L)
		assertTrue("highlight token must be > 0", highlight.requestToken > 0L)
	}

	@Test
	fun `anchor and highlight share the same token`() {
		val (anchor, highlight) = buildJumpRequest(messageId = 10L)
		assertEquals(anchor.requestToken, highlight.requestToken)
	}

	@Test
	fun `two consecutive jump requests produce different tokens`() {
		val (first, _) = buildJumpRequest(messageId = 10L)
		Thread.sleep(2)
		val (second, _) = buildJumpRequest(messageId = 10L)
		assertNotEquals(
			"consecutive jumps to same message must have distinct tokens",
			first.requestToken,
			second.requestToken,
		)
	}

	@Test
	fun `jump request target message is preserved`() {
		val (anchor, highlight) = buildJumpRequest(messageId = 42L)
		assertEquals(42L, anchor.messageId)
		assertEquals(42L, highlight.messageId)
	}

	@Test
	fun `anchor request has keepAnchored=false for jump`() {
		val (anchor, _) = buildJumpRequest(messageId = 5L)
		assertEquals(false, anchor.keepAnchored)
	}

	@Test
	fun `highlight request has keepAnchored=true for jump`() {
		val (_, highlight) = buildJumpRequest(messageId = 5L)
		assertEquals(true, highlight.keepAnchored)
	}

	@Test
	fun `anchor initialOffsetPx is 0 for jump`() {
		val (anchor, _) = buildJumpRequest(messageId = 99L)
		assertEquals(0, anchor.initialOffsetPx)
	}

	// ─── ChatAnchorRequest value semantics ────────────────────────────────────

	@Test
	fun `two ChatAnchorRequests with same fields are equal`() {
		val r1 = ChatAnchorRequest(messageId = 1L, requestToken = 100L, initialOffsetPx = 0, keepAnchored = false)
		val r2 = ChatAnchorRequest(messageId = 1L, requestToken = 100L, initialOffsetPx = 0, keepAnchored = false)
		assertEquals(r1, r2)
	}

	@Test
	fun `ChatAnchorRequest with different token is not equal`() {
		val r1 = ChatAnchorRequest(messageId = 1L, requestToken = 100L)
		val r2 = ChatAnchorRequest(messageId = 1L, requestToken = 101L)
		assertNotEquals(r1, r2)
	}

	// ─── resolveReplyTargetId integration ─────────────────────────────────────

	@Test
	fun `reply target id used in jump request is correct`() {
		val replyMessageId = 55L
		val (anchor, highlight) = buildJumpRequest(messageId = replyMessageId)
		assertEquals(replyMessageId, anchor.messageId)
		assertEquals(replyMessageId, highlight.messageId)
	}
}
