package me.floow.shared.chats.ui

import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.shared.chats.uilogic.replies.RepliesScreenState
import me.floow.uikit.chat.model.ChatHighlightRequest
import me.floow.uikit.chat.model.ChatInitialViewport
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatMessageDeliveryStatus
import me.floow.uikit.chat.model.ChatScrollRequest
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.MessageFieldReply
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VideoCircleUploadStatus
import me.floow.uikit.chat.model.chatLocalDayStartMillis

internal data class SharedJumpRequest(
	val messageId: Long,
	val requestToken: Long,
)

internal fun DirectChatScreenState.headerOrNull(): ChatThreadHeaderModel? = when (this) {
	is DirectChatScreenState.HasData -> header
	is DirectChatScreenState.NoMessages -> header
	DirectChatScreenState.Loading,
	is DirectChatScreenState.Error -> null
}

internal fun DirectChatScreenState.inputValue(): String = when (this) {
	is DirectChatScreenState.HasData -> input
	is DirectChatScreenState.NoMessages -> input
	DirectChatScreenState.Loading,
	is DirectChatScreenState.Error -> ""
}

internal fun DirectChatScreenState.toSharedChatUiState(
	selectionState: ChatSelectionState,
	jumpRequest: SharedJumpRequest?,
): ChatScreenUiState {
	return when (this) {
		DirectChatScreenState.Loading -> ChatScreenUiState.Loading(
			chatInterlocutorId = "",
			chatInterlocutorAvatarUrl = null,
			messageFieldValue = "",
			chatInterlocutorName = "",
			messageFieldReply = null,
		)

		is DirectChatScreenState.Error -> ChatScreenUiState.Error(
			chatInterlocutorId = "",
			chatInterlocutorAvatarUrl = null,
			messageFieldValue = "",
			chatInterlocutorName = "",
			messageFieldReply = null,
		)

		is DirectChatScreenState.NoMessages -> ChatScreenUiState.NoMessages(
			chatInterlocutorId = header.peerUserId,
			chatInterlocutorAvatarUrl = header.avatarUrl,
			messageFieldValue = input,
			chatInterlocutorName = header.title,
			messageFieldReply = null,
		)

		is DirectChatScreenState.HasData -> ChatScreenUiState.HasData(
			messages = messages.toDatedChatMessages(),
			chatInterlocutorId = header.peerUserId,
			chatInterlocutorAvatarUrl = header.avatarUrl,
			messageFieldValue = input,
			chatInterlocutorName = header.title,
			messageFieldReply = currentReplyMessageId
				?.let { replyMessageId -> messages.firstOrNull { it.id == replyMessageId } }
				?.toFieldReply(),
			timelineSessionToken = conversationId,
			highlightRequest = jumpRequest?.let { request ->
				ChatHighlightRequest(
					messageId = request.messageId,
					requestToken = request.requestToken,
				)
			},
			scrollRequest = if (scrollToBottomRequestToken > 0L) {
				ChatScrollRequest(requestToken = scrollToBottomRequestToken)
			} else {
				null
			},
			pinnedMessages = messages.filter(ChatMessageItemModel::isPinned).map(ChatMessageItemModel::toChatMessage),
			messageToEditId = editingMessageId,
			scrollToBottomRequestToken = scrollToBottomRequestToken,
			scrollToBottomBadgeCount = scrollToBottomBadgeCount,
			peerLastReadMessageId = peerLastReadMessageId,
			canLoadMore = canLoadMore,
			isLoadingMore = isLoadingMore,
			selectionState = selectionState,
		)
	}
}

internal fun RepliesScreenState.toSharedRepliesUiState(): ChatScreenUiState = when (this) {
	RepliesScreenState.Loading -> ChatScreenUiState.Loading(
		chatInterlocutorId = "replies",
		chatInterlocutorAvatarUrl = null,
		messageFieldValue = "",
		chatInterlocutorName = "Ответы",
		messageFieldReply = null,
	)

	is RepliesScreenState.Error -> ChatScreenUiState.Error(
		chatInterlocutorId = "replies",
		chatInterlocutorAvatarUrl = null,
		messageFieldValue = "",
		chatInterlocutorName = "Ответы",
		messageFieldReply = null,
	)

	RepliesScreenState.Empty -> ChatScreenUiState.NoMessages(
		chatInterlocutorId = "replies",
		chatInterlocutorAvatarUrl = null,
		messageFieldValue = "",
		chatInterlocutorName = "Ответы",
		messageFieldReply = null,
	)

	is RepliesScreenState.HasData -> ChatScreenUiState.HasData(
		messages = items.toRepliesTimeline(),
		chatInterlocutorId = "replies",
		chatInterlocutorAvatarUrl = null,
		messageFieldValue = "",
		chatInterlocutorName = "Ответы",
		messageFieldReply = null,
		timelineSessionToken = repliesTimelineSessionToken(
			unreadCount = unreadCount,
			openAnchorMessageId = openAnchorMessageId,
			unreadBoundaryMessageId = unreadBoundaryMessageId,
		),
		initialViewport = repliesInitialViewport(
			messages = items.toRepliesTimeline(),
			openAnchorMessageId = openAnchorMessageId,
		),
		unreadBoundaryMessageId = unreadBoundaryMessageId,
		canLoadMore = false,
		isLoadingMore = false,
	)
}

private fun List<ChatMessageItemModel>.toDatedChatMessages(): List<DatedChatMessages> {
	return groupBy { message -> chatLocalDayStartMillis(message.createdAtMillis) }
		.entries
		.sortedBy { it.key }
		.map { entry ->
			DatedChatMessages(
				dayStartMillis = entry.key,
				messages = entry.value
					.sortedBy(ChatMessageItemModel::createdAtMillis)
					.map(ChatMessageItemModel::toChatMessage)
			)
		}
}

private fun List<RepliesThreadItemModel>.toRepliesTimeline(): List<DatedChatMessages> {
	return groupBy { item -> chatLocalDayStartMillis(item.createdAtMillis) }
		.entries
		.sortedBy { it.key }
		.map { entry ->
			DatedChatMessages(
				dayStartMillis = entry.key,
				messages = entry.value
					.sortedBy(RepliesThreadItemModel::createdAtMillis)
					.map(RepliesThreadItemModel::toChatMessage)
			)
		}
}

private fun ChatMessageItemModel.toChatMessage(): ChatMessage {
	val deliveryStatus = when (deliveryState) {
		ChatDeliveryState.SENDING -> ChatMessageDeliveryStatus.SENDING
		ChatDeliveryState.FAILED -> ChatMessageDeliveryStatus.FAILED
		ChatDeliveryState.SENT,
		ChatDeliveryState.READ,
		null -> ChatMessageDeliveryStatus.SENT
	}
	val videoCircle = content as? ChatMessageContent.VideoCircle
	if (videoCircle != null) {
		return VideoCircleOutMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
			localVideoPath = videoCircle.localPath,
			remoteVideoUrl = videoCircle.remoteUrl,
			durationMs = videoCircle.durationMs,
			thumbnailPath = videoCircle.thumbnailPath,
			videoWidth = videoCircle.width,
			videoHeight = videoCircle.height,
			uploadStatus = videoCircle.uploadState.toUploadStatus(),
		)
	}
	val postPreview = content as? ChatMessageContent.PostPreview
	if (postPreview != null) {
		return PostPreviewMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			imageVariants = listOf(
				me.floow.uikit.chat.model.ChatPostImageVariant(
					previewUrl = postPreview.imageUrl,
					fullUrl = postPreview.imageUrl,
				)
			),
			likesCount = postPreview.likesCount,
			authorAvatarUrl = null,
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
		)
	}
	return when {
		isOutgoing && replyToMessageId != null -> ReplyOutMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			replyMessageId = replyToMessageId,
			replyMessageText = replyToMessageText.orEmpty(),
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
		)

		!isOutgoing && replyToMessageId != null -> ReplyInMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			replyMessageId = replyToMessageId,
			replyMessageText = replyToMessageText.orEmpty(),
			isPinned = isPinned,
			authorName = senderDisplayName,
			authorAvatarUrl = null,
			deliveryStatus = deliveryStatus,
		)

		isOutgoing -> PrimaryOutMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
		)

		else -> PrimaryInMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			isPinned = isPinned,
			authorName = senderDisplayName,
			authorAvatarUrl = null,
			deliveryStatus = deliveryStatus,
		)
	}
}

private fun repliesInitialViewport(
	messages: List<DatedChatMessages>,
	openAnchorMessageId: Long?,
): ChatInitialViewport? {
	return findRepliesMessageIndex(
		messages = messages,
		targetMessageId = openAnchorMessageId,
	)?.let { itemIndex ->
		ChatInitialViewport(
			itemIndex = itemIndex,
			itemScrollOffsetPx = 0,
		)
	}
}

private fun findRepliesMessageIndex(
	messages: List<DatedChatMessages>,
	targetMessageId: Long?,
): Int? {
	val messageId = targetMessageId ?: return null
	var flatIndex = 0
	for (group in messages) {
		flatIndex += 1
		for (message in group.messages) {
			if (message.id == messageId) return flatIndex
			flatIndex += 1
		}
	}
	return null
}

private fun repliesTimelineSessionToken(
	unreadCount: Int,
	openAnchorMessageId: Long?,
	unreadBoundaryMessageId: Long?,
): Long {
	var token = unreadCount.toLong()
	token = token * 31 + (openAnchorMessageId ?: 0L)
	token = token * 31 + (unreadBoundaryMessageId ?: 0L)
	return token
}

private fun RepliesThreadItemModel.toChatMessage(): ChatMessage {
	return PrimaryInMessage(
		id = messageId,
		messageText = text,
		createdAtMillis = createdAtMillis,
		authorName = actorDisplayName,
		authorAvatarUrl = actorAvatarUrl,
	)
}

private fun VideoUploadState.toUploadStatus(): VideoCircleUploadStatus = when (this) {
	VideoUploadState.Pending -> VideoCircleUploadStatus.Pending
	VideoUploadState.Uploading -> VideoCircleUploadStatus.Uploading
	VideoUploadState.Uploaded -> VideoCircleUploadStatus.Uploaded
	VideoUploadState.Failed -> VideoCircleUploadStatus.Failed
}

private fun ChatMessageItemModel.toFieldReply(): MessageFieldReply {
	return MessageFieldReply(
		replyId = id,
		replyAuthorName = senderDisplayName.orEmpty().ifBlank { if (isOutgoing) "Ты" else "Пользователь" },
		replyMessageText = text,
	)
}
