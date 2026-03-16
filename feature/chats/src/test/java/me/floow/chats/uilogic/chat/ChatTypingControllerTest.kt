package me.floow.chats.uilogic.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatTypingControllerTest {

	private val typingChanges = mutableListOf<Pair<Boolean, String>>()
	private val fakeRepo = FakeChatsRepository()

	private fun makeController(scope: TestScope): ChatTypingController {
		return ChatTypingController(
			scope = scope,
			chatsRepository = fakeRepo,
			onIncomingTypingChanged = { isTyping, name -> typingChanges.add(isTyping to name) },
		)
	}

	@Test
	fun `onInput blank stops outgoing typing`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		controller.onInput("hello", conversationId = 1L)
		assertTrue("should start typing", controller.isOutgoingTypingActive())

		controller.onInput("", conversationId = 1L)
		assertFalse("should stop typing on blank input", controller.isOutgoingTypingActive())
		assertTrue(
			"sendTyping(false) should have been called",
			fakeRepo.typingEvents.any { (_, isTyping) -> !isTyping },
		)
	}

	@Test
	fun `onInput non-blank starts outgoing typing`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		controller.onInput("hi", conversationId = 2L)

		assertTrue(controller.isOutgoingTypingActive())
		assertEquals(1, fakeRepo.typingEvents.count { (cid, isTyping) -> cid == 2L && isTyping })
	}

	@Test
	fun `typing stops automatically after idle timeout`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		controller.onInput("typing...", conversationId = 3L)
		assertTrue(controller.isOutgoingTypingActive())

		advanceTimeBy(1_201L)
		assertFalse("typing should stop after 1200ms idle", controller.isOutgoingTypingActive())
	}

	@Test
	fun `applyIncomingTyping true then fires callback with displayName`() =
		runTest(UnconfinedTestDispatcher()) {
			val controller = makeController(this)
			controller.applyIncomingTyping(isTyping = true, ttlMs = 3_000L, displayName = "Alice")

			assertEquals(1, typingChanges.size)
			assertEquals(true to "Alice", typingChanges.first())
		}

	@Test
	fun `applyIncomingTyping false immediately clears`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		controller.applyIncomingTyping(isTyping = true, ttlMs = 3_000L, displayName = "Bob")
		controller.applyIncomingTyping(isTyping = false, ttlMs = 0L, displayName = "Bob")

		val lastChange = typingChanges.last()
		assertEquals(false to "Bob", lastChange)
	}

	@Test
	fun `applyIncomingTyping true clears after ttl`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		controller.applyIncomingTyping(isTyping = true, ttlMs = 2_000L, displayName = "Charlie")

		advanceTimeBy(2_001L)

		val clearEvent = typingChanges.lastOrNull()
		assertEquals(false to "Charlie", clearEvent)
	}

	@Test
	fun `reset stops active outgoing typing job`() = runTest(UnconfinedTestDispatcher()) {
		val controller = makeController(this)
		controller.onInput("text", conversationId = 5L)
		assertTrue(controller.isOutgoingTypingActive())

		controller.reset()
		assertFalse(controller.isOutgoingTypingActive())
	}
}
