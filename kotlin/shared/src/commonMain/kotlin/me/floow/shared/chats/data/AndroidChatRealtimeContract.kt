package me.floow.shared.chats.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeEvent
import me.floow.shared.chats.uilogic.direct.RealtimeConnectionState

class AndroidChatRealtimeContract(
	private val chatsRepository: ChatsRepository,
	private val authenticationManager: me.floow.domain.auth.AuthenticationManager,
	private val presenceRepository: me.floow.domain.data.repos.PresenceRepository,
) : ChatRealtimeContract {
	override val connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Idle)

	override fun observeConversation(conversationId: Long, afterSeq: Long): Flow<ChatRealtimeEvent> {
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		return chatsRepository.subscribeConversation(conversationId, afterSeq).map { event ->
			event.toSharedEvent(selfUserId)
		}
	}

	override suspend fun setTyping(conversationId: Long, isTyping: Boolean, action: String): Result<Unit> = runCatching {
		when (chatsRepository.sendTyping(conversationId, isTyping, action)) {
			is UpdateDataResponse.Success -> Unit
			is UpdateDataResponse.Failure -> error("failed to update typing")
		}
	}
}

private fun DirectChatRealtimeEvent.toSharedEvent(selfUserId: String?): ChatRealtimeEvent = when (this) {
	is DirectChatRealtimeEvent.MessageCreated -> ChatRealtimeEvent.MessageUpserted(
		message = message.toRealtimeItemModel(selfUserId),
		peerLastReadMessageId = null,
	)
	is DirectChatRealtimeEvent.MessageUpdated -> ChatRealtimeEvent.MessageUpserted(
		message = message.toRealtimeItemModel(selfUserId),
		peerLastReadMessageId = null,
        isNew = false,
	)
	is DirectChatRealtimeEvent.MessagePinnedUpdated -> ChatRealtimeEvent.MessagePinned(
		messageId = message.id,
		isPinned = isPinned,
	)
	is DirectChatRealtimeEvent.MessageDeleted -> ChatRealtimeEvent.MessageDeleted(deletedMessageId)
	is DirectChatRealtimeEvent.ReadUpToUpdated -> {
		val updatedMessageId = actorLastReadMessageId
		if (actorUserId == selfUserId || updatedMessageId == null) {
			ChatRealtimeEvent.OwnReadUpdated
		} else {
			ChatRealtimeEvent.PeerReadUpdated(updatedMessageId)
		}
	}
	is DirectChatRealtimeEvent.Typing -> ChatRealtimeEvent.TypingUpdated(
		actorUserId = actorUserId,
		displayName = "",
		isTyping = isTyping,
		action = action,
		typingTtlMs = typingTtlMs,
	)
	is DirectChatRealtimeEvent.ConversationDeleted -> ChatRealtimeEvent.ConversationDeleted(actorUserId)
	is DirectChatRealtimeEvent.Hello -> ChatRealtimeEvent.Hello(
		lastReadMessageId = lastReadMessageId,
		unreadCount = unreadCount,
		maxMessageId = maxMessageId,
	)
	is DirectChatRealtimeEvent.ResyncRequired -> ChatRealtimeEvent.ResyncRequired
}

private fun me.floow.domain.models.DirectChatMessage.toRealtimeItemModel(selfUserId: String?): ChatMessageItemModel {
	val normalizedSelfUserId = selfUserId?.trim()?.takeIf(String::isNotEmpty)
	val isOutgoing = normalizedSelfUserId != null && sender.id.trim() == normalizedSelfUserId
	val messageMedia = media
	val normalizedContentType = contentType?.trim()?.lowercase()
	val normalizedText = text.trim().lowercase()
	val normalizedMimeType = messageMedia?.mimeType?.trim()?.lowercase().orEmpty()
	val hasMediaSource = messageMedia != null &&
		(!messageMedia.url.isNullOrBlank() || !messageMedia.localPath.isNullOrBlank() || messageMedia.sizeBytes != null)
	val isVideoCircleType = normalizedContentType == "video_circle" ||
		normalizedContentType == "video" ||
		normalizedContentType?.contains("video") == true ||
		(normalizedText == "видеосообщение" && hasMediaSource) ||
		normalizedMimeType.startsWith("video/")
	val isVoiceType = normalizedContentType == "voice" ||
		normalizedContentType == "audio" ||
		normalizedContentType?.contains("voice") == true ||
		normalizedContentType?.contains("audio") == true ||
		(normalizedText == "голосовое сообщение" && (hasMediaSource || !messageMedia?.waveform.isNullOrEmpty())) ||
		normalizedMimeType.startsWith("audio/") ||
		!messageMedia?.waveform.isNullOrEmpty()
	val isImageType = normalizedContentType == "image" ||
		normalizedContentType == "photo" ||
		normalizedContentType?.contains("image") == true ||
		(normalizedText == "фотография" && hasMediaSource) ||
		normalizedMimeType.startsWith("image/") ||
		(messageMedia?.width ?: 0) > 0

	val content = when {
		isVideoCircleType -> ChatMessageContent.VideoCircle(
			localPath = null,
			remoteUrl = messageMedia?.url,
			durationMs = messageMedia?.durationMs ?: 0L,
			remoteThumbnailUrl = messageMedia?.thumbnailUrl,
			width = messageMedia?.width ?: 0,
			height = messageMedia?.height ?: 0,
			uploadState = VideoUploadState.Uploaded
		)
		isVoiceType -> ChatMessageContent.Voice(
			localPath = null,
			remoteUrl = messageMedia?.url,
			durationMs = messageMedia?.durationMs ?: 0L,
			waveform = messageMedia?.waveform ?: emptyList(),
			uploadState = VideoUploadState.Uploaded
		)
		isImageType -> ChatMessageContent.Image(
			localPath = null,
			remoteUrl = messageMedia?.url,
			width = messageMedia?.width ?: 0,
			height = messageMedia?.height ?: 0,
			groupId = groupId ?: messageMedia?.groupId,
			position = if (position > 0) position else (messageMedia?.position ?: 0),
			uploadState = VideoUploadState.Uploaded,
			thumbnailUrl = messageMedia?.thumbnailUrl,
			sizeBytes = messageMedia?.sizeBytes ?: 0L,
		)
		else -> ChatMessageContent.Text
	}
	return ChatMessageItemModel(
		id = id,
		clientMessageId = clientMessageId,
		senderUserId = sender.id,
		senderDisplayName = sender.name ?: sender.username,
		text = text,
		createdAtMillis = createdAt,
		isOutgoing = isOutgoing,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		pinnedAtMillis = pinnedAt,
		isDeleted = false,
		deliveryState = if (!isOutgoing) null else when (deliveryStatus) {
			MessageDeliveryStatus.SENDING -> ChatDeliveryState.SENDING
			MessageDeliveryStatus.SENT -> ChatDeliveryState.SENT
			MessageDeliveryStatus.FAILED -> ChatDeliveryState.FAILED
		},
		content = content,
	)
}
