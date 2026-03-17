package me.floow.chats.uilogic.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.DirectChatViewportSnapshot
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.UserPresence
import me.floow.domain.utils.Logger
import me.floow.uikit.chat.model.ChatScreenUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatScreenViewModelLoadDataTest {

	private val dispatcher = StandardTestDispatcher()

	@Before
	fun setUp() {
		Dispatchers.setMain(dispatcher)
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	@Test
	fun `loadData shows cached messages before refresh completes`() = runTest(dispatcher) {
		val repo = FakeChatsRepository()
		val readCursorStore = TestDirectMessagesReadCursorStore()
		val viewModel = ChatScreenViewModel(
			chatsRepository = repo,
			directMessagesReadCursorStore = readCursorStore,
			authenticationManager = TestAuthenticationManager(),
			logger = TestLogger(),
			presenceRepository = TestPresenceRepository()
		)
		val conversation = testConversation()
		val cachedMessages = listOf(
			testMessage(id = 101L, conversationId = conversation.id, senderId = conversation.peer.id, text = "cached in"),
			testMessage(id = 102L, conversationId = conversation.id, senderId = "self", text = "cached out")
		)
		repo.observedMessagesPage = DirectChatMessagesPage(
			items = cachedMessages,
			nextBeforeId = null,
			peerLastReadMessageId = null
		)
		repo.observedMessagesFlow.value = repo.observedMessagesPage
		repo.refreshLatestMessagesWindowResult = GetDataResponse.Error(me.floow.domain.data.GetDataError.Other)

		viewModel.setInitialData(
			chatInterlocutorId = conversation.peer.id,
			chatInterlocutorName = "Peer",
			chatInterlocutorAvatarUrl = null,
			conversationId = conversation.id,
			openMode = DirectChatOpenMode.FROM_LAST_SEEN
		)

		viewModel.loadData()
		repeat(20) {
			advanceUntilIdle()
			if (viewModel.state.value is ChatScreenUiState.HasData) return@repeat
			Thread.sleep(10)
		}

		val state = viewModel.state.value
		assertTrue("expected HasData but was $state", state is ChatScreenUiState.HasData)
		state as ChatScreenUiState.HasData
		assertEquals(listOf(101L, 102L), state.messages.flatMap { it.messages }.map { it.id })
		assertFalse("local cache must suppress blocking loading", state.isLoadingMore)
		assertEquals(listOf(conversation.id to 50), repo.observeMessagesCalls)
		assertEquals(listOf(conversation.id to 50), repo.refreshLatestMessagesWindowCalls)
	}

	@Test
	fun `loadData uses local anchored window for last seen warm start`() = runTest(dispatcher) {
		val repo = FakeChatsRepository()
		val readCursorStore = TestDirectMessagesReadCursorStore().apply {
			setOpenViewportSnapshot(
				conversationId = 7L,
				snapshot = DirectChatViewportSnapshot(
					anchorMessageId = 101L,
					anchorOffsetPx = 24,
					isBottomPinned = false
				)
			)
		}
		val viewModel = ChatScreenViewModel(
			chatsRepository = repo,
			directMessagesReadCursorStore = readCursorStore,
			authenticationManager = TestAuthenticationManager(),
			logger = TestLogger(),
			presenceRepository = TestPresenceRepository()
		)
		val conversation = testConversation()
		val cachedWindowMessages = listOf(
			testMessage(id = 100L, conversationId = conversation.id, senderId = conversation.peer.id, text = "older"),
			testMessage(id = 101L, conversationId = conversation.id, senderId = conversation.peer.id, text = "anchor"),
			testMessage(id = 102L, conversationId = conversation.id, senderId = "self", text = "newer")
		)
		repo.observedMessagesFlow.value = DirectChatMessagesPage(
			items = cachedWindowMessages,
			nextBeforeId = null,
			peerLastReadMessageId = null
		)
		repo.getAnchoredMessagesWindowResult = GetDataResponse.Success(
			DirectChatAnchoredMessagesWindow(
				items = cachedWindowMessages,
				anchorMessageId = 101L,
				anchorIndex = 1,
				hasOlderMessages = false,
				newerCachedCount = 1,
				latestCachedMessageId = 102L,
				peerLastReadMessageId = null
			)
		)
		repo.refreshLatestMessagesWindowResult = GetDataResponse.Success(
			DirectChatMessagesPage(
				items = cachedWindowMessages,
				nextBeforeId = null,
				peerLastReadMessageId = null
			)
		)

		viewModel.setInitialData(
			chatInterlocutorId = conversation.peer.id,
			chatInterlocutorName = "Peer",
			chatInterlocutorAvatarUrl = null,
			conversationId = conversation.id,
			openMode = DirectChatOpenMode.FROM_LAST_SEEN
		)

		viewModel.loadData()
		repeat(20) {
			advanceUntilIdle()
			if (viewModel.state.value is ChatScreenUiState.HasData) return@repeat
			Thread.sleep(10)
		}

		val state = viewModel.state.value
		assertTrue("expected HasData but was $state", state is ChatScreenUiState.HasData)
		state as ChatScreenUiState.HasData
		assertTrue(state.messages.flatMap { it.messages }.any { it.id == 101L })
		assertEquals(listOf(Triple(conversation.id, 101L, 30 to 30)), repo.getAnchoredMessagesWindowCalls)
		assertEquals(listOf(conversation.id to 50), repo.observeMessagesCalls)
		assertEquals(listOf(conversation.id to 50), repo.refreshLatestMessagesWindowCalls)
		assertTrue(repo.getMessagesAroundCalls.isEmpty())
	}

	@Test
	fun `loadData preloads around message link when local anchor warm start is unavailable`() = runTest(dispatcher) {
		val repo = FakeChatsRepository()
		val readCursorStore = TestDirectMessagesReadCursorStore()
		val viewModel = ChatScreenViewModel(
			chatsRepository = repo,
			directMessagesReadCursorStore = readCursorStore,
			authenticationManager = TestAuthenticationManager(),
			logger = TestLogger(),
			presenceRepository = TestPresenceRepository()
		)
		val conversation = testConversation()
		val cachedMessages = listOf(
			testMessage(id = 100L, conversationId = conversation.id, senderId = conversation.peer.id, text = "latest 1"),
			testMessage(id = 101L, conversationId = conversation.id, senderId = "self", text = "latest 2")
		)
		repo.observedMessagesFlow.value = DirectChatMessagesPage(
			items = cachedMessages,
			nextBeforeId = null,
			peerLastReadMessageId = null
		)
		repo.getMessagesAroundResult = GetDataResponse.Success(
			DirectChatAnchoredMessagesWindow(
				items = cachedMessages + testMessage(id = 150L, conversationId = conversation.id, senderId = conversation.peer.id, text = "linked"),
				anchorMessageId = 150L,
				anchorIndex = 2,
				hasOlderMessages = true,
				newerCachedCount = 0,
				latestCachedMessageId = 150L,
				peerLastReadMessageId = null
			)
		)

		viewModel.setInitialData(
			chatInterlocutorId = conversation.peer.id,
			chatInterlocutorName = "Peer",
			chatInterlocutorAvatarUrl = null,
			conversationId = conversation.id,
			messageAnchorId = 150L,
			openMode = DirectChatOpenMode.FROM_MESSAGE_LINK
		)

		viewModel.loadData()
		repeat(20) {
			advanceUntilIdle()
			if (repo.getMessagesAroundCalls.isNotEmpty()) return@repeat
			Thread.sleep(10)
		}

		assertEquals(listOf(Triple(conversation.id, 150L, 50 to 50)), repo.getMessagesAroundCalls)
	}

	@Test
	fun `loadData falls back to local latest window before refresh when last seen anchor window is unavailable`() = runTest(dispatcher) {
		val repo = FakeChatsRepository()
		val readCursorStore = TestDirectMessagesReadCursorStore().apply {
			setOpenViewportSnapshot(
				conversationId = 7L,
				snapshot = DirectChatViewportSnapshot(
					anchorMessageId = 333L,
					anchorOffsetPx = 18,
					isBottomPinned = false
				)
			)
		}
		val viewModel = ChatScreenViewModel(
			chatsRepository = repo,
			directMessagesReadCursorStore = readCursorStore,
			authenticationManager = TestAuthenticationManager(),
			logger = TestLogger(),
			presenceRepository = TestPresenceRepository()
		)
		val conversation = testConversation()
		val cachedMessages = listOf(
			testMessage(id = 100L, conversationId = conversation.id, senderId = conversation.peer.id, text = "latest 1"),
			testMessage(id = 101L, conversationId = conversation.id, senderId = "self", text = "latest 2")
		)
		repo.observedMessagesPage = DirectChatMessagesPage(
			items = cachedMessages,
			nextBeforeId = null,
			peerLastReadMessageId = null
		)
		repo.observedMessagesFlow.value = repo.observedMessagesPage

		viewModel.setInitialData(
			chatInterlocutorId = conversation.peer.id,
			chatInterlocutorName = "Peer",
			chatInterlocutorAvatarUrl = null,
			conversationId = conversation.id,
			openMode = DirectChatOpenMode.FROM_LAST_SEEN
		)

		viewModel.loadData()
		repeat(20) {
			advanceUntilIdle()
			if (viewModel.state.value is ChatScreenUiState.HasData) return@repeat
			Thread.sleep(10)
		}

		val state = viewModel.state.value
		assertTrue("expected HasData but was $state", state is ChatScreenUiState.HasData)
		state as ChatScreenUiState.HasData
		assertEquals(listOf(100L, 101L), state.messages.flatMap { it.messages }.map { it.id })
		assertEquals(listOf(Triple(conversation.id, 333L, 30 to 30)), repo.getAnchoredMessagesWindowCalls)
		assertEquals(listOf(conversation.id to 50), repo.observeMessagesCalls)
		assertEquals(listOf(conversation.id to 50), repo.refreshLatestMessagesWindowCalls)
		assertTrue(repo.getMessagesAroundCalls.isEmpty())
	}

	private fun testConversation(): DirectChatConversation {
		return DirectChatConversation(
			id = 7L,
			kind = "direct",
			peer = DirectChatPeer(
				id = "peer-7",
				username = "peer7",
				name = "Peer",
				avatarUrl = null
			),
			lastMessage = null,
			unreadCount = 0,
			lastReadMessageId = 0L,
			peerLastReadMessageId = null,
			createdAt = 0L,
			updatedAt = 0L
		)
	}

	private fun testMessage(
		id: Long,
		conversationId: Long,
		senderId: String,
		text: String
	): DirectChatMessage {
		return DirectChatMessage(
			id = id,
			conversationId = conversationId,
			sender = DirectChatPeer(
				id = senderId,
				username = senderId,
				name = senderId,
				avatarUrl = null
			),
			text = text,
			clientMessageId = null,
			replyToMessageId = null,
			replyToMessageText = null,
			isPinned = false,
			pinnedAt = null,
			pinnedByUserId = null,
			deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT,
			createdAt = id,
			updatedAt = id
		)
	}
}

private class TestPresenceRepository : PresenceRepository {
	override val presences: StateFlow<Map<String, UserPresence>> = MutableStateFlow(emptyMap())
	override fun setTargets(owner: String, userIds: List<String>) = Unit
	override fun clearTargets(owner: String) = Unit
	override fun onAppForeground() = Unit
	override fun onAppBackground() = Unit
}

private class TestAuthenticationManager : AuthenticationManager {
	override val authenticationStateFlow: StateFlow<AuthState> = MutableStateFlow(AuthState.NoIdToken)
	override suspend fun handleGoogleOAuthCode(code: String) = Unit
	override suspend fun getAuthTokenOrNull(): String? = null
	override fun getSelfUserIdOrNull(): String? = "self"
	override fun saveSelfUserId(userId: String) = Unit
	override fun isSignedIn(): Boolean = true
	override fun hasPendingRegistration(): Boolean = false
	override fun getPendingRegistrationTokenOrNull(): String? = null
	override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? = null
	override suspend fun startGoogleAuthentication() = Unit
	override suspend fun writeAuthToken(token: String) = Unit
	override suspend fun clearPendingRegistration() = Unit
	override suspend fun clearAuth() = Unit
}

private class TestLogger : Logger {
	override fun d(tag: String?, message: String) = Unit
}

private class TestDirectMessagesReadCursorStore : DirectMessagesReadCursorStore {
	private val snapshots = mutableMapOf<Long, DirectChatViewportSnapshot>()

	override suspend fun getLocalLastReadMessageId(conversationId: Long): Long = 0L
	override suspend fun setLocalLastReadMessageId(conversationId: Long, messageId: Long) = Unit
	override suspend fun getOpenAnchorSeq(conversationId: Long): Long = 0L
	override suspend fun setOpenAnchorSeq(conversationId: Long, seq: Long) = Unit
	override suspend fun getOpenAnchorMessageId(conversationId: Long): Long =
		snapshots[conversationId]?.anchorMessageId ?: 0L
	override suspend fun setOpenAnchorMessageId(conversationId: Long, messageId: Long) {
		val current = snapshots[conversationId] ?: DirectChatViewportSnapshot(null, 0, false)
		snapshots[conversationId] = current.copy(anchorMessageId = messageId.takeIf { it > 0L })
	}
	override suspend fun getOpenAnchorOffsetPx(conversationId: Long): Int =
		snapshots[conversationId]?.anchorOffsetPx ?: 0
	override suspend fun setOpenAnchorOffsetPx(conversationId: Long, offsetPx: Int) {
		val current = snapshots[conversationId] ?: DirectChatViewportSnapshot(null, 0, false)
		snapshots[conversationId] = current.copy(anchorOffsetPx = offsetPx)
	}
	override suspend fun isOpenAnchorBottomPinned(conversationId: Long): Boolean =
		snapshots[conversationId]?.isBottomPinned ?: false
	override suspend fun setOpenAnchorBottomPinned(conversationId: Long, isBottomPinned: Boolean) {
		val current = snapshots[conversationId] ?: DirectChatViewportSnapshot(null, 0, false)
		snapshots[conversationId] = current.copy(isBottomPinned = isBottomPinned)
	}
	override suspend fun enqueueReadUpTo(conversationId: Long, messageId: Long) = Unit
	override suspend fun getPendingReadUpTo(conversationId: Long): Long = 0L
	override suspend fun markPendingReadUpToApplied(conversationId: Long, appliedMessageId: Long) = Unit
	override suspend fun getPendingConversationIds(limit: Int): List<Long> = emptyList()
	override suspend fun getPendingEnqueuedAtMillis(conversationId: Long): Long = 0L
	override suspend fun incrementPendingRetryCount(conversationId: Long): Int = 0
	override suspend fun resetPendingRetryCount(conversationId: Long) = Unit
}
