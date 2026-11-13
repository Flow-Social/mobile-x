package me.floow.shared.media.outbox

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.models.DirectChatUploadStage
import me.floow.domain.models.MessageDeliveryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaOutboxEngineTest {

    private class FakeUploadsApi(
        var presignSuccess: Boolean = true,
        var uploadSuccess: Boolean = true,
    ) : UploadsApi {
        var uploadCallCount = 0
        var recordedProgresses = mutableListOf<Float>()

        override suspend fun createPresign(data: CreateUploadPresignData): CreateUploadPresignResponse {
            return if (presignSuccess) {
                CreateUploadPresignResponse.Success(
                    uploadUrl = "https://s3.amazonaws.com/test-bucket/upload",
                    fileUrl = "https://cdn.floow.me/media/test.mp4",
                    objectKey = "media/test.mp4",
                    expiresInSeconds = 3600L,
                    maxSizeBytes = 100 * 1024 * 1024L,
                )
            } else {
                CreateUploadPresignResponse.Error
            }
        }

        override suspend fun uploadFile(
            uploadUrl: String,
            contentType: String,
            bytes: ByteArray,
            onProgress: ((Float) -> Unit)?
        ): Boolean {
            uploadCallCount++
            onProgress?.invoke(0.5f)
            onProgress?.invoke(1.0f)
            return uploadSuccess
        }

        var maxConcurrentUploads = 0
        var currentConcurrentUploads = 0
        var simulateDelay = false

        override suspend fun uploadFilePath(
            uploadUrl: String,
            contentType: String,
            filePath: String,
            onProgress: ((Float) -> Unit)?
        ): Boolean {
            currentConcurrentUploads++
            if (currentConcurrentUploads > maxConcurrentUploads) {
                maxConcurrentUploads = currentConcurrentUploads
            }
            uploadCallCount++
            if (simulateDelay) {
                kotlinx.coroutines.delay(100)
            }
            onProgress?.invoke(0.5f)
            recordedProgresses.add(0.5f)
            onProgress?.invoke(1.0f)
            recordedProgresses.add(1.0f)
            currentConcurrentUploads--
            return uploadSuccess
        }
    }

    private class FakeMediaOutboxStore : MediaOutboxStore {
        val updatedStages = mutableListOf<Pair<String, DirectChatUploadStage>>()
        val updatedStatuses = mutableListOf<Triple<Long, String, MessageDeliveryStatus>>()
        val cancelledCommands = mutableListOf<String>()

        override suspend fun updateOutboxMediaStage(
            clientMessageId: String,
            uploadStage: DirectChatUploadStage,
            mediaUrl: String?,
            mediaThumbnailUrl: String?,
            error: String?
        ) {
            updatedStages.add(clientMessageId to uploadStage)
        }

        override suspend fun updateMessageDeliveryStatusByClientMessageId(
            conversationId: Long,
            clientMessageId: String,
            deliveryStatus: MessageDeliveryStatus
        ) {
            updatedStatuses.add(Triple(conversationId, clientMessageId, deliveryStatus))
        }

        override suspend fun cancelOutgoing(conversationId: Long, clientMessageId: String) {
            cancelledCommands.add(clientMessageId)
        }
    }

    @Test
    fun successfulUploadTransitionsToSent() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val uploadsApi = FakeUploadsApi(presignSuccess = true, uploadSuccess = true)
        val localStore = FakeMediaOutboxStore()

        val engine = MediaOutboxEngine(
            localStore = localStore,
            uploadsApi = uploadsApi,
            scope = testScope,
            dispatcher = testDispatcher,
        )

        engine.enqueue(
            clientMessageId = "msg_123",
            conversationId = 42L,
            filePath = "/tmp/video.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1024 * 1024L,
        )

        testScheduler.advanceUntilIdle()

        val task = engine.tasks.value["msg_123"]
        assertEquals(MediaOutboxStatus.Sent, task?.status)
        assertEquals(1.0f, task?.progress)
        assertEquals("https://cdn.floow.me/media/test.mp4", task?.remoteMediaUrl)

        // Verify LocalStore was notified
        assertTrue(localStore.updatedStages.any { it.first == "msg_123" && it.second == DirectChatUploadStage.SENT })
        assertTrue(localStore.updatedStatuses.any { it.second == "msg_123" && it.third == MessageDeliveryStatus.SENT })
    }

    @Test
    fun failedUploadRetriesAndEventuallyFails() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val uploadsApi = FakeUploadsApi(presignSuccess = false)
        val localStore = FakeMediaOutboxStore()

        val engine = MediaOutboxEngine(
            localStore = localStore,
            uploadsApi = uploadsApi,
            scope = testScope,
            dispatcher = testDispatcher,
            maxRetries = 2,
            retryBackoffBaseMs = 1000L,
        )

        engine.enqueue(
            clientMessageId = "msg_fail",
            conversationId = 42L,
            filePath = "/tmp/voice.ogg",
            mimeType = "audio/ogg",
            sizeBytes = 2048L,
        )

        // Run the first attempt
        testScheduler.runCurrent()

        // First attempt failed, retries is 1 < 2 -> Queued, scheduled with 1000ms backoff
        assertEquals(MediaOutboxStatus.Queued, engine.tasks.value["msg_fail"]?.status)
        assertEquals(1, engine.tasks.value["msg_fail"]?.attemptCount)

        // Advance time partially; retry shouldn't have fired yet
        testScheduler.advanceTimeBy(500L)
        assertEquals(MediaOutboxStatus.Queued, engine.tasks.value["msg_fail"]?.status)
        assertEquals(1, engine.tasks.value["msg_fail"]?.attemptCount)

        // Advance past backoff delay -> second attempt triggers automatically
        testScheduler.advanceUntilIdle()

        // Max retries reached (2) -> Failed automatically without manual intervention
        assertEquals(MediaOutboxStatus.Failed, engine.tasks.value["msg_fail"]?.status)
        assertTrue(localStore.updatedStages.any { it.first == "msg_fail" && it.second == DirectChatUploadStage.FAILED })
        assertTrue(localStore.updatedStatuses.any { it.second == "msg_fail" && it.third == MessageDeliveryStatus.FAILED })
    }

    @Test
    fun cancelUpdatesStatusAndCancelsInLocalStore() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val uploadsApi = FakeUploadsApi()
        val localStore = FakeMediaOutboxStore()

        val engine = MediaOutboxEngine(
            localStore = localStore,
            uploadsApi = uploadsApi,
            scope = testScope,
            dispatcher = testDispatcher,
        )

        engine.enqueue(
            clientMessageId = "msg_cancel",
            conversationId = 42L,
            filePath = "/tmp/video.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1000L,
        )

        engine.cancel("msg_cancel")
        testScheduler.advanceUntilIdle()

        assertEquals(MediaOutboxStatus.Cancelled, engine.tasks.value["msg_cancel"]?.status)
        assertTrue(localStore.cancelledCommands.contains("msg_cancel"))
    }

    @Test
    fun boundedParallelUploadConcurrencyUpToThree() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)
        val uploadsApi = FakeUploadsApi(presignSuccess = true, uploadSuccess = true).apply {
            simulateDelay = true
        }
        val localStore = FakeMediaOutboxStore()

        val engine = MediaOutboxEngine(
            localStore = localStore,
            uploadsApi = uploadsApi,
            scope = testScope,
            dispatcher = testDispatcher,
        )

        for (i in 1..5) {
            engine.enqueue(
                clientMessageId = "msg_parallel_$i",
                conversationId = 42L,
                filePath = "/tmp/img_$i.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1000L,
            )
        }

        testScheduler.advanceUntilIdle()

        assertEquals(3, uploadsApi.maxConcurrentUploads)
        for (i in 1..5) {
            assertEquals(MediaOutboxStatus.Sent, engine.tasks.value["msg_parallel_$i"]?.status)
        }
    }
}
