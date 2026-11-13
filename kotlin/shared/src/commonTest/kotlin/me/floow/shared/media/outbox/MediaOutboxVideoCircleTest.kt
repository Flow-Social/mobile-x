package me.floow.shared.media.outbox

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.ChatMessageItem
import me.floow.domain.api.models.ChatMessageMediaItem
import me.floow.domain.api.models.ChatUserItem
import me.floow.domain.api.models.ChatsSendMessageResponse
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.models.DirectChatUploadStage
import me.floow.domain.models.MessageDeliveryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MediaOutboxVideoCircleTest {

    private class FakeMediaOutboxStore : MediaOutboxStore {
        val stages = mutableListOf<DirectChatUploadStage>()
        val deliveryStatuses = mutableListOf<MessageDeliveryStatus>()

        override suspend fun updateOutboxMediaStage(
            clientMessageId: String,
            uploadStage: DirectChatUploadStage,
            mediaUrl: String?,
            mediaThumbnailUrl: String?,
            error: String?
        ) {
            stages.add(uploadStage)
        }

        override suspend fun updateMessageDeliveryStatusByClientMessageId(
            conversationId: Long,
            clientMessageId: String,
            deliveryStatus: MessageDeliveryStatus
        ) {
            deliveryStatuses.add(deliveryStatus)
        }

        override suspend fun cancelOutgoing(conversationId: Long, clientMessageId: String) {}
    }

    private class FakeUploadsApi : UploadsApi {
        var lastPresignData: CreateUploadPresignData? = null
        var uploadFilePathCalled = false

        override suspend fun createPresign(data: CreateUploadPresignData): CreateUploadPresignResponse {
            lastPresignData = data
            return CreateUploadPresignResponse.Success(
                uploadUrl = "https://s3.example.com/upload/circle.mp4",
                fileUrl = "https://cdn.example.com/circle.mp4",
                objectKey = "obj_circle_123",
                expiresInSeconds = 3600L,
                maxSizeBytes = 104857600L
            )
        }

        override suspend fun uploadFile(
            uploadUrl: String,
            contentType: String,
            bytes: ByteArray,
            onProgress: ((Float) -> Unit)?
        ): Boolean = true

        override suspend fun uploadFilePath(
            uploadUrl: String,
            contentType: String,
            filePath: String,
            onProgress: ((Float) -> Unit)?
        ): Boolean {
            uploadFilePathCalled = true
            onProgress?.invoke(1.0f)
            return true
        }
    }

    private class FakeChatsApi : ChatsApi {
        var sentVideoCircle: Boolean = false
        var sentDurationMs: Long = 0L
        var sentSizeBytes: Long = 0L
        var sentThumbnailUrl: String? = null

        override suspend fun sendVideoCircleMessage(
            conversationId: Long,
            clientMessageId: String?,
            mediaUrl: String,
            mediaObjectKey: String,
            mediaSizeBytes: Long,
            mediaDurationMs: Long,
            mediaWidth: Int,
            mediaHeight: Int,
            thumbnailUrl: String?,
            replyToMessageId: Long?
        ): ChatsSendMessageResponse {
            sentVideoCircle = true
            sentDurationMs = mediaDurationMs
            sentSizeBytes = mediaSizeBytes
            sentThumbnailUrl = thumbnailUrl

            val messageItem = ChatMessageItem(
                id = 1001L,
                conversationId = conversationId,
                sender = ChatUserItem(
                    id = "user_42",
                    username = "testuser",
                    name = "Test User",
                    avatar = null
                ),
                text = "",
                contentType = "video_circle",
                media = ChatMessageMediaItem(
                    url = mediaUrl,
                    objectKey = mediaObjectKey,
                    mimeType = "video/mp4",
                    sizeBytes = mediaSizeBytes,
                    durationMs = mediaDurationMs,
                    width = mediaWidth,
                    height = mediaHeight,
                    thumbnailUrl = thumbnailUrl
                ),
                clientMessageId = clientMessageId,
                replyToMessageId = replyToMessageId,
                replyToMessageText = null,
                isPinned = false,
                pinnedAt = null,
                pinnedByUserId = null,
                createdAt = 1700000000000L,
                updatedAt = 1700000000000L
            )
            return ChatsSendMessageResponse.Success(messageItem, deduped = false)
        }

        override suspend fun getOrCreateDirectConversation(peerUserId: String) = error("Not implemented")
        override suspend fun getOrCreateSavedMessagesConversation() = error("Not implemented")
        override suspend fun getConversations(limit: Int, cursor: String?) = error("Not implemented")
        override suspend fun getConversation(conversationId: Long) = error("Not implemented")
        override suspend fun deleteConversation(conversationId: Long, forEveryone: Boolean) = error("Not implemented")
        override suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean) = error("Not implemented")
        override suspend fun getMessages(conversationId: Long, limit: Int, beforeId: Long?) = error("Not implemented")
        override suspend fun getMessagesAround(conversationId: Long, anchorId: Long, olderLimit: Int, newerLimit: Int) = error("Not implemented")
        override suspend fun sendMessage(conversationId: Long, text: String, clientMessageId: String?, replyToMessageId: Long?) = error("Not implemented")
        override suspend fun getMessageByClientMessageId(conversationId: Long, clientMessageId: String) = error("Not implemented")
        override suspend fun sendVoiceMessage(conversationId: Long, clientMessageId: String?, mediaUrl: String, mediaObjectKey: String, mediaSizeBytes: Long, mediaDurationMs: Long, waveform: List<Int>, replyToMessageId: Long?) = error("Not implemented")
        override suspend fun sendImageMessage(conversationId: Long, clientMessageId: String?, mediaUrl: String, mediaObjectKey: String, mediaSizeBytes: Long, mediaWidth: Int, mediaHeight: Int, caption: String?, groupId: String?, position: Int, replyToMessageId: Long?, thumbnailUrl: String?) = error("Not implemented")
        override suspend fun sendAlbumMessage(conversationId: Long, items: List<me.floow.domain.api.models.ChatAlbumItemPayload>, caption: String?, replyToMessageId: Long?, idempotencyKey: String) = error("Not implemented")
        override suspend fun deleteMessage(conversationId: Long, messageId: Long) = error("Not implemented")
        override suspend fun deleteMessagesBatch(conversationId: Long, messageIds: List<Long>) = error("Not implemented")
        override suspend fun updateMessage(conversationId: Long, messageId: Long, text: String) = error("Not implemented")
        override suspend fun getPinnedMessages(conversationId: Long, limit: Int) = error("Not implemented")
        override suspend fun pinMessage(conversationId: Long, messageId: Long) = error("Not implemented")
        override suspend fun unpinMessage(conversationId: Long, messageId: Long) = error("Not implemented")
        override suspend fun markReadUpTo(conversationId: Long, messageId: Long) = error("Not implemented")
        override suspend fun sendTyping(conversationId: Long, isTyping: Boolean, action: String) = error("Not implemented")
    }

    @Test
    fun testVideoCircleSuccessfulProcessing() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeMediaOutboxStore()
        val uploadsApi = FakeUploadsApi()
        val chatsApi = FakeChatsApi()

        val engine = MediaOutboxEngine(
            localStore = store,
            uploadsApi = uploadsApi,
            chatsApi = chatsApi,
            scope = this,
            dispatcher = testDispatcher
        )

        engine.enqueue(
            clientMessageId = "cmid_test_circle_1",
            conversationId = 777L,
            filePath = "/fake/path/circle.mp4",
            mimeType = "video/mp4",
            sizeBytes = 2048L,
            durationMs = 3500L,
            thumbnailUrl = "https://cdn.example.com/thumb.jpg",
        )

        advanceUntilIdle()

        // Проверяем статус в хранилище и задачи
        val task = engine.tasks.value["cmid_test_circle_1"]
        assertEquals(MediaOutboxStatus.Sent, task?.status)
        assertTrue(uploadsApi.uploadFilePathCalled)
        assertTrue(chatsApi.sentVideoCircle)
        assertEquals(3500L, chatsApi.sentDurationMs)
        assertEquals(2048L, chatsApi.sentSizeBytes)
        assertEquals("https://cdn.example.com/thumb.jpg", chatsApi.sentThumbnailUrl)
        assertTrue(store.deliveryStatuses.contains(MessageDeliveryStatus.SENT))
    }

    @Test
    fun testVideoCircleFiltersLocalThumbnailUrl() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeMediaOutboxStore()
        val uploadsApi = FakeUploadsApi()
        val chatsApi = FakeChatsApi()

        val engine = MediaOutboxEngine(
            localStore = store,
            uploadsApi = uploadsApi,
            chatsApi = chatsApi,
            scope = this,
            dispatcher = testDispatcher
        )

        // Передаем локальный file:// путь превью, который бэкенд реджектит
        engine.enqueue(
            clientMessageId = "cmid_test_circle_2",
            conversationId = 777L,
            filePath = "/fake/path/circle.mp4",
            mimeType = "video/mp4",
            sizeBytes = 1024L,
            durationMs = 2000L,
            thumbnailUrl = "file:///var/mobile/Containers/tmp/thumb.jpg",
        )

        advanceUntilIdle()

        val task = engine.tasks.value["cmid_test_circle_2"]
        assertEquals(MediaOutboxStatus.Sent, task?.status)
        assertTrue(chatsApi.sentVideoCircle)
        // Thumbnail URL локального файла должен быть отфильтрован в null перед отправкой на бэк
        assertEquals(null, chatsApi.sentThumbnailUrl)
    }
}
