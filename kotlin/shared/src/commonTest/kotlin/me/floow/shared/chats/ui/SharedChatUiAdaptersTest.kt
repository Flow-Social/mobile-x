package me.floow.shared.chats.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.resolveReplyTargetId

class SharedChatUiAdaptersTest {

	@Test
	fun `incoming album grouping ignores changing sender display name and keeps ten images`() {
		val groupId = "album-10"
		val messages = (0 until 10).map { position ->
			ChatMessageItemModel(
				id = 100L + position,
				senderUserId = "peer",
				senderDisplayName = if (position == 0) "Peer" else "",
				text = "Фотография",
				createdAtMillis = 1_000L,
				isOutgoing = false,
				content = ChatMessageContent.Image(
					remoteUrl = "https://example.com/$position.jpg",
					width = 100,
					height = 100,
					groupId = groupId,
					position = position,
					uploadState = VideoUploadState.Uploaded,
				)
			)
		}

		val uiState = DirectChatScreenState.HasData(
			conversationId = 42L,
			header = ChatThreadHeaderModel(peerUserId = "peer", title = "Peer"),
			messages = messages,
		).toSharedChatUiState(
			selectionState = me.floow.uikit.chat.model.ChatSelectionState(),
			jumpRequest = null,
		)

		val hasData = assertIs<ChatScreenUiState.HasData>(uiState)
		val dated = assertIs<DatedChatMessages>(hasData.messages.single())
		val album = assertIs<ImageMessage>(dated.messages.single())
		assertEquals(10, album.images.size)
		assertEquals((0 until 10).toList(), album.images.map { it.position })
	}

	@Test
	fun `shared chat ui state forwards peer presence for no messages`() {
		val uiState = DirectChatScreenState.NoMessages(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "u1",
				title = "User",
			),
			peerIsOnline = true,
			peerLastSeenAtMillis = 456L,
		).toSharedChatUiState(
			selectionState = me.floow.uikit.chat.model.ChatSelectionState(),
			jumpRequest = null,
		)

		val noMessages = assertIs<ChatScreenUiState.NoMessages>(uiState)
		assertEquals(true, noMessages.peerIsOnline)
		assertEquals(456L, noMessages.peerLastSeenAtMillis)
	}

	@Test
	fun `shared chat ui state forwards peer presence for has data`() {
		val uiState = DirectChatScreenState.HasData(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "u1",
				title = "User",
			),
			messages = emptyList(),
			peerIsOnline = false,
			peerLastSeenAtMillis = 789L,
		).toSharedChatUiState(
			selectionState = me.floow.uikit.chat.model.ChatSelectionState(),
			jumpRequest = null,
		)

		val hasData = assertIs<ChatScreenUiState.HasData>(uiState)
		assertEquals(false, hasData.peerIsOnline)
		assertEquals(789L, hasData.peerLastSeenAtMillis)
	}

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

	@Test
	fun `shared chat ui state sets initial viewport from direct anchor`() {
		val uiState = DirectChatScreenState.HasData(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "u1",
				title = "User",
			),
			messages = listOf(
				ChatMessageItemModel(
					id = 11L,
					senderUserId = "u1",
					senderDisplayName = "User",
					text = "first",
					createdAtMillis = 1L,
					isOutgoing = false,
				),
				ChatMessageItemModel(
					id = 12L,
					senderUserId = "u1",
					senderDisplayName = "User",
					text = "anchor",
					createdAtMillis = 2L,
					isOutgoing = false,
				),
			),
			anchorRequestMessageId = 12L,
			anchorRequestOffsetPx = 24,
		).toSharedChatUiState(
			selectionState = me.floow.uikit.chat.model.ChatSelectionState(),
			jumpRequest = null,
		)

		val hasData = assertIs<ChatScreenUiState.HasData>(uiState)
		val initialViewport = assertNotNull(hasData.initialViewport)
		assertEquals(0, initialViewport.itemIndex)
		assertEquals(24, initialViewport.itemScrollOffsetPx)
	}

	@Test
	fun `shared chat ui state keeps incoming ownership for video circles`() {
		val uiState = DirectChatScreenState.HasData(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "u1",
				title = "User",
			),
			messages = listOf(
				ChatMessageItemModel(
					id = 17L,
					senderUserId = "u1",
					senderDisplayName = "User",
					text = "",
					createdAtMillis = 2L,
					isOutgoing = false,
					content = ChatMessageContent.VideoCircle(
						remoteUrl = "https://example.com/circle.mp4",
						localPath = null,
						durationMs = 1200L,
						thumbnailPath = null,
						width = 240,
						height = 240,
						uploadState = VideoUploadState.Uploaded,
					),
				)
			),
		).toSharedChatUiState(
			selectionState = me.floow.uikit.chat.model.ChatSelectionState(),
			jumpRequest = null,
		)

		val hasData = assertIs<ChatScreenUiState.HasData>(uiState)
		val videoCircle = assertIs<VideoCircleOutMessage>(
			assertIs<DatedChatMessages>(hasData.messages.single()).messages.single()
		)
		assertEquals(false, videoCircle.isOutgoing)
	}
}
