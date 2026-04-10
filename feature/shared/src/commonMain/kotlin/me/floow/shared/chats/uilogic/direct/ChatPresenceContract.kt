package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatTypingState

interface ChatPresenceContract {
	val connectionState: StateFlow<RealtimeConnectionState>
		get() = DefaultRealtimeConnectionState

	fun setTargets(owner: String, userIds: List<String>) = Unit

	fun clearTargets(owner: String) = Unit

	fun observePeerPresence(peerUserId: String): Flow<ChatPresenceState>
	fun observePeerTyping(peerUserId: String): Flow<ChatTypingState>
}
