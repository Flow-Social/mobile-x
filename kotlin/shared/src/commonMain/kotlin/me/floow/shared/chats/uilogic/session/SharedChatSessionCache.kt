package me.floow.shared.chats.uilogic.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest

class SharedChatSessionCache {
	private val _chats = MutableStateFlow<List<ChatListItemModel>?>(null)
	val chats: StateFlow<List<ChatListItemModel>?> = _chats.asStateFlow()

	private val threadSnapshotsByConversationId = mutableMapOf<Long, ChatThreadSnapshot>()
	private val conversationIdByPeerUserId = mutableMapOf<String, Long>()

	var savedMessagesEnsureStarted: Boolean = false
		private set
	var savedMessagesEnsured: Boolean = false
		private set
	var savedMessagesConversationId: Long? = null
		private set

	fun updateChats(items: List<ChatListItemModel>) {
		_chats.value = items
	}

	fun markSavedMessagesEnsureStarted(): Boolean {
		if (savedMessagesEnsureStarted) return false
		savedMessagesEnsureStarted = true
		return true
	}

	fun markSavedMessagesEnsureFinished(success: Boolean) {
		if (success) {
			savedMessagesEnsured = true
		} else {
			savedMessagesEnsureStarted = false
		}
	}

	fun cacheThreadSnapshot(snapshot: ChatThreadSnapshot) {
		val conversationId = snapshot.conversationId ?: return
		threadSnapshotsByConversationId[conversationId] = snapshot
		conversationIdByPeerUserId[snapshot.header.peerUserId] = conversationId
		if (snapshot.header.isSavedMessages) {
			savedMessagesConversationId = conversationId
		}
	}

	fun cachedThreadSnapshot(request: DirectChatInitialRequest): ChatThreadSnapshot? {
		return when {
			request.conversationId != null -> threadSnapshotsByConversationId[request.conversationId]
			request.isSavedMessages -> savedMessagesConversationId?.let(threadSnapshotsByConversationId::get)
			request.peerUserId.isNotBlank() -> conversationIdByPeerUserId[request.peerUserId]
				?.let(threadSnapshotsByConversationId::get)
			else -> null
		}
	}

	fun rememberPeer(conversationId: Long, peerUserId: String) {
		if (peerUserId.isBlank()) return
		conversationIdByPeerUserId[peerUserId] = conversationId
	}

	fun peerUserId(conversationId: Long): String? = conversationIdByPeerUserId.entries
		.firstOrNull { it.value == conversationId }
		?.key

	fun threadSnapshot(conversationId: Long): ChatThreadSnapshot? = threadSnapshotsByConversationId[conversationId]

	fun updateThreadMessages(
		conversationId: Long,
		transform: (List<ChatMessageItemModel>) -> List<ChatMessageItemModel>,
		updateMetadata: (ChatThreadSnapshot) -> ChatThreadSnapshot = { it },
	) {
		val cached = threadSnapshotsByConversationId[conversationId] ?: return
		threadSnapshotsByConversationId[conversationId] = updateMetadata(
			cached.copy(messages = transform(cached.messages))
		)
	}
}
