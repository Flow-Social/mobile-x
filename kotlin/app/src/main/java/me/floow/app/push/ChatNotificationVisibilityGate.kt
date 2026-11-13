package me.floow.app.push

class ChatNotificationVisibilityGate(
	private val foregroundVisibleChatStore: ForegroundVisibleChatStore,
	private val selfUserIdProvider: () -> String?
) {
	data class Decision(
		val shouldSuppress: Boolean,
		val reason: String
	)

	fun evaluate(payload: ChatNotificationPayload): Decision {
		val normalizedSenderId = payload.senderId
			?.trim()
			?.takeIf(String::isNotEmpty)
		val selfUserId = selfUserIdProvider()
			?.trim()
			?.takeIf(String::isNotEmpty)
		if (normalizedSenderId != null && normalizedSenderId == selfUserId) {
			return Decision(shouldSuppress = true, reason = "self_message")
		}

		val visibleChat = foregroundVisibleChatStore.snapshot()
		if (!visibleChat.isAppInForeground) {
			return Decision(shouldSuppress = false, reason = "app_background")
		}

		val visibleConversationId = visibleChat.conversationId
		if (visibleConversationId != null && visibleConversationId == payload.conversationId) {
			return Decision(shouldSuppress = true, reason = "visible_foreground_conversation")
		}

		return Decision(shouldSuppress = false, reason = "no_visible_chat_match")
	}
}
