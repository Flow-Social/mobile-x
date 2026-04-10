package me.floow.shared.chats.uilogic.direct

sealed interface RealtimeConnectionState {
	data object Idle : RealtimeConnectionState

	data class Connecting(
		val endpoint: String,
		val authMode: String,
		val conversationId: Long? = null,
	) : RealtimeConnectionState

	data class Open(
		val endpoint: String,
		val authMode: String,
		val conversationId: Long? = null,
	) : RealtimeConnectionState

	data class Reconnecting(
		val endpoint: String,
		val authMode: String,
		val conversationId: Long? = null,
		val attempt: Int,
		val backoffMs: Long,
	) : RealtimeConnectionState

	data class Resyncing(
		val endpoint: String,
		val authMode: String,
		val conversationId: Long? = null,
	) : RealtimeConnectionState

	data class Closed(
		val endpoint: String,
		val authMode: String,
		val conversationId: Long? = null,
		val code: Int? = null,
		val reason: String? = null,
		val wasClean: Boolean? = null,
		val message: String? = null,
	) : RealtimeConnectionState

	data class AuthFailed(
		val endpoint: String,
		val authMode: String,
		val conversationId: Long? = null,
		val code: Int? = null,
		val reason: String? = null,
		val message: String? = null,
	) : RealtimeConnectionState
}
