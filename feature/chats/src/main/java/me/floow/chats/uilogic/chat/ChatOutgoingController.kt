package me.floow.chats.uilogic.chat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.uikit.chat.model.ChatMessage

internal class ChatOutgoingController(
	private val scope: CoroutineScope,
	private val chatsRepository: ChatsRepository,
	private val onRestoreDeleted: (ChatMessage) -> Unit,
	private val onClearDeletedMessage: () -> Unit,
) {
	val pendingOutgoingClientMessageIds = mutableMapOf<Long, String>()
	val pendingOutgoingSendJobs = mutableMapOf<Long, Job>()
	private val cancelledClientMessageIds = LinkedHashSet<String>()

	private var pendingDeleteJob: Job? = null
	private var pendingDeleteMessageId: Long? = null
	private var pendingDeleteConversationId: Long? = null
	private var pendingDeletedMessage: ChatMessage? = null

	fun reset() {
		pendingOutgoingClientMessageIds.clear()
		pendingOutgoingSendJobs.values.forEach(Job::cancel)
		pendingOutgoingSendJobs.clear()
		cancelledClientMessageIds.clear()
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		pendingDeleteMessageId = null
		pendingDeleteConversationId = null
		pendingDeletedMessage = null
	}

	fun addCancelledClientMessageId(clientMessageId: String) {
		if (cancelledClientMessageIds.size >= MAX_CANCELLED_IDS) {
			val oldest = cancelledClientMessageIds.iterator().next()
			cancelledClientMessageIds.remove(oldest)
		}
		cancelledClientMessageIds.add(clientMessageId)
	}

	fun removeCancelledClientMessageId(clientMessageId: String) {
		cancelledClientMessageIds.remove(clientMessageId)
	}

	fun isCancelled(clientMessageId: String): Boolean {
		return clientMessageId in cancelledClientMessageIds
	}

	fun isPendingOptimisticId(optimisticId: Long): Boolean {
		return pendingOutgoingClientMessageIds.containsKey(optimisticId)
	}

	fun resolvePendingOptimisticId(clientMessageId: String?): Long? {
		val normalized = clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return null
		return pendingOutgoingClientMessageIds.entries
			.firstOrNull { (_, v) -> v == normalized }
			?.key
	}

	fun schedulePendingDeleteCommit(message: ChatMessage, conversationId: Long?) {
		pendingDeleteJob?.cancel()
		pendingDeleteMessageId = message.id
		pendingDeleteConversationId = conversationId
		pendingDeletedMessage = message
		pendingDeleteJob = scope.launch {
			delay(UNDO_DELETE_TIMEOUT_MS)
			commitPendingDelete()
		}
	}

	fun cancelPendingDelete(): ChatMessage? {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		pendingDeleteMessageId = null
		pendingDeleteConversationId = null
		val message = pendingDeletedMessage
		pendingDeletedMessage = null
		onClearDeletedMessage()
		return message
	}

	fun cancelAllPendingDeletes() {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		pendingDeleteMessageId = null
		pendingDeleteConversationId = null
		pendingDeletedMessage = null
	}

	fun flushPendingDeleteNow() {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		commitPendingDelete()
	}

	private fun commitPendingDelete() {
		val messageId = pendingDeleteMessageId ?: return
		val cid = pendingDeleteConversationId
		val message = pendingDeletedMessage
		pendingDeleteMessageId = null
		pendingDeleteConversationId = null
		pendingDeletedMessage = null
		pendingDeleteJob = null
		onClearDeletedMessage()
		if (cid == null || message == null) return
		scope.launch {
			when (chatsRepository.deleteMessage(conversationId = cid, messageId = messageId)) {
				is UpdateDataResponse.Success -> Unit
				is UpdateDataResponse.Failure -> onRestoreDeleted(message)
			}
		}
	}

	private companion object {
		const val UNDO_DELETE_TIMEOUT_MS = 4_000L
		const val MAX_CANCELLED_IDS = 64
	}
}
