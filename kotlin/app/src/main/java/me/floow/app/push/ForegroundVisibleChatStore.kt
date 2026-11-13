package me.floow.app.push

data class ForegroundVisibleChatSnapshot(
	val isAppInForeground: Boolean = false,
	val conversationId: Long? = null,
	val interlocutorId: String? = null,
)

class ForegroundVisibleChatStore {
	@Volatile
	private var isAppInForeground: Boolean = false

	@Volatile
	private var visibleConversationId: Long? = null

	@Volatile
	private var visibleInterlocutorId: String? = null

	@Synchronized
	fun onAppForeground() {
		isAppInForeground = true
	}

	@Synchronized
	fun onAppBackground() {
		isAppInForeground = false
		visibleConversationId = null
		visibleInterlocutorId = null
	}

	@Synchronized
	fun setVisibleChat(
		conversationId: Long?,
		interlocutorId: String?
	) {
		visibleConversationId = conversationId?.takeIf { it > 0L }
		visibleInterlocutorId = interlocutorId
			?.trim()
			?.takeIf(String::isNotEmpty)
	}

	@Synchronized
	fun clearVisibleChat() {
		visibleConversationId = null
		visibleInterlocutorId = null
	}

	fun snapshot(): ForegroundVisibleChatSnapshot {
		return ForegroundVisibleChatSnapshot(
			isAppInForeground = isAppInForeground,
			conversationId = visibleConversationId,
			interlocutorId = visibleInterlocutorId
		)
	}
}
