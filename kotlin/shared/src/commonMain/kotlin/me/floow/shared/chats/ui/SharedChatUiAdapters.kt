package me.floow.shared.chats.ui

import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.RepliesThreadItemModel
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.shared.chats.uilogic.replies.RepliesScreenState
import me.floow.uikit.chat.model.ChatAnchorRequest
import me.floow.uikit.chat.model.ChatHighlightRequest
import me.floow.uikit.chat.model.ChatInitialViewport
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatQuote
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
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VideoCircleUploadStatus
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.chat.model.chatLocalDayStartMillis

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
			peerIsOnline = peerIsOnline,
			peerLastSeenAtMillis = peerLastSeenAtMillis,
		)

		is DirectChatScreenState.HasData -> {
			val visibleWindow = historyUpperBound?.let { bound -> messages.filter { it.id in 1L..bound } } ?: messages
            val datedMessages = visibleWindow.toDatedChatMessages()
			val latestServerMessageId = messages.maxOfOrNull { message ->
				message.id.takeIf { id -> id > 0L } ?: 0L
			} ?: 0L
			val resolvedPeerLastReadMessageId = if (header.isSavedMessages) {
				maxOf(peerLastReadMessageId, latestServerMessageId)
			} else {
				peerLastReadMessageId
			}
			ChatScreenUiState.HasData(
				messages = datedMessages,
				chatInterlocutorId = header.peerUserId,
				chatInterlocutorAvatarUrl = header.avatarUrl,
				messageFieldValue = input,
				chatInterlocutorName = header.title,
				messageFieldReply = replyDraft?.let { MessageFieldReply(it.messageId, it.author, it.preview) },
				peerIsOnline = peerIsOnline,
				peerLastSeenAtMillis = peerLastSeenAtMillis,
				timelineSessionToken = conversationId,
				initialViewport = directInitialViewport(
					messages = datedMessages,
					openAnchorMessageId = anchorRequestMessageId,
					openAnchorOffsetPx = anchorRequestOffsetPx,
				),
				anchorRequest = anchorRequestMessageId?.let { messageId ->
					ChatAnchorRequest(
						messageId = messageId,
						requestToken = anchorRequestToken,
						initialOffsetPx = anchorRequestOffsetPx,
						keepAnchored = keepAnchorRequest,
					)
				},
				highlightRequest = highlightedMessageId?.let { messageId ->
                    ChatHighlightRequest(messageId, anchorRequestToken, keepAnchorRequest && messageId == anchorRequestMessageId)
                },
                scrollRequest = if (scrollToBottomRequestToken > 0L) {
					ChatScrollRequest(requestToken = scrollToBottomRequestToken)
				} else {
					null
				},
                unreadBoundaryMessageId = unreadBoundaryMessageId,
                hasNewerHistory = hasNewerMessages,
                provisionalHistory = provisionalHistory,
                typingUserNames = typingUserNames,
				pinnedMessages = pinnedMessages.map(ChatMessageItemModel::toChatMessage),
				messageToEditId = editingMessageId,
				scrollToBottomRequestToken = scrollToBottomRequestToken,
				scrollToBottomBadgeCount = scrollToBottomBadgeCount,
				peerLastReadMessageId = resolvedPeerLastReadMessageId,
				showUnreadIndicators = showUnreadIndicators && !header.isSavedMessages,
				canLoadMore = canLoadMore,
				isLoadingMore = isLoadingMore,
				selectionState = selectionState,
			)
		}
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
			anchorRequest = openAnchorMessageId?.let { messageId ->
				ChatAnchorRequest(
					messageId = messageId,
					requestToken = repliesTimelineSessionToken(
						unreadCount = unreadCount,
						openAnchorMessageId = openAnchorMessageId,
						unreadBoundaryMessageId = unreadBoundaryMessageId,
					),
					keepAnchored = true,
				)
		},
			unreadBoundaryMessageId = unreadBoundaryMessageId,
		showUnreadIndicators = true,
		canLoadMore = false,
		isLoadingMore = false,
	)
}

private val chatMessageSortComparator = compareBy<ChatMessage>(
	{ it.createdAtMillis },
	{ (it as? ImageMessage)?.groupId ?: "" },
	{ (it as? ImageMessage)?.images?.firstOrNull()?.position ?: 0 },
	{ it.clientMessageId?.takeIf(String::isNotBlank) ?: it.id.toString() }
)

private fun List<ChatMessageItemModel>.toDatedChatMessages(): List<DatedChatMessages> {
	val groupedMessages = map(ChatMessageItemModel::toChatMessage)
		.sortedWith(chatMessageSortComparator)
		.groupAlbumMessages()
	return groupedMessages.groupBy { message -> chatLocalDayStartMillis(message.createdAtMillis) }
		.entries
		.sortedBy { it.key }
		.map { entry ->
			DatedChatMessages(
				dayStartMillis = entry.key,
				messages = entry.value
					.sortedWith(chatMessageSortComparator)
			)
		}
}

internal fun ChatMessageItemModel.imageCaption(): String =
	text.takeUnless { it == "Фотография" }.orEmpty()

private fun List<ChatMessage>.groupAlbumMessages(): List<ChatMessage> {
	if (isEmpty()) return emptyList()

	val albumGroups = LinkedHashMap<String, MutableList<ImageMessage>>()
	for (msg in this) {
		if (msg is ImageMessage && !msg.groupId.isNullOrBlank()) {
			val groupKey = "${if (msg.isOutgoing) "out" else "in"}_${msg.groupId}"
			albumGroups.getOrPut(groupKey) { ArrayList() }.add(msg)
		}
	}

	val processedGroupKeys = HashSet<String>()
	val result = ArrayList<ChatMessage>()

	for (msg in this) {
		if (msg is ImageMessage && !msg.groupId.isNullOrBlank()) {
			val groupKey = "${if (msg.isOutgoing) "out" else "in"}_${msg.groupId}"
			if (processedGroupKeys.contains(groupKey)) {
				continue
			}
			processedGroupKeys.add(groupKey)
			val group = albumGroups[groupKey] ?: listOf(msg)

			val stableGroupId = msg.groupId?.takeIf(String::isNotEmpty)
			if (group.size == 1) {
				val single = group[0]
				val stableUiKey = single.clientMessageId?.takeIf(String::isNotBlank)?.let { "cmid_$it" }
					?: if (!stableGroupId.isNullOrBlank()) "album_${stableGroupId}_0" else single.uiKey
				result.add(single.copy(uiKey = stableUiKey))
			} else {
				val chunks = group.chunked(10)
				chunks.forEachIndexed { chunkIdx, chunk ->
					if (chunk.size == 1) {
						val single = chunk[0]
						val stableUiKey = single.clientMessageId?.takeIf(String::isNotBlank)?.let { "cmid_$it" }
							?: if (!stableGroupId.isNullOrBlank()) "album_${stableGroupId}_$chunkIdx" else single.uiKey
						result.add(single.copy(uiKey = stableUiKey))
					} else {
						val first = chunk[0]
						val allImages = chunk.flatMap { it.images }
							.sortedWith(compareBy({ it.position }, { it.clientMessageId?.takeIf(String::isNotBlank) ?: it.id.toString() }))
							.mapIndexed { contiguousIdx, img ->
								img.copy(position = contiguousIdx)
							}
						val caption = chunk.map { it.messageText }
							.firstOrNull { it.isNotBlank() && it != "Фотография" }
							.orEmpty()
						val aggregateDeliveryStatus = when {
							chunk.any { it.deliveryStatus == ChatMessageDeliveryStatus.FAILED } -> ChatMessageDeliveryStatus.FAILED
							chunk.any { it.deliveryStatus == ChatMessageDeliveryStatus.SENDING } -> ChatMessageDeliveryStatus.SENDING
							else -> ChatMessageDeliveryStatus.SENT
						}
						val stableUiKey = if (!stableGroupId.isNullOrBlank()) {
							"album_${stableGroupId}_$chunkIdx"
						} else {
							first.clientMessageId?.takeIf(String::isNotBlank)?.let { "cmid_${it}_album_$chunkIdx" }
								?: "album_${first.id}_$chunkIdx"
						}
						result.add(
							first.copy(
								uiKey = stableUiKey,
								messageText = caption,
								images = allImages,
								deliveryStatus = aggregateDeliveryStatus,
                                quote = chunk.firstNotNullOfOrNull { it.quote },
							)
						)
					}
				}
			}
		} else {
			result.add(msg)
		}
	}

	return result
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
	val quote = replyToMessageId?.let { ChatQuote(it, replyToMessageText.orEmpty()) }
    val image = content as? ChatMessageContent.Image
	if (image != null) {
		return ImageMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = imageCaption(),
			createdAtMillis = createdAtMillis,
			isOutgoing = isOutgoing,
			isPinned = isPinned,
			authorName = senderDisplayName,
			authorAvatarUrl = authorAvatarUrl,
			authorAvatarThumb = authorAvatarThumb,
			deliveryStatus = deliveryStatus,
			images = listOf(
				ChatImageItem(
					id = id,
					clientMessageId = clientMessageId,
					localPath = image.localPath,
					remoteUrl = image.remoteUrl,
					width = image.width,
					height = image.height,
					position = image.position,
					uploadStatus = image.uploadState.toUploadStatus(),
					thumbnailUrl = image.thumbnailUrl,
					sizeBytes = image.sizeBytes,
				)
			),
			groupId = image.groupId,
            quote = quote,
		)
	}
	val videoCircle = content as? ChatMessageContent.VideoCircle
	if (videoCircle != null) {
		return VideoCircleOutMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			isOutgoing = isOutgoing,
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
			localVideoPath = videoCircle.localPath,
			remoteVideoUrl = videoCircle.remoteUrl,
			durationMs = videoCircle.durationMs,
			thumbnailPath = videoCircle.thumbnailPath,
			remoteThumbnailUrl = videoCircle.remoteThumbnailUrl,
			videoWidth = videoCircle.width,
			videoHeight = videoCircle.height,
			uploadStatus = videoCircle.uploadState.toUploadStatus(),
            quote = quote,
		)
	}
	val voice = content as? ChatMessageContent.Voice
	if (voice != null) {
		return VoiceMessage(
			id = id,
			uiKey = clientMessageId?.let { "cmid_$it" } ?: "msg_$id",
			clientMessageId = clientMessageId,
			messageText = text,
			createdAtMillis = createdAtMillis,
			isOutgoing = isOutgoing,
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
			localAudioPath = voice.localPath,
			remoteAudioUrl = voice.remoteUrl,
			durationMs = voice.durationMs,
			waveform = voice.waveform,
			uploadStatus = voice.uploadState.toUploadStatus(),
            quote = quote,
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
					thumbUrl = postPreview.imageUrl,
					fullUrl = postPreview.imageUrl,
				)
			),
			likesCount = postPreview.likesCount,
			authorAvatarUrl = authorAvatarUrl,
			authorAvatarThumb = authorAvatarThumb,
			isPinned = isPinned,
			authorName = senderDisplayName,
			deliveryStatus = deliveryStatus,
            quote = quote,
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
			authorAvatarUrl = authorAvatarUrl,
			authorAvatarThumb = authorAvatarThumb,
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
			authorAvatarUrl = authorAvatarUrl,
			authorAvatarThumb = authorAvatarThumb,
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

private fun directInitialViewport(
	messages: List<DatedChatMessages>,
	openAnchorMessageId: Long?,
	openAnchorOffsetPx: Int,
): ChatInitialViewport? {
	return findNewestAtBottomMessageIndex(
		messages = messages,
		targetMessageId = openAnchorMessageId,
	)?.let { itemIndex ->
		ChatInitialViewport(
			itemIndex = itemIndex,
			itemScrollOffsetPx = openAnchorOffsetPx,
		)
	}
}

private fun findNewestAtBottomMessageIndex(
	messages: List<DatedChatMessages>,
	targetMessageId: Long?,
): Int? {
	val messageId = targetMessageId ?: return null
	var flatIndex = 0
	for (group in messages.asReversed()) {
		for (message in group.messages.asReversed()) {
			if (message.id == messageId) return flatIndex
			flatIndex += 1
		}
		flatIndex += 1
	}
	return null
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
		authorAvatarThumb = actorAvatarThumb,
	)
}

private fun VideoUploadState.toUploadStatus(): VideoCircleUploadStatus = when (this) {
	VideoUploadState.Pending -> VideoCircleUploadStatus.Pending
	VideoUploadState.Uploading -> VideoCircleUploadStatus.Uploading
	VideoUploadState.Uploaded -> VideoCircleUploadStatus.Uploaded
	VideoUploadState.Failed -> VideoCircleUploadStatus.Failed
}
