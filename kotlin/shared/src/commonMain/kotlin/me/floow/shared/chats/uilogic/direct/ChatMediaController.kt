package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.floow.api.util.PlatformFileReader
import me.floow.domain.utils.Logger
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.shared.media.outbox.MediaOutboxEngine
import me.floow.shared.media.outbox.MediaOutboxStatus
import me.floow.shared.media.outbox.MediaOutboxTask
import me.floow.uikit.chat.model.VideoCirclePlaybackUiState

data class ActiveVideoCirclePlayback(
	val uiKey: String? = null,
	val messageId: Long? = null,
	val isPaused: Boolean = false,
	val progress: Float = 0f,
	val positionMs: Long = 0L,
	val durationMs: Long = 0L,
)

class ChatMediaController(
	private val repository: ChatThreadRepository,
	private val mediaOutboxEngine: MediaOutboxEngine?,
	private val mediaDownloadController: me.floow.domain.media.MediaDownloadController?,
	private val timelineReducer: ChatTimelineReducer,
	private val stateFlow: MutableStateFlow<DirectChatScreenState>,
	private val scope: CoroutineScope,
	private val logger: Logger? = null,
	private val nextScrollRequestToken: () -> Long,
	private val onActivityChanged: () -> Unit,
	private val activeConversationIdOrNull: () -> Long?,
) {
	val mediaOutboxTasks: StateFlow<Map<String, MediaOutboxTask>> =
		mediaOutboxEngine?.tasks ?: MutableStateFlow(emptyMap())

	val mediaDownloadStates: StateFlow<Map<String, me.floow.domain.media.MediaDownloadState>> =
		mediaDownloadController?.states ?: MutableStateFlow(emptyMap())

	var localRecordingStartedAtMillis: Long? = null
		private set
	private var outgoingRecordingJob: Job? = null

	val localVideoCircleMessages = LinkedHashMap<String, ChatMessageItemModel>()
	val localVoiceMessages = LinkedHashMap<String, ChatMessageItemModel>()
	val localImageMessages = LinkedHashMap<String, ChatMessageItemModel>()

	private val _activeVideoCircle = MutableStateFlow(ActiveVideoCirclePlayback())
	val activeVideoCircle: StateFlow<ActiveVideoCirclePlayback> = _activeVideoCircle.asStateFlow()

	fun toggleVideoCircle(uiKey: String, messageId: Long) {
		val current = _activeVideoCircle.value
		if (current.uiKey == uiKey) {
			_activeVideoCircle.value = current.copy(isPaused = !current.isPaused)
		} else {
			_activeVideoCircle.value = ActiveVideoCirclePlayback(
				uiKey = uiKey,
				messageId = messageId,
				isPaused = false,
			)
		}
	}

	fun setVideoCirclePaused(paused: Boolean) {
		val current = _activeVideoCircle.value
		if (current.uiKey != null) {
			_activeVideoCircle.value = current.copy(isPaused = paused)
		}
	}

	fun updateVideoCircleProgress(progress: Float, positionMs: Long = 0L, durationMs: Long = 0L) {
		_activeVideoCircle.update { current ->
			if (current.uiKey == null) current
			else current.copy(progress = progress.coerceIn(0f, 1f), positionMs = positionMs, durationMs = durationMs)
		}
	}

	fun clearActiveVideoCircle() {
		_activeVideoCircle.value = ActiveVideoCirclePlayback()
	}

	fun resolveVideoCircleUiState(
		active: ActiveVideoCirclePlayback,
		uiKey: String,
		isUploading: Boolean,
		isFailed: Boolean,
	): VideoCirclePlaybackUiState {
		if (isUploading || isFailed) return VideoCirclePlaybackUiState.Uploading
		if (active.uiKey == uiKey) {
			return if (active.isPaused) VideoCirclePlaybackUiState.ActivePaused else VideoCirclePlaybackUiState.ActivePlaying
		}
		return VideoCirclePlaybackUiState.IdlePreview
	}

	init {
		mediaOutboxEngine?.tasks?.let { tasksFlow ->
			scope.launch {
				tasksFlow.map { tasks ->
					tasks.filterValues { it.status == MediaOutboxStatus.Sent || it.status == MediaOutboxStatus.Failed }
				}.distinctUntilChanged().collect(::onMediaOutboxTasksChanged)
			}
		}
	}

	fun downloadImage(url: String) {
		mediaDownloadController?.enqueue(
			url,
			me.floow.domain.media.DownloadableMediaType.PHOTO,
			me.floow.domain.media.MediaDownloadPriority.VISIBLE,
		)
	}

	fun cancelImageDownload(url: String) {
		mediaDownloadController?.cancel(url)
	}

	fun seedMediaDownloadStates(messages: List<ChatMessageItemModel>) {
		val controller = mediaDownloadController ?: return
		for (msg in messages) {
			val img = msg.content as? ChatMessageContent.Image ?: continue
			val url = img.remoteUrl?.trim()?.takeIf(String::isNotEmpty) ?: continue
			if (img.localPath != null) {
				controller.registerLocalFile(url, img.localPath, me.floow.domain.media.DownloadableMediaType.PHOTO)
			} else {
				val state = controller.getDownloadState(url)
				if (state is me.floow.domain.media.MediaDownloadState.Idle) {
					controller.enqueueAutoDownload(
						url = url,
						mediaType = me.floow.domain.media.DownloadableMediaType.PHOTO,
						sizeBytes = img.sizeBytes ?: 0L,
					)
				}
			}
		}
	}

	fun onMediaOutboxTasksChanged(tasks: Map<String, MediaOutboxTask>) {
		if (tasks.isEmpty()) return
		stateFlow.update { state ->
			val current = state as? DirectChatScreenState.HasData ?: return@update state
			var hasChanges = false
			val updatedMessages = current.messages.map { msg ->
				val cmid = msg.clientMessageId ?: return@map msg
				val task = tasks[cmid] ?: return@map msg
				when (task.status) {
					MediaOutboxStatus.Sent -> {
						if (msg.deliveryState != ChatDeliveryState.SENT) {
							hasChanges = true
							val updatedContent = when (val c = msg.content) {
								is ChatMessageContent.Image -> c.copy(
									uploadState = VideoUploadState.Uploaded,
									remoteUrl = task.remoteMediaUrl ?: c.remoteUrl,
								)
								is ChatMessageContent.Voice -> c.copy(
									uploadState = VideoUploadState.Uploaded,
									remoteUrl = task.remoteMediaUrl ?: c.remoteUrl,
								)
								is ChatMessageContent.VideoCircle -> c.copy(
									uploadState = VideoUploadState.Uploaded,
									remoteUrl = task.remoteMediaUrl ?: c.remoteUrl,
								)
								else -> c
							}
							msg.copy(
								deliveryState = ChatDeliveryState.SENT,
								content = updatedContent,
							)
						} else msg
					}
					MediaOutboxStatus.Failed -> {
						if (msg.deliveryState != ChatDeliveryState.FAILED) {
							hasChanges = true
							val updatedContent = when (val c = msg.content) {
								is ChatMessageContent.Image -> c.copy(uploadState = VideoUploadState.Failed)
								is ChatMessageContent.Voice -> c.copy(uploadState = VideoUploadState.Failed)
								is ChatMessageContent.VideoCircle -> c.copy(uploadState = VideoUploadState.Failed)
								else -> c
							}
							msg.copy(
								deliveryState = ChatDeliveryState.FAILED,
								content = updatedContent,
							)
						} else msg
					}
					else -> msg
				}
			}
			if (hasChanges) {
				current.copy(messages = updatedMessages)
			} else {
				state
			}
		}
	}

	fun setLocalRecordingActive(isActive: Boolean) {
		val nextStartedAt = if (isActive) {
			localRecordingStartedAtMillis ?: currentChatEpochMillis()
		} else {
			null
		}
		if (localRecordingStartedAtMillis == nextStartedAt) return
		localRecordingStartedAtMillis = nextStartedAt
		onActivityChanged()
		val conversationId = activeConversationIdOrNull()
		if (conversationId != null && conversationId > 0L) {
			outgoingRecordingJob?.cancel()
			if (isActive) {
				outgoingRecordingJob = scope.launch {
					while (isActive) {
						repository.setTyping(conversationId, true, "recording_video")
						delay(2000L)
					}
				}
			} else {
				outgoingRecordingJob = null
				scope.launch {
					repository.setTyping(conversationId, false, "recording_video")
				}
			}
		}
	}

	fun retryMediaMessage(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		val current = stateFlow.value as? DirectChatScreenState.HasData ?: return false
		val targetMessage = current.messages.firstOrNull { it.clientMessageId == normalizedClientMessageId }
			?: localImageMessages[normalizedClientMessageId]
			?: localVoiceMessages[normalizedClientMessageId]
			?: localVideoCircleMessages[normalizedClientMessageId]
			?: return false

		val activeConversationId = current.conversationId.takeIf { it > 0L } ?: return false

		when (val content = targetMessage.content) {
			is ChatMessageContent.Image -> {
				markLocalImageUploading(normalizedClientMessageId)
				val outboxTask = mediaOutboxEngine?.tasks?.value?.get(normalizedClientMessageId)
				if (outboxTask != null) {
					mediaOutboxEngine.retry(normalizedClientMessageId)
				} else {
					val localPath = content.localPath ?: return false
					mediaOutboxEngine?.enqueue(
						clientMessageId = normalizedClientMessageId,
						conversationId = activeConversationId,
						filePath = localPath,
						mimeType = "image/jpeg",
						width = content.width,
						height = content.height,
						caption = targetMessage.text.takeIf(String::isNotBlank),
						groupId = content.groupId,
						position = content.position,
						replyToMessageId = targetMessage.replyToMessageId,
					)
				}
				return true
			}
			is ChatMessageContent.Voice -> {
				markLocalVoiceUploading(normalizedClientMessageId)
				val outboxTask = mediaOutboxEngine?.tasks?.value?.get(normalizedClientMessageId)
				if (outboxTask != null) {
					mediaOutboxEngine.retry(normalizedClientMessageId)
				} else {
					val localPath = content.localPath ?: return false
					mediaOutboxEngine?.enqueue(
						clientMessageId = normalizedClientMessageId,
						conversationId = activeConversationId,
						filePath = localPath,
						mimeType = "audio/m4a",
						durationMs = content.durationMs,
						waveform = content.waveform ?: emptyList(),
						replyToMessageId = targetMessage.replyToMessageId,
					)
				}
				return true
			}
			is ChatMessageContent.VideoCircle -> {
				markLocalVideoCircleUploading(normalizedClientMessageId)
				val outboxTask = mediaOutboxEngine?.tasks?.value?.get(normalizedClientMessageId)
				if (outboxTask != null) {
					mediaOutboxEngine.retry(normalizedClientMessageId)
				} else {
					val localPath = content.localPath ?: return false
					mediaOutboxEngine?.enqueue(
						clientMessageId = normalizedClientMessageId,
						conversationId = activeConversationId,
						filePath = localPath,
						mimeType = "video/mp4",
						durationMs = content.durationMs,
						width = content.width,
						height = content.height,
						replyToMessageId = targetMessage.replyToMessageId,
					)
				}
				return true
			}
			else -> return false
		}
	}

	fun addLocalVideoCircle(clip: RecordedClip, enqueueOutbox: Boolean = true): String {
		val videoPath = clip.path
		val current = stateFlow.value
        val draft = (current as? DirectChatScreenState.HasData)?.replyDraft
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> return ""
		}
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
		}
		val optimisticId = timelineReducer.nextOptimisticMessageId()
		val clientMessageId = timelineReducer.nextClientMessageId(optimisticId)
		val currentMessages = when (current) {
			is DirectChatScreenState.HasData -> current.messages
			is DirectChatScreenState.NoMessages -> emptyList()
		}
		val message = ChatMessageItemModel(
			id = optimisticId,
			clientMessageId = clientMessageId,
			senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self",
			text = "",
			createdAtMillis = timelineReducer.nextOutgoingCreatedAtMillis(currentMessages),
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENDING,
			replyToMessageId = draft?.messageId,
            replyToMessageText = draft?.preview,
            content = ChatMessageContent.VideoCircle(
				localPath = videoPath,
				durationMs = clip.durationMs.coerceAtLeast(500L),
				thumbnailPath = clip.thumbnailPath,
				width = if (clip.width > 0) clip.width else 480,
				height = if (clip.height > 0) clip.height else 480,
				uploadState = VideoUploadState.Pending,
			),
		)
		localVideoCircleMessages[clientMessageId] = message
		if (enqueueOutbox) {
			conversationId?.takeIf { it > 0L }?.let { activeConversationId ->
				scope.launch {
					repository.storeOptimisticOutgoingMessage(activeConversationId, header, message)
				}
				val fileReader = PlatformFileReader(videoPath)
				mediaOutboxEngine?.enqueue(
					clientMessageId = clientMessageId,
					conversationId = activeConversationId,
					filePath = videoPath,
					mimeType = "video/mp4",
					sizeBytes = fileReader.length,
					durationMs = clip.durationMs.coerceAtLeast(500L),
					width = if (clip.width > 0) clip.width else 480,
					height = if (clip.height > 0) clip.height else 480,
					thumbnailUrl = clip.thumbnailPath,
                    replyToMessageId = message.replyToMessageId,
				)
			}
		}
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> state.copy(
					messages = timelineReducer.upsertMessage(state.messages, message),
                    replyDraft = if (state.replyDraft == draft) null else state.replyDraft,
					scrollToBottomRequestToken = nextScrollRequestToken(),
				)
				is DirectChatScreenState.NoMessages -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: ChatTimelineReducer.LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = listOf(message),
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				}
				else -> state
			}
		}
		return "cmid_$clientMessageId"
	}

	fun updateLocalVideoCircleClip(clientMessageId: String, clip: RecordedClip) {
		updateLocalVideoCircleMessage(clientMessageId) { message ->
			val content = message.content as? ChatMessageContent.VideoCircle
			if (content != null) {
				message.copy(
					content = content.copy(
						localPath = clip.path,
						durationMs = clip.durationMs.coerceAtLeast(500L),
						thumbnailPath = clip.thumbnailPath ?: content.thumbnailPath,
						width = if (clip.width > 0) clip.width else 480,
						height = if (clip.height > 0) clip.height else 480,
					),
				)
			} else {
				message
			}
		}

		activeConversationIdOrNull()?.let { activeConversationId ->
			val header = when (val current = stateFlow.value) {
				is DirectChatScreenState.HasData -> current.header
				is DirectChatScreenState.NoMessages -> current.header
				else -> null
			}
			val updatedMsg = localVideoCircleMessages[clientMessageId]
			if (header != null && updatedMsg != null) {
				scope.launch {
					repository.storeOptimisticOutgoingMessage(activeConversationId, header, updatedMsg)
				}
			}
			scope.launch {
				var fileReader = PlatformFileReader(clip.path)
				var attempts = 0
				while ((!fileReader.exists || fileReader.length <= 0L) && attempts < 10) {
					delay(30)
					fileReader = PlatformFileReader(clip.path)
					attempts++
				}
				val finalLength = fileReader.length.coerceAtLeast(1L)
				mediaOutboxEngine?.enqueue(
					clientMessageId = clientMessageId,
					conversationId = activeConversationId,
					filePath = clip.path,
					mimeType = "video/mp4",
					sizeBytes = finalLength,
					durationMs = clip.durationMs.coerceAtLeast(500L),
					width = if (clip.width > 0) clip.width else 480,
					height = if (clip.height > 0) clip.height else 480,
                    replyToMessageId = updatedMsg?.replyToMessageId,
				)
			}
		}
	}

	fun markLocalVideoCircleUploading(clientMessageId: String) {
		updateLocalVideoCircleMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.SENDING,
				content = (message.content as? ChatMessageContent.VideoCircle)?.copy(
					uploadState = VideoUploadState.Uploading,
				) ?: message.content,
			)
		}
	}

	fun markLocalVideoCircleFailed(clientMessageId: String) {
		updateLocalVideoCircleMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.FAILED,
				content = (message.content as? ChatMessageContent.VideoCircle)?.copy(
					uploadState = VideoUploadState.Failed,
				) ?: message.content,
			)
		}
	}

	fun cancelLocalVideoCircle(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		timelineReducer.addCancelledClientMessageId(normalizedClientMessageId)
		mediaOutboxEngine?.cancel(normalizedClientMessageId)
		val localMsg = localVideoCircleMessages.remove(normalizedClientMessageId)
		val currentMsg = (stateFlow.value as? DirectChatScreenState.HasData)?.messages?.firstOrNull {
			it.clientMessageId == normalizedClientMessageId
		} ?: localMsg
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
		stateFlow.update { state ->
			if (state is DirectChatScreenState.HasData) {
				val remaining = state.messages.filterNot {
					it.clientMessageId == normalizedClientMessageId || (currentMsg != null && it.id == currentMsg.id)
				}
				if (remaining.isEmpty()) {
					DirectChatScreenState.NoMessages(
						conversationId = state.conversationId,
						header = state.header,
						input = state.input,
						scrollToBottomRequestToken = state.scrollToBottomRequestToken,
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				} else {
					state.copy(messages = remaining)
				}
			} else state
		}
		return true
	}

	fun flushPendingLocalVideoOutbox(conversationId: Long, header: ChatThreadHeaderModel) {
		localVideoCircleMessages.forEach { (clientMessageId, message) ->
			val content = message.content as? ChatMessageContent.VideoCircle ?: return@forEach
			val localPath = content.localPath ?: return@forEach
			if (content.uploadState == VideoUploadState.Pending && mediaOutboxEngine?.tasks?.value?.containsKey(clientMessageId) != true) {
				val fileReader = PlatformFileReader(localPath)
				if (fileReader.exists && fileReader.length > 0L) {
					scope.launch {
						repository.storeOptimisticOutgoingMessage(conversationId, header, message)
					}
					mediaOutboxEngine?.enqueue(
						clientMessageId = clientMessageId,
						conversationId = conversationId,
						filePath = localPath,
						mimeType = "video/mp4",
						sizeBytes = fileReader.length,
						durationMs = content.durationMs.coerceAtLeast(500L),
						width = if (content.width > 0) content.width else 480,
						height = if (content.height > 0) content.height else 480,
                        replyToMessageId = message.replyToMessageId,
					)
				}
			}
		}
	}

	fun resolveLocalVideoCircleSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		remoteThumbnailUrl: String?,
		durationMs: Long,
		width: Int,
		height: Int,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		if (timelineReducer.isCancelledClientMessageId(normalizedClientMessageId)) {
			localVideoCircleMessages.remove(normalizedClientMessageId)
			return
		}
		val localMessage = localVideoCircleMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.VideoCircle
		val resolvedContent = ChatMessageContent.VideoCircle(
			localPath = localContent?.localPath,
			remoteUrl = remoteUrl,
			durationMs = durationMs,
			thumbnailPath = localContent?.thumbnailPath,
			remoteThumbnailUrl = remoteThumbnailUrl ?: localContent?.remoteThumbnailUrl,
			width = if (width > 0) width else (localContent?.width ?: 0),
			height = if (height > 0) height else (localContent?.height ?: 0),
			uploadState = VideoUploadState.Uploaded,
		)
		val resolvedMessage = serverMessage.copy(
			clientMessageId = serverMessage.clientMessageId ?: normalizedClientMessageId,
			deliveryState = ChatDeliveryState.SENT,
			isOutgoing = true,
			content = resolvedContent,
		)
		localVideoCircleMessages.remove(normalizedClientMessageId)
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = timelineReducer.upsertMessage(state.messages, resolvedMessage)
					state.copy(
						messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
					)
				}
				else -> state
			}
		}
	}

	fun addLocalVoice(clip: RecordedVoiceClip): String {
		val voicePath = clip.filePath
		val current = stateFlow.value
        val draft = (current as? DirectChatScreenState.HasData)?.replyDraft
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> return ""
		}
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
		}
		val optimisticId = timelineReducer.nextOptimisticMessageId()
		val clientMessageId = timelineReducer.nextClientMessageId(optimisticId)
		val currentMessages = when (current) {
			is DirectChatScreenState.HasData -> current.messages
			is DirectChatScreenState.NoMessages -> emptyList()
		}
		val message = ChatMessageItemModel(
			id = optimisticId,
			clientMessageId = clientMessageId,
			senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self",
			text = "",
			createdAtMillis = timelineReducer.nextOutgoingCreatedAtMillis(currentMessages),
			isOutgoing = true,
			deliveryState = ChatDeliveryState.SENDING,
			replyToMessageId = draft?.messageId,
            replyToMessageText = draft?.preview,
            content = ChatMessageContent.Voice(
				localPath = voicePath,
				durationMs = clip.durationMs,
				waveform = clip.waveform,
				uploadState = VideoUploadState.Pending,
			),
		)
		localVoiceMessages[clientMessageId] = message
		conversationId?.takeIf { it > 0L }?.let { activeConversationId ->
			scope.launch(start = CoroutineStart.UNDISPATCHED) {
				repository.storeOptimisticOutgoingMessage(activeConversationId, header, message)
			}
			mediaOutboxEngine?.enqueue(
				clientMessageId = clientMessageId,
				conversationId = activeConversationId,
				filePath = voicePath,
				mimeType = "audio/m4a",
				durationMs = clip.durationMs,
				waveform = clip.waveform,
                replyToMessageId = message.replyToMessageId,
			)
		}
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> state.copy(
					messages = timelineReducer.upsertMessage(state.messages, message),
                    replyDraft = if (state.replyDraft == draft) null else state.replyDraft,
					scrollToBottomRequestToken = nextScrollRequestToken(),
				)
				is DirectChatScreenState.NoMessages -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: ChatTimelineReducer.LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = listOf(message),
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				}
				else -> state
			}
		}
		return "cmid_$clientMessageId"
	}

	fun addLocalImages(
		items: List<DirectChatStateHolder.LocalImageDraft>,
		groupId: String?,
		fallbackConversationId: Long? = null,
		fallbackHeader: ChatThreadHeaderModel? = null,
		replyToMessageId: Long? = null,
		enqueueOutbox: Boolean = true,
	): List<String> {
		if (items.isEmpty()) return emptyList()
		val current = stateFlow.value
        val draft = (current as? DirectChatScreenState.HasData)?.replyDraft
        val targetId = replyToMessageId ?: draft?.messageId
		val header = when (current) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> fallbackHeader ?: return emptyList()
		}
		val conversationId = when (current) {
			is DirectChatScreenState.HasData -> current.conversationId
			is DirectChatScreenState.NoMessages -> current.conversationId
			else -> fallbackConversationId
		}
		val currentMessages = when (current) {
			is DirectChatScreenState.HasData -> current.messages
			is DirectChatScreenState.NoMessages -> emptyList()
			else -> emptyList()
		}
		val baseCreatedAt = timelineReducer.nextOutgoingCreatedAtMillis(currentMessages)
		val clientMessageIds = ArrayList<String>(items.size)
		val newMessages = ArrayList<ChatMessageItemModel>(items.size)

		items.forEachIndexed { index, item ->
			val optimisticId = timelineReducer.nextOptimisticMessageId()
			val clientMessageId = timelineReducer.nextClientMessageId(optimisticId)
			clientMessageIds += clientMessageId
			val message = ChatMessageItemModel(
				id = optimisticId,
				clientMessageId = clientMessageId,
				senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: "self",
				text = item.caption.orEmpty(),
				createdAtMillis = baseCreatedAt + index,
				isOutgoing = true,
				replyToMessageId = targetId,
                replyToMessageText = draft?.takeIf { it.messageId == targetId }?.preview,
				deliveryState = ChatDeliveryState.SENDING,
				content = ChatMessageContent.Image(
					localPath = item.localPath,
					remoteUrl = null,
					width = item.width,
					height = item.height,
					groupId = groupId,
					position = index,
					uploadState = VideoUploadState.Uploading,
					thumbnailUrl = item.thumbnailUrl,
				),
			)
			localImageMessages[clientMessageId] = message
			newMessages += message

			conversationId?.takeIf { it > 0L }?.let { activeConversationId ->
				if (enqueueOutbox) {
					scope.launch(start = CoroutineStart.UNDISPATCHED) {
						repository.storeOptimisticOutgoingMessage(activeConversationId, header, message)
					}
					mediaOutboxEngine?.enqueue(
						clientMessageId = clientMessageId,
						conversationId = activeConversationId,
						filePath = item.localPath,
						mimeType = "image/jpeg",
						width = item.width,
						height = item.height,
						caption = item.caption,
						groupId = groupId,
						position = index,
						replyToMessageId = targetId,
						thumbnailUrl = item.thumbnailUrl,
					)
				}
			}
		}

		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					var updatedMessages = state.messages
					for (msg in newMessages) {
						updatedMessages = timelineReducer.upsertMessage(updatedMessages, msg)
					}
					state.copy(
						messages = updatedMessages,
                        replyDraft = if (state.replyDraft == draft) null else state.replyDraft,
						scrollToBottomRequestToken = nextScrollRequestToken(),
					)
				}
				is DirectChatScreenState.NoMessages -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: ChatTimelineReducer.LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = newMessages,
						scrollToBottomRequestToken = nextScrollRequestToken(),
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				}
				else -> {
					DirectChatScreenState.HasData(
						conversationId = conversationId ?: ChatTimelineReducer.LOCAL_ONLY_CONVERSATION_ID,
						header = header,
						messages = newMessages,
						scrollToBottomRequestToken = nextScrollRequestToken(),
					)
				}
			}
		}

		return clientMessageIds
	}

	fun addLocalImage(
		localPath: String,
		caption: String?,
		groupId: String?,
		position: Int,
		width: Int,
		height: Int,
		fallbackConversationId: Long? = null,
		fallbackHeader: ChatThreadHeaderModel? = null,
		replyToMessageId: Long? = null,
	): String {
		val ids = addLocalImages(
			items = listOf(
				DirectChatStateHolder.LocalImageDraft(
					localPath = localPath,
					caption = caption,
					width = width,
					height = height,
				)
			),
			groupId = groupId,
			fallbackConversationId = fallbackConversationId,
			fallbackHeader = fallbackHeader,
			replyToMessageId = replyToMessageId,
		)
		return ids.firstOrNull()?.let { "cmid_$it" }.orEmpty()
	}

	fun updateLocalImageSource(
		clientMessageId: String,
		localPath: String,
		width: Int,
		height: Int,
		thumbnailUrl: String? = null,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localImageMessages[normalizedClientMessageId] ?: return
		val updatedMessage = currentMessage.copy(
			content = (currentMessage.content as? ChatMessageContent.Image)?.copy(
				localPath = localPath,
				width = if (width > 0) width else (currentMessage.content as? ChatMessageContent.Image)?.width ?: 0,
				height = if (height > 0) height else (currentMessage.content as? ChatMessageContent.Image)?.height ?: 0,
				thumbnailUrl = thumbnailUrl ?: (currentMessage.content as? ChatMessageContent.Image)?.thumbnailUrl,
			) ?: currentMessage.content,
		)
		localImageMessages[normalizedClientMessageId] = updatedMessage
		stateFlow.update { state ->
			if (state is DirectChatScreenState.HasData) {
				state.copy(messages = timelineReducer.upsertMessage(state.messages, updatedMessage))
			} else state
		}
		activeConversationIdOrNull()?.let { conversationId ->
			val header = when (val s = stateFlow.value) {
				is DirectChatScreenState.HasData -> s.header
				is DirectChatScreenState.NoMessages -> s.header
				else -> null
			}
			if (header != null) {
				scope.launch {
					repository.storeOptimisticOutgoingMessage(conversationId, header, updatedMessage)
				}
			}
		}
	}

	fun enqueuePreparedImageOutbox(
		clientMessageId: String,
		filePath: String,
		width: Int,
		height: Int,
		caption: String? = null,
		groupId: String? = null,
		position: Int = 0,
		replyToMessageId: Long? = null,
		thumbnailUrl: String? = null,
	) {
		val conversationId = activeConversationIdOrNull() ?: return
		mediaOutboxEngine?.enqueue(
			clientMessageId = clientMessageId,
			conversationId = conversationId,
			filePath = filePath,
			mimeType = "image/jpeg",
			width = width,
			height = height,
			caption = caption,
			groupId = groupId,
			position = position,
			replyToMessageId = replyToMessageId,
			thumbnailUrl = thumbnailUrl,
		)
	}

	fun resolveLocalImageSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		width: Int,
		height: Int,
		groupId: String?,
		position: Int,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		if (timelineReducer.isCancelledClientMessageId(normalizedClientMessageId)) {
			localImageMessages.remove(normalizedClientMessageId)
			return
		}
		val localMessage = localImageMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.Image
		val fallbackExisting = (stateFlow.value as? DirectChatScreenState.HasData)?.messages?.find {
			it.clientMessageId?.trim() == normalizedClientMessageId || (serverMessage.id > 0L && it.id == serverMessage.id)
		}
		val existingContent = localContent ?: (fallbackExisting?.content as? ChatMessageContent.Image)
		val serverContent = serverMessage.content as? ChatMessageContent.Image

		val resolvedLocalPath = serverContent?.localPath?.takeIf { it.isNotBlank() }
			?: existingContent?.localPath?.takeIf { it.isNotBlank() }
			?: serverContent?.localPath
			?: existingContent?.localPath

		val resolvedWidth = if (width > 0) width else (serverContent?.width?.takeIf { it > 0 } ?: existingContent?.width ?: 0)
		val resolvedHeight = if (height > 0) height else (serverContent?.height?.takeIf { it > 0 } ?: existingContent?.height ?: 0)
		val resolvedThumbnailUrl = serverContent?.thumbnailUrl?.takeIf { it.isNotBlank() } ?: existingContent?.thumbnailUrl
		val resolvedGroupId = existingContent?.groupId?.takeIf { it.isNotBlank() } ?: groupId ?: serverContent?.groupId

		val resolvedContent = ChatMessageContent.Image(
			localPath = resolvedLocalPath,
			remoteUrl = remoteUrl,
			width = resolvedWidth,
			height = resolvedHeight,
			groupId = resolvedGroupId,
			position = if (position > 0) position else (serverContent?.position?.takeIf { it > 0 } ?: existingContent?.position ?: 0),
			uploadState = VideoUploadState.Uploaded,
			thumbnailUrl = resolvedThumbnailUrl,
			sizeBytes = serverContent?.sizeBytes ?: (existingContent?.sizeBytes ?: 0L),
		)
		val resolvedMessage = serverMessage.copy(
			clientMessageId = serverMessage.clientMessageId ?: normalizedClientMessageId,
			deliveryState = ChatDeliveryState.SENT,
			isOutgoing = true,
			content = resolvedContent,
		)
		localImageMessages.remove(normalizedClientMessageId)
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = timelineReducer.upsertMessage(state.messages, resolvedMessage)
					state.copy(
						messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
					)
				}
				else -> state
			}
		}
	}

	fun markLocalImageFailed(clientMessageId: String) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localImageMessages[normalizedClientMessageId] ?: return
		val updatedMessage = currentMessage.copy(
			deliveryState = ChatDeliveryState.FAILED,
			content = (currentMessage.content as? ChatMessageContent.Image)?.copy(
				uploadState = VideoUploadState.Failed
			) ?: currentMessage.content
		)
		localImageMessages[normalizedClientMessageId] = updatedMessage
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = timelineReducer.upsertMessage(state.messages, updatedMessage)
					state.copy(messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId))
				}
				else -> state
			}
		}
	}

	fun markLocalImageUploading(clientMessageId: String) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localImageMessages[normalizedClientMessageId] ?: return
		val updatedMessage = currentMessage.copy(
			deliveryState = ChatDeliveryState.SENDING,
			content = (currentMessage.content as? ChatMessageContent.Image)?.copy(
				uploadState = VideoUploadState.Uploading,
			) ?: currentMessage.content,
		)
		localImageMessages[normalizedClientMessageId] = updatedMessage
		stateFlow.update { state ->
			if (state is DirectChatScreenState.HasData) {
				state.copy(messages = timelineReducer.upsertMessage(state.messages, updatedMessage))
			} else state
		}
	}

	fun cancelLocalImage(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		timelineReducer.addCancelledClientMessageId(normalizedClientMessageId)
		mediaOutboxEngine?.cancel(normalizedClientMessageId)
		val localMsg = localImageMessages.remove(normalizedClientMessageId)
		val currentMsg = (stateFlow.value as? DirectChatScreenState.HasData)?.messages?.firstOrNull {
			it.clientMessageId == normalizedClientMessageId
		} ?: localMsg
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
		stateFlow.update { state ->
			if (state is DirectChatScreenState.HasData) {
				val remaining = state.messages.filterNot {
					it.clientMessageId == normalizedClientMessageId || (currentMsg != null && it.id == currentMsg.id)
				}
				if (remaining.isEmpty()) {
					DirectChatScreenState.NoMessages(
						conversationId = state.conversationId,
						header = state.header,
						input = state.input,
						scrollToBottomRequestToken = state.scrollToBottomRequestToken,
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				} else {
					state.copy(messages = remaining)
				}
			} else state
		}
		return true
	}

	fun markLocalVoiceUploading(clientMessageId: String) {
		updateLocalVoiceMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.SENDING,
				content = (message.content as? ChatMessageContent.Voice)?.copy(
					uploadState = VideoUploadState.Uploading,
				) ?: message.content,
			)
		}
	}

	fun markLocalVoiceFailed(clientMessageId: String) {
		updateLocalVoiceMessage(clientMessageId) { message ->
			message.copy(
				deliveryState = ChatDeliveryState.FAILED,
				content = (message.content as? ChatMessageContent.Voice)?.copy(
					uploadState = VideoUploadState.Failed,
				) ?: message.content,
			)
		}
	}

	fun cancelLocalVoice(clientMessageId: String): Boolean {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return false
		timelineReducer.addCancelledClientMessageId(normalizedClientMessageId)
		mediaOutboxEngine?.cancel(normalizedClientMessageId)
		val localMsg = localVoiceMessages.remove(normalizedClientMessageId)
		val currentMsg = (stateFlow.value as? DirectChatScreenState.HasData)?.messages?.firstOrNull {
			it.clientMessageId == normalizedClientMessageId
		} ?: localMsg
		activeConversationIdOrNull()?.let { conversationId ->
			scope.launch(NonCancellable) {
				withContext(NonCancellable) {
					repository.deleteLocalMessageByClientMessageId(conversationId, normalizedClientMessageId)
				}
			}
		}
		stateFlow.update { state ->
			if (state is DirectChatScreenState.HasData) {
				val remaining = state.messages.filterNot {
					it.clientMessageId == normalizedClientMessageId || (currentMsg != null && it.id == currentMsg.id)
				}
				if (remaining.isEmpty()) {
					DirectChatScreenState.NoMessages(
						conversationId = state.conversationId,
						header = state.header,
						input = state.input,
						scrollToBottomRequestToken = state.scrollToBottomRequestToken,
						peerIsOnline = state.peerIsOnline,
						peerLastSeenAtMillis = state.peerLastSeenAtMillis,
					)
				} else {
					state.copy(messages = remaining)
				}
			} else state
		}
		return true
	}

	fun resolveLocalVoiceSent(
		clientMessageId: String,
		serverMessage: ChatMessageItemModel,
		remoteUrl: String,
		durationMs: Long,
		waveform: List<Int>,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		if (timelineReducer.isCancelledClientMessageId(normalizedClientMessageId)) {
			localVoiceMessages.remove(normalizedClientMessageId)
			return
		}
		val localMessage = localVoiceMessages[normalizedClientMessageId]
		val localContent = localMessage?.content as? ChatMessageContent.Voice
		val resolvedContent = ChatMessageContent.Voice(
			localPath = localContent?.localPath,
			remoteUrl = remoteUrl,
			durationMs = durationMs,
			waveform = if (waveform.isNotEmpty()) waveform else (localContent?.waveform ?: emptyList()),
			uploadState = VideoUploadState.Uploaded,
		)
		val resolvedMessage = serverMessage.copy(
			clientMessageId = serverMessage.clientMessageId ?: normalizedClientMessageId,
			deliveryState = ChatDeliveryState.SENT,
			isOutgoing = true,
			content = resolvedContent,
		)
		localVoiceMessages.remove(normalizedClientMessageId)
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = timelineReducer.upsertMessage(state.messages, resolvedMessage)
					state.copy(
						messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId),
					)
				}
				else -> state
			}
		}
	}

	fun updateLocalVoiceMessage(
		clientMessageId: String,
		transform: (ChatMessageItemModel) -> ChatMessageItemModel,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localVoiceMessages[normalizedClientMessageId] ?: return
		val updatedMessage = transform(currentMessage)
		localVoiceMessages[normalizedClientMessageId] = updatedMessage
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = timelineReducer.upsertMessage(state.messages, updatedMessage)
					state.copy(messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId))
				}
				else -> state
			}
		}
	}

	private fun updateLocalVideoCircleMessage(
		clientMessageId: String,
		transform: (ChatMessageItemModel) -> ChatMessageItemModel,
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val currentMessage = localVideoCircleMessages[normalizedClientMessageId] ?: return
		val updatedMessage = transform(currentMessage)
		localVideoCircleMessages[normalizedClientMessageId] = updatedMessage
		stateFlow.update { state ->
			when (state) {
				is DirectChatScreenState.HasData -> {
					val updatedMessages = timelineReducer.upsertMessage(state.messages, updatedMessage)
					state.copy(messages = timelineReducer.applyPeerReadState(updatedMessages, state.peerLastReadMessageId))
				}
				else -> state
			}
		}
	}

	fun mergeLocalMediaMessages(messages: List<ChatMessageItemModel>): List<ChatMessageItemModel> {
		var result = messages
		if (localVideoCircleMessages.isNotEmpty()) {
			result = localVideoCircleMessages.values.fold(result) { current, localMessage ->
				timelineReducer.upsertMessage(current, localMessage)
			}
		}
		if (localVoiceMessages.isNotEmpty()) {
			result = localVoiceMessages.values.fold(result) { current, localMessage ->
				timelineReducer.upsertMessage(current, localMessage)
			}
		}
		if (localImageMessages.isNotEmpty()) {
			result = localImageMessages.values.fold(result) { current, localMessage ->
				timelineReducer.upsertMessage(current, localMessage)
			}
		}
		return result
	}

	fun removeLocalMessage(clientMessageId: String) {
		localVideoCircleMessages.remove(clientMessageId)
		localVoiceMessages.remove(clientMessageId)
		localImageMessages.remove(clientMessageId)
	}

	fun clearLocalMedia() {
		localVideoCircleMessages.clear()
		localVoiceMessages.clear()
		localImageMessages.clear()
	}

	fun dispose(clearLocalMedia: Boolean) {
		outgoingRecordingJob?.cancel()
		outgoingRecordingJob = null
		localRecordingStartedAtMillis = null
		clearActiveVideoCircle()
		if (clearLocalMedia) {
			clearLocalMedia()
		}
	}
}
