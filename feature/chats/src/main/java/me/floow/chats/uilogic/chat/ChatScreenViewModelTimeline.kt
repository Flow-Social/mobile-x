package me.floow.chats.uilogic.chat

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.chats.uilogic.shared.toTimelineReadOpenMode
import me.floow.chats.uilogic.shared.UnifiedOpenMode
import me.floow.chats.uilogic.shared.resolveAnchorMessageIdByCursor
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.readmodel.TimelineReadItem
import me.floow.domain.readmodel.TimelineReadProjectorInput
import me.floow.domain.readmodel.projectTimelineReadModel
import me.floow.domain.utils.toLocalDateTimeFromEpochMillis
import me.floow.uikit.chat.model.ChatInitialViewport
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import java.time.LocalDateTime

internal fun projectDirectChatReadModel(
	input: DirectChatReadModelInput
): DirectChatReadModelProjection {
	val incomingMessages = input.messages
		.asSequence()
		.filter { message -> message is PrimaryInMessage || message is ReplyInMessage }
		.toList()
	val serverReadUpToMessageId = input.serverReadUpToMessageId.coerceAtLeast(0L)
	val localReadUpToMessageId = input.localReadUpToMessageId.coerceAtLeast(0L)
	val effectiveReadUpToMessageId = maxOf(serverReadUpToMessageId, localReadUpToMessageId)
	val normalizedOpenAnchorMessageId = input.openAnchorMessageId
		?.takeIf { id -> id > 0L }
	val firstUnreadMessageId = input.firstUnreadMessageId
		?.takeIf { id -> id > effectiveReadUpToMessageId }
		?: incomingMessages.firstOrNull { message -> message.id > effectiveReadUpToMessageId }?.id

	val readProjection = projectTimelineReadModel(
		input = TimelineReadProjectorInput(
			items = incomingMessages.map { message ->
				TimelineReadItem(
					messageId = message.id,
					cursor = message.id,
					isIncoming = true
				)
			},
			serverReadUpToCursor = serverReadUpToMessageId,
			localReadUpToCursor = localReadUpToMessageId,
			firstUnreadCursor = firstUnreadMessageId,
			storedOpenAnchorCursor = normalizedOpenAnchorMessageId,
			resolvedOpenAnchorCursor = null,
			openMode = input.openMode.toTimelineReadOpenMode(),
			messageLinkAnchorCursor = input.messageLinkAnchorMessageId?.takeIf { id -> id > 0L },
			preferCeilOpenAnchor = input.openMode != DirectChatOpenMode.FROM_LAST_SEEN,
			fallbackToOldestOpenAnchor = true
		)
	)

	return DirectChatReadModelProjection(
		unreadMessageIds = readProjection.unreadMessageIds,
		unreadBoundaryMessageId = readProjection.unreadBoundaryMessageId,
		openAnchorMessageId = readProjection.openAnchorMessageId,
		readUpToMessageId = readProjection.readUpToCursor
	)
}

internal fun DirectChatOpenMode.toUnifiedOpenMode(): UnifiedOpenMode {
	return this
}

internal fun DirectChatConversation.displayPeerName(): String {
	return peer.name?.trim().takeIf { !it.isNullOrEmpty() }
		?: peer.username?.trim()?.takeIf(String::isNotEmpty)
		?: "User ${peer.id}"
}

internal fun DirectChatMessage.toUiMessage(
	peerUserId: String,
	selfUserId: String? = null,
	uiKey: String? = null
): ChatMessage {
	val resolvedUiKey = uiKey
		?: clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		?.let { key -> "cmid_$key" }
		?: "msg_$id"
	val normalizedSelfUserId = selfUserId?.trim()?.takeIf(String::isNotEmpty)
	val isOutgoing = normalizedSelfUserId?.let { sender.id == it } ?: (sender.id != peerUserId)
	val dateTime = createdAt.toLocalDateTimeFromEpochMillis()
	val senderName = sender.name?.trim()?.takeIf(String::isNotEmpty)
		?: sender.username?.trim()?.takeIf(String::isNotEmpty)
		?: "User ${sender.id}"
	val senderUsername = sender.username?.trim()?.takeIf(String::isNotEmpty)
	val senderAvatar = sender.avatarUrl?.trim()?.takeIf(String::isNotEmpty)
	val replyToId = replyToMessageId
	val replyText = replyToMessageText.orEmpty()
	return if (!isOutgoing) {
		if (replyToId != null) {
			ReplyInMessage(
				id = id,
				uiKey = resolvedUiKey,
				clientMessageId = clientMessageId,
				replyMessageId = replyToId,
				replyMessageText = replyText,
				messageText = text,
				dateTime = dateTime,
				isPinned = isPinned,
				authorName = senderName,
				authorUsername = senderUsername,
				authorAvatarUrl = senderAvatar,
				deliveryStatus = deliveryStatus
			)
		} else {
			PrimaryInMessage(
				id = id,
				uiKey = resolvedUiKey,
				clientMessageId = clientMessageId,
				messageText = text,
				dateTime = dateTime,
				isPinned = isPinned,
				authorName = senderName,
				authorUsername = senderUsername,
				authorAvatarUrl = senderAvatar,
				deliveryStatus = deliveryStatus
			)
		}
	} else {
		if (replyToId != null) {
			ReplyOutMessage(
				id = id,
				uiKey = resolvedUiKey,
				clientMessageId = clientMessageId,
				replyMessageId = replyToId,
				replyMessageText = replyText,
				messageText = text,
				dateTime = dateTime,
				isPinned = isPinned,
				authorName = senderName,
				authorUsername = senderUsername,
				authorAvatarUrl = senderAvatar,
				deliveryStatus = deliveryStatus
			)
		} else {
			PrimaryOutMessage(
				id = id,
				uiKey = resolvedUiKey,
				clientMessageId = clientMessageId,
				messageText = text,
				dateTime = dateTime,
				isPinned = isPinned,
				authorName = senderName,
				authorUsername = senderUsername,
				authorAvatarUrl = senderAvatar,
				deliveryStatus = deliveryStatus
			)
		}
	}
}

internal fun groupMessagesByDate(messages: List<ChatMessage>): List<DatedChatMessages> {
	if (messages.isEmpty()) return emptyList()
	return messages
		.sortedBy(ChatMessage::dateTime)
		.groupBy { message -> message.dateTime.toLocalDate() }
		.toSortedMap()
		.map { (date, items) ->
			DatedChatMessages(datetime = date, messages = items)
		}
}

internal fun resolveTimelineInitialViewport(
	groupedMessages: List<DatedChatMessages>,
	targetMessageId: Long,
	offsetPx: Int
): ChatInitialViewport? {
	if (targetMessageId <= 0L) return null
	var timelineIndex = 0
	// ChatScreen uses NewestAtBottom by default, which renders the timeline in reverse:
	// newer groups first, newer messages first inside the group, and the date header after
	// the group's messages. Cold start must calculate the same flattened index order as the UI.
	groupedMessages.asReversed().forEach { group ->
		group.messages.asReversed().forEach { message ->
			if (message.id == targetMessageId) {
				return ChatInitialViewport(
					itemIndex = timelineIndex,
					itemScrollOffsetPx = offsetPx.coerceAtLeast(0)
				)
			}
			timelineIndex += 1
		}
		timelineIndex += 1
	}
	return null
}

internal fun flattenMessages(groups: List<DatedChatMessages>?): List<ChatMessage> {
	return groups.orEmpty().flatMap(DatedChatMessages::messages).sortedBy(ChatMessage::dateTime)
}

internal fun mergeMessages(base: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
	if (incoming.isEmpty()) return base.sortedBy(ChatMessage::dateTime)
	val all = base + incoming
	val (withClientIds, withoutClientIds) = all.partition { !it.clientMessageId.isNullOrBlank() }
	val mergedByClientId = withClientIds
		.groupBy { it.clientMessageId!!.trim() }
		.map { (_, messages) ->
			val preferred = messages.maxByOrNull(ChatMessage::id) ?: messages.first()
			val optimisticOutgoing = messages.firstOrNull { message ->
				message.id <= 0L && (message is PrimaryOutMessage || message is ReplyOutMessage)
			}
			if (optimisticOutgoing != null) {
				preferred.withDateTime(optimisticOutgoing.dateTime)
			} else {
				preferred
			}
		}
	val mergedById = withoutClientIds
		.associateBy(ChatMessage::id)
		.values
	return (mergedByClientId + mergedById).sortedBy(ChatMessage::dateTime)
}

internal fun replaceOrMergeMessage(
	base: List<ChatMessage>,
	replaceId: Long,
	confirmed: ChatMessage
): List<ChatMessage> {
	val index = base.indexOfFirst { it.id == replaceId }
	if (index < 0) {
		return mergeMessages(base, listOf(confirmed))
	}
	val placeholder = base[index]
	val updated = base.toMutableList()
	updated[index] = confirmed.withDateTime(placeholder.dateTime)
	return updated
		.distinctBy(ChatMessage::id)
}

internal fun mergeObservedLatestMessages(
	currentMessages: List<ChatMessage>,
	observedMessages: List<ChatMessage>,
	pendingOutgoingOptimisticIds: Set<Long>
): List<ChatMessage> {
	if (currentMessages.isEmpty()) return observedMessages.sortedBy(ChatMessage::dateTime)
	if (observedMessages.isEmpty()) return currentMessages.sortedBy(ChatMessage::dateTime)
	val observedClientIds = observedMessages
		.asSequence()
		.mapNotNull(ChatMessage::clientMessageId)
		.map(String::trim)
		.filter(String::isNotEmpty)
		.toSet()
	val optimisticMessages = currentMessages.filter { message ->
		message.id <= 0L &&
			pendingOutgoingOptimisticIds.contains(message.id) &&
			(message.clientMessageId.isNullOrBlank() || !observedClientIds.contains(message.clientMessageId!!.trim()))
	}
	val oldestObservedMessageId = observedMessages
		.asSequence()
		.map(ChatMessage::id)
		.filter { it > 0L }
		.minOrNull()
	val loadedOlderMessages = if (oldestObservedMessageId != null) {
		currentMessages.filter { message ->
			message.id > 0L && message.id < oldestObservedMessageId
		}
	} else {
		currentMessages.filter { message -> message.id > 0L }
	}
	return mergeMessages(
		base = loadedOlderMessages + optimisticMessages,
		incoming = observedMessages
	)
}

internal suspend fun buildStateSnapshot(
	conversationId: Long?,
	peerUserId: String,
	peerName: String,
	peerAvatarUrl: Uri?,
	messages: List<ChatMessage>,
	serverReadUpToMessageId: Long,
	localReadUpToMessageId: Long,
	openAnchorMessageId: Long?,
	openMode: DirectChatOpenMode,
	messageLinkAnchorMessageId: Long?,
	firstUnreadMessageIdOverride: Long?
): DirectChatRenderedMessagesSnapshot? {
	if (conversationId == null || peerUserId.isBlank()) return null
	return buildRenderedMessagesSnapshot(
		conversation = DirectChatConversation(
			id = conversationId,
			kind = "direct",
			peer = me.floow.domain.models.DirectChatPeer(
				id = peerUserId,
				username = null,
				name = peerName.takeIf { it.isNotBlank() },
				avatarUrl = peerAvatarUrl?.toString()
			),
			lastMessage = null,
			unreadCount = 0,
			lastReadMessageId = serverReadUpToMessageId,
			createdAt = 0L,
			updatedAt = 0L
		),
		messages = messages,
		serverReadUpToMessageId = serverReadUpToMessageId,
		localReadUpToMessageId = localReadUpToMessageId,
		openAnchorMessageId = openAnchorMessageId,
		openMode = openMode,
		messageLinkAnchorMessageId = messageLinkAnchorMessageId,
		firstUnreadMessageIdOverride = firstUnreadMessageIdOverride
	)
}

internal suspend fun buildObservedPageProjection(
	conversation: DirectChatConversation,
	page: DirectChatMessagesPage,
	currentMessages: List<ChatMessage>,
	pendingOutgoingOptimisticIds: Set<Long>,
	pendingOutgoingClientMessageIds: Map<Long, String>,
	confirmedReadUpToMessageId: Long,
	localReadUpToMessageId: Long,
	selfUserId: String?,
	openAnchorMessageId: Long?,
	openMode: DirectChatOpenMode,
	messageLinkAnchorMessageId: Long?,
	onResolvedOutgoing: (List<DirectChatMessage>) -> Unit
): DirectChatObservedPageProjection? {
	val confirmedClientMessageIds = page.items
		.asSequence()
		.filter { message -> message.id > 0L }
		.mapNotNull(DirectChatMessage::clientMessageId)
		.map(String::trim)
		.filter(String::isNotEmpty)
		.toSet()
	val observedItems = if (confirmedClientMessageIds.isEmpty()) {
		page.items
	} else {
		page.items.filterNot { message ->
			if (message.id > 0L) {
				false
			} else {
				val clientMessageId = message.clientMessageId?.trim()
				!clientMessageId.isNullOrEmpty() && confirmedClientMessageIds.contains(clientMessageId)
			}
		}
	}
	onResolvedOutgoing(observedItems)
	val persistedMessages = currentMessages.filterNot { message ->
		message.id <= 0L && !pendingOutgoingOptimisticIds.contains(message.id)
	}
	val optimisticByClientId = pendingOutgoingClientMessageIds
		.mapNotNull { (optimisticId, clientMessageId) ->
			val key = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
			val optimistic = currentMessages.firstOrNull { it.id == optimisticId } ?: return@mapNotNull null
			key to optimistic
		}
		.toMap()
	val observedMessages = observedItems.map { message ->
		val clientMessageId = message.clientMessageId?.trim()
		val optimistic = if (!clientMessageId.isNullOrEmpty()) optimisticByClientId[clientMessageId] else null
		val uiMessage = message.toUiMessage(
			peerUserId = conversation.peer.id,
			selfUserId = selfUserId,
			uiKey = optimistic?.uiKey
		)
		if (optimistic != null) {
			uiMessage.withDateTime(optimistic.dateTime)
		} else {
			uiMessage
		}
	}
	val mergedMessages = mergeObservedLatestMessages(
		currentMessages = persistedMessages,
		observedMessages = observedMessages,
		pendingOutgoingOptimisticIds = pendingOutgoingOptimisticIds
	)
	val renderedSnapshot = buildRenderedMessagesSnapshot(
		conversation = conversation,
		messages = mergedMessages,
		serverReadUpToMessageId = maxOf(
			confirmedReadUpToMessageId,
			conversation.lastReadMessageId.coerceAtLeast(0L)
		),
		localReadUpToMessageId = localReadUpToMessageId,
		openAnchorMessageId = openAnchorMessageId,
		openMode = openMode,
		messageLinkAnchorMessageId = messageLinkAnchorMessageId
	)
	return DirectChatObservedPageProjection(
		renderedSnapshot = renderedSnapshot,
		nextBeforeId = page.nextBeforeId,
		canLoadMore = page.nextBeforeId != null,
		peerLastReadMessageId = page.peerLastReadMessageId?.coerceAtLeast(0L) ?: 0L,
		preservePaginationCursor = hasLoadedOlderMessages(
			currentMessages = currentMessages,
			observedMessages = observedMessages
		)
	)
}

internal fun currentConversationSnapshot(
	state: ChatScreenVmState,
	confirmedReadUpToMessageId: Long
): DirectChatConversation? {
	val conversationId = state.conversationId ?: return null
	val peerUserId = state.chatInterlocutorId.trim().takeIf(String::isNotEmpty) ?: return null
	return DirectChatConversation(
		id = conversationId,
		kind = "direct",
		peer = me.floow.domain.models.DirectChatPeer(
			id = peerUserId,
			username = null,
			name = state.chatInterlocutorName.takeIf { it.isNotBlank() },
			avatarUrl = state.chatInterlocutorAvatarUrl?.toString()
		),
		lastMessage = null,
		unreadCount = state.unreadMessageIds.size,
		lastReadMessageId = confirmedReadUpToMessageId,
		createdAt = 0L,
		updatedAt = 0L
	)
}

internal fun hasLoadedOlderMessages(
	currentMessages: List<ChatMessage>,
	observedMessages: List<ChatMessage>
): Boolean {
	val oldestObservedMessageId = observedMessages.minOfOrNull(ChatMessage::id) ?: return false
	return currentMessages.any { message ->
		message.id > 0L && message.id < oldestObservedMessageId
	}
}

internal suspend fun buildRenderedMessagesSnapshot(
	conversation: DirectChatConversation,
	messages: List<ChatMessage>,
	serverReadUpToMessageId: Long,
	localReadUpToMessageId: Long,
	openAnchorMessageId: Long?,
	openMode: DirectChatOpenMode,
	messageLinkAnchorMessageId: Long?,
	firstUnreadMessageIdOverride: Long? = null
): DirectChatRenderedMessagesSnapshot = withContext(Dispatchers.Default) {
	val groupedMessages = groupMessagesByDate(messages)
	val projection = projectDirectChatReadModel(
		input = DirectChatReadModelInput(
			peerUserId = conversation.peer.id,
			messages = messages,
			serverReadUpToMessageId = serverReadUpToMessageId,
			localReadUpToMessageId = localReadUpToMessageId,
			firstUnreadMessageId = firstUnreadMessageIdOverride
				?.takeIf {
					it > maxOf(
						serverReadUpToMessageId.coerceAtLeast(0L),
						localReadUpToMessageId.coerceAtLeast(0L)
					)
				}
				?: inferUnreadBoundaryMessageId(
					messages = messages,
					lastReadMessageId = maxOf(
						serverReadUpToMessageId.coerceAtLeast(0L),
						localReadUpToMessageId.coerceAtLeast(0L)
					),
					peerUserId = conversation.peer.id
				),
			openAnchorMessageId = openAnchorMessageId,
			openMode = openMode,
			messageLinkAnchorMessageId = messageLinkAnchorMessageId
		)
	)
	DirectChatRenderedMessagesSnapshot(
		messages = messages,
		groupedMessages = groupedMessages,
		projection = projection
	)
}

internal fun removeMessageFromState(
	state: ChatScreenVmState,
	messageId: Long,
	readUpToMessageId: Long
): ChatScreenVmState {
	val oldMessages = flattenMessages(state.messages)
	if (oldMessages.none { it.id == messageId }) return state
	val updatedMessages = oldMessages.filterNot { it.id == messageId }
	val groupedMessages = groupMessagesByDate(updatedMessages)
	return state.copy(
		messages = groupedMessages,
		pinnedMessages = state.pinnedMessages.filterNot { message -> message.id == messageId },
		lastDeletedMessage = null,
		messageToEditId = state.messageToEditId.takeUnless { it == messageId },
		unreadBoundaryMessageId = inferUnreadBoundaryMessageId(
			messages = updatedMessages,
			lastReadMessageId = readUpToMessageId,
			peerUserId = state.chatInterlocutorId
		),
		highlightRequest = state.highlightRequest?.takeUnless { it.messageId == messageId }
	)
}

internal fun applyPinnedFlagToState(
	state: ChatScreenVmState,
	messageId: Long,
	isPinned: Boolean
): ChatScreenVmState {
	val updatedGroups = state.messages?.map { group ->
		group.copy(
			messages = group.messages.map { message ->
				if (message.id != messageId) {
					message
				} else {
					message.withPinned(isPinned)
				}
			}
		)
	}
	val updatedPinned = state.pinnedMessages
		.filterNot { message -> message.id == messageId }
		.toMutableList()
	val updatedMessage = updatedGroups
		?.asSequence()
		?.flatMap { group -> group.messages.asSequence() }
		?.firstOrNull { message -> message.id == messageId }
	if (isPinned && updatedMessage != null) {
		updatedPinned.add(updatedMessage)
	}
	return state.copy(
		messages = updatedGroups,
		pinnedMessages = updatedPinned
	)
}

internal fun extractPinnedMessages(groups: List<DatedChatMessages>?): List<ChatMessage> {
	return groups.orEmpty()
		.flatMap(DatedChatMessages::messages)
		.filter(ChatMessage::isPinned)
}

internal fun ChatMessage.withPinned(isPinned: Boolean): ChatMessage {
	return when (this) {
		is PrimaryOutMessage -> copy(isPinned = isPinned)
		is ReplyOutMessage -> copy(isPinned = isPinned)
		is PrimaryInMessage -> copy(isPinned = isPinned)
		is ReplyInMessage -> copy(isPinned = isPinned)
		is PostPreviewMessage -> this
	}
}

internal fun inferUnreadBoundaryMessageId(
	messages: List<ChatMessage>,
	lastReadMessageId: Long,
	peerUserId: String
): Long? {
	if (messages.isEmpty() || peerUserId.isBlank()) return null
	return messages
		.firstOrNull { message ->
			message.id > lastReadMessageId && (message is PrimaryInMessage || message is ReplyInMessage)
		}
		?.id
}

internal fun latestRenderableMessageId(groups: List<DatedChatMessages>?): Long? {
	return flattenMessages(groups).maxOfOrNull(ChatMessage::id)
}

internal fun latestPersistableMessageId(groups: List<DatedChatMessages>?): Long? {
	return flattenMessages(groups)
		.asSequence()
		.map(ChatMessage::id)
		.filter { id -> id > 0L }
		.maxOrNull()
}


internal fun ChatMessage.withDateTime(dateTime: LocalDateTime): ChatMessage {
	return when (this) {
		is PrimaryOutMessage -> copy(dateTime = dateTime)
		is ReplyOutMessage -> copy(dateTime = dateTime)
		is PrimaryInMessage -> copy(dateTime = dateTime)
		is ReplyInMessage -> copy(dateTime = dateTime)
		is PostPreviewMessage -> copy(dateTime = dateTime)
	}
}
