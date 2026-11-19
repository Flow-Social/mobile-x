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

    private val presenceOwner = "direct-chat:${kotlin.random.Random.nextLong()}"
    private var observedPeerId: String? = null

    private fun clearPresence() {
        presenceJob?.cancel()
        presenceJob = null
        if (observedPeerId != null) presenceContract?.clearTargets(presenceOwner)
        observedPeerId = null
        latestPresenceState = ChatPresenceState()
        hasObservedPresenceState = false
    }

	fun observePresence(
		header: ChatThreadHeaderModel,
		onPresenceChanged: (ChatPresenceState) -> Unit,
	) {
        val peerId = header.peerUserId.takeUnless { header.isSavedMessages || it.isBlank() }
        if (peerId == null || presenceContract == null) {
            clearPresence()
            onPresenceChanged(latestPresenceState)
            return
        }
        if (observedPeerId == peerId && presenceJob?.isActive == true) return
        clearPresence()
        observedPeerId = peerId
        presenceContract.setTargets(presenceOwner, listOf(peerId))
        presenceJob = scope.launch {
            presenceContract.observePeerPresence(peerId).collectLatest { presence ->
                latestPresenceState = presence
                hasObservedPresenceState = presence.isKnown
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
        clearPresence()
	}

	fun dispose() {
		stopAll()
	}
}
