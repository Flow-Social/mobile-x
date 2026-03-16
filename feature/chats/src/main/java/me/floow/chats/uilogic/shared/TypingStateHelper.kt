package me.floow.chats.uilogic.shared

internal data class TypingStateDecision(
	val shouldApply: Boolean,
	val isTyping: Boolean,
	val displayName: String,
	val typingTtlMs: Long
)

internal fun resolveTypingDecision(
	actorUserId: String?,
	peerUserId: String?,
	isTyping: Boolean,
	typingTtlMs: Long,
	peerDisplayName: String
): TypingStateDecision {
	val isPeerActor = !actorUserId.isNullOrBlank() && actorUserId == peerUserId
	if (!isPeerActor) {
		return TypingStateDecision(
			shouldApply = false,
			isTyping = false,
			displayName = peerDisplayName,
			typingTtlMs = typingTtlMs
		)
	}
	return TypingStateDecision(
		shouldApply = true,
		isTyping = isTyping,
		displayName = peerDisplayName,
		typingTtlMs = typingTtlMs
	)
}
