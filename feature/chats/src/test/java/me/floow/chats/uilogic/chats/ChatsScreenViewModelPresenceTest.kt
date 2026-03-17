package me.floow.chats.uilogic.chats

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.floow.chats.uilogic.chat.FakeChatsRepository
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.RepliesRealtimeState
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.UserNotificationsPage
import me.floow.domain.models.UserPresence
import me.floow.domain.utils.Logger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsScreenViewModelPresenceTest {

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
	fun `presence update patches only affected direct chat item`() = runTest(dispatcher) {
		val repo = FakeChatsRepository()
		val presenceRepository = TestChatsPresenceRepository()
		val notificationsRepository = TestNotificationsRealtimeRepository()
		val viewModel = ChatsScreenViewModel(
			notificationsRealtimeRepository = notificationsRepository,
			chatsRepository = repo,
			presenceRepository = presenceRepository,
			authenticationManager = TestChatsAuthenticationManager(),
			logger = TestChatsLogger()
		)
		repo.observedConversationsFlow.value = listOf(
			testConversation(id = 11L, peerId = "peer-1", peerName = "Alice", updatedAt = 100L),
			testConversation(id = 12L, peerId = "peer-2", peerName = "Bob", updatedAt = 90L)
		)
		advanceUntilIdle()

		val initial = viewModel.state.value as ChatsScreenUiState.HasData
		val initialDirectChats = initial.chats.filter { it.type == ChatType.DIRECT }
		val firstChatBefore = initialDirectChats.first { it.id == "peer-1" }
		val secondChatBefore = initialDirectChats.first { it.id == "peer-2" }

		presenceRepository.emit(
			UserPresence(
				userId = "peer-1",
				isOnline = true,
				lastSeenAtMillis = null,
				serverTimestampMillis = 1L
			)
		)
		advanceUntilIdle()

		val updated = viewModel.state.value as ChatsScreenUiState.HasData
		val updatedDirectChats = updated.chats.filter { it.type == ChatType.DIRECT }
		val firstChatAfter = updatedDirectChats.first { it.id == "peer-1" }
		val secondChatAfter = updatedDirectChats.first { it.id == "peer-2" }

		assertTrue(firstChatAfter.isOnline)
		assertFalse(secondChatAfter.isOnline)
		assertSame("unaffected chat should keep same instance", secondChatBefore, secondChatAfter)
		assertTrue("affected chat should be replaced", firstChatBefore !== firstChatAfter)
	}

	@Test
	fun `presence update keeps replies first and saved messages above direct chats`() = runTest(dispatcher) {
		val repo = FakeChatsRepository()
		val presenceRepository = TestChatsPresenceRepository()
		val notificationsRepository = TestNotificationsRealtimeRepository()
		val viewModel = ChatsScreenViewModel(
			notificationsRealtimeRepository = notificationsRepository,
			chatsRepository = repo,
			presenceRepository = presenceRepository,
			authenticationManager = TestChatsAuthenticationManager(),
			logger = TestChatsLogger()
		)
		repo.observedConversationsFlow.value = listOf(
			testConversation(id = 10L, peerId = "self", peerName = "Saved", updatedAt = 50L),
			testConversation(id = 11L, peerId = "peer-1", peerName = "Alice", updatedAt = 100L),
			testConversation(id = 12L, peerId = "peer-2", peerName = "Bob", updatedAt = 90L)
		)
		advanceUntilIdle()

		presenceRepository.emit(
			UserPresence(
				userId = "peer-2",
				isOnline = true,
				lastSeenAtMillis = null,
				serverTimestampMillis = 1L
			)
		)
		advanceUntilIdle()

		val state = viewModel.state.value as ChatsScreenUiState.HasData
		assertEquals(ChatType.SYSTEM, state.chats[0].type)
		assertEquals(ChatType.SAVED, state.chats[1].type)
		assertEquals(listOf("peer-1", "peer-2"), state.chats.drop(2).map { it.id })
	}

	private fun testConversation(
		id: Long,
		peerId: String,
		peerName: String,
		updatedAt: Long
	): DirectChatConversation {
		return DirectChatConversation(
			id = id,
			kind = "direct",
			peer = DirectChatPeer(
				id = peerId,
				username = peerId,
				name = peerName,
				avatarUrl = null
			),
			lastMessage = null,
			unreadCount = 0,
			lastReadMessageId = 0L,
			peerLastReadMessageId = null,
			createdAt = 0L,
			updatedAt = updatedAt
		)
	}
}

private class TestChatsPresenceRepository : PresenceRepository {
	private val _presences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
	override val presences: StateFlow<Map<String, UserPresence>> = _presences

	fun emit(vararg values: UserPresence) {
		_presences.value = values.associateBy(UserPresence::userId)
	}

	override fun setTargets(owner: String, userIds: List<String>) = Unit
	override fun clearTargets(owner: String) = Unit
	override fun onAppForeground() = Unit
	override fun onAppBackground() = Unit
}

private class TestNotificationsRealtimeRepository : NotificationsRealtimeRepository {
	override val repliesState: StateFlow<RepliesRealtimeState> = MutableStateFlow(
		RepliesRealtimeState(
			isBootstrapping = false,
			isConnected = true,
			hasError = false,
			page = UserNotificationsPage(items = emptyList(), nextCursor = null)
		)
	)

	override fun start() = Unit
	override fun stop(resetState: Boolean) = Unit
	override suspend fun refresh() = Unit
	override suspend fun applyLocalReadState(lastReadSeq: Long) = Unit
}

private class TestChatsAuthenticationManager : AuthenticationManager {
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

private class TestChatsLogger : Logger {
	override fun d(tag: String?, message: String) = Unit
}
