package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.floow.shared.chats.model.ChatMessageItemModel

sealed interface ChatRealtimeEvent {
	data class Hello(
		val lastReadMessageId: Long,
		val unreadCount: Int,
		val maxMessageId: Long,
	) : ChatRealtimeEvent
	data class MessageUpserted(
		val message: ChatMessageItemModel,
		val peerLastReadMessageId: Long? = null,
        val isNew: Boolean = true,
        val animateEntry: Boolean = isNew,
    ) : ChatRealtimeEvent
	data class MessageDeleted(val messageId: Long) : ChatRealtimeEvent
	data class MessagePinned(val messageId: Long, val isPinned: Boolean) : ChatRealtimeEvent
	data object OwnReadUpdated : ChatRealtimeEvent
	data class PeerReadUpdated(val messageId: Long) : ChatRealtimeEvent
	data class TypingUpdated(
		val actorUserId: String,
		val displayName: String = "",
		val isTyping: Boolean,
		val action: String = "typing",
		val typingTtlMs: Long = 0L,
	) : ChatRealtimeEvent
	data class ConversationDeleted(val actorUserId: String) : ChatRealtimeEvent
	data object ResyncRequired : ChatRealtimeEvent
}

interface ChatRealtimeContract {
	val connectionState: StateFlow<RealtimeConnectionState>
		get() = DefaultRealtimeConnectionState

	fun observeConversation(conversationId: Long, afterSeq: Long = 0L): Flow<ChatRealtimeEvent>

	suspend fun setTyping(conversationId: Long, isTyping: Boolean, action: String = "typing"): Result<Unit> = Result.success(Unit)

	suspend fun markReadUpTo(conversationId: Long, messageId: Long): Result<Unit> = Result.success(Unit)
}
