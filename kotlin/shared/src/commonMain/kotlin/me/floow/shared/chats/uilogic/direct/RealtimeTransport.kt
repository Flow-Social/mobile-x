package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.flow.StateFlow

internal data class RealtimeSocketAuth(
	val queryKey: String,
	val queryValue: String,
	val mode: String,
)

internal data class RealtimeSocketLifecycleInfo(
	val endpoint: String,
	val authMode: String,
	val conversationId: Long? = null,
	val url: String? = null,
	val code: Int? = null,
	val reason: String? = null,
	val wasClean: Boolean? = null,
	val readyState: Int? = null,
	val message: String? = null,
)

internal data class ChatRealtimeOpenParams(
	val conversationId: Long,
	val afterSeq: Long,
	val replayLimit: Int,
	val auth: RealtimeSocketAuth,
)

internal data class PresenceRealtimeOpenParams(
	val auth: RealtimeSocketAuth,
)

internal interface ChatRealtimeTransportListener {
	fun onOpen()
	fun onEvent(raw: String)
	fun onClosed(info: RealtimeSocketLifecycleInfo)
	fun onError(info: RealtimeSocketLifecycleInfo)
}

internal interface PresenceRealtimeTransportListener {
	fun onOpen()
	fun onEvent(raw: String)
	fun onClosed(info: RealtimeSocketLifecycleInfo)
	fun onError(info: RealtimeSocketLifecycleInfo)
}

internal interface ChatRealtimeTransport {
	fun open(
		params: ChatRealtimeOpenParams,
		listener: ChatRealtimeTransportListener,
	): Result<String>

	suspend fun send(handle: String, payloadJson: String): Result<Unit>

	fun close(handle: String)
}

internal interface PresenceRealtimeTransport {
	fun open(
		params: PresenceRealtimeOpenParams,
		listener: PresenceRealtimeTransportListener,
	): Result<String>

	suspend fun send(handle: String, payloadJson: String): Result<Unit>

	fun close(handle: String)
}

internal interface RealtimeAuthProvider {
	suspend fun resolveChatAuth(): Result<RealtimeSocketAuth>
	suspend fun resolvePresenceAuth(): Result<RealtimeSocketAuth> = resolveChatAuth()
}

private val defaultConnectionStateFlow = kotlinx.coroutines.flow.MutableStateFlow<RealtimeConnectionState>(
	RealtimeConnectionState.Idle
)

internal val DefaultRealtimeConnectionState: StateFlow<RealtimeConnectionState>
	get() = defaultConnectionStateFlow
