package me.floow.shared.chats.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.resolveReplyTargetId

class SharedChatUiAdaptersTest {

	@Test
	fun `shared chat ui state keeps reply preview text for timeline messages`() {
		val uiState = DirectChatScreenState.HasData(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "u1",
				title = "User",
			),
			messages = listOf(
				ChatMessageItemModel(
					id = 2L,
					senderUserId = "u1",
					senderDisplayName = "User",
					text = "reply body",
					createdAtMillis = 2L,
					isOutgoing = false,
					replyToMessageId = 1L,
					replyToMessageText = "quoted text",
				)
			),
		).toSharedChatUiState(
			selectionState = me.floow.uikit.chat.model.ChatSelectionState(),
			jumpRequest = null,
		)

		val hasData = assertIs<ChatScreenUiState.HasData>(uiState)
		val replyMessage = assertIs<ReplyInMessage>(
			assertIs<DatedChatMessages>(hasData.messages.single()).messages.single()
		)
		assertEquals("quoted text", replyMessage.replyMessageText)
		assertEquals(1L, resolveReplyTargetId(replyMessage))
	}
}
