package me.floow.app.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.app.push.ChatNotificationPayload
import me.floow.app.push.NotificationPipeline
import me.floow.app.push.ChatNotificationSource
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.utils.Logger

class DirectChatsSyncCoordinator(
	private val chatsRepository: ChatsRepository,
	private val notificationPipeline: NotificationPipeline,
	private val logger: Logger,
	private val currentUserIdProvider: () -> String?
) {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private var realtimeJob: Job? = null
	private var isStarted: Boolean = false

	fun start() {
		if (isStarted) return
		isStarted = true
		scope.launch {
			chatsRepository.getConversations(limit = 50, cursor = null)
		}
		realtimeJob = scope.launch {
			chatsRepository.subscribeAllConversations(
				afterSeq = 0L,
				replayLimit = 200
			).collectLatest { event ->
				when (event) {
					is DirectChatRealtimeEvent.MessageCreated -> {
						if (event.isReplay) return@collectLatest
						val currentUserId = currentUserIdProvider()
						if (!currentUserId.isNullOrBlank() && event.message.sender.id == currentUserId) {
							return@collectLatest
						}
						val payload = buildPayloadFromEvent(event)
						if (payload != null) {
							notificationPipeline.process(payload, ChatNotificationSource.WS)
						}
					}
					is DirectChatRealtimeEvent.ResyncRequired -> {
						when (chatsRepository.getConversations(limit = 50, cursor = null)) {
							is GetDataResponse.Success -> Unit
							is GetDataResponse.Error -> {
								logger.d(
									"DirectChatsSyncCoordinator.start",
									"Failed to resync direct chats snapshot"
								)
							}
						}
					}
					else -> Unit
				}
			}
		}
	}

	fun stop() {
		isStarted = false
		realtimeJob?.cancel()
		realtimeJob = null
		scope.coroutineContext.cancelChildren()
	}

	private fun buildPayloadFromEvent(event: DirectChatRealtimeEvent.MessageCreated): ChatNotificationPayload? {
		val message = event.message
		val conversationId = event.conversationId.takeIf { it > 0L } ?: return null
		val messageId = message.id.takeIf { it > 0L } ?: return null
		val text = message.text.trim().ifBlank { "Новое сообщение" }
		val timestampMs = normalizeTimestamp(message.createdAt).takeIf { it > 0L } ?: System.currentTimeMillis()
		val notificationId = "chat:$conversationId:$messageId"
		return ChatNotificationPayload(
			type = "chat_message",
			notificationId = notificationId,
			conversationId = conversationId,
			messageId = messageId,
			senderId = message.sender.id,
			senderName = message.sender.name?.takeIf(String::isNotEmpty) ?: message.sender.username,
			senderAvatarUrl = message.sender.avatarUrl,
			messageText = text,
			messageTimestampMs = timestampMs,
			isGroup = false,
			conversationTitle = null,
			isFallback = false,
			source = ChatNotificationSource.WS
		)
	}

	private fun normalizeTimestamp(raw: Long): Long {
		return if (raw in 1L..9_999_999_999L) raw * 1000L else raw
	}
}
