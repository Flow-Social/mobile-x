package me.floow.shared.chats.uilogic.direct.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DeleteMessageResult
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder

class ChatActions(
	private val repository: ChatThreadRepository,
	private val scope: CoroutineScope,
	private val onShowMessage: (String) -> Unit,
) {
	companion object {
		const val UNDO_DELETE_TIMEOUT_MS = 4_000L
		const val MAX_CANCELLED_CLIENT_MESSAGE_IDS = 64
	}

	val pendingOutgoingClientMessageIds = mutableMapOf<Long, String>()
	val pendingOutgoingSendJobs = mutableMapOf<Long, Job>()
	val cancelledClientMessageIds = LinkedHashSet<String>()

	var pendingDeleteJob: Job? = null
		private set
	var pendingDeletedMessage: ChatMessageItemModel? = null
		private set
	var pendingDeleteConversationId: Long? = null
		private set

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

	fun schedulePendingDeleteCommit(
		message: ChatMessageItemModel,
		conversationId: Long,
		onCommitFailed: (ChatMessageItemModel) -> Unit,
	) {
		flushPendingDeleteNow()
		pendingDeletedMessage = message
		pendingDeleteConversationId = conversationId
		pendingDeleteJob = scope.launch {
			delay(UNDO_DELETE_TIMEOUT_MS)
			val commitMessage = pendingDeletedMessage ?: return@launch
			val commitConversationId = pendingDeleteConversationId ?: return@launch
			pendingDeletedMessage = null
			pendingDeleteConversationId = null
			repository.deleteMessage(commitConversationId, commitMessage.id)
				.onFailure {
					onCommitFailed(commitMessage)
					onShowMessage(it.message ?: "delete failed")
				}
		}
	}

	fun flushPendingDeleteNow() {
		val job = pendingDeleteJob ?: return
		job.cancel()
		pendingDeleteJob = null
		val commitMessage = pendingDeletedMessage ?: return
		val commitConversationId = pendingDeleteConversationId ?: return
		pendingDeletedMessage = null
		pendingDeleteConversationId = null
		scope.launch(NonCancellable) {
			withContext(NonCancellable) {
				repository.deleteMessage(commitConversationId, commitMessage.id)
			}
		}
	}

	fun undoDeleteMessage(
		onRestore: (ChatMessageItemModel, Long?) -> Unit,
	): Boolean {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		val deletedMessage = pendingDeletedMessage ?: return false
		val conversationId = pendingDeleteConversationId
		pendingDeletedMessage = null
		pendingDeleteConversationId = null
		onRestore(deletedMessage, conversationId)
		return true
	}

	fun togglePin(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		onOptimisticSuccess: () -> Unit,
	) {
		if (messageId <= 0L) return
		scope.launch {
			repository.setMessagePinned(conversationId, messageId, isPinned)
				.onSuccess {
					onOptimisticSuccess()
				}
				.onFailure {
					onShowMessage(it.message ?: "pin failed")
				}
		}
	}

	fun cancelPendingOutgoingMessage(message: ChatMessageItemModel) {
		message.clientMessageId?.let(::addCancelledClientMessageId)
		val targetId = message.id
		pendingOutgoingSendJobs.remove(targetId)?.cancel()
		pendingOutgoingClientMessageIds.remove(targetId)
	}

	fun dispose() {
		flushPendingDeleteNow()
		pendingOutgoingSendJobs.values.forEach { it.cancel() }
		pendingOutgoingSendJobs.clear()
		pendingOutgoingClientMessageIds.clear()
		cancelledClientMessageIds.clear()
	}
}
