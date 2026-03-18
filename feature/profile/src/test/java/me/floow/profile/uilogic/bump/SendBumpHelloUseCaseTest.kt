package me.floow.profile.uilogic.bump

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.domain.utils.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SendBumpHelloUseCaseTest {

    @Test
    fun `invoke resolves conversation and sends hello`() = runTest {
        val chatsRepository = FakeBumpChatsRepository().apply {
            getOrCreateConversationResult = GetDataResponse.Success(buildConversation())
            sendMessageResult = GetDataResponse.Success(buildMessage())
        }
        val useCase = SendBumpHelloUseCase(chatsRepository, TestLogger())

        val result = useCase("42")

        assertEquals(listOf("42"), chatsRepository.getOrCreateDirectConversationCalls)
        assertEquals(1, chatsRepository.sendMessageCalls.size)
        assertEquals(77L, chatsRepository.sendMessageCalls.single().conversationId)
        assertEquals("Привет! 💥📱", chatsRepository.sendMessageCalls.single().text)
        assertTrue(result.helloSent)
        assertEquals(77L, result.conversationId)
        assertEquals("42", result.matchedUserId)
        assertNotNull(chatsRepository.sendMessageCalls.single().clientMessageId)
    }

    @Test
    fun `invoke returns profile outcome when conversation resolve fails`() = runTest {
        val chatsRepository = FakeBumpChatsRepository().apply {
            getOrCreateConversationResult = GetDataResponse.Error(GetDataError.Other)
        }
        val useCase = SendBumpHelloUseCase(chatsRepository, TestLogger())

        val result = useCase("42")

        assertEquals(listOf("42"), chatsRepository.getOrCreateDirectConversationCalls)
        assertTrue(chatsRepository.sendMessageCalls.isEmpty())
        assertFalse(result.helloSent)
        assertNull(result.conversationId)
        assertEquals("42", result.matchedUserId)
    }

    @Test
    fun `invoke returns profile outcome when hello send fails`() = runTest {
        val chatsRepository = FakeBumpChatsRepository().apply {
            getOrCreateConversationResult = GetDataResponse.Success(buildConversation())
            sendMessageResult = GetDataResponse.Error(GetDataError.Other)
        }
        val useCase = SendBumpHelloUseCase(chatsRepository, TestLogger())

        val result = useCase("42")

        assertEquals(1, chatsRepository.sendMessageCalls.size)
        assertFalse(result.helloSent)
        assertEquals(77L, result.conversationId)
        assertEquals("42", result.matchedUserId)
    }
}

class FakeBumpChatsRepository : ChatsRepository {
    data class SendMessageCall(
        val conversationId: Long,
        val text: String,
        val clientMessageId: String?,
    )

    val getOrCreateDirectConversationCalls = mutableListOf<String>()
    val sendMessageCalls = mutableListOf<SendMessageCall>()
    var getOrCreateConversationResult: GetDataResponse<DirectChatConversation> =
        GetDataResponse.Error(GetDataError.Other)
    var sendMessageResult: GetDataResponse<DirectChatMessage> =
        GetDataResponse.Error(GetDataError.Other)

    override fun observeConversations(): Flow<List<DirectChatConversation>> = emptyFlow()

    override fun observeMessages(conversationId: Long, limit: Int): Flow<DirectChatMessagesPage> = emptyFlow()

    override fun observePinnedMessages(conversationId: Long, limit: Int): Flow<List<DirectChatMessage>> = emptyFlow()

    override suspend fun getOrCreateDirectConversation(peerUserId: String): GetDataResponse<DirectChatConversation> {
        getOrCreateDirectConversationCalls += peerUserId
        return getOrCreateConversationResult
    }

    override suspend fun getOrCreateSavedMessagesConversation(): GetDataResponse<DirectChatConversation> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun getConversations(limit: Int, cursor: String?): GetDataResponse<DirectChatConversationsPage> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun getConversation(conversationId: Long): GetDataResponse<DirectChatConversation> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun getMessages(
        conversationId: Long,
        limit: Int,
        beforeId: Long?,
    ): GetDataResponse<DirectChatMessagesPage> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun getMessagesAround(
        conversationId: Long,
        anchorId: Long,
        olderLimit: Int,
        newerLimit: Int,
    ): GetDataResponse<DirectChatAnchoredMessagesWindow> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun getAnchoredMessagesWindow(
        conversationId: Long,
        anchorMessageId: Long,
        olderLimit: Int,
        newerLimit: Int,
    ): GetDataResponse<DirectChatAnchoredMessagesWindow> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun refreshLatestMessagesWindow(
        conversationId: Long,
        limit: Int,
    ): GetDataResponse<DirectChatMessagesPage> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun sendMessage(
        conversationId: Long,
        text: String,
        clientMessageId: String?,
        replyToMessageId: Long?,
    ): GetDataResponse<DirectChatMessage> {
        sendMessageCalls += SendMessageCall(
            conversationId = conversationId,
            text = text,
            clientMessageId = clientMessageId,
        )
        return sendMessageResult
    }

    override suspend fun retryPendingOutgoingMessages(conversationId: Long, limit: Int) = Unit

    override suspend fun retryOutgoingMessage(conversationId: Long, clientMessageId: String) = Unit

    override suspend fun deleteMessage(conversationId: Long, messageId: Long): UpdateDataResponse = UpdateDataResponse.Success

    override suspend fun updateMessage(
        conversationId: Long,
        messageId: Long,
        text: String,
    ): GetDataResponse<DirectChatMessage> = GetDataResponse.Error(GetDataError.Other)

    override suspend fun getPinnedMessages(conversationId: Long, limit: Int): GetDataResponse<List<DirectChatMessage>> {
        return GetDataResponse.Error(GetDataError.Other)
    }

    override suspend fun setMessagePinned(
        conversationId: Long,
        messageId: Long,
        isPinned: Boolean,
    ): GetDataResponse<DirectChatMessage> = GetDataResponse.Error(GetDataError.Other)

    override suspend fun storeOutgoingOptimisticMessage(message: DirectChatMessage) = Unit

    override suspend fun deleteLocalMessage(conversationId: Long, messageId: Long) = Unit

    override suspend fun deleteLocalMessageByClientMessageId(conversationId: Long, clientMessageId: String) = Unit

    override suspend fun applyLocalPinnedState(
        conversationId: Long,
        messageId: Long,
        isPinned: Boolean,
        pinnedAt: Long?,
        pinnedByUserId: String?,
    ): DirectChatMessage? = null

    override suspend fun updateLocalMessageDeliveryStatus(
        conversationId: Long,
        messageId: Long,
        status: MessageDeliveryStatus,
    ) = Unit

    override suspend fun updateLocalMessageDeliveryStatusByClientMessageId(
        conversationId: Long,
        clientMessageId: String,
        status: MessageDeliveryStatus,
    ) = Unit

    override suspend fun markReadUpTo(
        conversationId: Long,
        messageId: Long,
    ): GetDataResponse<DirectChatReadState> = GetDataResponse.Error(GetDataError.Other)

    override suspend fun applyLocalReadUpTo(conversationId: Long, messageId: Long): DirectChatReadState? = null

    override suspend fun applyPeerLastReadUpTo(conversationId: Long, messageId: Long) = Unit

    override suspend fun sendTyping(conversationId: Long, isTyping: Boolean): UpdateDataResponse = UpdateDataResponse.Success

    override fun subscribeConversation(conversationId: Long, afterSeq: Long, replayLimit: Int): Flow<DirectChatRealtimeEvent> =
        emptyFlow()

    override fun subscribeAllConversations(afterSeq: Long, replayLimit: Int): Flow<DirectChatRealtimeEvent> = emptyFlow()
}

class TestLogger : Logger {
    val entries = mutableListOf<String>()

    override fun d(tag: String?, message: String) {
        entries += "${tag.orEmpty()}:$message"
    }
}

fun buildConversation() = DirectChatConversation(
    id = 77L,
    kind = "direct",
    peer = DirectChatPeer(
        id = "42",
        username = "u42",
        name = "User 42",
        avatarUrl = null,
    ),
    lastMessage = null,
    unreadCount = 0,
    lastReadMessageId = 0L,
    peerLastReadMessageId = null,
    createdAt = 0L,
    updatedAt = 0L,
)

fun buildMessage() = DirectChatMessage(
    id = 10L,
    conversationId = 77L,
    sender = DirectChatPeer(
        id = "self",
        username = "self",
        name = "Self",
        avatarUrl = null,
    ),
    text = "Привет! 💥📱",
    clientMessageId = "client-id",
    replyToMessageId = null,
    replyToMessageText = null,
    isPinned = false,
    pinnedAt = null,
    pinnedByUserId = null,
    deliveryStatus = MessageDeliveryStatus.SENT,
    createdAt = 0L,
    updatedAt = 0L,
)
