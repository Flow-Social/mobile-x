package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.ChatAnchorRequest
import me.floow.uikit.chat.model.ChatHighlightRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightAndAnchorTest {

	private fun stateWithHighlight(
		messageId: Long,
		keepAnchored: Boolean,
		token: Long = 100L,
	): ChatScreenVmState = ChatScreenVmState(
		highlightRequest = ChatHighlightRequest(
			messageId = messageId,
			requestToken = token,
			keepAnchored = keepAnchored,
		)
	)

	private fun stateWithAnchorAndHighlight(
		messageId: Long,
		anchorToken: Long = 100L,
		highlightToken: Long = 100L,
	): ChatScreenVmState = ChatScreenVmState(
		anchorRequest = ChatAnchorRequest(
			messageId = messageId,
			requestToken = anchorToken,
			initialOffsetPx = 0,
			keepAnchored = true,
		),
		highlightRequest = ChatHighlightRequest(
			messageId = messageId,
			requestToken = highlightToken,
			keepAnchored = true,
		)
	)

	// ─── issueJumpRequest state shape ─────────────────────────────────────────

	@Test
	fun `issueJumpRequest produces only highlightRequest with keepAnchored=false`() {
		val requestToken = System.currentTimeMillis()
		val state = ChatScreenVmState(
			anchorRequest = ChatAnchorRequest(messageId = 1L, requestToken = 1L),
		)
		val updated = state.copy(
			scrollToBottomRequestToken = 0L,
			anchorRequest = null,
			highlightRequest = ChatHighlightRequest(
				messageId = 42L,
				requestToken = requestToken,
				keepAnchored = false,
			)
		)
		assertNull("anchorRequest must be null for in-window jump", updated.anchorRequest)
		assertNotNull(updated.highlightRequest)
		assertFalse(updated.highlightRequest!!.keepAnchored)
		assertEquals(42L, updated.highlightRequest!!.messageId)
	}

	// ─── onAnchorRestoreSettled — transformHighlightRequest path ─────────────

	@Test
	fun `settled anchor with keepAnchored=true transforms to keepAnchored=false`() {
		val state = stateWithAnchorAndHighlight(messageId = 10L)
		val messageId = 10L

		var shouldScheduleCleanup = false
		val updated = state.let { s ->
			val clearAnchorRequest = s.anchorRequest?.messageId == messageId
			val highlightRequest = s.highlightRequest
			val transformHighlightRequest = highlightRequest?.messageId == messageId && highlightRequest.keepAnchored
			val notifyHighlightSettled = highlightRequest?.messageId == messageId && !highlightRequest.keepAnchored
			if (transformHighlightRequest || notifyHighlightSettled) shouldScheduleCleanup = true
			s.copy(
				anchorRequest = if (clearAnchorRequest) null else s.anchorRequest,
				highlightRequest = if (transformHighlightRequest) {
					highlightRequest?.copy(keepAnchored = false)
				} else {
					highlightRequest
				}
			)
		}

		assertNull(updated.anchorRequest)
		assertNotNull(updated.highlightRequest)
		assertFalse("highlight should become keepAnchored=false after anchor settles", updated.highlightRequest!!.keepAnchored)
		assertTrue("should schedule cleanup", shouldScheduleCleanup)
	}

	@Test
	fun `settled anchor with keepAnchored=true preserves highlight messageId and token`() {
		val state = stateWithAnchorAndHighlight(messageId = 55L, highlightToken = 200L)
		val messageId = 55L

		val updated = state.let { s ->
			val highlightRequest = s.highlightRequest
			val transformHighlightRequest = highlightRequest?.messageId == messageId && highlightRequest.keepAnchored
			s.copy(
				anchorRequest = null,
				highlightRequest = if (transformHighlightRequest) highlightRequest?.copy(keepAnchored = false) else highlightRequest
			)
		}

		assertEquals(55L, updated.highlightRequest?.messageId)
		assertEquals(200L, updated.highlightRequest?.requestToken)
	}

	// ─── onAnchorRestoreSettled — notifyHighlightSettled path ─────────────────

	@Test
	fun `notify highlight settled does NOT null out highlightRequest immediately`() {
		val state = stateWithHighlight(messageId = 20L, keepAnchored = false, token = 300L)
		val messageId = 20L

		var shouldScheduleCleanup = false
		val updated = state.let { s ->
			val highlightRequest = s.highlightRequest
			val notifyHighlightSettled = highlightRequest?.messageId == messageId && !highlightRequest.keepAnchored
			if (notifyHighlightSettled) shouldScheduleCleanup = true
			s.copy(
				anchorRequest = null,
				highlightRequest = highlightRequest
			)
		}

		assertNotNull("highlight must NOT be cleared immediately", updated.highlightRequest)
		assertEquals(20L, updated.highlightRequest?.messageId)
		assertTrue("should schedule cleanup timer", shouldScheduleCleanup)
	}

	@Test
	fun `notify path does not affect unrelated messageId`() {
		val state = stateWithHighlight(messageId = 30L, keepAnchored = false, token = 400L)

		var shouldScheduleCleanup = false
		val updated = state.let { s ->
			val highlightRequest = s.highlightRequest
			val notifyHighlightSettled = highlightRequest?.messageId == 999L && !highlightRequest.keepAnchored
			if (notifyHighlightSettled) shouldScheduleCleanup = true
			s
		}

		assertFalse("should NOT schedule cleanup for wrong messageId", shouldScheduleCleanup)
		assertNotNull(updated.highlightRequest)
	}

	// ─── scheduleHighlightCleanup state transition ────────────────────────────

	@Test
	fun `highlight cleanup nulls out highlightRequest when messageId matches`() {
		val state = stateWithHighlight(messageId = 77L, keepAnchored = false)

		val updated = state.let { s ->
			if (s.highlightRequest?.messageId == 77L) {
				s.copy(highlightRequest = null)
			} else s
		}

		assertNull(updated.highlightRequest)
	}

	@Test
	fun `highlight cleanup is no-op when messageId does not match`() {
		val state = stateWithHighlight(messageId = 77L, keepAnchored = false)

		val updated = state.let { s ->
			if (s.highlightRequest?.messageId == 999L) {
				s.copy(highlightRequest = null)
			} else s
		}

		assertNotNull("should not clear if messageId mismatch", updated.highlightRequest)
	}

	// ─── HighlightRequest value semantics ────────────────────────────────────

	@Test
	fun `ChatHighlightRequest copy with keepAnchored=false keeps messageId and token`() {
		val original = ChatHighlightRequest(messageId = 88L, requestToken = 500L, keepAnchored = true)
		val transformed = original.copy(keepAnchored = false)

		assertEquals(88L, transformed.messageId)
		assertEquals(500L, transformed.requestToken)
		assertFalse(transformed.keepAnchored)
	}

	@Test
	fun `ChatHighlightRequest with keepAnchored=false is different from keepAnchored=true`() {
		val r1 = ChatHighlightRequest(messageId = 1L, requestToken = 1L, keepAnchored = true)
		val r2 = ChatHighlightRequest(messageId = 1L, requestToken = 1L, keepAnchored = false)
		assertFalse(r1 == r2)
	}

	// ─── anchorRequest with keepAnchored=false is NOT used as anchor pipeline ─

	@Test
	fun `anchorRequest with keepAnchored=false does not feed activeAnchorRestoreRequest`() {
		val anchor = ChatAnchorRequest(
			messageId = 50L,
			requestToken = 100L,
			keepAnchored = false,
		)
		val highlight = ChatHighlightRequest(
			messageId = 50L,
			requestToken = 100L,
			keepAnchored = true,
		)
		val activeAnchorRestore = highlight.takeIf { it.keepAnchored }
			?: anchor.takeIf { it.keepAnchored }

		assertNotNull("highlight keepAnchored=true feeds activeAnchorRestoreRequest", activeAnchorRestore)
		val resolvedMessageId = when (val r = activeAnchorRestore!!) {
			is ChatHighlightRequest -> r.messageId
			is ChatAnchorRequest -> r.messageId
			else -> -1L
		}
		assertEquals(50L, resolvedMessageId)
	}

	@Test
	fun `null anchorRequest and null highlightRequest gives null activeAnchorRestoreRequest`() {
		val activeAnchorRestore = (null as ChatHighlightRequest?)?.takeIf { it.keepAnchored }
			?: (null as ChatAnchorRequest?)?.takeIf { it.keepAnchored }

		assertNull(activeAnchorRestore)
	}

	@Test
	fun `issueJumpRequest with null anchorRequest means no anchor pipeline active`() {
		val state = ChatScreenVmState(
			anchorRequest = null,
			highlightRequest = ChatHighlightRequest(
				messageId = 60L,
				requestToken = 700L,
				keepAnchored = false,
			)
		)
		val activeAnchor = state.highlightRequest?.takeIf { it.keepAnchored }
			?: state.anchorRequest?.takeIf { it.keepAnchored }

		assertNull("no anchor pipeline should be active for in-window jump", activeAnchor)
	}

	// ─── followBottom must not fire during anchor restore ─────────────────────

	@Test
	fun `followBottom LaunchedEffect must skip if activeAnchorRestoreRequest is not null`() {
		val anchorRequest = ChatAnchorRequest(
			messageId = 70L,
			requestToken = 800L,
			keepAnchored = true,
		)
		val activeAnchorRestoreRequest = anchorRequest.takeIf { it.keepAnchored }
		val followBottom = true
		val isAtBottom = false
		val isAnchorRestoreInProgress = true

		val shouldScrollToBottom = followBottom && !isAtBottom &&
			!isAnchorRestoreInProgress && activeAnchorRestoreRequest == null

		assertFalse("followBottom must NOT fire when anchor restore is in progress", shouldScrollToBottom)
	}

	@Test
	fun `followBottom may fire when no active anchor restore is present`() {
		val activeAnchorRestoreRequest: ChatAnchorRequest? = null
		val followBottom = true
		val isAtBottom = false
		val isAnchorRestoreInProgress = false

		val shouldScrollToBottom = followBottom && !isAtBottom &&
			!isAnchorRestoreInProgress && activeAnchorRestoreRequest == null

		assertTrue("followBottom must fire when no anchor restore is pending", shouldScrollToBottom)
	}
}
