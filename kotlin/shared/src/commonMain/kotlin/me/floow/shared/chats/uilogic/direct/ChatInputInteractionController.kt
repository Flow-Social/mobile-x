package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.toReplyDraft
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.ChatTypingState
import me.floow.shared.chats.model.ConversationActivityState
import me.floow.shared.chats.model.withSavedMessagesSemantics
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.shared.chats.uilogic.ConversationActivityStore
import me.floow.shared.chats.uilogic.direct.coordinator.ChatActions
import me.floow.shared.chats.uilogic.direct.coordinator.ChatTypingCoordinator
import me.floow.uikit.util.formatPresenceStatusLabel

internal fun ChatPresenceState.resolveIsOnline(): Boolean = isOnline

internal fun ChatPresenceState.resolveLastSeenAtMillis(fallback: Long? = null): Long? {
	return lastSeenAtMillis ?: fallback
}

class ChatInputInteractionController(
	private val repository: ChatThreadRepository,
	private val activityStore: ConversationActivityStore?,
	private val typingCoordinator: ChatTypingCoordinator,
	private val timelineReducer: ChatTimelineReducer,
	private val stateFlow: MutableStateFlow<DirectChatScreenState>,
	private val scope: CoroutineScope,
	private val localRecordingStartedAtMillisProvider: () -> Long?,
	private val activeRequestProvider: () -> DirectChatInitialRequest?,
) {
	companion object {
		private const val MIN_INCOMING_TYPING_TTL_MS = 800L
	}

	val inFlightEdits = mutableMapOf<Long, String>()
	var latestPresenceState: ChatPresenceState = ChatPresenceState()
		private set
	var hasObservedPresenceState: Boolean = false
		private set
	var latestTypingState: ChatTypingState = ChatTypingState()
		private set

	fun updatePresence(presence: ChatPresenceState, hasObserved: Boolean) {
		latestPresenceState = presence
		hasObservedPresenceState = hasObserved
	}

	fun updateTyping(typing: ChatTypingState) {
		latestTypingState = typing
	}

	fun updateInput(text: String) {
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					input = text,
					conversationActivityState = resolveConversationActivityState(
						header = current.header,
						messages = current.messages,
						input = text,
					),
				)
				is DirectChatScreenState.NoMessages -> current.copy(
					input = text,
					conversationActivityState = resolveConversationActivityState(
						header = current.header,
						messages = emptyList(),
						input = text,
					),
				)
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
		scheduleOutgoingTyping(text)
	}

	fun scheduleOutgoingTyping(text: String) {
		val conversationId = (stateFlow.value as? DirectChatScreenState.HasData)?.conversationId ?: return
		typingCoordinator.scheduleOutgoingTyping(conversationId, text)
	}

	fun stopOutgoingTyping(conversationId: Long) {
		typingCoordinator.stopOutgoingTyping(conversationId)
	}

	fun addReply(messageId: Long) {
		if (messageId <= 0L) return
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.messages.firstOrNull { it.id == messageId && !it.isDeleted }
                    ?.let { current.copy(replyDraft = it.toReplyDraft()) } ?: current
				else -> current
			}
		}
	}

	fun clearReply() {
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(replyDraft = null)
				else -> current
			}
		}
	}

	fun startEditing(messageId: Long) {
		if (messageId <= 0L) return
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> {
					val message = current.messages.firstOrNull { it.id == messageId }
					current.copy(editingMessageId = messageId, input = message?.text ?: current.input)
				}
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
	}

	fun cancelEditing() {
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(editingMessageId = null, input = "")
				else -> current
			}
		}
		publishLocalActivityFromCurrentState()
	}

	fun togglePin(conversationId: Long, messageId: Long, isPinned: Boolean, chatActions: ChatActions) {
		if (messageId <= 0L) return
        chatActions.togglePin(conversationId, messageId, isPinned) {}
	}

	fun pinMessage(conversationId: Long, messageId: Long, chatActions: ChatActions) {
		togglePin(conversationId, messageId, true, chatActions)
	}

	fun unpinMessage(conversationId: Long, messageId: Long, chatActions: ChatActions) {
		togglePin(conversationId, messageId, false, chatActions)
	}

	fun normalizePinnedMessages(
		messages: List<ChatMessageItemModel>,
		peerLastReadMessageId: Long?,
	): List<ChatMessageItemModel> {
		return timelineReducer.applyPeerReadState(messages, peerLastReadMessageId)
			.filter(ChatMessageItemModel::isPinned)
			.distinctBy { message -> message.id.takeIf { it > 0L } ?: message.clientMessageId.orEmpty() }
			.sortedWith(
				compareByDescending<ChatMessageItemModel> { it.pinnedAtMillis ?: 0L }
					.thenByDescending { it.createdAtMillis }
					.thenByDescending { it.id }
			)
	}

	fun mergePinnedFlagsIntoTimeline(
		timeline: List<ChatMessageItemModel>,
		pinnedMessages: List<ChatMessageItemModel>,
	): List<ChatMessageItemModel> {
		val pinnedById = pinnedMessages
			.filter { it.id > 0L }
			.associateBy(ChatMessageItemModel::id)
		return timeline.map { message ->
			val pinned = pinnedById[message.id]
			when {
				pinned != null -> message.copy(isPinned = true, pinnedAtMillis = pinned.pinnedAtMillis)
				message.isPinned -> message.copy(isPinned = false, pinnedAtMillis = null)
				else -> message
			}
		}
	}

	fun publishLocalActivityFromCurrentState() {
		when (val current = stateFlow.value) {
			is DirectChatScreenState.HasData -> publishLocalActivity(
				conversationId = current.conversationId,
				header = current.header,
				input = current.input,
				isEditing = current.editingMessageId != null,
			)
			is DirectChatScreenState.NoMessages -> publishLocalActivity(
				conversationId = current.conversationId,
				header = current.header,
				input = current.input,
				isEditing = false,
			)
			else -> Unit
		}
	}

	fun draftLookupKey(conversationId: Long?, header: ChatThreadHeaderModel): String {
		val activeRequest = activeRequestProvider()
		return conversationId?.takeIf { it > 0L }?.toString()
			?: activeRequest?.conversationId?.takeIf { it > 0L }?.toString()
			?: header.peerUserId.trim().takeIf(String::isNotEmpty)
			?: activeRequest?.peerUserId?.trim()?.takeIf(String::isNotEmpty)
			?: ""
	}

	fun publishLocalActivity(
		conversationId: Long?,
		header: ChatThreadHeaderModel,
		input: String,
		isEditing: Boolean,
	) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L }
		val draftKey = draftLookupKey(conversationId, header)
		if (header.isSavedMessages) {
			if (resolvedConversationId != null) activityStore?.clearLocalActivity(resolvedConversationId)
			if (draftKey.isNotEmpty()) activityStore?.setDraftTextByKey(draftKey, "")
			return
		}
		if (isEditing) {
			if (resolvedConversationId != null) activityStore?.setDraftText(resolvedConversationId, "")
			if (draftKey.isNotEmpty()) activityStore?.setDraftTextByKey(draftKey, "")
			return
		}
		val isBlankInput = input.isBlank()
		val localRecordingStartedAtMillis = localRecordingStartedAtMillisProvider()
		val localActivity = localRecordingStartedAtMillis?.let { startedAt ->
			ConversationActivityState.RecordingVideo(startedAtMillis = startedAt)
		} ?: if (!isBlankInput) {
			ConversationActivityState.Draft(
				updatedAtMillis = currentChatEpochMillis(),
				text = input.trim(),
			)
		} else {
			ConversationActivityState.None
		}
		if (resolvedConversationId != null) {
			activityStore?.setDraftText(resolvedConversationId, input)
			if (localActivity is ConversationActivityState.None) {
				activityStore?.clearLocalActivity(resolvedConversationId)
			} else {
				activityStore?.setLocalActivity(resolvedConversationId, localActivity)
			}
		}
		if (draftKey.isNotEmpty()) {
			activityStore?.setDraftTextByKey(draftKey, input)
		}
	}

	fun restoredDraftInput(
		conversationId: Long?,
		header: ChatThreadHeaderModel,
	): String {
		if (header.isSavedMessages) return ""
		val draftKey = draftLookupKey(conversationId, header)
		val textByKey = if (draftKey.isNotEmpty()) activityStore?.getDraftTextByKey(draftKey).orEmpty() else ""
		if (textByKey.isNotBlank()) return textByKey
		return activityStore?.getDraftText(conversationId).orEmpty()
	}

	fun clearRecordingActivityForCurrentConversation() {
		if (localRecordingStartedAtMillisProvider() == null) return
		when (val current = stateFlow.value) {
			is DirectChatScreenState.HasData -> activityStore?.clearLocalActivity(current.conversationId)
			is DirectChatScreenState.NoMessages -> current.conversationId
				?.takeIf { it > 0L }
				?.let { activityStore?.clearLocalActivity(it) }
			else -> Unit
		}
	}

	fun mergeLiveHeader(header: ChatThreadHeaderModel): ChatThreadHeaderModel {
		if (header.isSavedMessages) {
			return header.withSavedMessagesSemantics()
		}
		return header.copy(subtitle = resolveLiveHeaderSubtitle() ?: header.subtitle)
	}

	fun applyHeaderSubtitle() {
		val currentHeader = when (val current = stateFlow.value) {
			is DirectChatScreenState.HasData -> current.header
			is DirectChatScreenState.NoMessages -> current.header
			else -> null
		}
		if (currentHeader?.isSavedMessages == true) {
			updateHeaderSubtitle(null)
			return
		}
		updateHeaderSubtitle(resolveLiveHeaderSubtitle())
	}

	fun updateHeaderSubtitle(subtitle: String?) {
		stateFlow.update { current ->
			when (current) {
				is DirectChatScreenState.HasData -> current.copy(
					header = current.header.copy(subtitle = subtitle),
					peerIsOnline = latestPresenceState.resolveIsOnline(),
					peerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
					typingUserNames = resolveTypingUserNames(current.header),
					conversationActivityState = resolveConversationActivityState(current.header, current.messages, current.input),
				)
				is DirectChatScreenState.NoMessages -> current.copy(
					header = current.header.copy(subtitle = subtitle),
					peerIsOnline = latestPresenceState.resolveIsOnline(),
					peerLastSeenAtMillis = latestPresenceState.resolveLastSeenAtMillis(current.peerLastSeenAtMillis),
					conversationActivityState = resolveConversationActivityState(current.header, emptyList(), current.input),
				)
				else -> current
			}
		}
	}

	fun resolveTypingUserNames(header: ChatThreadHeaderModel): List<String> {
		val typing = resolveConversationActivityState(header, emptyList(), "") as? ConversationActivityState.Typing
			?: return emptyList()
		return listOf(typing.displayName.ifBlank { header.title })
	}

	fun resolveConversationActivityState(
		header: ChatThreadHeaderModel,
		messages: List<ChatMessageItemModel>,
		input: String,
	): ConversationActivityState {
		if (header.isSavedMessages) return ConversationActivityState.None
		if (latestTypingState.isTyping) {
			val ttlMs = latestTypingState.typingTtlMs.takeIf { it > 0L } ?: MIN_INCOMING_TYPING_TTL_MS
			return ConversationActivityState.Typing(
				displayName = latestTypingState.displayName.ifBlank { header.title },
				expiresAtMillis = currentChatEpochMillis() + ttlMs,
			)
		}
		localRecordingStartedAtMillisProvider()?.let { startedAt ->
			return ConversationActivityState.RecordingVideo(startedAtMillis = startedAt)
		}
		val latestSendingMessage = messages
			.asSequence()
			.filter { it.isOutgoing && it.deliveryState == ChatDeliveryState.SENDING }
			.maxByOrNull(ChatMessageItemModel::createdAtMillis)
		latestSendingMessage?.let {
			ConversationActivityState.Sending(startedAtMillis = it.createdAtMillis)
		}?.let { return it }
		return if (input.isNotBlank()) {
			ConversationActivityState.Draft(
				updatedAtMillis = currentChatEpochMillis(),
				text = input.trim(),
			)
		} else {
			ConversationActivityState.None
		}
	}

	fun resolveLiveHeaderSubtitle(): String? {
		val lastSeenAtMillis = latestPresenceState.lastSeenAtMillis
		return when {
			latestTypingState.isTyping -> {
				val isRecordingVideo = latestTypingState.action == "recording_video"
				if (latestTypingState.displayName.isNotBlank()) {
					if (isRecordingVideo) "${latestTypingState.displayName} записывает видео…" else "${latestTypingState.displayName} печатает…"
				} else {
					if (isRecordingVideo) "записывает видео…" else "печатает…"
				}
			}
			latestPresenceState.isOnline || lastSeenAtMillis != null || hasObservedPresenceState -> formatPresenceStatusLabel(
				isOnline = latestPresenceState.isOnline,
				lastSeenAtMillis = lastSeenAtMillis,
			)
			else -> null
		}
	}

	fun reset() {
		latestPresenceState = ChatPresenceState()
		hasObservedPresenceState = false
		latestTypingState = ChatTypingState()
		inFlightEdits.clear()
	}
}
