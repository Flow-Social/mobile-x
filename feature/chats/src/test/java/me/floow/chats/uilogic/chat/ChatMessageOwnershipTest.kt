package me.floow.chats.uilogic.chat

import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatPeer
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageOwnershipTest {

	@Test
	fun `saved messages maps self sender to outgoing when peer equals self`() {
		val message = buildMessage(senderId = "self_1")

		val ui = message.toUiMessage(
			peerUserId = "self_1",
			selfUserId = "self_1"
		)

		assertTrue(ui is PrimaryOutMessage)
	}

	@Test
	fun `direct messages maps peer sender to incoming when self is known`() {
		val message = buildMessage(senderId = "peer_1")

		val ui = message.toUiMessage(
			peerUserId = "peer_1",
			selfUserId = "self_1"
		)

		assertTrue(ui is PrimaryInMessage)
	}

	@Test
	fun `fallback keeps peer-based behavior when self id is unavailable`() {
		val message = buildMessage(senderId = "peer_1")

		val ui = message.toUiMessage(
			peerUserId = "peer_1",
			selfUserId = null
		)

		assertTrue(ui is PrimaryInMessage)
	}

	private fun buildMessage(senderId: String): DirectChatMessage {
		return DirectChatMessage(
			id = 11L,
			conversationId = 5L,
			sender = DirectChatPeer(
				id = senderId,
				username = null,
				name = null,
				avatarUrl = null
			),
			text = "hello",
			replyToMessageId = null,
			replyToMessageText = null,
			isPinned = false,
			pinnedAt = null,
			pinnedByUserId = null,
			createdAt = 1_700_000_000_000L,
			updatedAt = 1_700_000_000_000L
		)
	}
}
