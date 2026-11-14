package me.floow.shared.chats.uilogic.direct

import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.ui.currentChatEpochMillis

class ChatTimelineReducer {

	companion object {
		const val MAX_CANCELLED_CLIENT_MESSAGE_IDS = 64
		const val LOCAL_ONLY_CONVERSATION_ID = -1L
	}

	private var optimisticMessageIdSeed: Long = -1L
	private val cancelledClientMessageIds = LinkedHashSet<String>()

	fun nextOptimisticMessageId(): Long = optimisticMessageIdSeed--

	fun nextClientMessageId(optimisticMessageId: Long): String =
		"web-${currentChatEpochMillis()}-${kotlin.math.abs(optimisticMessageId)}"

	fun addCancelledClientMessageId(clientMessageId: String) {
		if (cancelledClientMessageIds.size >= MAX_CANCELLED_CLIENT_MESSAGE_IDS) {
			val oldest = cancelledClientMessageIds.iterator().next()
			cancelledClientMessageIds.remove(oldest)
		}
		cancelledClientMessageIds.add(clientMessageId)
	}

	fun removeCancelledClientMessageId(clientMessageId: String) {
		cancelledClientMessageIds.remove(clientMessageId)
	}

	fun isCancelledClientMessageId(clientMessageId: String?): Boolean {
		val normalized = clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return false
		return normalized in cancelledClientMessageIds
	}

	fun isOutgoingMessage(senderUserId: String?, header: ChatThreadHeaderModel): Boolean {
		val normalizedSenderUserId = senderUserId?.trim()?.takeIf(String::isNotEmpty) ?: return false
		if (normalizedSenderUserId == "self") return true
		val normalizedSelfUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty)
		return normalizedSelfUserId?.let { normalizedSenderUserId == it }
			?: (normalizedSenderUserId != header.peerUserId)
	}

	fun normalizeRealtimeMessage(
		message: ChatMessageItemModel,
		header: ChatThreadHeaderModel,
		peerLastReadMessageId: Long?,
	): ChatMessageItemModel {
		val isOutgoing = isOutgoingMessage(message.senderUserId, header)
		val deliveryState = when {
			!isOutgoing -> null
			peerLastReadMessageId != null && peerLastReadMessageId >= message.id -> ChatDeliveryState.READ
			else -> ChatDeliveryState.SENT
		}
		return message.copy(
			isOutgoing = isOutgoing,
			deliveryState = deliveryState,
		)
	}

	fun applyPeerReadState(
		messages: List<ChatMessageItemModel>,
		peerLastReadMessageId: Long?,
	): List<ChatMessageItemModel> {
		val normalizedPeerLastReadMessageId = peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L
		return messages.map { message ->
			if (!message.isOutgoing || message.id <= 0L) {
				message
			} else {
				val deliveryState = when (message.deliveryState) {
					ChatDeliveryState.SENDING,
					ChatDeliveryState.FAILED -> message.deliveryState
					ChatDeliveryState.READ,
					ChatDeliveryState.SENT,
					null -> if (normalizedPeerLastReadMessageId >= message.id) {
						ChatDeliveryState.READ
					} else {
						ChatDeliveryState.SENT
					}
				}
				message.copy(deliveryState = deliveryState)
			}
		}
	}

	fun isLocalOutgoingPending(message: ChatMessageItemModel): Boolean {
		return message.isOutgoing &&
			!message.clientMessageId.isNullOrBlank() &&
			message.id <= 0L &&
			(message.deliveryState == ChatDeliveryState.SENDING || message.deliveryState == ChatDeliveryState.FAILED)
	}

	fun buildOptimisticOutgoingMessage(
		header: ChatThreadHeaderModel,
		id: Long,
		clientMessageId: String,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String?,
		createdAtMillis: Long,
		deliveryState: ChatDeliveryState,
	): ChatMessageItemModel {
		val senderUserId = header.selfUserId?.trim()?.takeIf(String::isNotEmpty) ?: header.peerUserId
		return ChatMessageItemModel(
			id = id,
			clientMessageId = clientMessageId,
			senderUserId = senderUserId,
			senderDisplayName = null,
			text = text,
			createdAtMillis = createdAtMillis,
			isOutgoing = true,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			deliveryState = deliveryState,
		)
	}

	fun nextOutgoingCreatedAtMillis(currentMessages: List<ChatMessageItemModel>): Long {
		val now = currentChatEpochMillis()
		val latestVisibleMessageAt = currentMessages.maxOfOrNull { it.createdAtMillis } ?: return now
		return if (latestVisibleMessageAt >= now) latestVisibleMessageAt + 1L else now
	}

	fun upsertMessage(
		currentMessages: List<ChatMessageItemModel>,
		message: ChatMessageItemModel,
		inFlightEdits: Map<Long, String> = emptyMap(),
	): List<ChatMessageItemModel> {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		val n = currentMessages.size

		var firstMatchIndex = -1
		var firstMatch: ChatMessageItemModel? = null
		for (i in 0 until n) {
			val candidate = currentMessages[i]
			val matchesById = message.id > 0L && candidate.id == message.id
			val matchesByClient = normalizedClientMessageId != null &&
				candidate.clientMessageId?.trim() == normalizedClientMessageId
			if (matchesById || matchesByClient) {
				firstMatchIndex = i
				firstMatch = candidate
				break
			}
		}

		val normalizedMessage = if (firstMatch != null) {
			val preservedContent = mergeMessageContent(firstMatch.content, message.content)
			val resolvedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
				?: firstMatch.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
			val inFlightText = inFlightEdits[firstMatch.id] ?: inFlightEdits[message.id]
			val resolvedText = inFlightText ?: message.text
			val resolvedDeliveryState = when {
				firstMatch.deliveryState == ChatDeliveryState.SENDING && message.deliveryState == null -> ChatDeliveryState.SENDING
				else -> message.deliveryState ?: firstMatch.deliveryState
			}
			message.copy(
				clientMessageId = resolvedClientMessageId,
				createdAtMillis = if (message.id > 0L && firstMatch.createdAtMillis > 0L) firstMatch.createdAtMillis else message.createdAtMillis,
				content = preservedContent,
				text = resolvedText,
				deliveryState = resolvedDeliveryState,
			)
		} else {
			val inFlightText = inFlightEdits[message.id]
			if (inFlightText != null) message.copy(text = inFlightText) else message
		}

		val result = ArrayList<ChatMessageItemModel>(if (firstMatchIndex >= 0) n else n + 1)
		var inserted = false
		for (i in 0 until n) {
			val existing = currentMessages[i]
			if (i == firstMatchIndex) {
				result += normalizedMessage
				inserted = true
				continue
			}
			val matchesById = normalizedMessage.id > 0L && existing.id == normalizedMessage.id
			val matchesByClient = normalizedClientMessageId != null &&
				existing.clientMessageId?.trim() == normalizedClientMessageId
			if (matchesById || matchesByClient) continue
			result += existing
		}
		if (!inserted) result += normalizedMessage

		return result.sortedBy(ChatMessageItemModel::createdAtMillis)
	}

	fun mergeMessageContent(existing: ChatMessageContent, incoming: ChatMessageContent): ChatMessageContent {
		return when {
			existing is ChatMessageContent.Image && incoming is ChatMessageContent.Image -> {
				val resolvedLocalPath = incoming.localPath?.takeIf { it.isNotBlank() }
					?: existing.localPath?.takeIf { it.isNotBlank() }
					?: incoming.localPath
					?: existing.localPath
				incoming.copy(
					localPath = resolvedLocalPath,
					remoteUrl = incoming.remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
					width = if (existing.width > 0) existing.width else incoming.width,
					height = if (existing.height > 0) existing.height else incoming.height,
					groupId = existing.groupId?.takeIf { it.isNotBlank() } ?: incoming.groupId,
					position = if (existing.position > 0) existing.position else incoming.position,
					thumbnailUrl = existing.thumbnailUrl?.takeIf { it.isNotBlank() } ?: incoming.thumbnailUrl,
				)
			}
			existing is ChatMessageContent.VideoCircle && incoming is ChatMessageContent.VideoCircle -> {
				val resolvedLocalPath = incoming.localPath?.takeIf { it.isNotBlank() }
					?: existing.localPath?.takeIf { it.isNotBlank() }
					?: incoming.localPath
					?: existing.localPath
				incoming.copy(
					localPath = resolvedLocalPath,
					remoteUrl = incoming.remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
					width = if (incoming.width > 0) incoming.width else existing.width,
					height = if (incoming.height > 0) incoming.height else existing.height,
					durationMs = if (incoming.durationMs > 0) incoming.durationMs else existing.durationMs,
					thumbnailPath = incoming.thumbnailPath ?: existing.thumbnailPath,
					remoteThumbnailUrl = incoming.remoteThumbnailUrl ?: existing.remoteThumbnailUrl,
				)
			}
			existing is ChatMessageContent.Voice && incoming is ChatMessageContent.Voice -> {
				val resolvedLocalPath = incoming.localPath?.takeIf { it.isNotBlank() }
					?: existing.localPath?.takeIf { it.isNotBlank() }
					?: incoming.localPath
					?: existing.localPath
				incoming.copy(
					localPath = resolvedLocalPath,
					remoteUrl = incoming.remoteUrl?.takeIf { it.isNotBlank() } ?: existing.remoteUrl,
					durationMs = if (incoming.durationMs > 0) incoming.durationMs else existing.durationMs,
					waveform = if (incoming.waveform.isNotEmpty()) incoming.waveform else existing.waveform,
				)
			}
			existing !is ChatMessageContent.Text && incoming is ChatMessageContent.Text -> existing
			else -> incoming
		}
	}

	fun matchesMessageIdentity(
		message: ChatMessageItemModel,
		other: ChatMessageItemModel,
		otherClientMessageId: String? = other.clientMessageId?.trim()?.takeIf(String::isNotEmpty),
	): Boolean {
		return (message.id > 0L && other.id > 0L && message.id == other.id) ||
			(otherClientMessageId != null && message.clientMessageId?.trim() == otherClientMessageId)
	}

	fun identityKey(message: ChatMessageItemModel): String {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		return normalizedClientMessageId?.let { "client:$it" } ?: "id:${message.id}"
	}

	fun mergeMessages(
		messages: List<ChatMessageItemModel>,
		inFlightEdits: Map<Long, String> = emptyMap(),
	): List<ChatMessageItemModel> {
		if (messages.size <= 1) return messages
		val merged = ArrayList<ChatMessageItemModel>(messages.size)
		val indexByServerId = HashMap<Long, Int>(messages.size)
		val indexByClientMessageId = HashMap<String, Int>(messages.size)
		messages.forEach { incoming ->
			val clientMessageId = incoming.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
			val serverIndex = incoming.id.takeIf { it > 0L }?.let(indexByServerId::get)
			val clientIndex = clientMessageId?.let(indexByClientMessageId::get)
			val existingIndex = serverIndex ?: clientIndex
			if (existingIndex == null) {
				val nextIndex = merged.size
				merged += incoming
				if (incoming.id > 0L) indexByServerId[incoming.id] = nextIndex
				clientMessageId?.let { indexByClientMessageId[it] = nextIndex }
			} else {
				val existing = merged[existingIndex]
				val preservedContent = mergeMessageContent(existing.content, incoming.content)
				val resolvedClientMessageId = incoming.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
					?: existing.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
				val inFlightText = inFlightEdits[existing.id] ?: inFlightEdits[incoming.id]
				val resolvedText = inFlightText ?: incoming.text
				val resolvedDeliveryState = when {
					existing.deliveryState == ChatDeliveryState.SENDING && incoming.deliveryState == null -> ChatDeliveryState.SENDING
					else -> incoming.deliveryState ?: existing.deliveryState
				}
				val normalizedIncoming = if (existing.clientMessageId != null && incoming.id > 0L) {
					incoming.copy(
						clientMessageId = resolvedClientMessageId,
						createdAtMillis = existing.createdAtMillis,
						content = preservedContent,
						text = resolvedText,
						deliveryState = resolvedDeliveryState,
					)
				} else {
					incoming.copy(
						clientMessageId = resolvedClientMessageId,
						content = preservedContent,
						text = resolvedText,
						deliveryState = resolvedDeliveryState,
					)
				}
				merged[existingIndex] = normalizedIncoming
				if (normalizedIncoming.id > 0L) indexByServerId[normalizedIncoming.id] = existingIndex
				clientMessageId?.let { indexByClientMessageId[it] = existingIndex }
				existing.clientMessageId?.trim()?.takeIf(String::isNotEmpty)?.let { indexByClientMessageId[it] = existingIndex }
			}
		}
		return merged.sortedBy(ChatMessageItemModel::createdAtMillis)
	}

	fun mergeFreshSnapshotIntoCurrent(
		current: DirectChatScreenState,
		loadedState: DirectChatScreenState,
	): DirectChatScreenState {
		if (current !is DirectChatScreenState.HasData || loadedState !is DirectChatScreenState.HasData) {
			return loadedState
		}
		return loadedState.copy(
			messages = current.messages
				.filter(::isLocalOutgoingPending)
				.fold(loadedState.messages) { messages, pending -> upsertMessage(messages, pending) },
			pinnedMessages = loadedState.pinnedMessages.ifEmpty { current.pinnedMessages },
			input = current.input,
			currentReplyMessageId = current.currentReplyMessageId,
			editingMessageId = current.editingMessageId,
			highlightedMessageId = current.highlightedMessageId ?: loadedState.highlightedMessageId,
			anchorRequestMessageId = current.anchorRequestMessageId,
			anchorRequestOffsetPx = current.anchorRequestOffsetPx,
			anchorRequestToken = current.anchorRequestToken,
			keepAnchorRequest = current.keepAnchorRequest,
			scrollToBottomRequestToken = current.scrollToBottomRequestToken,
			scrollToBottomBadgeCount = current.scrollToBottomBadgeCount,
			typingUserNames = current.typingUserNames,
		)
	}
}
