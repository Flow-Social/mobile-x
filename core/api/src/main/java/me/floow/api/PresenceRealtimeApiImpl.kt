package me.floow.api

import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.domain.api.PresenceRealtimeApi
import me.floow.domain.api.PresenceRealtimeSession
import me.floow.domain.api.models.PresenceItem
import me.floow.domain.api.models.PresenceRealtimeEvent
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

private const val PRESENCE_EVENT_SNAPSHOT = "presence_snapshot"
private const val PRESENCE_EVENT_CHANGED = "presence_changed"
private const val PRESENCE_EVENT_RESYNC_REQUIRED = "resync_required"

@Serializable
private data class PresenceSubscriptionRequest(
	val type: String,
	@SerialName("user_ids") val userIds: List<Int>
)

class PresenceRealtimeApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : PresenceRealtimeApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()

	override suspend fun openSession(): PresenceRealtimeSession? {
		val authToken = authenticationManager.getAuthTokenOrNull() ?: return null
		val wsUrl = buildPresenceRealtimeWsUrl(config.apiUrl)
		val session = runCatching {
			httpClient.webSocketSession {
				addAuthTokenHeader(authToken)
				url {
					protocol = wsUrl.protocol
					host = wsUrl.host
					port = wsUrl.port
					pathSegments = wsUrl.rawSegments
				}
			}
		}.getOrElse { throwable ->
			logger.d("PresenceRealtimeApiImpl.openSession", "Failed to open websocket: ${throwable.message}")
			return null
		}
		return PresenceRealtimeSessionImpl(session, logger)
	}
}

private class PresenceRealtimeSessionImpl(
	private val socketSession: WebSocketSession,
	private val logger: Logger
) : PresenceRealtimeSession {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val eventsChannel = Channel<PresenceRealtimeEvent>(capacity = Channel.BUFFERED)

	override val events: Flow<PresenceRealtimeEvent> = eventsChannel.receiveAsFlow()

	init {
		scope.launch {
			try {
				for (frame in socketSession.incoming) {
					if (frame !is Frame.Text) continue
					parsePresenceEvent(frame.readText())?.let { event ->
						eventsChannel.send(event)
					}
				}
			} catch (throwable: Throwable) {
				logger.d("PresenceRealtimeSessionImpl.events", "WebSocket stream error: ${throwable.message}")
			} finally {
				eventsChannel.close()
				close()
			}
		}
	}

	override suspend fun subscribe(userIds: List<String>) {
		sendCommand(type = "subscribe", userIds = userIds)
	}

	override suspend fun unsubscribe(userIds: List<String>) {
		sendCommand(type = "unsubscribe", userIds = userIds)
	}

	override suspend fun close() {
		scope.cancel()
		runCatching { socketSession.outgoing.close() }
	}

	private suspend fun sendCommand(type: String, userIds: List<String>) {
		val numericIds = userIds
			.mapNotNull { it.toIntOrNull()?.takeIf { id -> id > 0 } }
			.distinct()
		if (numericIds.isEmpty()) return
		val payload = JsonSerializer.encodeToString(
			PresenceSubscriptionRequest(type = type, userIds = numericIds)
		)
		socketSession.send(Frame.Text(payload))
	}
}

private fun parsePresenceEvent(rawText: String): PresenceRealtimeEvent? {
	val root = runCatching { JsonSerializer.parseToJsonElement(rawText).jsonObject }.getOrNull() ?: return null
	val type = root.stringOrNull("type") ?: return null
	val serverTimestampMillis = root.longOrNullFlexible("server_timestamp") ?: System.currentTimeMillis()
	return when (type) {
		PRESENCE_EVENT_SNAPSHOT -> {
			val items = root.arrayOrNull("items")
				?.mapNotNull { it as? JsonObject }
				?.mapNotNull { item -> item.toPresenceItem(rootTimestampMillis = serverTimestampMillis) }
				.orEmpty()
			PresenceRealtimeEvent.Snapshot(
				items = items,
				serverTimestampMillis = serverTimestampMillis
			)
		}
		PRESENCE_EVENT_CHANGED -> {
			val item = root.toPresenceItem(rootTimestampMillis = serverTimestampMillis) ?: return null
			PresenceRealtimeEvent.PresenceChanged(item)
		}
		PRESENCE_EVENT_RESYNC_REQUIRED -> PresenceRealtimeEvent.ResyncRequired(serverTimestampMillis)
		else -> null
	}
}

private fun JsonObject.toPresenceItem(rootTimestampMillis: Long): PresenceItem? {
	val userId = stringOrNullFlexible("user_id") ?: return null
	val isOnline = booleanOrNullFlexible("is_online") ?: false
	val lastSeenAt = longOrNullFlexible("last_seen_at")
	val serverTimestamp = longOrNullFlexible("server_timestamp") ?: rootTimestampMillis
	val expiresAt = longOrNullFlexible("expires_at")
	return PresenceItem(
		userId = userId,
		isOnline = isOnline,
		lastSeenAtMillis = lastSeenAt,
		serverTimestampMillis = serverTimestamp,
		expiresAtMillis = expiresAt
	)
}

private fun buildPresenceRealtimeWsUrl(apiUrl: String): Url {
	val base = Url(apiUrl)
	val basePath = base.encodedPath.trimEnd('/')
	val realtimePath = "$basePath/presence/ws"
	val wsProtocol = when (base.protocol) {
		URLProtocol.HTTPS, URLProtocol.WSS -> URLProtocol.WSS
		else -> URLProtocol.WS
	}
	val includePort = when (wsProtocol) {
		URLProtocol.WS -> base.port != URLProtocol.WS.defaultPort
		URLProtocol.WSS -> base.port != URLProtocol.WSS.defaultPort
		else -> true
	}
	val portSegment = if (includePort) ":${base.port}" else ""
	return Url("${wsProtocol.name}://${base.host}$portSegment$realtimePath")
}

private fun JsonObject.arrayOrNull(key: String): JsonArray? = (this[key] as? JsonArray) ?: this[key]?.jsonArray

private fun JsonObject.stringOrNull(key: String): String? {
	return this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}

private fun JsonObject.stringOrNullFlexible(key: String): String? {
	val value = this[key]?.jsonPrimitive ?: return null
	return value.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
		?: value.longOrNull?.toString()
}

private fun JsonObject.booleanOrNullFlexible(key: String): Boolean? {
	val primitive = this[key]?.jsonPrimitive ?: return null
	primitive.booleanOrNull?.let { return it }
	return when (primitive.contentOrNull?.trim()?.lowercase()) {
		"true", "1", "yes" -> true
		"false", "0", "no" -> false
		else -> null
	}
}

private fun JsonObject.longOrNullFlexible(key: String): Long? {
	val primitive = this[key]?.jsonPrimitive ?: return null
	primitive.longOrNull?.let { return it }
	return primitive.contentOrNull?.trim()?.toLongOrNull()
}
