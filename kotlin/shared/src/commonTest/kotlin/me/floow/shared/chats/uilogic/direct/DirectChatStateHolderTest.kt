package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
import me.floow.uikit.chat.model.ChatViewportSnapshot

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
	fun `dispose does not cancel pending outgoing text send`() = runTest {
		val sendCompleted = CompletableDeferred<Unit>()
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
							messages = emptyList(),
							canLoadMore = false,
						)
					)

					override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
						Result.success(ChatThreadPage(items = emptyList()))

					override suspend fun sendMessage(
						conversationId: Long,
						text: String,
						replyToMessageId: Long?,
					): Result<ChatMessageItemModel> =
						sendMessage(conversationId, text, clientMessageId = null, replyToMessageId = replyToMessageId)

					override suspend fun sendMessage(
						conversationId: Long,
					text: String,
					clientMessageId: String?,
					replyToMessageId: Long?,
				): Result<ChatMessageItemModel> {
					delay(25)
					sendCompleted.complete(Unit)
					return Result.success(
						ChatMessageItemModel(
							id = 100L,
							clientMessageId = clientMessageId,
							text = text,
							createdAtMillis = 100L,
							isOutgoing = true,
						)
					)
				}

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
		holder.dispose()
		advanceTimeBy(30)
		advanceUntilIdle()

		assertTrue(sendCompleted.isCompleted)
	}

	@Test
	fun `add local video circle persists optimistic message`() = runTest {
		var storedMessage: ChatMessageItemModel? = null
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
							messages = emptyList(),
							canLoadMore = false,
						)
					)

				override suspend fun storeOptimisticOutgoingMessage(
					conversationId: Long,
					header: ChatThreadHeaderModel,
					message: ChatMessageItemModel,
				): Result<Unit> {
					storedMessage = message
					return Result.success(Unit)
				}

					override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
						Result.success(ChatThreadPage(items = emptyList()))

					override suspend fun sendMessage(
						conversationId: Long,
						text: String,
						replyToMessageId: Long?,
					): Result<ChatMessageItemModel> =
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

		holder.addLocalVideoCircle(
			RecordedClip(
				path = "/tmp/video_circle.mp4",
				durationMs = 1_500L,
				width = 720,
				height = 720,
			)
		)
		advanceUntilIdle()

		val persisted = assertNotNull(storedMessage)
		assertTrue(persisted.content is ChatMessageContent.VideoCircle)
		assertEquals(ChatDeliveryState.SENDING, persisted.deliveryState)
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
			remoteThumbnailUrl = null,
			durationMs = 2_000L,
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
	fun `local voice transitions through uploading failure and sent states`() = runTest {
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

		val clientMessageId = holder.addLocalVoice(
			RecordedVoiceClip(
				filePath = "/tmp/voice_test.ogg",
				durationMs = 5_000L,
				waveform = listOf(10, 20, 50, 80, 40),
			)
		).removePrefix("cmid_")

		holder.markLocalVoiceUploading(clientMessageId)
		var state = holder.state.value as DirectChatScreenState.HasData
		var message = state.messages.first { it.clientMessageId == clientMessageId }
		assertEquals(VideoUploadState.Uploading, (message.content as ChatMessageContent.Voice).uploadState)
		assertEquals(ChatDeliveryState.SENDING, message.deliveryState)

		holder.markLocalVoiceFailed(clientMessageId)
		state = holder.state.value as DirectChatScreenState.HasData
		message = state.messages.first { it.clientMessageId == clientMessageId }
		assertEquals(VideoUploadState.Failed, (message.content as ChatMessageContent.Voice).uploadState)
		assertEquals(ChatDeliveryState.FAILED, message.deliveryState)

		holder.resolveLocalVoiceSent(
			clientMessageId = clientMessageId,
			serverMessage = ChatMessageItemModel(
				id = 88L,
				clientMessageId = clientMessageId,
				senderUserId = "self",
				text = "Голосовое сообщение",
				createdAtMillis = 88L,
				isOutgoing = true,
			),
			remoteUrl = "https://cdn.example.com/voice_test.ogg",
			durationMs = 5_000L,
			waveform = listOf(10, 20, 50, 80, 40),
		)

		state = holder.state.value as DirectChatScreenState.HasData
		message = state.messages.first { it.clientMessageId == clientMessageId }
		val content = message.content as? ChatMessageContent.Voice
		assertNotNull(content)
		assertEquals(VideoUploadState.Uploaded, content.uploadState)
		assertEquals("https://cdn.example.com/voice_test.ogg", content.remoteUrl)
		assertEquals(listOf(10, 20, 50, 80, 40), content.waveform)
		assertEquals(ChatDeliveryState.SENT, message.deliveryState)
		assertEquals(88L, message.id)
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
	fun `cached load keeps chat visible when remote refresh times out`() = runTest {
		val cachedSnapshot = ChatThreadSnapshot(
			conversationId = 42L,
			header = ChatThreadHeaderModel(
				peerUserId = "u1",
				title = "User",
			),
			messages = listOf(
				ChatMessageItemModel(
					id = 1L,
					text = "cached",
					createdAtMillis = 1L,
					isOutgoing = false,
				)
			),
			canLoadMore = false,
		)
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadCachedInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot?> =
					Result.success(cachedSnapshot)

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

		val state = holder.state.value as? DirectChatScreenState.HasData
		assertNotNull(state)
		assertEquals("cached", state.messages.first().text)
	}

	@Test
	fun `only a visible unread prefix advances the read cursor`() = runTest {
		val markReadCalls = mutableListOf<Pair<Long, Long>>()
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
									id = 10L,
									text = "first",
									createdAtMillis = 10L,
									isOutgoing = false,
								),
								ChatMessageItemModel(
									id = 11L,
									text = "latest",
									createdAtMillis = 11L,
									isOutgoing = false,
								),
							),
							canLoadMore = false,
						)
					)

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

				override suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> {
					markReadCalls += conversationId to messageId
					return Result.success(Unit)
				}

				override suspend fun sendMessage(conversationId: Long, text: String, replyToMessageId: Long?): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> =
					Result.failure(UnsupportedOperationException())

				override suspend fun deleteMessage(conversationId: Long, messageId: Long): Result<Unit> =
					Result.success(Unit)

				override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean): Result<Unit> =
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

		assertTrue(markReadCalls.isEmpty())
        holder.requestScrollToBottom()
        advanceUntilIdle()
        assertTrue(markReadCalls.isEmpty())

        fun viewport(vararg ids: Long) = ChatViewportSnapshot(
            visibleMessageIds = ids.toSet(), firstVisibleMessageId = ids.firstOrNull(),
            firstVisibleOffsetPx = 0, firstVisibleItemIndex = 0, firstVisibleItemScrollOffsetPx = 0,
            visibleReadCandidateId = ids.maxOrNull(), isAtBottom = true, readableIncomingIds = ids.toSet(),
        )
        holder.onViewportSnapshotChanged(viewport(11L))
        advanceUntilIdle()
        assertTrue(markReadCalls.isEmpty(), "Reading must not cross a skipped incoming message")
        holder.onViewportSnapshotChanged(viewport(10L, 11L))
        advanceUntilIdle()
        assertEquals(listOf(42L to 11L), markReadCalls)
	}

	@Test
	fun `local sent ack keeps equal timestamp messages in send order`() = runTest {
		val localMessages = MutableStateFlow(ChatThreadPage(items = emptyList()))
		val firstPending = ChatMessageItemModel(
			id = -1L,
			clientMessageId = "client-1",
			text = "first",
			createdAtMillis = 100L,
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENDING,
		)
		val secondPending = ChatMessageItemModel(
			id = -2L,
			clientMessageId = "client-2",
			text = "second",
			createdAtMillis = 100L,
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENDING,
		)
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(peerUserId = "u1", title = "User"),
							messages = listOf(firstPending, secondPending),
							canLoadMore = false,
						)
					)

				override fun observeMessages(conversationId: Long) = localMessages

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

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

		localMessages.value = ChatThreadPage(
			items = listOf(
				firstPending.copy(id = 10L, deliveryState = ChatDeliveryState.SENT),
				secondPending,
			)
		)
		advanceUntilIdle()

		val state = holder.state.value as DirectChatScreenState.HasData
		assertEquals(listOf("first", "second"), state.messages.map { it.text })
		assertEquals(listOf(10L, -2L), state.messages.map { it.id })
		holder.dispose()
	}

	@Test
	fun `local message observer appends incoming message immediately`() = runTest {
		val localMessages = MutableStateFlow(ChatThreadPage(items = emptyList()))
		val initial = ChatMessageItemModel(
			id = 1L,
			text = "old",
			createdAtMillis = 100L,
			isOutgoing = false,
		)
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 42L,
							header = ChatThreadHeaderModel(peerUserId = "u1", title = "User"),
							messages = listOf(initial),
							canLoadMore = false,
						)
					)

				override fun observeMessages(conversationId: Long) = localMessages

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(ChatThreadPage(items = emptyList()))

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

		localMessages.value = ChatThreadPage(
			items = listOf(
				initial,
				ChatMessageItemModel(
					id = 2L,
					text = "new",
					createdAtMillis = 200L,
					isOutgoing = false,
				),
			)
		)
		advanceUntilIdle()

		val state = holder.state.value as DirectChatScreenState.HasData
		assertEquals(listOf("old", "new"), state.messages.map { it.text })
		holder.dispose()
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
	fun `local observer does not reopen load more after remote page says exhausted`() = runTest {
		val localMessages = MutableSharedFlow<ChatThreadPage>()
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

				override fun observeMessages(conversationId: Long) = localMessages

				override suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> =
					Result.success(
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

		localMessages.emit(
			ChatThreadPage(
				items = listOf(
					ChatMessageItemModel(
						id = 40L,
						text = "older",
						createdAtMillis = 40L,
						isOutgoing = false,
					),
					ChatMessageItemModel(
						id = 100L,
						text = "latest",
						createdAtMillis = 100L,
						isOutgoing = false,
					),
				),
				nextBeforeMessageId = 40L,
				canLoadMore = true,
			)
		)
		advanceUntilIdle()

		val state = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(state != null)
		assertEquals(listOf(40L, 100L), state.messages.map(ChatMessageItemModel::id))
		assertFalse(state.canLoadMore)
		assertNull(state.nextBeforeMessageId)
		holder.dispose()
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

	@Test
	fun `anchor restore settled persists signed offset and remains pending for close flush`() = runTest {
		val persistedSnapshots = mutableListOf<DirectChatOpenViewportSnapshot>()
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
									text = "hello",
									createdAtMillis = 1L,
									isOutgoing = false,
								)
							),
							canLoadMore = false,
                            openAnchorMessageId = 1L,
                            keepOpenAnchorLocked = true,
						)
					)

				override suspend fun setOpenViewportSnapshot(
					conversationId: Long,
					snapshot: DirectChatOpenViewportSnapshot,
				): Result<Unit> {
					persistedSnapshots += snapshot
					return Result.success(Unit)
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
		advanceUntilIdle()

		holder.onViewportSnapshotChanged(
			ChatViewportSnapshot(
				visibleMessageIds = setOf(1L),
				firstVisibleMessageId = 1L,
				firstVisibleOffsetPx = 12,
				firstVisibleItemIndex = 0,
				firstVisibleItemScrollOffsetPx = 12,
				visibleReadCandidateId = null,
				isAtBottom = false,
			)
		)
		advanceUntilIdle()

		holder.onAnchorRestoreSettled(messageId = 1L, offsetPx = -24)
		advanceUntilIdle()

		assertTrue(persistedSnapshots.isNotEmpty())
		assertEquals(-24, persistedSnapshots.last().anchorOffsetPx)
		assertEquals(1L, persistedSnapshots.last().anchorMessageId)
		assertFalse(persistedSnapshots.last().isBottomPinned)

		holder.dispose()
		advanceUntilIdle()
		assertEquals(-24, persistedSnapshots.last().anchorOffsetPx)
	}

	@Test
	fun `anchor restore timed out clears keep anchor lock`() = runTest {
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
									text = "hello",
									createdAtMillis = 1L,
									isOutgoing = false,
								)
							),
							canLoadMore = false,
							openAnchorMessageId = 1L,
							keepOpenAnchorLocked = true,
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
				conversationId = 42L,
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		val beforeTimeout = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(beforeTimeout != null)
		assertTrue(beforeTimeout.keepAnchorRequest)

		holder.onAnchorRestoreTimedOut(messageId = 1L)
		advanceUntilIdle()

		val afterTimeout = holder.state.value as? DirectChatScreenState.HasData
		assertTrue(afterTimeout != null)
		assertFalse(afterTimeout.keepAnchorRequest)
	}

	@Test
	fun `addLocalImages with enqueueOutbox false creates optimistic messages immediately and allows subsequent update`() = runTest {
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

		val drafts = listOf(
			DirectChatStateHolder.LocalImageDraft(
				localPath = "content://media/external/images/media/42",
				caption = "Optimistic test",
				width = 0,
				height = 0,
			)
		)

		val clientMessageIds = holder.addLocalImages(items = drafts, groupId = null, enqueueOutbox = false)
		assertEquals(1, clientMessageIds.size)
		val clientMessageId = clientMessageIds.first()

		// Verify optimistic message is present immediately in UI state
		val state = holder.state.value as DirectChatScreenState.HasData
		val message = state.messages.firstOrNull { it.clientMessageId == clientMessageId }
		assertNotNull(message)
		val content = message.content as? ChatMessageContent.Image
		assertNotNull(content)
		assertEquals("content://media/external/images/media/42", content.localPath)
		assertEquals(0, content.width)
		assertEquals(0, content.height)
		assertEquals(ChatDeliveryState.SENDING, message.deliveryState)

		// Now simulate media preparation completion updating source
		holder.updateLocalImageSource(
			clientMessageId = clientMessageId,
			localPath = "/cache/prepared_42.jpg",
			width = 1080,
			height = 1920,
		)

		val updatedState = holder.state.value as DirectChatScreenState.HasData
		val updatedMessage = updatedState.messages.firstOrNull { it.clientMessageId == clientMessageId }
		assertNotNull(updatedMessage)
		val updatedContent = updatedMessage.content as? ChatMessageContent.Image
		assertNotNull(updatedContent)
		assertEquals("/cache/prepared_42.jpg", updatedContent.localPath)
		assertEquals(1080, updatedContent.width)
		assertEquals(1920, updatedContent.height)
	}

	@Test
	fun `optimistic edit is preserved during concurrent local message emissions`() = runTest {
		val editDeferred = CompletableDeferred<ChatMessageItemModel>()
		val initialMessage = ChatMessageItemModel(
			id = 42L,
			clientMessageId = "msg-42",
			senderUserId = "self",
			text = "Original message text",
			createdAtMillis = 1000L,
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENT,
		)
		val holder = DirectChatStateHolder(
			repository = object : ChatThreadRepository by StaticChatThreadRepository() {
				override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> =
					Result.success(
						ChatThreadSnapshot(
							conversationId = 1L,
							header = ChatThreadHeaderModel(
								peerUserId = "peer1",
								selfUserId = "self",
								title = "Peer",
							),
							messages = listOf(initialMessage),
							canLoadMore = false,
						)
					)

				override suspend fun editMessage(conversationId: Long, messageId: Long, newText: String): Result<ChatMessageItemModel> = runCatching {
					editDeferred.await()
				}
			},
			scope = this,
		)

		holder.load(
			DirectChatInitialRequest(
				peerUserId = "peer1",
				peerDisplayName = "Peer",
				openMode = ChatOpenMode.FROM_LAST_SEEN,
			)
		)
		advanceUntilIdle()

		// Start editing
		holder.startEditing(42L)
		holder.updateInput("Optimistically edited text")
		holder.sendOrEdit()

		// Immediately, optimistic text should be present in state
		val stateDuringEdit = holder.state.value as DirectChatScreenState.HasData
		val msgDuringEdit = stateDuringEdit.messages.first { it.id == 42L }
		assertEquals("Optimistically edited text", msgDuringEdit.text)
		assertTrue(stateDuringEdit.sending)

		// Simulate local mutation that triggers mergeMessages
		val drafts = listOf(
			DirectChatStateHolder.LocalImageDraft(
				localPath = "/cache/img.jpg",
				caption = "attachment",
			)
		)
		holder.addLocalImages(drafts, groupId = null, enqueueOutbox = false)

		val stateAfterEmission = holder.state.value as DirectChatScreenState.HasData
		val msgAfterEmission = stateAfterEmission.messages.first { it.id == 42L }
		// Crucial assertion: the in-flight edit text must NOT be overwritten!
		assertEquals("Optimistically edited text", msgAfterEmission.text)

		// Complete the edit
		editDeferred.complete(initialMessage.copy(text = "Optimistically edited text"))
		advanceUntilIdle()

		val finalState = holder.state.value as DirectChatScreenState.HasData
		val finalMsg = finalState.messages.first { it.id == 42L }
		assertEquals("Optimistically edited text", finalMsg.text)
		assertFalse(finalState.sending)
	}
}

