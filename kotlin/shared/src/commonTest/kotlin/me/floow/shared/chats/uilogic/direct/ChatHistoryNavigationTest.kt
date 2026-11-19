package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.uikit.chat.model.ChatViewportSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatHistoryNavigationTest {
    private val header = ChatThreadHeaderModel(peerUserId = "peer", title = "Peer")
    private val request = DirectChatInitialRequest("peer", "Peer", conversationId = 42)
    private fun message(id: Long) = ChatMessageItemModel(id = id, text = "msg $id", createdAtMillis = id, isOutgoing = false)
    private fun DirectChatStateHolder.data() = state.value as DirectChatScreenState.HasData

    @Test
    fun `historic window only joins the next verified page and can return to latest`() = runTest {
        val newerCursors = mutableListOf<Long>()
        val remoteRequests = mutableListOf<ChatOpenMode>()
        val repo = object : ChatThreadRepository by StaticChatThreadRepository() {
            override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> {
                remoteRequests += request.openMode
                return Result.success(ChatThreadSnapshot(42, header, listOf(message(100), message(101)), false))
            }

            override suspend fun loadAroundMessage(conversationId: Long, messageId: Long) =
                Result.success(ChatThreadPage(listOf(message(10), message(12)), hasNewerMessages = true))

            override suspend fun loadNewer(conversationId: Long, afterMessageId: Long): Result<ChatThreadPage> {
                newerCursors += afterMessageId
                return Result.success(ChatThreadPage(listOf(message(17), message(21)), hasNewerMessages = true))
            }
        }
        val events = MutableSharedFlow<ChatRealtimeEvent>()
        val realtime = object : ChatRealtimeContract {
            override fun observeConversation(conversationId: Long, afterSeq: Long) = events
        }
        val holder = DirectChatStateHolder(repo, realtimeContract = realtime, scope = this)
        holder.load(request)
        advanceUntilIdle()
        holder.jumpToMessage(10)
        advanceUntilIdle()
        events.emit(ChatRealtimeEvent.MessageUpserted(message(101)))
        advanceUntilIdle()
        assertEquals(listOf(10L, 12L), holder.data().messages.map { it.id })
        assertEquals(1, holder.data().scrollToBottomBadgeCount)
        holder.onAnchorRestoreSettled(10, 0)
        holder.onViewportSnapshotChanged(ChatViewportSnapshot(
            visibleMessageIds = setOf(12L), firstVisibleMessageId = 12L,
            firstVisibleOffsetPx = 0, firstVisibleItemIndex = 0, firstVisibleItemScrollOffsetPx = 0,
            visibleReadCandidateId = 12L, isAtBottom = true,
        ))
        advanceUntilIdle()
        assertEquals(listOf(12L), newerCursors)
        assertEquals(listOf(10L, 12L, 17L, 21L), holder.data().messages.map { it.id })
        holder.requestScrollToBottom()
        advanceUntilIdle()
        assertEquals(ChatOpenMode.LATEST, remoteRequests.last())
        assertEquals(listOf(100L, 101L), holder.data().messages.map { it.id })
        assertFalse(holder.data().hasNewerMessages)
        holder.dispose()
    }

    @Test
    fun `a slow first jump cannot replace the second target`() = runTest {
        val started = CompletableDeferred<Unit>()
        val repo = object : ChatThreadRepository by StaticChatThreadRepository() {
            override suspend fun loadInitial(request: DirectChatInitialRequest) =
                Result.success(ChatThreadSnapshot(42, header, listOf(message(100)), false))

            override suspend fun loadAroundMessage(conversationId: Long, messageId: Long): Result<ChatThreadPage> {
                if (messageId == 10L) {
                    started.complete(Unit)
                    delay(500)
                }
                return Result.success(ChatThreadPage(listOf(message(messageId)), hasNewerMessages = true))
            }
        }
        val holder = DirectChatStateHolder(repo, scope = this)
        holder.load(request)
        advanceUntilIdle()
        holder.jumpToMessage(10)
        runCurrent()
        assertTrue(started.isCompleted)
        holder.jumpToMessage(20)
        advanceUntilIdle()
        assertEquals(20L, holder.data().anchorRequestMessageId)
        assertEquals(listOf(20L), holder.data().messages.map { it.id })
    }

    @Test
    fun `switching chats ignores the previous load cancellation`() = runTest {
        val previousStarted = CompletableDeferred<Unit>()
        val repo = object : ChatThreadRepository by StaticChatThreadRepository() {
            override suspend fun loadInitial(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> = runCatching {
                if (request.conversationId == 42L) {
                    previousStarted.complete(Unit)
                    delay(500)
                } else {
                    delay(100)
                }
                ChatThreadSnapshot(request.conversationId, header, listOf(message(100)), false)
            }
        }
        val holder = DirectChatStateHolder(repo, scope = this)
        holder.load(request)
        runCurrent()
        assertTrue(previousStarted.isCompleted)
        holder.load(request.copy(conversationId = 43))
        runCurrent()
        assertEquals(DirectChatScreenState.Loading, holder.state.value)
        advanceUntilIdle()
        assertEquals(43L, holder.data().conversationId)
    }

    @Test
    fun `image preparation keeps the picked quote even after composer is cleared`() = runTest {
        val repo = object : ChatThreadRepository by StaticChatThreadRepository() {
            override suspend fun loadInitial(request: DirectChatInitialRequest) =
                Result.success(ChatThreadSnapshot(42, header, listOf(message(10)), false))
        }
        val holder = DirectChatStateHolder(repo, scope = this)
        holder.load(request)
        advanceUntilIdle()
        holder.addReply(10)
        holder.addLocalImages(listOf(DirectChatStateHolder.LocalImageDraft("test.jpg")), null, enqueueOutbox = false)
        assertEquals(null, holder.data().replyDraft)
        val image = holder.data().messages.single { it.content is ChatMessageContent.Image }
        assertEquals(10L, image.replyToMessageId)
        assertEquals("msg 10", image.replyToMessageText)
    }
}
