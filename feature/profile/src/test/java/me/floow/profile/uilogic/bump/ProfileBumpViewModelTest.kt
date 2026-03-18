package me.floow.profile.uilogic.bump

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import me.floow.domain.api.models.BumpCancelSessionRequest
import me.floow.domain.api.models.BumpCancelSessionResult
import me.floow.domain.api.models.BumpEventRequest
import me.floow.domain.api.models.BumpEventResult
import me.floow.domain.api.models.BumpSessionResult
import me.floow.domain.api.models.BumpSessionStatus
import me.floow.domain.api.models.BumpStartSessionRequest
import me.floow.domain.api.models.BumpStartSessionResponse
import me.floow.domain.api.models.CancelBumpSessionResponse
import me.floow.domain.api.models.GetBumpSessionResultResponse
import me.floow.domain.api.models.StartBumpSessionResponse
import me.floow.domain.api.models.SubmitBumpEventResponse
import me.floow.domain.data.repos.BumpRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileBumpViewModelTest {

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
    fun `matched emits navigation result after hello send succeeds`() = runTest(dispatcher) {
        val bumpRepository = FakeBumpRepository().apply {
            startSessionResponse = StartBumpSessionResponse.Success(
                BumpStartSessionResponse(
                    sessionId = "session-1",
                    expiresAtMs = System.currentTimeMillis() + 60_000L,
                    bleToken = "ble-token",
                )
            )
            submitEventResponse = SubmitBumpEventResponse.Success(
                BumpEventResult(
                    status = BumpSessionStatus.matched,
                    matchedUserId = "42",
                    sessionId = "session-1",
                )
            )
        }
        val helloUseCase = FakeSendBumpHelloUseCase(
            result = BumpMatchResult(
                matchedUserId = "42",
                conversationId = 77L,
                helloSent = true,
            )
        )
        val viewModel = ProfileBumpViewModel(bumpRepository, helloUseCase)
        val results = mutableListOf<BumpMatchResult>()
        val job = backgroundScope.launch { viewModel.matchCompleted.collect(results::add) }

        viewModel.openSheet()
        viewModel.startSession()
        advanceUntilIdle()
        viewModel.submitImpact(
            lat = 1.0,
            lon = 2.0,
            accuracyMeters = 3f,
            accelPeak = 4f,
            deviceNonce = "nonce",
        )
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("42", results.single().matchedUserId)
        assertTrue(results.single().helloSent)
        assertEquals(1, helloUseCase.calls.size)
        assertEquals("42", helloUseCase.calls.single())
        viewModel.resetToIdle()
        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `matched still emits navigation result when hello send fails`() = runTest(dispatcher) {
        val bumpRepository = FakeBumpRepository().apply {
            startSessionResponse = StartBumpSessionResponse.Success(
                BumpStartSessionResponse(
                    sessionId = "session-2",
                    expiresAtMs = System.currentTimeMillis() + 60_000L,
                    bleToken = "ble-token",
                )
            )
            submitEventResponse = SubmitBumpEventResponse.Success(
                BumpEventResult(
                    status = BumpSessionStatus.matched,
                    matchedUserId = "77",
                    sessionId = "session-2",
                )
            )
        }
        val helloUseCase = FakeSendBumpHelloUseCase(
            result = BumpMatchResult(
                matchedUserId = "77",
                conversationId = null,
                helloSent = false,
            )
        )
        val viewModel = ProfileBumpViewModel(bumpRepository, helloUseCase)
        val results = mutableListOf<BumpMatchResult>()
        val job = backgroundScope.launch { viewModel.matchCompleted.collect(results::add) }

        viewModel.openSheet()
        viewModel.startSession()
        advanceUntilIdle()
        viewModel.submitImpact(
            lat = 1.0,
            lon = 2.0,
            accuracyMeters = 3f,
            accelPeak = 4f,
            deviceNonce = "nonce",
        )
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("77", results.single().matchedUserId)
        assertFalse(results.single().helloSent)
        assertNull(results.single().conversationId)
        viewModel.resetToIdle()
        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `blank matched user id does not emit navigation result`() = runTest(dispatcher) {
        val bumpRepository = FakeBumpRepository().apply {
            startSessionResponse = StartBumpSessionResponse.Success(
                BumpStartSessionResponse(
                    sessionId = "session-3",
                    expiresAtMs = System.currentTimeMillis() + 60_000L,
                    bleToken = "ble-token",
                )
            )
            submitEventResponse = SubmitBumpEventResponse.Success(
                BumpEventResult(
                    status = BumpSessionStatus.matched,
                    matchedUserId = " ",
                    sessionId = "session-3",
                )
            )
        }
        val helloUseCase = FakeSendBumpHelloUseCase(
            result = BumpMatchResult(
                matchedUserId = "",
                conversationId = null,
                helloSent = false,
            )
        )
        val viewModel = ProfileBumpViewModel(bumpRepository, helloUseCase)
        val results = mutableListOf<BumpMatchResult>()
        val job = backgroundScope.launch { viewModel.matchCompleted.collect(results::add) }

        viewModel.openSheet()
        viewModel.startSession()
        advanceUntilIdle()
        viewModel.submitImpact(
            lat = 1.0,
            lon = 2.0,
            accuracyMeters = 3f,
            accelPeak = 4f,
            deviceNonce = "nonce",
        )
        advanceUntilIdle()

        assertTrue(results.isEmpty())
        assertTrue(helloUseCase.calls.isEmpty())
        viewModel.resetToIdle()
        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `polling match emits hello only once for the same session`() = runTest(dispatcher) {
        val bumpRepository = FakeBumpRepository().apply {
            startSessionResponse = StartBumpSessionResponse.Success(
                BumpStartSessionResponse(
                    sessionId = "session-4",
                    expiresAtMs = System.currentTimeMillis() + 60_000L,
                    bleToken = "ble-token",
                )
            )
            submitEventResponse = SubmitBumpEventResponse.Success(
                BumpEventResult(
                    status = BumpSessionStatus.matching,
                    matchedUserId = null,
                    sessionId = "session-4",
                )
            )
            sessionResults += GetBumpSessionResultResponse.Success(
                BumpSessionResult(
                    status = BumpSessionStatus.matched,
                    matchedUserId = "99",
                    sessionId = "session-4",
                )
            )
            sessionResults += GetBumpSessionResultResponse.Success(
                BumpSessionResult(
                    status = BumpSessionStatus.matched,
                    matchedUserId = "99",
                    sessionId = "session-4",
                )
            )
        }
        val helloUseCase = FakeSendBumpHelloUseCase(
            result = BumpMatchResult(
                matchedUserId = "99",
                conversationId = 77L,
                helloSent = true,
            )
        )
        val viewModel = ProfileBumpViewModel(bumpRepository, helloUseCase)
        val results = mutableListOf<BumpMatchResult>()
        val job = backgroundScope.launch { viewModel.matchCompleted.collect(results::add) }

        viewModel.openSheet()
        viewModel.startSession()
        advanceUntilIdle()
        viewModel.submitImpact(
            lat = 1.0,
            lon = 2.0,
            accuracyMeters = 3f,
            accelPeak = 4f,
            deviceNonce = "nonce",
        )
        advanceUntilIdle()
        advanceTimeBy(701L)
        advanceUntilIdle()
        advanceTimeBy(701L)
        advanceUntilIdle()

        assertEquals(1, helloUseCase.calls.size)
        assertEquals(1, results.size)
        assertEquals("99", results.single().matchedUserId)
        viewModel.resetToIdle()
        job.cancel()
        advanceUntilIdle()
    }

    @Test
    fun `timeout does not invoke hello use case`() = runTest(dispatcher) {
        val bumpRepository = FakeBumpRepository().apply {
            startSessionResponse = StartBumpSessionResponse.Success(
                BumpStartSessionResponse(
                    sessionId = "session-5",
                    expiresAtMs = System.currentTimeMillis() + 60_000L,
                    bleToken = "ble-token",
                )
            )
            submitEventResponse = SubmitBumpEventResponse.Success(
                BumpEventResult(
                    status = BumpSessionStatus.timeout,
                    matchedUserId = null,
                    sessionId = "session-5",
                )
            )
        }
        val helloUseCase = FakeSendBumpHelloUseCase(
            result = BumpMatchResult(
                matchedUserId = "ignored",
                conversationId = null,
                helloSent = false,
            )
        )
        val viewModel = ProfileBumpViewModel(bumpRepository, helloUseCase)
        val results = mutableListOf<BumpMatchResult>()
        val job = backgroundScope.launch { viewModel.matchCompleted.collect(results::add) }

        viewModel.openSheet()
        viewModel.startSession()
        advanceUntilIdle()
        viewModel.submitImpact(
            lat = 1.0,
            lon = 2.0,
            accuracyMeters = 3f,
            accelPeak = 4f,
            deviceNonce = "nonce",
        )
        advanceUntilIdle()

        assertTrue(results.isEmpty())
        assertTrue(helloUseCase.calls.isEmpty())
        viewModel.resetToIdle()
        job.cancel()
        advanceUntilIdle()
    }
}

private class FakeBumpRepository : BumpRepository {
    var startSessionResponse: StartBumpSessionResponse = StartBumpSessionResponse.Error
    var submitEventResponse: SubmitBumpEventResponse = SubmitBumpEventResponse.Error
    val sessionResults = ArrayDeque<GetBumpSessionResultResponse>()

    override suspend fun startSession(request: BumpStartSessionRequest): StartBumpSessionResponse = startSessionResponse

    override suspend fun submitEvent(request: BumpEventRequest): SubmitBumpEventResponse = submitEventResponse

    override suspend fun getSessionResult(sessionId: String): GetBumpSessionResultResponse {
        return sessionResults.removeFirstOrNull() ?: GetBumpSessionResultResponse.Success(
            BumpSessionResult(
                status = BumpSessionStatus.waiting,
                matchedUserId = null,
                sessionId = sessionId,
            )
        )
    }

    override suspend fun cancelSession(request: BumpCancelSessionRequest): CancelBumpSessionResponse {
        return CancelBumpSessionResponse.Success(
            BumpCancelSessionResult(
                ok = true,
                sessionId = request.sessionId,
            )
        )
    }
}

private class FakeSendBumpHelloUseCase(
    private val result: BumpMatchResult,
) : SendBumpHelloUseCase(
    chatsRepository = FakeBumpChatsRepository(),
    logger = TestLogger(),
) {
    val calls = mutableListOf<String>()

    override suspend operator fun invoke(matchedUserId: String): BumpMatchResult {
        calls += matchedUserId
        return result
    }
}
