package me.floow.app.notifications

import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.utils.Logger
import me.floow.domain.utils.currentTimeMillis

class DirectChatsRefreshOrchestrator(
	private val chatsRepository: ChatsRepository,
	private val logger: Logger,
) {
	private companion object {
		private const val CONVERSATION_REFRESH_THROTTLE_MS = 1_000L
		private const val CONVERSATIONS_REFRESH_THROTTLE_MS = 1_500L
	}

	private val lock = Any()
	private val lastConversationRefreshAtMs = mutableMapOf<Long, Long>()
	private var lastConversationsRefreshAtMs: Long = 0L

	suspend fun onIncomingMessage(
		conversationId: Long,
		source: String,
	) {
		if (conversationId <= 0L) return
		val now = currentTimeMillis()
		if (shouldRefreshConversation(conversationId, now)) {
			val startedAt = currentTimeMillis()
			when (val response = chatsRepository.refreshLatestMessagesWindow(conversationId = conversationId, limit = 50)) {
				is GetDataResponse.Success -> logger.d(
					"DirectChatsRefreshOrchestrator",
					"apply_event_latency_ms=${currentTimeMillis() - startedAt} source=$source kind=latest_window conversation_id=$conversationId items=${response.data.items.size}"
				)
				is GetDataResponse.Error -> logger.d(
					"DirectChatsRefreshOrchestrator",
					"apply_event_failed source=$source kind=latest_window conversation_id=$conversationId"
				)
			}
		}
		refreshConversationsIfNeeded(source = source, reason = "incoming_message", force = false)
	}

	suspend fun onResyncRequired(
		conversationId: Long?,
		source: String,
	) {
		conversationId?.takeIf { it > 0L }?.let { targetConversationId ->
			val now = currentTimeMillis()
			if (shouldRefreshConversation(targetConversationId, now, force = true)) {
				when (chatsRepository.refreshLatestMessagesWindow(conversationId = targetConversationId, limit = 50)) {
					is GetDataResponse.Success -> Unit
					is GetDataResponse.Error -> logger.d(
						"DirectChatsRefreshOrchestrator",
						"resync_refresh_failed source=$source kind=latest_window conversation_id=$targetConversationId"
					)
				}
			}
		}
		refreshConversationsIfNeeded(source = source, reason = "resync_required", force = true)
	}

	private suspend fun refreshConversationsIfNeeded(
		source: String,
		reason: String,
		force: Boolean,
	) {
		val now = currentTimeMillis()
		if (!shouldRefreshConversations(now, force)) return
		val startedAt = currentTimeMillis()
		when (val response = chatsRepository.getConversations(limit = 50, cursor = null)) {
			is GetDataResponse.Success -> logger.d(
				"DirectChatsRefreshOrchestrator",
				"apply_event_latency_ms=${currentTimeMillis() - startedAt} source=$source kind=conversations reason=$reason items=${response.data.items.size}"
			)
			is GetDataResponse.Error -> logger.d(
				"DirectChatsRefreshOrchestrator",
				"apply_event_failed source=$source kind=conversations reason=$reason"
			)
		}
	}

	private fun shouldRefreshConversation(conversationId: Long, nowMs: Long, force: Boolean = false): Boolean {
		synchronized(lock) {
			val lastMs = lastConversationRefreshAtMs[conversationId] ?: 0L
			if (!force && nowMs - lastMs < CONVERSATION_REFRESH_THROTTLE_MS) {
				return false
			}
			lastConversationRefreshAtMs[conversationId] = nowMs
			if (lastConversationRefreshAtMs.size > 256) {
				val staleCutoff = nowMs - 60_000L
				lastConversationRefreshAtMs.entries.removeAll { it.value < staleCutoff }
			}
			return true
		}
	}

	private fun shouldRefreshConversations(nowMs: Long, force: Boolean = false): Boolean {
		synchronized(lock) {
			if (!force && nowMs - lastConversationsRefreshAtMs < CONVERSATIONS_REFRESH_THROTTLE_MS) {
				return false
			}
			lastConversationsRefreshAtMs = nowMs
			return true
		}
	}
}
