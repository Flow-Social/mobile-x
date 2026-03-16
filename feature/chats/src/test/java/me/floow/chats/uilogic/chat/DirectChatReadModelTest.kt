package me.floow.chats.uilogic.chat

import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DirectChatReadModelTest {

	@Test
	fun `from unread opens at first unread boundary`() {
		val messages = buildIncomingMessages(1L..15L)

		val projection = projectDirectChatReadModel(
			input = DirectChatReadModelInput(
				peerUserId = "42",
				messages = messages,
				serverReadUpToMessageId = 5L,
				localReadUpToMessageId = 5L,
				firstUnreadMessageId = 6L,
				openAnchorMessageId = null,
				openMode = DirectChatOpenMode.FROM_UNREAD,
				messageLinkAnchorMessageId = null
			)
		)

		assertEquals(6L, projection.openAnchorMessageId)
		assertEquals(6L, projection.unreadBoundaryMessageId)
		assertEquals(10, projection.unreadMessageIds.size)
		assertEquals(5L, projection.readUpToMessageId)
	}

	@Test
	fun `from last seen keeps stored anchor`() {
		val messages = buildIncomingMessages(1L..15L)

		val projection = projectDirectChatReadModel(
			input = DirectChatReadModelInput(
				peerUserId = "42",
				messages = messages,
				serverReadUpToMessageId = 8L,
				localReadUpToMessageId = 8L,
				firstUnreadMessageId = 9L,
				openAnchorMessageId = 8L,
				openMode = DirectChatOpenMode.FROM_LAST_SEEN,
				messageLinkAnchorMessageId = null
			)
		)

		assertEquals(8L, projection.openAnchorMessageId)
		assertEquals(9L, projection.unreadBoundaryMessageId)
		assertEquals(7, projection.unreadMessageIds.size)
		assertEquals(8L, projection.readUpToMessageId)
	}

	@Test
	fun `message link mode prioritizes link anchor`() {
		val messages = buildIncomingMessages(100L..110L)

		val projection = projectDirectChatReadModel(
			input = DirectChatReadModelInput(
				peerUserId = "42",
				messages = messages,
				serverReadUpToMessageId = 100L,
				localReadUpToMessageId = 100L,
				firstUnreadMessageId = 101L,
				openAnchorMessageId = null,
				openMode = DirectChatOpenMode.FROM_MESSAGE_LINK,
				messageLinkAnchorMessageId = 108L
			)
		)

		assertEquals(108L, projection.openAnchorMessageId)
		assertEquals(101L, projection.unreadBoundaryMessageId)
		assertEquals(10, projection.unreadMessageIds.size)
	}

	@Test
	fun `all read projection clears unread boundary`() {
		val messages = buildIncomingMessages(1L..5L)

		val projection = projectDirectChatReadModel(
			input = DirectChatReadModelInput(
				peerUserId = "42",
				messages = messages,
				serverReadUpToMessageId = 5L,
				localReadUpToMessageId = 5L,
				firstUnreadMessageId = null,
				openAnchorMessageId = 5L,
				openMode = DirectChatOpenMode.FROM_LAST_SEEN,
				messageLinkAnchorMessageId = null
			)
		)

		assertEquals(5L, projection.openAnchorMessageId)
		assertNull(projection.unreadBoundaryMessageId)
		assertTrue(projection.unreadMessageIds.isEmpty())
	}

	@Test
	fun `latest persistable message ignores optimistic ids`() {
		val groups = listOf(
			DatedChatMessages(
				datetime = LocalDateTime.of(2026, 1, 1, 10, 0).toLocalDate(),
				messages = listOf(
					PrimaryOutMessage(
						id = -5L,
						messageText = "tmp",
						dateTime = LocalDateTime.of(2026, 1, 1, 10, 1)
					),
					PrimaryInMessage(
						id = 11L,
						messageText = "server",
						dateTime = LocalDateTime.of(2026, 1, 1, 10, 0)
					)
				)
			)
		)

		assertEquals(11L, latestPersistableMessageId(groups))
		assertEquals(11L, latestRenderableMessageId(groups))
	}

	private fun buildIncomingMessages(ids: LongRange): List<ChatMessage> {
		return ids.map { id ->
			PrimaryInMessage(
				id = id,
				messageText = "message_$id",
				dateTime = LocalDateTime.of(2026, 1, 1, 10, 0).plusSeconds(id)
			)
		}
	}
}
