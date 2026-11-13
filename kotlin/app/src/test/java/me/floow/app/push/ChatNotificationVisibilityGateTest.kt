package me.floow.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatNotificationVisibilityGateTest {

	private val store = ForegroundVisibleChatStore()
	private val gate = ChatNotificationVisibilityGate(
		foregroundVisibleChatStore = store,
		selfUserIdProvider = { "self" }
	)

	@Test
	fun `suppresses only when same conversation is visible in foreground`() {
		store.onAppForeground()
		store.setVisibleChat(conversationId = 42L, interlocutorId = "peer-1")

		val decision = gate.evaluate(testPayload(conversationId = 42L, senderId = "peer-1"))

		assertTrue(decision.shouldSuppress)
		assertEquals("visible_foreground_conversation", decision.reason)
	}

	@Test
	fun `does not suppress same conversation when app is background`() {
		store.onAppForeground()
		store.setVisibleChat(conversationId = 42L, interlocutorId = "peer-1")
		store.onAppBackground()

		val decision = gate.evaluate(testPayload(conversationId = 42L, senderId = "peer-1"))

		assertFalse(decision.shouldSuppress)
		assertEquals("app_background", decision.reason)
	}

	@Test
	fun `does not suppress different conversation in foreground`() {
		store.onAppForeground()
		store.setVisibleChat(conversationId = 42L, interlocutorId = "peer-1")

		val decision = gate.evaluate(testPayload(conversationId = 99L, senderId = "peer-1"))

		assertFalse(decision.shouldSuppress)
		assertEquals("no_visible_chat_match", decision.reason)
	}

	@Test
	fun `suppresses self messages regardless of visibility`() {
		val decision = gate.evaluate(testPayload(conversationId = 42L, senderId = "self"))

		assertTrue(decision.shouldSuppress)
		assertEquals("self_message", decision.reason)
	}

	@Test
	fun `background transition clears stale visible chat state`() {
		store.onAppForeground()
		store.setVisibleChat(conversationId = 42L, interlocutorId = "peer-1")
		store.onAppBackground()
		store.onAppForeground()

		val decision = gate.evaluate(testPayload(conversationId = 42L, senderId = "peer-1"))

		assertFalse(decision.shouldSuppress)
		assertEquals("no_visible_chat_match", decision.reason)
	}

	private fun testPayload(
		conversationId: Long,
		senderId: String
	): ChatNotificationPayload {
		return ChatNotificationPayload(
			type = "chat_message",
			notificationId = "chat:$conversationId:1",
			conversationId = conversationId,
			messageId = 1L,
			senderId = senderId,
			senderName = "Peer",
			senderAvatarUrl = null,
			messageText = "hello",
			messageTimestampMs = 1L,
			isGroup = false,
			conversationTitle = null,
			isFallback = false,
			source = ChatNotificationSource.WS
		)
	}
}
