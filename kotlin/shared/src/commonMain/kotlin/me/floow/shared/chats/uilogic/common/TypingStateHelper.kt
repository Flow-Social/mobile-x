package me.floow.shared.chats.uilogic.common

import me.floow.shared.chats.model.ChatTypingState

data class TypingStateDecision(
	val shouldApply: Boolean,
	val typingState: ChatTypingState,
)

fun resolveTypingDecision(
	actorUserId: String?,
	peerUserId: String?,
	isTyping: Boolean,
	typingTtlMs: Long,
	peerDisplayName: String,
): TypingStateDecision {
	val isPeerActor = !actorUserId.isNullOrBlank() && actorUserId == peerUserId
	if (!isPeerActor) {
		return TypingStateDecision(
			shouldApply = false,
			typingState = ChatTypingState(
				isTyping = false,
				displayName = peerDisplayName,
				typingTtlMs = typingTtlMs,
			),
		)
	}
	return TypingStateDecision(
		shouldApply = true,
		typingState = ChatTypingState(
			isTyping = isTyping,
			displayName = peerDisplayName,
			typingTtlMs = typingTtlMs,
		),
	)
}
