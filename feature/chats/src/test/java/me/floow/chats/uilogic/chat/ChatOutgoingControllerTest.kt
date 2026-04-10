package me.floow.chats.uilogic.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import me.floow.uikit.chat.model.PrimaryOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ChatOutgoingControllerTest {

	private val restoredMessages = mutableListOf<me.floow.uikit.chat.model.ChatMessage>()
	private var clearDeletedCalled = false

	private fun makeController(scope: TestScope): ChatOutgoingController {
		return ChatOutgoingController(
			scope = scope,
			chatsRepository = FakeChatsRepository(),
			onRestoreDeleted = { msg -> restoredMessages.add(msg) },
			onClearDeletedMessage = { clearDeletedCalled = true },
		)
	}

	@Test
	fun `addCancelledClientMessageId evicts oldest when limit exceeded`() = runTest {
		val controller = makeController(this)
		val maxSize = 64
		repeat(maxSize + 1) { i ->
			controller.addCancelledClientMessageId("key_$i")
		}
		assertFalse("oldest key_0 should be evicted", controller.isCancelled("key_0"))
		assertTrue("latest key should still be present", controller.isCancelled("key_$maxSize"))
	}

	@Test
	fun `addCancelledClientMessageId clears on reset`() = runTest {
		val controller = makeController(this)
		controller.addCancelledClientMessageId("abc")
		assertTrue(controller.isCancelled("abc"))
		controller.reset()
		assertFalse(controller.isCancelled("abc"))
	}

	@Test
	fun `resolvePendingOptimisticId returns correct optimistic id`() = runTest {
		val controller = makeController(this)
		controller.pendingOutgoingClientMessageIds[-1L] = "idempotency-key-1"
		controller.pendingOutgoingClientMessageIds[-2L] = "idempotency-key-2"

		assertEquals(-1L, controller.resolvePendingOptimisticId("idempotency-key-1"))
		assertEquals(-2L, controller.resolvePendingOptimisticId("idempotency-key-2"))
		assertNull(controller.resolvePendingOptimisticId("unknown-key"))
		assertNull(controller.resolvePendingOptimisticId(null))
	}

	@Test
	fun `resolvePendingOptimisticId trims whitespace from clientMessageId`() = runTest {
		val controller = makeController(this)
		controller.pendingOutgoingClientMessageIds[-3L] = "key-abc"

		assertEquals(-3L, controller.resolvePendingOptimisticId("  key-abc  "))
	}

	@Test
	fun `isPendingOptimisticId returns true only for known ids`() = runTest {
		val controller = makeController(this)
		controller.pendingOutgoingClientMessageIds[-10L] = "key"

		assertTrue(controller.isPendingOptimisticId(-10L))
		assertFalse(controller.isPendingOptimisticId(-11L))
	}

	@Test
	fun `cancelPendingDelete restores message immediately`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		val message = buildOutMessage(id = 5L, text = "hello")

		controller.schedulePendingDeleteCommit(message, conversationId = 100L)
		val restored = controller.cancelPendingDelete()

		assertNotNull(restored)
		assertEquals(5L, restored!!.id)
		assertTrue(clearDeletedCalled)
	}

	@Test
	fun `schedulePendingDeleteCommit calls onClearDeletedMessage after timeout`() =
		runTest(UnconfinedTestDispatcher()) {
			val controller = makeController(this)
			val message = buildOutMessage(id = 7L, text = "to delete")

			controller.schedulePendingDeleteCommit(message, conversationId = null)
			assertFalse("should not clear before timeout", clearDeletedCalled)

			advanceTimeBy(4_001L)
			assertTrue("should clear after 4s timeout", clearDeletedCalled)
		}

	@Test
	fun `cancelAllPendingDeletes stops scheduled commit`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		val message = buildOutMessage(id = 8L, text = "will be cancelled")

		controller.schedulePendingDeleteCommit(message, conversationId = 99L)
		controller.cancelAllPendingDeletes()

		advanceTimeBy(5_000L)
		assertFalse("clear should NOT have been called after cancelAll", clearDeletedCalled)
	}

	@Test
	fun `flushPendingDeleteNow commits immediately without waiting timeout`() = runTest(UnconfinedTestDispatcher()) {
		val repo = FakeChatsRepository()
		val controller = ChatOutgoingController(
			scope = this,
			chatsRepository = repo,
			onRestoreDeleted = { msg -> restoredMessages.add(msg) },
			onClearDeletedMessage = { clearDeletedCalled = true },
		)
		val message = buildOutMessage(id = 9L, text = "flush")

		controller.schedulePendingDeleteCommit(message, conversationId = 321L)
		controller.flushPendingDeleteNow()

		advanceTimeBy(1L)
		assertTrue(clearDeletedCalled)
		assertTrue(repo.deletedMessageIds.contains(9L))
	}

	@Test
	fun `reset clears all pending outgoing maps`() = runTest {
		val controller = makeController(this)
		controller.pendingOutgoingClientMessageIds[-1L] = "k1"
		controller.pendingOutgoingClientMessageIds[-2L] = "k2"
		controller.addCancelledClientMessageId("c1")

		controller.reset()

		assertTrue(controller.pendingOutgoingClientMessageIds.isEmpty())
		assertFalse(controller.isCancelled("c1"))
	}

	private fun buildOutMessage(id: Long, text: String): PrimaryOutMessage {
		return PrimaryOutMessage(
			id = id,
			messageText = text,
			createdAtMillis = LocalDateTime.of(2026, 1, 1, 10, 0).toEpochMillis(),
		)
	}
}

private fun LocalDateTime.toEpochMillis(): Long {
	return toInstant(ZoneOffset.UTC).toEpochMilli()
}
