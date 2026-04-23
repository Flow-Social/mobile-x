package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.model.VideoUploadState

@OptIn(ExperimentalCoroutinesApi::class)
class DirectChatStateHolderTest {

	@Test
	fun `load sets no messages when snapshot is empty`() = runTest {
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								title = "User",
							),
							messages = emptyList(),
							canLoadMore = false,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		assertTrue(holder.state.value is DirectChatScreenState.NoMessages)
	}

	@Test
	fun `load sets error when messages exist but conversation id is missing`() = runTest {
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = null,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								title = "User",
							),
							messages = listOf(
								ChatMessageItemModel(
									id = 1L,
									text = "hello",
									createdAtMillis = 1L,
									isOutgoing = false,
								)
							),
							canLoadMore = false,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		assertTrue(holder.state.value is DirectChatScreenState.Error)
	}

	@Test
	fun `send from no messages creates has data state`() = runTest {
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								title = "User",
							),
							messages = emptyList(),
							canLoadMore = false,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.success(
						ChatMessageItemModel(
							id = 1L,
							text = text,
							createdAtMillis = 1L,
							isOutgoing = true,
						)
					)

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()
		holder.updateInput("hello")
		holder.sendOrEdit()
		advanceUntilIdle()

		val state = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(state != null)
		assertEquals(1, state.messages.size)
		assertEquals("hello", state.messages.first().text)
	}

	@Test
	fun `local video circle uses stable pending media state and survives reload merge`() = runTest {
		val holder = DirectChatStateHolder(
			repository = StaticChatThreadRepository(),
			scope = this,
		)
		val request = DirectChatInitialRequest(
			peerUserId = "u1",
			peerDisplayName = "User",
			openMode = ChatOpenMode.FROM_LAST_SEEN,
		)

		holder.load(request)
		advanceUntilIdle()

		val uiKey = holder.addLocalVideoCircle(
			RecordedClip(
				path = "/tmp/video_circle.mp4",
				durationMs = 1_200L,
				width = 720,
				height = 720,
			)
		)
		val clientMessageId = uiKey.removePrefix("cmid_")
		val stateAfterInsert = holder.state.value as DirectChatScreenState.HasData
		val inserted = stateAfterInsert.messages.firstOrNull { it.clientMessageId == clientMessageId }
		assertNotNull(inserted)
		assertEquals(ChatDeliveryState.SENDING, inserted.deliveryState)
		val insertedContent = inserted.content as? ChatMessageContent.VideoCircle
		assertNotNull(insertedContent)
		assertEquals(VideoUploadState.Pending, insertedContent.uploadState)
		assertEquals("/tmp/video_circle.mp4", insertedContent.localPath)

		holder.load(request)
		advanceUntilIdle()

		val stateAfterReload = holder.state.value as DirectChatScreenState.HasData
		val reloaded = stateAfterReload.messages.firstOrNull { it.clientMessageId == clientMessageId }
		assertNotNull(reloaded)
		assertTrue(reloaded.content is ChatMessageContent.VideoCircle)
	}

	@Test
	fun `local video circle transitions through uploading failure and sent states`() = runTest {
		val holder = DirectChatStateHolder(
			repository = StaticChatThreadRepository(),
			scope = this,
		)
		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		val clientMessageId = holder.addLocalVideoCircle(
			RecordedClip(
				path = "/tmp/video_circle_state.mp4",
				durationMs = 1_500L,
				width = 360,
				height = 360,
			)
		).removePrefix("cmid_")

		holder.markLocalVideoCircleUploading(clientMessageId)
		var state = holder.state.value as DirectChatScreenState.HasData
		var message = state.messages.first { it.clientMessageId == clientMessageId }
		assertEquals(VideoUploadState.Uploading, (message.content as ChatMessageContent.VideoCircle).uploadState)
		assertEquals(ChatDeliveryState.SENDING, message.deliveryState)

		holder.markLocalVideoCircleFailed(clientMessageId)
		state = holder.state.value as DirectChatScreenState.HasData
		message = state.messages.first { it.clientMessageId == clientMessageId }
		assertEquals(VideoUploadState.Failed, (message.content as ChatMessageContent.VideoCircle).uploadState)
		assertEquals(ChatDeliveryState.FAILED, message.deliveryState)

		holder.resolveLocalVideoCircleSent(
			clientMessageId = clientMessageId,
			serverMessage = ChatMessageItemModel(
				id = 77L,
				clientMessageId = clientMessageId,
				senderUserId = "self",
				text = "Видеосообщение",
				createdAtMillis = 77L,
				isOutgoing = true,
			),
			remoteUrl = "https://cdn.example.com/video_circle.mp4",
			durationMs = 1_500L,
			width = 360,
			height = 360,
		)

		state = holder.state.value as DirectChatScreenState.HasData
		message = state.messages.first { it.clientMessageId == clientMessageId }
		val content = message.content as? ChatMessageContent.VideoCircle
		assertNotNull(content)
		assertEquals(VideoUploadState.Uploaded, content.uploadState)
		assertEquals("https://cdn.example.com/video_circle.mp4", content.remoteUrl)
		assertEquals(ChatDeliveryState.SENT, message.deliveryState)
		assertEquals(77L, message.id)
	}

	@Test
	fun `load reapplies read status from peerLastReadMessageId`() = runTest {
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								selfUserId = "self",
								title = "User",
							),
							messages = listOf(
								ChatMessageItemModel(
									id = 1L,
									senderUserId = "self",
									text = "hello",
									createdAtMillis = 1L,
									isOutgoing = true,
									deliveryState = ChatDeliveryState.SENT,
								),
								ChatMessageItemModel(
									id = 2L,
									senderUserId = "self",
									text = "second",
									createdAtMillis = 2L,
									isOutgoing = true,
									deliveryState = ChatDeliveryState.SENT,
								),
							),
							canLoadMore = false,
							peerLastReadMessageId = 2L,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(
					conversationId: Long,
					text: String,
					replyToMessageId: Long?,
				): Result<ChatMessageItemModel> = Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(
					conversationId: Long,
					messageId: Long,
					newText: String,
				): Result<ChatMessageItemModel> = Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				conversationId = 42L,
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		val state = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(state != null)
		assertEquals(ChatDeliveryState.READ, state.messages[0].deliveryState)
		assertEquals(ChatDeliveryState.READ, state.messages[1].deliveryState)
	}

	@Test
	fun `load fails with timeout instead of infinite loading`() = runTest {
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> {
					delay(30_000L)
					return Result.failure(IllegalStateException("should timeout first"))
				}

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				conversationId = 42L,
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)

		advanceTimeBy(15_001L)
		advanceUntilIdle()

		val state = holder.state.value as? DirectChatScreenState.Error
		assertTrue(state != null)
		assertEquals("chat load timed out", state.message)
	}

	@Test
	fun `resync required keeps chat data visible without returning to loading`() = runTest {
		val realtimeEvents = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 8)
		var loadCount = 0
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> {
					loadCount += 1
					return Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								title = "User",
							),
							messages = listOf(
								ChatMessageItemModel(
									id = loadCount.toLong(),
									text = "hello $loadCount",
									createdAtMillis = loadCount.toLong(),
									isOutgoing = loadCount % 2 == 0,
								)
							),
							canLoadMore = false,
						)
					)
				}

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			realtimeContract = object : ChatRealtimeContract {
				override fun observeConversation(conversationId: Long, afterSeq: Long) = realtimeEvents.asSharedFlow()
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				conversationId = 42L,
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		val beforeResync = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(beforeResync != null)
		assertEquals("hello 1", beforeResync.messages.single().text)

		realtimeEvents.tryEmit(ChatRealtimeEvent.ResyncRequired)
		advanceUntilIdle()

		assertTrue(holder.state.value !is DirectChatScreenState.Loading)
		val afterResync = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(afterResync != null)
		assertEquals("hello 2", afterResync.messages.single().text)
		holder.dispose()
	}

	@Test
	fun `load more uses snapshot cursor and updates canLoadMore from page contract`() = runTest {
		var receivedBeforeMessageId: Long? = null
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								title = "User",
							),
							messages = listOf(
								ChatMessageItemModel(
									id = 100L,
									text = "latest",
									createdAtMillis = 100L,
									isOutgoing = false,
								)
							),
							canLoadMore = true,
							nextBeforeMessageId = 50L,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> {
					receivedBeforeMessageId = beforeMessageId
					return Result.success(
						ChatThreadPage(
							items = listOf(
								ChatMessageItemModel(
									id = 40L,
									text = "older",
									createdAtMillis = 40L,
									isOutgoing = false,
								)
							),
							nextBeforeMessageId = null,
							canLoadMore = false,
						)
					)
				}

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				conversationId = 42L,
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()
		holder.loadMore()
		advanceUntilIdle()

		assertEquals(50L, receivedBeforeMessageId)
		val state = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(state != null)
		assertEquals(listOf(40L, 100L), state.messages.map(ChatMessageItemModel::id))
		assertEquals(false, state.canLoadMore)
		assertNull(state.nextBeforeMessageId)
	}

	@Test
	fun `send reply preserves reply preview text in shared state`() = runTest {
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(
								peerUserId = "u1",
								title = "User",
							),
							messages = listOf(
								ChatMessageItemModel(
									id = 1L,
									senderUserId = "u1",
									senderDisplayName = "User",
									text = "quoted text",
									createdAtMillis = 1L,
									isOutgoing = false,
								)
							),
							canLoadMore = false,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.success(
						ChatMessageItemModel(
							id = 2L,
							senderUserId = "self",
							senderDisplayName = "You",
							text = text,
							createdAtMillis = 2L,
							isOutgoing = true,
							replyToMessageId = replyToMessageId,
							replyToMessageText = "quoted text",
						)
					)

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
					Result.success(Unit)

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "u1",
				peerDisplayName = "User",
				conversationId = 42L,
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		holder.addReply(1L)
		holder.updateInput("reply body")
		holder.sendOrEdit()
		advanceUntilIdle()

		val state = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(state != null)
		assertEquals("quoted text", state.messages.last().replyToMessageText)
	}
}
