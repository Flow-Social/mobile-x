package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import me.floow.shared.chats.uilogic.WasmChatsApiSupport
import me.floow.shared.profile.auth.flowWasmChatsRealtimeClose
import me.floow.shared.profile.auth.flowWasmChatsRealtimeOpen
import me.floow.shared.profile.auth.flowWasmChatsRealtimeSend
import me.floow.shared.profile.auth.flowWasmPresenceRealtimeClose
import me.floow.shared.profile.auth.flowWasmPresenceRealtimeOpen
import me.floow.shared.profile.auth.flowWasmPresenceRealtimeSend
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class WasmRealtimeAuthProvider : RealtimeAuthProvider {
	override suspend fun resolveChatAuth(): Result<RealtimeSocketAuth> = runCatching {
		val ticket = resolveWsTicket()
		RealtimeSocketAuth(
			queryKey = "ticket",
			queryValue = ticket,
			mode = "ws_ticket",
		)
	}

	private suspend fun resolveWsTicket(): String {
		val now = JsDate.now().toLong()
		cachedTicket
			?.takeIf { it.expiresAtEpochMillis > now }
			?.let { return it.value }

		val authToken = WasmChatsApiSupport.requireAuthToken()
		val raw = WasmChatsApiSupport.apiRequestV1(
			method = "POST",
			path = "/auth/ws-ticket",
			authToken = authToken,
		)
		val response = wasmRealtimeJson.decodeFromString<WasmWsTicketResponse>(raw)
		cachedTicket = CachedWsTicket(
			value = response.ticket,
			// Backend TTL is 60s. Reuse only for a short warm window to avoid stale reconnects.
			expiresAtEpochMillis = now + TICKET_CACHE_TTL_MILLIS,
		)
		return response.ticket
	}

	private companion object {
		private const val TICKET_CACHE_TTL_MILLIS = 10_000L
		private var cachedTicket: CachedWsTicket? = null
	}
}

internal class WasmChatRealtimeTransport : ChatRealtimeTransport {
	override fun open(
		params: ChatRealtimeOpenParams,
		listener: ChatRealtimeTransportListener,
	): Result<String> = runCatching {
		flowWasmChatsRealtimeOpen(
			apiUrl = WasmChatsApiSupport.apiUrlV2,
			authQueryKey = params.auth.queryKey,
			authQueryValue = params.auth.queryValue,
			conversationId = params.conversationId,
			afterSeq = params.afterSeq,
			replayLimit = params.replayLimit,
			onOpen = listener::onOpen,
			onEvent = listener::onEvent,
			onClosed = { payload ->
				listener.onClosed(payload.toLifecycleInfo(CHAT_ENDPOINT, params.auth.mode, params.conversationId))
			},
			onError = { payload ->
				listener.onError(payload.toLifecycleInfo(CHAT_ENDPOINT, params.auth.mode, params.conversationId))
			},
		) ?: error("Failed to open chats realtime socket")
	}

	override suspend fun send(handle: String, payloadJson: String): Result<Unit> = runCatching {
		suspendCancellableCoroutine { continuation ->
			flowWasmChatsRealtimeSend(
				handle = handle,
				payloadJson = payloadJson,
				onSuccess = { continuation.resume(Unit) },
				onError = { error -> continuation.resumeWithException(IllegalStateException(error)) },
			)
		}
	}

	override fun close(handle: String) {
		flowWasmChatsRealtimeClose(handle)
	}

	private companion object {
		private const val CHAT_ENDPOINT = "/chats/realtime/ws"
	}
}

internal class WasmPresenceRealtimeTransport : PresenceRealtimeTransport {
	override fun open(
		params: PresenceRealtimeOpenParams,
		listener: PresenceRealtimeTransportListener,
	): Result<String> = runCatching {
		flowWasmPresenceRealtimeOpen(
			apiUrl = WasmChatsApiSupport.apiUrlV1,
			authQueryKey = params.auth.queryKey,
			authQueryValue = params.auth.queryValue,
			onOpen = listener::onOpen,
			onEvent = listener::onEvent,
			onClosed = { payload ->
				listener.onClosed(payload.toLifecycleInfo(PRESENCE_ENDPOINT, params.auth.mode, null))
			},
			onError = { payload ->
				listener.onError(payload.toLifecycleInfo(PRESENCE_ENDPOINT, params.auth.mode, null))
			},
		) ?: error("Failed to open presence realtime socket")
	}

	override suspend fun send(handle: String, payloadJson: String): Result<Unit> = runCatching {
		suspendCancellableCoroutine { continuation ->
			flowWasmPresenceRealtimeSend(
				handle = handle,
				payloadJson = payloadJson,
				onSuccess = { continuation.resume(Unit) },
				onError = { error -> continuation.resumeWithException(IllegalStateException(error)) },
			)
		}
	}

	override fun close(handle: String) {
		flowWasmPresenceRealtimeClose(handle)
	}

	private companion object {
		private const val PRESENCE_ENDPOINT = "/presence/ws"
	}
}

@Serializable
private data class WasmSocketLifecyclePayload(
	val url: String? = null,
	val code: Int? = null,
	val reason: String? = null,
	val wasClean: Boolean? = null,
	val readyState: Int? = null,
	val message: String? = null,
)

@Serializable
private data class WasmWsTicketResponse(
	val ticket: String,
	val expires_at: String? = null,
)

private data class CachedWsTicket(
	val value: String,
	val expiresAtEpochMillis: Long,
)

private fun String.toLifecycleInfo(
	endpoint: String,
	authMode: String,
	conversationId: Long?,
): RealtimeSocketLifecycleInfo {
	val payload = runCatching {
		wasmRealtimeJson.decodeFromString<WasmSocketLifecyclePayload>(this)
	}.getOrNull()
	return RealtimeSocketLifecycleInfo(
		endpoint = endpoint,
		authMode = authMode,
		conversationId = conversationId,
		url = payload?.url,
		code = payload?.code,
		reason = payload?.reason,
		wasClean = payload?.wasClean,
		readyState = payload?.readyState,
		message = payload?.message ?: this.takeIf { payload == null },
	)
}

private val wasmRealtimeJson = Json { ignoreUnknownKeys = true }

@JsName("Date")
private external class JsDate {
	companion object {
		fun now(): Double
	}
}
