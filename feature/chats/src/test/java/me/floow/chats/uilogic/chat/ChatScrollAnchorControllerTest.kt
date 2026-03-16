package me.floow.chats.uilogic.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.floow.domain.data.repos.DirectChatViewportSnapshot
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatScrollAnchorControllerTest {

	private fun makeController(scope: TestScope): ChatScrollAnchorController {
		return ChatScrollAnchorController(
			scope = scope,
			readCursorStore = FakeDirectMessagesReadCursorStore(),
		)
	}

	@Test
	fun `resolveDurableAnchor returns explicit id when provided`() = runTest {
		val controller = makeController(this)
		controller.remember(messageId = 10L, offsetPx = 50, isBottomPinned = false)

		val anchor = controller.resolveDurableAnchor(explicitMessageId = 99L)

		assertNotNull(anchor)
		assertEquals(99L, anchor!!.messageId)
		assertEquals(0, anchor.offsetPx)
		assertFalse(anchor.isBottomPinned)
	}

	@Test
	fun `resolveDurableAnchor returns stored anchor when no explicit id`() = runTest {
		val controller = makeController(this)
		controller.remember(messageId = 42L, offsetPx = 120, isBottomPinned = false)

		val anchor = controller.resolveDurableAnchor(explicitMessageId = null)

		assertNotNull(anchor)
		assertEquals(42L, anchor!!.messageId)
		assertEquals(120, anchor.offsetPx)
	}

	@Test
	fun `resolveDurableAnchor returns null when anchor is zero`() = runTest {
		val controller = makeController(this)

		assertNull(controller.resolveDurableAnchor())
	}

	@Test
	fun `remember ignores non-positive message ids`() = runTest {
		val controller = makeController(this)
		controller.remember(messageId = 0L)
		controller.remember(messageId = -5L)

		assertNull(controller.resolveDurableAnchor())
	}

	@Test
	fun `remember clamps offsetPx to non-negative`() = runTest {
		val controller = makeController(this)
		controller.remember(messageId = 7L, offsetPx = -100)

		val anchor = controller.resolveDurableAnchor()
		assertEquals(0, anchor!!.offsetPx)
	}

	@Test
	fun `restoreFrom loads snapshot values`() = runTest {
		val controller = makeController(this)
		val snapshot = DirectChatViewportSnapshot(
			anchorMessageId = 55L,
			anchorOffsetPx = 300,
			isBottomPinned = true,
		)
		controller.restoreFrom(snapshot)

		assertEquals(55L, controller.currentAnchorMessageId)
		assertEquals(300, controller.currentAnchorOffsetPx)
		assertTrue(controller.currentAnchorBottomPinned)
	}

	@Test
	fun `updateViewportSnapshot stores index and offset`() = runTest {
		val controller = makeController(this)
		controller.updateViewportSnapshot(itemIndex = 5, itemScrollOffsetPx = 200)

		assertEquals(5, controller.lastViewportItemIndex)
		assertEquals(200, controller.lastViewportItemScrollOffsetPx)
		assertTrue(controller.hasLastViewportSnapshot)
	}

	@Test
	fun `cancelPersist returns pending values and clears them`() = runTest {
		val controller = makeController(this)
		controller.enqueuePersist(messageId = 77L, offsetPx = 50, isBottomPinned = false)

		val (mid, offset, bottomPinned) = controller.cancelPersist()

		assertEquals(77L, mid)
		assertEquals(50, offset)
		assertEquals(false, bottomPinned)

		val (mid2, _, _) = controller.cancelPersist()
		assertNull(mid2)
	}

	@Test
	fun `reset clears all state`() = runTest {
		val controller = makeController(this)
		controller.remember(messageId = 10L, offsetPx = 50, isBottomPinned = true)
		controller.updateViewportSnapshot(itemIndex = 3, itemScrollOffsetPx = 100)

		controller.reset()

		assertEquals(0L, controller.currentAnchorMessageId)
		assertEquals(0, controller.lastViewportItemIndex)
		assertFalse(controller.hasLastViewportSnapshot)
		assertNull(controller.resolveDurableAnchor())
	}
}

internal class FakeDirectMessagesReadCursorStore : DirectMessagesReadCursorStore {
	val snapshots = mutableMapOf<Long, DirectChatViewportSnapshot>()

	override suspend fun getLocalLastReadMessageId(conversationId: Long): Long = 0L
	override suspend fun setLocalLastReadMessageId(conversationId: Long, messageId: Long) = Unit
	override suspend fun getOpenAnchorSeq(conversationId: Long): Long = 0L
	override suspend fun setOpenAnchorSeq(conversationId: Long, seq: Long) = Unit
	override suspend fun getOpenAnchorMessageId(conversationId: Long): Long =
		snapshots[conversationId]?.anchorMessageId ?: 0L
	override suspend fun setOpenAnchorMessageId(conversationId: Long, messageId: Long) {
		val current = snapshots[conversationId] ?: DirectChatViewportSnapshot(null, 0, false)
		snapshots[conversationId] = current.copy(anchorMessageId = messageId.takeIf { it > 0L })
	}
	override suspend fun getOpenAnchorOffsetPx(conversationId: Long): Int =
		snapshots[conversationId]?.anchorOffsetPx ?: 0
	override suspend fun setOpenAnchorOffsetPx(conversationId: Long, offsetPx: Int) {
		val current = snapshots[conversationId] ?: DirectChatViewportSnapshot(null, 0, false)
		snapshots[conversationId] = current.copy(anchorOffsetPx = offsetPx)
	}
	override suspend fun isOpenAnchorBottomPinned(conversationId: Long): Boolean =
		snapshots[conversationId]?.isBottomPinned ?: false
	override suspend fun setOpenAnchorBottomPinned(conversationId: Long, isBottomPinned: Boolean) {
		val current = snapshots[conversationId] ?: DirectChatViewportSnapshot(null, 0, false)
		snapshots[conversationId] = current.copy(isBottomPinned = isBottomPinned)
	}
	override suspend fun enqueueReadUpTo(conversationId: Long, messageId: Long) = Unit
	override suspend fun getPendingReadUpTo(conversationId: Long): Long = 0L
	override suspend fun markPendingReadUpToApplied(conversationId: Long, appliedMessageId: Long) = Unit
	override suspend fun getPendingConversationIds(limit: Int): List<Long> = emptyList()
	override suspend fun getPendingEnqueuedAtMillis(conversationId: Long): Long = 0L
	override suspend fun incrementPendingRetryCount(conversationId: Long): Int = 0
	override suspend fun resetPendingRetryCount(conversationId: Long) = Unit
}
