package me.floow.chats.uilogic.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.models.DirectChatRealtimeEvent
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeEvent
import me.floow.shared.chats.uilogic.direct.RealtimeConnectionState

class AndroidChatRealtimeContract(
	private val chatsRepository: ChatsRepository,
	private val authenticationManager: me.floow.domain.auth.AuthenticationManager,
	private val presenceRepository: me.floow.domain.data.repos.PresenceRepository,
) : ChatRealtimeContract {
	override val connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Idle)

	override fun observeConversation(conversationId: Long, afterSeq: Long): Flow<ChatRealtimeEvent> {
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		return chatsRepository.subscribeConversation(conversationId, afterSeq).map { event ->
			event.toSharedEvent(selfUserId)
		}
	}

	override suspend fun setTyping(conversationId: Long, isTyping: Boolean): Result<Unit> = runCatching {
		when (chatsRepository.sendTyping(conversationId, isTyping)) {
			UpdateDataResponse.Success -> Unit
			is UpdateDataResponse.Failure -> error("failed to update typing")
		}
	}
}

private fun DirectChatRealtimeEvent.toSharedEvent(selfUserId: String?): ChatRealtimeEvent = when (this) {
	is DirectChatRealtimeEvent.MessageCreated -> ChatRealtimeEvent.MessageUpserted(
		message = message.toRealtimeItemModel(selfUserId),
		peerLastReadMessageId = null,
	)
	is DirectChatRealtimeEvent.MessageUpdated -> ChatRealtimeEvent.MessageUpserted(
		message = message.toRealtimeItemModel(selfUserId),
		peerLastReadMessageId = null,
	)
	is DirectChatRealtimeEvent.MessagePinnedUpdated -> ChatRealtimeEvent.MessageUpserted(
		message = message.toRealtimeItemModel(selfUserId),
		peerLastReadMessageId = null,
	)
	is DirectChatRealtimeEvent.MessageDeleted -> ChatRealtimeEvent.MessageDeleted(deletedMessageId)
	is DirectChatRealtimeEvent.ReadUpToUpdated -> {
		val updatedMessageId = actorLastReadMessageId
		if (actorUserId == selfUserId || updatedMessageId == null) {
			ChatRealtimeEvent.ResyncRequired
		} else {
			ChatRealtimeEvent.PeerReadUpdated(updatedMessageId)
		}
	}
	is DirectChatRealtimeEvent.Typing -> ChatRealtimeEvent.TypingUpdated(
		actorUserId = actorUserId,
		displayName = "",
		isTyping = isTyping,
		typingTtlMs = typingTtlMs,
	)
	is DirectChatRealtimeEvent.Hello -> ChatRealtimeEvent.ResyncRequired
	is DirectChatRealtimeEvent.ResyncRequired -> ChatRealtimeEvent.ResyncRequired
}

private fun me.floow.domain.models.DirectChatMessage.toRealtimeItemModel(selfUserId: String?): ChatMessageItemModel {
	val isOutgoing = selfUserId?.takeIf(String::isNotBlank) == sender.id
	return ChatMessageItemModel(
		id = id,
		clientMessageId = clientMessageId,
		senderUserId = sender.id,
		senderDisplayName = sender.name ?: sender.username,
		text = text,
		createdAtMillis = createdAt,
		isOutgoing = isOutgoing,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		isDeleted = false,
		deliveryState = if (!isOutgoing) null else when (deliveryStatus) {
			MessageDeliveryStatus.SENDING -> ChatDeliveryState.SENDING
			MessageDeliveryStatus.SENT -> ChatDeliveryState.SENT
			MessageDeliveryStatus.FAILED -> ChatDeliveryState.FAILED
		},
	)
}
