package me.floow.chats.uilogic.chats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedMessagesChatContractTest {

	@Test
	fun buildSavedMessagesChat_hasCorrectType() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "hello",
			lastMessageTimeMillis = 1000L,
		)
		assertEquals(ChatType.SAVED, chat.type)
	}

	@Test
	fun buildSavedMessagesChat_hasCorrectName() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "test",
			lastMessageTimeMillis = 1000L,
		)
		assertEquals("Избранное", chat.name.value)
	}

	@Test
	fun buildSavedMessagesChat_hasZeroUnreadCount() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "test",
			lastMessageTimeMillis = 1000L,
		)
		assertEquals(0, chat.unreadCount)
	}

	@Test
	fun buildSavedMessagesChat_isNotOnline() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "test",
			lastMessageTimeMillis = 1000L,
		)
		assertFalse(chat.isOnline)
	}

	@Test
	fun buildSavedMessagesChat_hasNoAvatar() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "test",
			lastMessageTimeMillis = 1000L,
		)
		assertNull(chat.avatarUrl)
	}

	@Test
	fun buildSavedMessagesChat_hasNullLastSentMessageState() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "test",
			lastMessageTimeMillis = 1000L,
		)
		assertNull(chat.lastSentMessageState)
	}

	@Test
	fun isSavedMessages_trueForSavedType() {
		val chat = buildSavedMessagesChat(
			peerId = "user-1",
			conversationId = 42L,
			lastMessageText = "test",
			lastMessageTimeMillis = 1000L,
		)
		assertTrue(chat.isSavedMessages())
	}

	@Test
	fun isSavedMessages_falseForDirectType() {
		val chat = Chat(
			id = "peer-1",
			conversationId = 1L,
			type = ChatType.DIRECT,
			name = me.floow.domain.values.ProfileName.create("Alice"),
			lastMessageText = "hi",
			lastMessageTimeMillis = 1000L,
			isOnline = false,
			unreadCount = 0,
			chatMuted = false,
			avatarUrl = null,
			attachedMediaUrl = null,
			lastSentMessageState = null,
		)
		assertFalse(chat.isSavedMessages())
	}

	@Test
	fun isSavedMessages_falseForSystemType() {
		val chat = buildRepliesInboxChat(
			lastMessageText = "reply",
			lastMessageTimeMillis = 1000L,
			unreadCount = 0,
		)
		assertFalse(chat.isSavedMessages())
	}

	@Test
	fun savedMessages_sortedFirstInList() {
		val savedChat = buildSavedMessagesChat(
			peerId = "self",
			conversationId = 1L,
			lastMessageText = "saved",
			lastMessageTimeMillis = 100L,
		)
		val directChat = Chat(
			id = "peer-1",
			conversationId = 2L,
			type = ChatType.DIRECT,
			name = me.floow.domain.values.ProfileName.create("Bob"),
			lastMessageText = "newer message",
			lastMessageTimeMillis = 9999L,
			isOnline = false,
			unreadCount = 5,
			chatMuted = false,
			avatarUrl = null,
			attachedMediaUrl = null,
			lastSentMessageState = null,
		)
		val sorted = listOf(directChat, savedChat).sortedWith(
			compareByDescending<Chat> { it.isSavedMessages() }
				.thenByDescending { it.lastMessageTimeMillis }
		)
		assertTrue(sorted.first().isSavedMessages())
		assertEquals("peer-1", sorted.last().id)
	}
}
