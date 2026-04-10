package me.floow.chats.uilogic.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import me.floow.domain.data.repos.PresenceRepository
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatTypingState
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.RealtimeConnectionState

class AndroidChatPresenceContract(
	private val presenceRepository: PresenceRepository,
) : ChatPresenceContract {
	override val connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Idle)

	override fun setTargets(owner: String, userIds: List<String>) {
		presenceRepository.setTargets(owner, userIds)
	}

	override fun clearTargets(owner: String) {
		presenceRepository.clearTargets(owner)
	}

	override fun observePeerPresence(peerUserId: String): Flow<ChatPresenceState> {
		return presenceRepository.presences.map { presences ->
			val presence = presences[peerUserId]
			ChatPresenceState(
				isOnline = presence?.isOnline == true,
				lastSeenAtMillis = presence?.lastSeenAtMillis,
			)
		}
	}

	override fun observePeerTyping(peerUserId: String): Flow<ChatTypingState> {
		return kotlinx.coroutines.flow.flowOf(ChatTypingState())
	}
}
