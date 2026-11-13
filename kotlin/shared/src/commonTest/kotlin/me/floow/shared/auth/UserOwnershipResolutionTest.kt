package me.floow.shared.auth

import kotlinx.coroutines.test.runTest
import me.floow.domain.api.AuthApi
import me.floow.domain.api.models.AuthApiResult
import me.floow.domain.api.models.CompleteGoogleRegistrationResult
import me.floow.domain.api.models.EditProfileData
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.domain.utils.Logger
import me.floow.shared.chats.data.toItemModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UserOwnershipResolutionTest {

    private val validJwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiNDIiLCJlbWFpbCI6InRlc3RAZmxvdy5tZSJ9.sig"

    private class NoOpLogger : Logger {
        override fun d(tag: String?, message: String) {}
    }

    private class FakeAuthApi : AuthApi {
        override suspend fun getAuthTokenByGoogleIdToken(idToken: String): AuthApiResult = AuthApiResult.Failure
        override suspend fun completeGoogleRegistration(
            pendingToken: String,
            data: EditProfileData,
            avatarUrl: String?
        ): CompleteGoogleRegistrationResult = CompleteGoogleRegistrationResult.Failure
    }

    @Test
    fun tokenStorage_autoExtractsUserId_whenTokensSaved() = runTest {
        val storage = InMemoryTokenStorage()
        storage.saveTokens(accessToken = validJwtToken)

        // Должен автоматически извлечь userId из JWT
        assertEquals("42", storage.getUserId())
    }

    @Test
    fun tokenStorage_extractsUserIdFromInitialTokensFallback() = runTest {
        val storage = InMemoryTokenStorage(
            initialTokens = AuthTokens(accessToken = validJwtToken)
        )
        // Даже если явно saveUserId не вызывался, getUserId возвращает ID из токена
        assertEquals("42", storage.getUserId())
    }

    @Test
    fun authenticationManager_getSelfUserId_synchronouslyAvailableFromToken() = runTest {
        val storage = InMemoryTokenStorage(
            initialTokens = AuthTokens(accessToken = validJwtToken)
        )
        val authManager = CommonAuthenticationManager(
            tokenStorage = storage,
            logger = NoOpLogger(),
            googleOAuth = null,
            authApi = FakeAuthApi(),
        )

        // Синхронный вызов getSelfUserIdOrNull() сразу после создания НЕ должен возвращать null!
        val selfUserId = authManager.getSelfUserIdOrNull()
        assertNotNull(selfUserId, "selfUserId must not be null when valid access token is present")
        assertEquals("42", selfUserId)
    }

    @Test
    fun messageOwnership_correctlyMarksOutgoingWhenSenderMatchesSelfUserId() {
        val selfUserId = "42"

        val myMessage = DirectChatMessage(
            id = 101L,
            conversationId = 1L,
            sender = DirectChatPeer(id = "42", username = "me", name = "Me", avatarUrl = null),
            text = "Hello from me",
            replyToMessageId = null,
            replyToMessageText = null,
            isPinned = false,
            pinnedAt = null,
            pinnedByUserId = null,
            deliveryStatus = MessageDeliveryStatus.SENT,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val peerMessage = DirectChatMessage(
            id = 102L,
            conversationId = 1L,
            sender = DirectChatPeer(id = "999", username = "friend", name = "Friend", avatarUrl = null),
            text = "Hello from friend",
            replyToMessageId = null,
            replyToMessageText = null,
            isPinned = false,
            pinnedAt = null,
            pinnedByUserId = null,
            deliveryStatus = MessageDeliveryStatus.SENT,
            createdAt = 1001L,
            updatedAt = 1001L,
        )

        val myItem = myMessage.toItemModel(selfUserId)
        val peerItem = peerMessage.toItemModel(selfUserId)

        assertTrue(myItem.isOutgoing, "Message sent by self must have isOutgoing = true")
        assertFalse(peerItem.isOutgoing, "Message sent by peer must have isOutgoing = false")
    }

    @Test
    fun messageOwnership_handlesWhitespaceInIds() {
        val selfUserId = " 42  "
        val message = DirectChatMessage(
            id = 101L,
            conversationId = 1L,
            sender = DirectChatPeer(id = "42", username = "me", name = "Me", avatarUrl = null),
            text = "Whitespace check",
            replyToMessageId = null,
            replyToMessageText = null,
            isPinned = false,
            pinnedAt = null,
            pinnedByUserId = null,
            deliveryStatus = MessageDeliveryStatus.SENT,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val item = message.toItemModel(selfUserId)
        assertTrue(item.isOutgoing, "Normalized IDs should match despite whitespace")
    }

    @Test
    fun messageOwnership_whenSelfUserIdIsNull_allMessagesMarkedIncomingWithoutCrash() {
        val message = DirectChatMessage(
            id = 101L,
            conversationId = 1L,
            sender = DirectChatPeer(id = "42", username = "me", name = "Me", avatarUrl = null),
            text = "Fallback check",
            replyToMessageId = null,
            replyToMessageText = null,
            isPinned = false,
            pinnedAt = null,
            pinnedByUserId = null,
            deliveryStatus = MessageDeliveryStatus.SENT,
            createdAt = 1000L,
            updatedAt = 1000L,
        )

        val item = message.toItemModel(null)
        assertFalse(item.isOutgoing, "Without selfUserId message is safely marked incoming")
    }

    @Test
    fun authenticationManager_getSelfUserId_availableSynchronouslyEvenWithoutPriorInitScopeCompletion() {
        val storage = InMemoryTokenStorage(initialTokens = AuthTokens(accessToken = validJwtToken))
        val authManager = CommonAuthenticationManager(
            tokenStorage = storage,
            logger = NoOpLogger(),
            googleOAuth = null,
            authApi = FakeAuthApi(),
        )

        // Должен быть доступен СРАЗУ синхронно без задержки / ожидания корутин
        assertEquals("42", authManager.getSelfUserIdOrNull())
        assertTrue(authManager.isSignedIn())
    }
}
