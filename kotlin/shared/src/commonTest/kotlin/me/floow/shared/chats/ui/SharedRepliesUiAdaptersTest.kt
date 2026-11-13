package me.floow.shared.chats.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.uilogic.replies.RepliesScreenState
import me.floow.uikit.chat.model.ChatScreenUiState

class SharedRepliesUiAdaptersTest {

	@Test
	fun `replies ui state restores viewport at open anchor`() {
		val uiState = RepliesScreenState.HasData(
			items = listOf(
				replyItem(messageId = 11L, createdAtMillis = 1L, isUnread = false),
				replyItem(messageId = 12L, createdAtMillis = 2L, isUnread = false),
				replyItem(messageId = 13L, createdAtMillis = 3L, isUnread = true),
			),
			unreadCount = 1,
			unreadBoundaryMessageId = 13L,
			openAnchorMessageId = 12L,
		).toSharedRepliesUiState() as ChatScreenUiState.HasData

		val initialViewport = assertNotNull(uiState.initialViewport)
		assertEquals(2, initialViewport.itemIndex)
		assertEquals(13L, uiState.unreadBoundaryMessageId)
	}
}

private fun replyItem(
	messageId: Long,
	createdAtMillis: Long,
	isUnread: Boolean,
): RepliesThreadItemModel {
	return RepliesThreadItemModel(
		messageId = messageId,
		actorDisplayName = "User $messageId",
		actorAvatarUrl = null,
		text = "Reply $messageId",
		createdAtMillis = createdAtMillis,
		isUnread = isUnread,
	)
}
