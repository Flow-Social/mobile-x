package me.floow.data.repos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import me.floow.domain.api.PresenceApi
import me.floow.domain.api.PresenceRealtimeApi
import me.floow.domain.api.PresenceRealtimeSession
import me.floow.domain.api.models.PresenceGetResponse
import me.floow.domain.api.models.PresenceItem
import me.floow.domain.api.models.PresenceRealtimeEvent
import me.floow.domain.api.models.PresenceUpdateResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.data.repos.PresenceSessionStore
import me.floow.domain.utils.Logger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PresenceRepositoryImplTest {

	private class FakeLogger : Logger {
		override fun d(tag: String?, message: String) = Unit
	}

	private class FakePresenceApi : PresenceApi {
		var snapshotItems: List<PresenceItem> = emptyList()
		var getPresenceCalls = 0

		override suspend fun getPresence(userIds: List<String>): PresenceGetResponse {
			getPresenceCalls += 1
			return PresenceGetResponse.Success(snapshotItems.filter { it.userId in userIds })
		}

		override suspend fun heartbeat(sessionId: String?): PresenceUpdateResponse {
			return PresenceUpdateResponse.Success(
				PresenceItem(
					userId = "1",
					isOnline = true,
					lastSeenAtMillis = 100L,
					serverTimestampMillis = 100L
				)
			)
		}

		override suspend fun offline(sessionId: String?): PresenceUpdateResponse {
			return PresenceUpdateResponse.Success(
				PresenceItem(
					userId = "1",
					isOnline = false,
					lastSeenAtMillis = 200L,
					serverTimestampMillis = 200L
				)
			)
		}
	}

	private class FakePresenceRealtimeSession : PresenceRealtimeSession {
		private val eventsFlow = MutableSharedFlow<PresenceRealtimeEvent>(extraBufferCapacity = 16)
		val subscribedBatches = mutableListOf<List<String>>()
		val unsubscribedBatches = mutableListOf<List<String>>()

		override val events: Flow<PresenceRealtimeEvent> = eventsFlow

		override suspend fun subscribe(userIds: List<String>) {
			subscribedBatches += userIds
		}

		override suspend fun unsubscribe(userIds: List<String>) {
			unsubscribedBatches += userIds
		}

		override suspend fun close() = Unit

		suspend fun emit(event: PresenceRealtimeEvent) {
			eventsFlow.emit(event)
		}
	}

	private class FakePresenceRealtimeApi(
		private val session: FakePresenceRealtimeSession
	) : PresenceRealtimeApi {
		override suspend fun openSession(): PresenceRealtimeSession = session
	}

	private class FakeAuthenticationManager : AuthenticationManager {
		override val authenticationStateFlow = MutableStateFlow<AuthState>(AuthState.NoIdToken)

		override suspend fun handleGoogleOAuthCode(code: String) = Unit
		override suspend fun getAuthTokenOrNull(): String? = "token"
		override fun getSelfUserIdOrNull(): String? = "1"
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

	private class FakePresenceSessionStore : PresenceSessionStore {
		override fun getOrCreateSessionId(): String = "session-1"
	}

	@Test
	fun `owner targets keep union and unsubscribe removed owner`() = runBlocking {
		val session = FakePresenceRealtimeSession()
		val repository = PresenceRepositoryImpl(
			logger = FakeLogger(),
			presenceApi = FakePresenceApi(),
			presenceRealtimeApi = FakePresenceRealtimeApi(session),
			authenticationManager = FakeAuthenticationManager(),
			sessionStore = FakePresenceSessionStore()
		)

		repository.setTargets("chat_list", listOf("2", "3"))
		repository.onAppForeground()
		Thread.sleep(120)
		repository.setTargets("profile:7", listOf("7"))
		Thread.sleep(120)
		repository.clearTargets("profile:7")
		Thread.sleep(120)

		assertEquals(listOf("2", "3"), session.subscribedBatches.first())
		assertEquals(listOf("7"), session.subscribedBatches.last())
		assertEquals(listOf("7"), session.unsubscribedBatches.last())
	}

	@Test
	fun `resync required refreshes snapshot and updates cached presence`() = runBlocking {
		val session = FakePresenceRealtimeSession()
		val api = FakePresenceApi().apply {
			snapshotItems = listOf(
				PresenceItem(
					userId = "7",
					isOnline = false,
					lastSeenAtMillis = 777L,
					serverTimestampMillis = 777L
				)
			)
		}
		val repository = PresenceRepositoryImpl(
			logger = FakeLogger(),
			presenceApi = api,
			presenceRealtimeApi = FakePresenceRealtimeApi(session),
			authenticationManager = FakeAuthenticationManager(),
			sessionStore = FakePresenceSessionStore()
		)

		repository.setTargets("profile:7", listOf("7"))
		repository.onAppForeground()
		Thread.sleep(120)
		session.emit(PresenceRealtimeEvent.ResyncRequired(serverTimestampMillis = 888L))
		Thread.sleep(120)

		assertEquals(1, api.getPresenceCalls)
		val presence = repository.presences.value["7"]
		assertNotNull(presence)
		assertEquals(false, presence?.isOnline)
		assertEquals(777L, presence?.lastSeenAtMillis)
	}
}
