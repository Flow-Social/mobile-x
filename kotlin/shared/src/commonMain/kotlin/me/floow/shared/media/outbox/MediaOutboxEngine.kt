package me.floow.shared.media.outbox

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.ChatsSendMessageResponse
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.cache.PendingPostMutation
import me.floow.domain.cache.PendingPostMutationState
import me.floow.domain.cache.PendingPostMutationType
import me.floow.domain.cache.PendingPostsLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PendingPostImageEntry
import me.floow.domain.data.repos.PendingPostImageEntryKind
import me.floow.domain.data.repos.PendingPostImagePayload
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostStagedMedia
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.models.DirectChatUploadStage
import me.floow.domain.models.MessageDeliveryStatus
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json

enum class MediaOutboxStatus {
    Queued,
    Uploading,
    Sent,
    Failed,
    Cancelled
}

data class MediaOutboxTask(
    val clientMessageId: String,
    val conversationId: Long,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val waveform: List<Int> = emptyList(),
    val caption: String? = null,
    val replyToMessageId: Long? = null,
    val groupId: String? = null,
    val position: Int = 0,
    val thumbnailUrl: String? = null,
    val mediaKind: String? = null,
    val status: MediaOutboxStatus = MediaOutboxStatus.Queued,
    val progress: Float = 0f,
    val remoteMediaUrl: String? = null,
    val mediaObjectKey: String? = null,
    val attemptCount: Int = 0,
    val error: String? = null,
)

interface MediaOutboxStore {
    suspend fun updateOutboxMediaStage(
        clientMessageId: String,
        uploadStage: DirectChatUploadStage,
        mediaUrl: String? = null,
        mediaThumbnailUrl: String? = null,
        error: String? = null,
    )

    suspend fun updateMessageDeliveryStatusByClientMessageId(
        conversationId: Long,
        clientMessageId: String,
        deliveryStatus: MessageDeliveryStatus,
    )

    suspend fun cancelOutgoing(
        conversationId: Long,
        clientMessageId: String,
    )

    suspend fun markOutgoingSent(
        clientMessageId: String,
        serverMessage: me.floow.domain.models.DirectChatMessage,
        now: Long = me.floow.domain.utils.currentTimeMillis(),
    ): me.floow.domain.models.DirectChatMessage? = null

    suspend fun getActiveMediaCommands(): List<me.floow.domain.models.DirectChatOutboxCommand> = emptyList()
    fun observeActiveMediaCommands(): kotlinx.coroutines.flow.Flow<List<me.floow.domain.models.DirectChatOutboxCommand>> = kotlinx.coroutines.flow.emptyFlow()
}

fun DirectChatsLocalStore.asMediaOutboxStore(): MediaOutboxStore = object : MediaOutboxStore {
    override suspend fun updateOutboxMediaStage(
        clientMessageId: String,
        uploadStage: DirectChatUploadStage,
        mediaUrl: String?,
        mediaThumbnailUrl: String?,
        error: String?,
    ) {
        this@asMediaOutboxStore.updateOutboxMediaStage(
            clientMessageId = clientMessageId,
            uploadStage = uploadStage,
            mediaUrl = mediaUrl,
            mediaThumbnailUrl = mediaThumbnailUrl,
            error = error,
        )
    }

    override suspend fun updateMessageDeliveryStatusByClientMessageId(
        conversationId: Long,
        clientMessageId: String,
        deliveryStatus: MessageDeliveryStatus,
    ) {
        this@asMediaOutboxStore.updateMessageDeliveryStatusByClientMessageId(
            conversationId = conversationId,
            clientMessageId = clientMessageId,
            deliveryStatus = deliveryStatus,
        )
    }

    override suspend fun cancelOutgoing(conversationId: Long, clientMessageId: String) {
        this@asMediaOutboxStore.deleteMessageByClientMessageId(
            conversationId = conversationId,
            clientMessageId = clientMessageId,
        )
    }

    override suspend fun markOutgoingSent(
        clientMessageId: String,
        serverMessage: me.floow.domain.models.DirectChatMessage,
        now: Long,
    ): me.floow.domain.models.DirectChatMessage? {
        return this@asMediaOutboxStore.markOutgoingSent(clientMessageId, serverMessage, now)
    }

    override suspend fun getActiveMediaCommands(): List<me.floow.domain.models.DirectChatOutboxCommand> {
        return this@asMediaOutboxStore.getActiveMediaCommands()
    }

    override fun observeActiveMediaCommands(): kotlinx.coroutines.flow.Flow<List<me.floow.domain.models.DirectChatOutboxCommand>> {
        return this@asMediaOutboxStore.observeActiveMediaCommands()
    }
}

class MediaOutboxEngine(
    private val localStore: MediaOutboxStore,
    private val uploadsApi: UploadsApi,
    private val chatsApi: ChatsApi? = null,
    private val pendingPostsLocalStore: PendingPostsLocalStore? = null,
    private val postsRepository: PostsRepository? = null,
    private val uploadsRepository: UploadsRepository? = null,
    private val postsLocalStore: PostsLocalStore? = null,
    private val pendingPostMediaStore: PendingPostMediaStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val maxRetries: Int = 3,
    private val retryBackoffBaseMs: Long = 1000L,
) {
    constructor(
        directChatsLocalStore: DirectChatsLocalStore,
        uploadsApi: UploadsApi,
        chatsApi: ChatsApi? = null,
        pendingPostsLocalStore: PendingPostsLocalStore? = null,
        postsRepository: PostsRepository? = null,
        uploadsRepository: UploadsRepository? = null,
        postsLocalStore: PostsLocalStore? = null,
        pendingPostMediaStore: PendingPostMediaStore? = null,
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
        maxRetries: Int = 3,
        retryBackoffBaseMs: Long = 1000L,
    ) : this(
        localStore = directChatsLocalStore.asMediaOutboxStore(),
        uploadsApi = uploadsApi,
        chatsApi = chatsApi,
        pendingPostsLocalStore = pendingPostsLocalStore,
        postsRepository = postsRepository,
        uploadsRepository = uploadsRepository,
        postsLocalStore = postsLocalStore,
        pendingPostMediaStore = pendingPostMediaStore,
        scope = scope,
        dispatcher = dispatcher,
        maxRetries = maxRetries,
        retryBackoffBaseMs = retryBackoffBaseMs,
    )

    companion object {
        const val CHUNK_SIZE_BYTES = 256 * 1024 // 256 KB
    }

    private val uploadSemaphore = kotlinx.coroutines.sync.Semaphore(3)
    private val inFlightTasksMutex = Mutex()
    private val inFlightTaskIds = mutableSetOf<String>()
    private val albumMutex = Mutex()
    private val _tasks = MutableStateFlow<Map<String, MediaOutboxTask>>(emptyMap())
    val tasks: StateFlow<Map<String, MediaOutboxTask>> = _tasks.asStateFlow()

    private val postMutex = Mutex()
    private val postJobsByLocalPostId = mutableMapOf<String, Job>()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }

    init {
        resumePendingTasks()
        observePendingMediaCommands()
        syncPosts()
    }

    private fun observePendingMediaCommands() {
        scope.launch(dispatcher) {
            localStore.observeActiveMediaCommands().collect { commands ->
                for (cmd in commands) {
                    val path = cmd.localFilePath ?: continue
                    val mime = cmd.mediaMimeType ?: "application/octet-stream"
                    if (_tasks.value.containsKey(cmd.clientMessageId)) continue

                    enqueue(
                        clientMessageId = cmd.clientMessageId,
                        conversationId = cmd.conversationId,
                        filePath = path,
                        mimeType = mime,
                        sizeBytes = cmd.mediaSizeBytes,
                        durationMs = cmd.mediaDurationMs,
                        width = cmd.mediaWidth,
                        height = cmd.mediaHeight,
                        waveform = cmd.mediaWaveform?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                        caption = cmd.text,
                        groupId = cmd.groupId,
                        position = cmd.mediaPosition,
                        replyToMessageId = cmd.replyToMessageId,
                        thumbnailUrl = cmd.mediaThumbnailUrl,
                    )
                }
            }
        }
    }

    fun resumePendingTasks() {
        scope.launch(dispatcher) {
            val pendingCommands = runCatching { localStore.getActiveMediaCommands() }.getOrDefault(emptyList())
            for (cmd in pendingCommands) {
                val path = cmd.localFilePath ?: continue
                val mime = cmd.mediaMimeType ?: "application/octet-stream"
                if (_tasks.value.containsKey(cmd.clientMessageId)) continue

                enqueue(
                    clientMessageId = cmd.clientMessageId,
                    conversationId = cmd.conversationId,
                    filePath = path,
                    mimeType = mime,
                    sizeBytes = cmd.mediaSizeBytes,
                    durationMs = cmd.mediaDurationMs,
                    width = cmd.mediaWidth,
                    height = cmd.mediaHeight,
                    waveform = cmd.mediaWaveform?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList(),
                    caption = cmd.text,
                    groupId = cmd.groupId,
                    position = cmd.mediaPosition,
                    replyToMessageId = cmd.replyToMessageId,
                )
            }
        }
    }

    fun enqueue(
        clientMessageId: String,
        conversationId: Long,
        filePath: String,
        mimeType: String,
        sizeBytes: Long = 0L,
        durationMs: Long = 0L,
        width: Int = 0,
        height: Int = 0,
        waveform: List<Int> = emptyList(),
        caption: String? = null,
        groupId: String? = null,
        position: Int = 0,
        replyToMessageId: Long? = null,
        thumbnailUrl: String? = null,
        mediaKind: String? = null,
    ) {
        val task = MediaOutboxTask(
            clientMessageId = clientMessageId,
            conversationId = conversationId,
            filePath = filePath,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            width = width,
            height = height,
            waveform = waveform,
            caption = caption,
            groupId = groupId,
            position = position,
            replyToMessageId = replyToMessageId,
            thumbnailUrl = thumbnailUrl,
            mediaKind = mediaKind,
            status = MediaOutboxStatus.Queued,
            progress = 0f,
        )
        _tasks.update { it + (clientMessageId to task) }

        scope.launch(dispatcher) {
            processTask(clientMessageId)
        }
    }

    suspend fun processTask(clientMessageId: String): Boolean {
        val canStart = inFlightTasksMutex.withLock {
            val currentTask = _tasks.value[clientMessageId] ?: return false
            if (currentTask.status == MediaOutboxStatus.Sent || currentTask.status == MediaOutboxStatus.Cancelled) {
                return false
            }
            if (inFlightTaskIds.contains(clientMessageId)) {
                return false
            }
            inFlightTaskIds.add(clientMessageId)
            true
        }
        if (!canStart) return false

        return try {
            uploadSemaphore.withPermit {
                executeTaskUpload(clientMessageId)
            }
        } finally {
            inFlightTasksMutex.withLock {
                inFlightTaskIds.remove(clientMessageId)
            }
        }
    }

    private suspend fun executeTaskUpload(clientMessageId: String): Boolean {
        val currentTask = _tasks.value[clientMessageId] ?: return false
        if (currentTask.status == MediaOutboxStatus.Sent || currentTask.status == MediaOutboxStatus.Cancelled) {
            return false
        }

        updateTaskStatus(clientMessageId, MediaOutboxStatus.Uploading, progress = 0.05f)
        localStore.updateOutboxMediaStage(
            clientMessageId = clientMessageId,
            uploadStage = DirectChatUploadStage.UPLOADING,
        )

        val fileName = currentTask.filePath.substringAfterLast('/').ifBlank { "media_file" }
        val presignKind = currentTask.mediaKind ?: when {
            currentTask.mimeType.startsWith("video") -> "chat_video_circle"
            currentTask.mimeType.startsWith("audio") -> "chat_voice"
            currentTask.mimeType.startsWith("image") -> "chat_image"
            else -> "chat_image"
        }

        val fileReader = me.floow.api.util.PlatformFileReader(currentTask.filePath)
        if (currentTask.sizeBytes <= 0L && !fileReader.exists) {
            handleFailure(clientMessageId, currentTask, "Local media file does not exist or unreadable: ${currentTask.filePath}")
            return false
        }
        val resolvedSizeBytes = if (currentTask.sizeBytes > 0L) currentTask.sizeBytes else fileReader.length.coerceAtLeast(1L)

        val presignResult = runCatching {
            uploadsApi.createPresign(
                CreateUploadPresignData(
                    fileName = fileName,
                    contentType = currentTask.mimeType,
                    sizeBytes = resolvedSizeBytes,
                    kind = presignKind,
                )
            )
        }.getOrNull()

        val presign = when (presignResult) {
            is CreateUploadPresignResponse.Success -> presignResult
            else -> {
                handleFailure(clientMessageId, currentTask, "Failed to obtain upload presigned URL")
                return false
            }
        }

        updateTaskStatus(clientMessageId, MediaOutboxStatus.Uploading, progress = 0.1f)

        val uploadSuccess = runCatching {
            uploadsApi.uploadFilePath(
                uploadUrl = presign.uploadUrl,
                contentType = currentTask.mimeType,
                filePath = currentTask.filePath,
                onProgress = { progressFraction ->
                    val normalizedProgress = 0.1f + progressFraction * 0.85f
                    updateTaskProgress(clientMessageId, normalizedProgress)
                }
            )
        }.getOrDefault(false)

        if (!uploadSuccess) {
            handleFailure(clientMessageId, currentTask, "Chunked media upload failed")
            return false
        }

        _tasks.update { map ->
            val t = map[clientMessageId] ?: return@update map
            map + (clientMessageId to t.copy(
                remoteMediaUrl = presign.fileUrl,
                mediaObjectKey = presign.objectKey,
                progress = 0.95f,
            ))
        }

        localStore.updateOutboxMediaStage(
            clientMessageId = clientMessageId,
            uploadStage = DirectChatUploadStage.UPLOADING,
            mediaUrl = presign.fileUrl,
        )

        val api = chatsApi
        if (api == null || currentTask.conversationId <= 0L) {
            _tasks.update { map ->
                val t = map[clientMessageId] ?: return@update map
                map + (clientMessageId to t.copy(
                    status = MediaOutboxStatus.Sent,
                    progress = 1.0f,
                    remoteMediaUrl = presign.fileUrl,
                    mediaObjectKey = presign.objectKey,
                    error = null,
                ))
            }
            localStore.updateOutboxMediaStage(
                clientMessageId = clientMessageId,
                uploadStage = DirectChatUploadStage.SENT,
                mediaUrl = presign.fileUrl,
            )
            localStore.updateMessageDeliveryStatusByClientMessageId(
                conversationId = currentTask.conversationId,
                clientMessageId = clientMessageId,
                deliveryStatus = MessageDeliveryStatus.SENT,
            )
            return true
        }

        val targetGroupId = currentTask.groupId
        if (targetGroupId.isNullOrBlank()) {
            // Одиночное медиа (фото, видеокружок, аудио)
            val sendResult = runCatching {
                when {
                    currentTask.mimeType.startsWith("video") -> {
                        val safeThumbnailUrl = currentTask.thumbnailUrl?.takeIf { url ->
                            url.startsWith("http://", ignoreCase = true) ||
                                url.startsWith("https://", ignoreCase = true) ||
                                url.startsWith("data:", ignoreCase = true)
                        }
                        api.sendVideoCircleMessage(
                            conversationId = currentTask.conversationId,
                            clientMessageId = clientMessageId,
                            mediaUrl = presign.fileUrl,
                            mediaObjectKey = presign.objectKey,
                            mediaSizeBytes = resolvedSizeBytes.coerceAtLeast(1L),
                            mediaDurationMs = currentTask.durationMs.coerceAtLeast(500L),
                            mediaWidth = if (currentTask.width > 0) currentTask.width else 480,
                            mediaHeight = if (currentTask.height > 0) currentTask.height else 480,
                            thumbnailUrl = safeThumbnailUrl,
                            replyToMessageId = currentTask.replyToMessageId,
                        )
                    }
                    currentTask.mimeType.startsWith("audio") -> {
                        api.sendVoiceMessage(
                            conversationId = currentTask.conversationId,
                            clientMessageId = clientMessageId,
                            mediaUrl = presign.fileUrl,
                            mediaObjectKey = presign.objectKey,
                            mediaSizeBytes = resolvedSizeBytes,
                            mediaDurationMs = currentTask.durationMs,
                            waveform = currentTask.waveform,
                            replyToMessageId = currentTask.replyToMessageId,
                        )
                    }
                    currentTask.mimeType.startsWith("image") -> {
                        api.sendImageMessage(
                            conversationId = currentTask.conversationId,
                            clientMessageId = clientMessageId,
                            mediaUrl = presign.fileUrl,
                            mediaObjectKey = presign.objectKey,
                            mediaSizeBytes = resolvedSizeBytes,
                            mediaWidth = currentTask.width,
                            mediaHeight = currentTask.height,
                            caption = currentTask.caption,
                            groupId = null,
                            position = currentTask.position,
                            replyToMessageId = currentTask.replyToMessageId,
                            thumbnailUrl = currentTask.thumbnailUrl,
                        )
                    }
                    else -> {
                        api.sendMessage(
                            conversationId = currentTask.conversationId,
                            text = currentTask.caption.orEmpty(),
                            clientMessageId = clientMessageId,
                            replyToMessageId = currentTask.replyToMessageId,
                        )
                    }
                }
            }.getOrNull()

            val isSuccess = when (sendResult) {
                is ChatsSendMessageResponse.Success -> true
                is ChatsSendMessageResponse.Conflict -> sendResult.existingMessage != null
                else -> false
            }

            if (!isSuccess) {
                handleFailure(clientMessageId, currentTask, "ChatsApi message sending failed")
                return false
            }

            val serverMsg = when (sendResult) {
                is ChatsSendMessageResponse.Success -> sendResult.message.toDirectChatMessage()
                is ChatsSendMessageResponse.Conflict -> sendResult.existingMessage?.toDirectChatMessage()
                else -> null
            }
            if (serverMsg != null) {
                localStore.markOutgoingSent(clientMessageId, serverMsg)
            }

            _tasks.update { map ->
                val t = map[clientMessageId] ?: return@update map
                map + (clientMessageId to t.copy(
                    status = MediaOutboxStatus.Sent,
                    progress = 1.0f,
                    remoteMediaUrl = presign.fileUrl,
                    mediaObjectKey = presign.objectKey,
                    error = null,
                ))
            }

            localStore.updateOutboxMediaStage(
                clientMessageId = clientMessageId,
                uploadStage = DirectChatUploadStage.SENT,
                mediaUrl = presign.fileUrl,
            )
            localStore.updateMessageDeliveryStatusByClientMessageId(
                conversationId = currentTask.conversationId,
                clientMessageId = clientMessageId,
                deliveryStatus = MessageDeliveryStatus.SENT,
            )
            return true
        }

        // Альбом: синхронизация и единая отправка через sendAlbumMessage (BUG 3, BUG 4)
        return albumMutex.withLock {
            val currentMap = _tasks.value
            val albumTasks = currentMap.values.filter { it.groupId == targetGroupId }
            val allUploaded = albumTasks.isNotEmpty() && albumTasks.all { it.remoteMediaUrl != null || it.status == MediaOutboxStatus.Sent }
            if (!allUploaded) {
                // Другие элементы альбома еще загружаются параллельно в S3
                return@withLock true
            }

            val unsentTasks = albumTasks.filter { it.status != MediaOutboxStatus.Sent }
            if (unsentTasks.isEmpty()) {
                return@withLock true
            }

            val sortedTasks = albumTasks.sortedBy { it.position }
            val albumItems = sortedTasks.map { t ->
                me.floow.domain.api.models.ChatAlbumItemPayload(
                    clientMessageId = t.clientMessageId,
                    mediaUrl = t.remoteMediaUrl ?: presign.fileUrl,
                    mediaObjectKey = t.mediaObjectKey ?: presign.objectKey,
                    mediaSizeBytes = t.sizeBytes.coerceAtLeast(1L),
                    mediaWidth = t.width,
                    mediaHeight = t.height,
                    mediaThumbnailUrl = t.thumbnailUrl,
                )
            }

            val caption = sortedTasks.firstNotNullOfOrNull { it.caption?.takeIf(String::isNotBlank) }
            val albumResponse = runCatching {
                api.sendAlbumMessage(
                    conversationId = currentTask.conversationId,
                    items = albumItems,
                    caption = caption,
                    replyToMessageId = currentTask.replyToMessageId,
                    idempotencyKey = targetGroupId,
                )
            }.getOrNull()

            val isAlbumSuccess = when (albumResponse) {
                is me.floow.domain.api.models.ChatsSendAlbumMessageResponse.Success -> true
                is me.floow.domain.api.models.ChatsSendAlbumMessageResponse.Conflict -> albumResponse.existingMessages.isNotEmpty()
                else -> false
            }

            if (!isAlbumSuccess) {
                handleFailure(clientMessageId, currentTask, "ChatsApi sendAlbumMessage failed")
                return@withLock false
            }

            val serverMessages = when (albumResponse) {
                is me.floow.domain.api.models.ChatsSendAlbumMessageResponse.Success -> albumResponse.messages
                is me.floow.domain.api.models.ChatsSendAlbumMessageResponse.Conflict -> albumResponse.existingMessages
                else -> emptyList()
            }
            val messagesByClient = serverMessages.associateBy { it.clientMessageId }

            for ((idx, t) in sortedTasks.withIndex()) {
                val serverItem = messagesByClient[t.clientMessageId] ?: serverMessages.getOrNull(idx)
                val directMsg = serverItem?.toDirectChatMessage()
                if (directMsg != null) {
                    localStore.markOutgoingSent(t.clientMessageId, directMsg)
                }
                localStore.updateOutboxMediaStage(
                    clientMessageId = t.clientMessageId,
                    uploadStage = DirectChatUploadStage.SENT,
                    mediaUrl = t.remoteMediaUrl ?: presign.fileUrl,
                )
                localStore.updateMessageDeliveryStatusByClientMessageId(
                    conversationId = t.conversationId,
                    clientMessageId = t.clientMessageId,
                    deliveryStatus = MessageDeliveryStatus.SENT,
                )
                _tasks.update { map ->
                    val existing = map[t.clientMessageId] ?: return@update map
                    map + (t.clientMessageId to existing.copy(
                        status = MediaOutboxStatus.Sent,
                        progress = 1.0f,
                        error = null,
                    ))
                }
            }
            true
        }
    }

    fun cancel(clientMessageId: String) {
        _tasks.update { map ->
            val t = map[clientMessageId] ?: return@update map
            map + (clientMessageId to t.copy(status = MediaOutboxStatus.Cancelled))
        }
        scope.launch(dispatcher) {
            localStore.cancelOutgoing(
                conversationId = -1,
                clientMessageId = clientMessageId,
            )
        }
    }

    fun retry(clientMessageId: String) {
        val task = _tasks.value[clientMessageId]
        if (task != null) {
            _tasks.update { map ->
                map + (clientMessageId to task.copy(
                    status = MediaOutboxStatus.Queued,
                    progress = 0f,
                    attemptCount = 0,
                    error = null,
                ))
            }
            scope.launch(dispatcher) {
                processTask(clientMessageId)
            }
        }
    }

    private suspend fun handleFailure(
        clientMessageId: String,
        task: MediaOutboxTask,
        errorMessage: String
    ) {
        val nextAttempt = task.attemptCount + 1
        val hasExceededRetries = nextAttempt >= maxRetries

        _tasks.update { map ->
            val t = map[clientMessageId] ?: return@update map
            map + (clientMessageId to t.copy(
                status = if (hasExceededRetries) MediaOutboxStatus.Failed else MediaOutboxStatus.Queued,
                attemptCount = nextAttempt,
                error = errorMessage,
            ))
        }

        if (hasExceededRetries) {
            localStore.updateOutboxMediaStage(
                clientMessageId = clientMessageId,
                uploadStage = DirectChatUploadStage.FAILED,
                error = errorMessage,
            )
            localStore.updateMessageDeliveryStatusByClientMessageId(
                conversationId = task.conversationId,
                clientMessageId = clientMessageId,
                deliveryStatus = MessageDeliveryStatus.FAILED,
            )
        } else {
            val delayMs = retryBackoffBaseMs * (1L shl (nextAttempt - 1).coerceAtMost(5))
            scope.launch(dispatcher) {
                delay(delayMs)
                val current = _tasks.value[clientMessageId]
                if (current != null && current.status == MediaOutboxStatus.Queued) {
                    processTask(clientMessageId)
                }
            }
        }
    }

    private fun updateTaskStatus(clientMessageId: String, status: MediaOutboxStatus, progress: Float) {
        _tasks.update { map ->
            val t = map[clientMessageId] ?: return@update map
            map + (clientMessageId to t.copy(status = status, progress = progress))
        }
    }

    private fun updateTaskProgress(clientMessageId: String, progress: Float) {
        _tasks.update { map ->
            val t = map[clientMessageId] ?: return@update map
            map + (clientMessageId to t.copy(progress = progress.coerceIn(0f, 1f)))
        }
    }

    fun cancelPost(localPostId: String) {
        scope.launch(dispatcher) {
            val job = postMutex.withLock { postJobsByLocalPostId.remove(localPostId) }
            job?.cancel()

            val store = pendingPostsLocalStore ?: return@launch
            val media = pendingPostMediaStore ?: return@launch
            val pending = store.getByLocalPostId(localPostId) ?: return@launch
            store.delete(pending.commandId)
            media.deleteStaged(pending.commandId)
        }
    }

    fun retryPost(localPostId: String) {
        scope.launch(dispatcher) {
            val store = pendingPostsLocalStore ?: return@launch
            val pending = store.getByLocalPostId(localPostId)
                ?: store.getByRemotePostId(localPostId)
                ?: return@launch
            val now = me.floow.domain.utils.currentTimeMillis()
            store.retry(commandId = pending.commandId, nextRetryAt = now, updatedAt = now)
            syncPosts()
        }
    }

    fun syncPosts() {
        val store = pendingPostsLocalStore ?: return
        val postsRepo = postsRepository ?: return
        val uploadsRepo = uploadsRepository ?: return
        val postsStore = postsLocalStore ?: return
        val mediaStore = pendingPostMediaStore ?: return

        scope.launch(dispatcher) {
            val now = me.floow.domain.utils.currentTimeMillis()
            val dueCommands = runCatching { store.getDueCommands(now) }.getOrDefault(emptyList())
            for (command in dueCommands) {
                val localId = command.localPostId
                val shouldSkip = postMutex.withLock {
                    val existingJob = postJobsByLocalPostId[localId]
                    existingJob != null && existingJob.isActive
                }
                if (shouldSkip) continue

                val job = launch {
                    when (command.type) {
                        PendingPostMutationType.CREATE -> processPostCreate(command, store, postsRepo, uploadsRepo, postsStore, mediaStore)
                        PendingPostMutationType.UPDATE -> processPostUpdate(command, store, postsRepo, uploadsRepo, postsStore, mediaStore)
                        PendingPostMutationType.DELETE -> Unit
                    }
                }
                postMutex.withLock {
                    postJobsByLocalPostId[localId] = job
                }
                job.invokeOnCompletion {
                    scope.launch(dispatcher) {
                        postMutex.withLock {
                            if (postJobsByLocalPostId[localId] === job) {
                                postJobsByLocalPostId.remove(localId)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun processPostCreate(
        command: PendingPostMutation,
        pendingStore: PendingPostsLocalStore,
        postsRepository: PostsRepository,
        uploadsRepository: UploadsRepository,
        postsLocalStore: PostsLocalStore,
        mediaStore: PendingPostMediaStore,
    ) {
        val payload = ensureRemotePayload(command, pendingStore, uploadsRepository, mediaStore)
            ?: return failPost(command, pendingStore, "Failed to upload staged media")
        val category = command.category ?: return failPost(command, pendingStore, "Missing category")
        val now = me.floow.domain.utils.currentTimeMillis()
        pendingStore.markState(command.commandId, PendingPostMutationState.SYNCING, now)
        val response = postsRepository.createPost(
            CreatePostData(
                imageUrls = payload.entries.mapNotNull(PendingPostImageEntry::remoteUrl),
                description = command.description,
                category = category,
                idempotencyKey = command.clientMutationId,
            )
        )
        when (response) {
            is UpdateDataResponse.Success -> {
                val post = response.post
                if (post != null) {
                    postsLocalStore.upsertPost(
                        userId = "me",
                        post = post,
                        updatedAt = me.floow.domain.utils.currentTimeMillis(),
                    )
                }
                pendingStore.delete(command.commandId)
                mediaStore.deleteStaged(command.commandId)
            }
            is UpdateDataResponse.Failure -> failPost(
                command = command,
                pendingStore = pendingStore,
                error = response.message ?: "Не удалось отправить пост",
            )
        }
    }

    private suspend fun processPostUpdate(
        command: PendingPostMutation,
        pendingStore: PendingPostsLocalStore,
        postsRepository: PostsRepository,
        uploadsRepository: UploadsRepository,
        postsLocalStore: PostsLocalStore,
        mediaStore: PendingPostMediaStore,
    ) {
        val remotePostId = command.remotePostId ?: return failPost(command, pendingStore, "Missing remote post id")
        val payload = ensureRemotePayload(command, pendingStore, uploadsRepository, mediaStore)
            ?: return failPost(command, pendingStore, "Failed to upload staged media")
        val now = me.floow.domain.utils.currentTimeMillis()
        pendingStore.markState(command.commandId, PendingPostMutationState.SYNCING, now)
        val response = postsRepository.updatePost(
            postId = remotePostId,
            description = command.description,
            imageUrls = payload.entries.mapNotNull(PendingPostImageEntry::remoteUrl),
            category = command.category,
        )
        when (response) {
            is UpdateDataResponse.Success -> {
                val post = response.post
                if (post != null) {
                    postsLocalStore.upsertPost(
                        userId = "me",
                        post = post,
                        updatedAt = me.floow.domain.utils.currentTimeMillis(),
                    )
                }
                pendingStore.delete(command.commandId)
                mediaStore.deleteStaged(command.commandId)
            }
            is UpdateDataResponse.Failure -> failPost(
                command = command,
                pendingStore = pendingStore,
                error = response.message ?: "Не удалось сохранить изменения",
            )
        }
    }

    private suspend fun ensureRemotePayload(
        command: PendingPostMutation,
        pendingStore: PendingPostsLocalStore,
        uploadsRepository: UploadsRepository,
        mediaStore: PendingPostMediaStore,
    ): PendingPostImagePayload? {
        val currentPayload = decodeImagePayload(command.imagePayloadJson)
        if (currentPayload.entries.none { it.kind == PendingPostImageEntryKind.STAGED }) {
            return currentPayload
        }
        val stagedMedia = decodeStagedMedia(command.stagedMediaJson)
        val stagedById = stagedMedia.associateBy(PendingPostStagedMedia::id)
        val uploadData = currentPayload.entries.mapNotNull { entry ->
            if (entry.kind != PendingPostImageEntryKind.STAGED) return@mapNotNull null
            val media = stagedById[entry.stagedMediaId] ?: return null
            mediaStore.readUploadData(media)
        }
        if (uploadData.size != currentPayload.entries.count { it.kind == PendingPostImageEntryKind.STAGED }) {
            return null
        }
        val now = me.floow.domain.utils.currentTimeMillis()
        pendingStore.markState(command.commandId, PendingPostMutationState.UPLOADING, now)
        val uploadedUrls = when (
            val result = uploadsRepository.uploadImages(
                data = uploadData,
                kind = "post",
                onProgress = null,
            )
        ) {
            is GetDataResponse.Success -> result.data
            is GetDataResponse.Error -> return null
        }
        val uploadedIterator = uploadedUrls.iterator()
        val nextPayload = PendingPostImagePayload(
            entries = currentPayload.entries.map { entry ->
                if (entry.kind == PendingPostImageEntryKind.REMOTE) {
                    entry
                } else {
                    PendingPostImageEntry(
                        kind = PendingPostImageEntryKind.REMOTE,
                        remoteUrl = uploadedIterator.next(),
                    )
                }
            }
        )
        pendingStore.updateUploadedPayload(
            commandId = command.commandId,
            imagePayloadJson = json.encodeToString(PendingPostImagePayload.serializer(), nextPayload),
            updatedAt = me.floow.domain.utils.currentTimeMillis(),
        )
        return nextPayload
    }

    private fun decodeImagePayload(rawJson: String?): PendingPostImagePayload {
        if (rawJson.isNullOrBlank()) return PendingPostImagePayload()
        return runCatching {
            json.decodeFromString(PendingPostImagePayload.serializer(), rawJson)
        }.getOrDefault(PendingPostImagePayload())
    }

    private fun decodeStagedMedia(rawJson: String?): List<PendingPostStagedMedia> {
        if (rawJson.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(PendingPostStagedMedia.serializer()),
                rawJson
            )
        }.getOrDefault(emptyList())
    }

    private suspend fun failPost(
        command: PendingPostMutation,
        pendingStore: PendingPostsLocalStore,
        error: String,
    ) {
        val now = me.floow.domain.utils.currentTimeMillis()
        val nextRetryAt = now + 10_000L
        pendingStore.markFailed(
            commandId = command.commandId,
            error = error,
            retryCount = command.retryCount + 1,
            nextRetryAt = nextRetryAt,
            updatedAt = now,
        )
    }
}

private fun me.floow.domain.api.models.ChatMessageItem.toDirectChatMessage(): me.floow.domain.models.DirectChatMessage {
    return me.floow.domain.models.DirectChatMessage(
        id = id,
        conversationId = conversationId,
        sender = me.floow.domain.models.DirectChatPeer(
            id = sender.id,
            name = sender.name,
            username = sender.username,
            avatarUrl = sender.avatar,
            avatarThumb = sender.avatarThumb,
        ),
        text = text,
        contentType = contentType,
        media = media?.let { m ->
            me.floow.domain.models.DirectChatMessageMedia(
                url = m.url,
                objectKey = m.objectKey,
                mimeType = m.mimeType,
                sizeBytes = m.sizeBytes,
                durationMs = m.durationMs,
                width = m.width,
                height = m.height,
                thumbnailUrl = m.thumbnailUrl,
                waveform = m.waveform,
            )
        },
        clientMessageId = clientMessageId,
        replyToMessageId = replyToMessageId,
        replyToMessageText = replyToMessageText,
        isPinned = isPinned,
        pinnedAt = pinnedAt,
        pinnedByUserId = pinnedByUserId,
        deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT,
        createdAt = createdAt,
        updatedAt = updatedAt,
        groupId = groupId ?: media?.groupId,
        position = if (position > 0) position else (media?.position ?: 0),
    )
}

