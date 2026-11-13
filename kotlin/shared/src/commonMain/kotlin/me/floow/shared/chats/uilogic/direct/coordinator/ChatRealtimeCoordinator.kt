package me.floow.shared.chats.uilogic.direct.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeEvent
import me.floow.shared.chats.uilogic.direct.ChatThreadPage
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository

class ChatRealtimeCoordinator(
	private val repository: ChatThreadRepository,
	private val realtimeContract: ChatRealtimeContract? = null,
	private val presenceContract: ChatPresenceContract? = null,
	private val scope: CoroutineScope,
) {
	private var realtimeJob: Job? = null
	private var localMessagesJob: Job? = null
	private var pinnedMessagesJob: Job? = null
	private var presenceJob: Job? = null

	var latestPresenceState: ChatPresenceState = ChatPresenceState()
		private set
	var hasObservedPresenceState: Boolean = false
		private set

	fun observeRealtime(
		conversationId: Long?,
		afterSeq: Long,
		onEvent: (ChatRealtimeEvent) -> Unit,
	) {
		if (conversationId == null || realtimeContract == null) return
		realtimeJob?.cancel()
		realtimeJob = scope.launch {
			realtimeContract.observeConversation(conversationId, afterSeq = afterSeq).collect { event ->
				onEvent(event)
			}
		}
	}

	fun observeLocalMessages(
		conversationId: Long?,
		onMessagesUpdated: (ChatThreadPage) -> Unit,
	) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L } ?: return
		localMessagesJob?.cancel()
		localMessagesJob = scope.launch {
			repository.observeMessages(resolvedConversationId).collect { page ->
				onMessagesUpdated(page)
			}
		}
	}

	fun observePinnedMessages(
		conversationId: Long?,
		onPinnedUpdated: (List<ChatMessageItemModel>) -> Unit,
	) {
		val resolvedConversationId = conversationId?.takeIf { it > 0L } ?: return
		pinnedMessagesJob?.cancel()
		pinnedMessagesJob = scope.launch {
			repository.observePinnedMessages(resolvedConversationId).collect { messages ->
				onPinnedUpdated(messages)
			}
		}
	}

	fun observePresence(
		header: ChatThreadHeaderModel,
		onPresenceChanged: (ChatPresenceState) -> Unit,
	) {
		if (header.isSavedMessages) {
			presenceJob?.cancel()
			presenceJob = null
			latestPresenceState = ChatPresenceState()
			hasObservedPresenceState = false
			onPresenceChanged(latestPresenceState)
			return
		}
		val peerUserId = header.peerUserId
		if (peerUserId.isBlank() || presenceContract == null) return
		presenceJob?.cancel()
		presenceJob = scope.launch {
			presenceContract.observePeerPresence(peerUserId).collectLatest { presence ->
				latestPresenceState = presence
				hasObservedPresenceState = true
				onPresenceChanged(presence)
			}
		}
	}

	fun stopAll() {
		realtimeJob?.cancel()
		realtimeJob = null
		localMessagesJob?.cancel()
		localMessagesJob = null
		pinnedMessagesJob?.cancel()
		pinnedMessagesJob = null
		presenceJob?.cancel()
		presenceJob = null
		latestPresenceState = ChatPresenceState()
		hasObservedPresenceState = false
	}

	fun dispose() {
		stopAll()
	}
}
