package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.floow.shared.chats.model.ChatMessageItemModel

sealed interface ChatRealtimeEvent {
	data class MessageUpserted(
		val message: ChatMessageItemModel,
		val peerLastReadMessageId: Long? = null,
	) : ChatRealtimeEvent
	data class MessageDeleted(val messageId: Long) : ChatRealtimeEvent
	data class MessagePinned(val messageId: Long, val isPinned: Boolean) : ChatRealtimeEvent
	data class PeerReadUpdated(val messageId: Long) : ChatRealtimeEvent
	data class TypingUpdated(
		val actorUserId: String,
		val displayName: String = "",
		val isTyping: Boolean,
		val typingTtlMs: Long = 0L,
	) : ChatRealtimeEvent
	data object ResyncRequired : ChatRealtimeEvent
}

interface ChatRealtimeContract {
	val connectionState: StateFlow<RealtimeConnectionState>
		get() = DefaultRealtimeConnectionState

	fun observeConversation(conversationId: Long, afterSeq: Long = 0L): Flow<ChatRealtimeEvent>

	suspend fun setTyping(conversationId: Long, isTyping: Boolean): Result<Unit> = Result.success(Unit)
}
