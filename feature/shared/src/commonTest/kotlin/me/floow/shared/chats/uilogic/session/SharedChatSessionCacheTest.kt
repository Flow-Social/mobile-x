package me.floow.shared.chats.uilogic.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest

class SharedChatSessionCacheTest {

	@Test
	fun `saved messages ensure starts only once`() {
		val cache = SharedChatSessionCache()

		assertTrue(cache.markSavedMessagesEnsureStarted())
		assertFalse(cache.markSavedMessagesEnsureStarted())

		cache.markSavedMessagesEnsureFinished(success = true)

		assertTrue(cache.savedMessagesEnsured)
	}

	@Test
	fun `thread snapshot is resolved by saved messages request and updated`() {
		val cache = SharedChatSessionCache()
		val snapshot = ChatThreadSnapshot(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "self",
				title = "Избранное",
				isSavedMessages = true,
			),
			messages = listOf(
				ChatMessageItemModel(
					id = 1L,
					text = "one",
					createdAtMillis = 1L,
					isOutgoing = true,
				)
			),
			canLoadMore = true,
			nextBeforeMessageId = 10L,
		)

		cache.cacheThreadSnapshot(snapshot)
		cache.updateThreadMessages(
			conversationId = 42L,
			transform = { existing ->
				existing + ChatMessageItemModel(
					id = 2L,
					text = "two",
					createdAtMillis = 2L,
					isOutgoing = false,
				)
			},
			updateMetadata = { it.copy(canLoadMore = false, nextBeforeMessageId = null) }
		)

		val resolved = cache.cachedThreadSnapshot(
			DirectChatInitialRequest(
				peerUserId = "self",
				peerDisplayName = "Избранное",
				isSavedMessages = true,
			)
		)

			assertEquals(listOf(1L, 2L), resolved?.messages?.map(ChatMessageItemModel::id))
			assertFalse(resolved!!.canLoadMore)
			assertEquals(null, resolved.nextBeforeMessageId)
	}
}
