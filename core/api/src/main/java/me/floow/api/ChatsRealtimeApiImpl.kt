package me.floow.api

import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.floow.api.chats.booleanOrNullFlexible
import me.floow.api.chats.buildChatsApiBaseUrl
import me.floow.api.chats.longOrNullFlexible
import me.floow.api.chats.objectOrNull
import me.floow.api.chats.stringOrNull
import me.floow.api.chats.stringOrNullFlexible
import me.floow.api.chats.toChatMessageItemOrNull
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.domain.api.ChatsRealtimeApi
import me.floow.domain.api.models.ChatsRealtimeEvent
import me.floow.domain.api.models.PushAckRequest
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

private const val REALTIME_EVENT_HELLO = "hello"
private const val REALTIME_EVENT_MESSAGE_CREATED = "message_created"
private const val REALTIME_EVENT_MESSAGE_UPDATED = "message_updated"
private const val REALTIME_EVENT_MESSAGE_DELETED = "message_deleted"
private const val REALTIME_EVENT_MESSAGE_PINNED_UPDATED = "message_pinned_updated"
private const val REALTIME_EVENT_READ_UP_TO_UPDATED = "read_up_to_updated"
private const val REALTIME_EVENT_TYPING = "typing"
private const val REALTIME_EVENT_RESYNC_REQUIRED = "resync_required"

private const val DEFAULT_REALTIME_REPLAY_LIMIT = 200
private const val MAX_REALTIME_REPLAY_LIMIT = 500
private const val MAX_REALTIME_RECONNECT_DELAY_MS = 5000L
private const val INITIAL_REALTIME_RECONNECT_DELAY_MS = 500L

class ChatsRealtimeApiImpl(
	config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : ChatsRealtimeApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val wsUrl = buildChatsRealtimeWsUrl(config.apiUrl)
	private val sessionRef = AtomicReference<WebSocketSession?>(null)

	override fun subscribeConversation(
		conversationId: Long,
		afterSeq: Long,
		replayLimit: Int
	): Flow<ChatsRealtimeEvent> {
		if (conversationId <= 0L) return flow { }
		return subscribeInternal(
			conversationId = conversationId,
			afterSeq = afterSeq,
			replayLimit = replayLimit,
			logScope = "ChatsRealtimeApiImpl.subscribeConversation"
		)
	}

	override fun subscribeAllConversations(
		afterSeq: Long,
		replayLimit: Int
	): Flow<ChatsRealtimeEvent> {
		return subscribeInternal(
			conversationId = null,
			afterSeq = afterSeq,
			replayLimit = replayLimit,
			logScope = "ChatsRealtimeApiImpl.subscribeAllConversations"
		)
	}

	private fun subscribeInternal(
		conversationId: Long?,
		afterSeq: Long,
		replayLimit: Int,
		logScope: String
	): Flow<ChatsRealtimeEvent> = flow {
		val authToken = authenticationManager.getAuthTokenOrNull()
			?: return@flow
		val sanitizedReplayLimit = replayLimit.coerceIn(1, MAX_REALTIME_REPLAY_LIMIT)
		var nextAfterSeq = afterSeq.coerceAtLeast(0L)
		var reconnectDelayMs = INITIAL_REALTIME_RECONNECT_DELAY_MS

		while (currentCoroutineContext().isActive) {
			val session = try {
				httpClient.webSocketSession {
					addAuthTokenHeader(authToken)
					url {
						protocol = wsUrl.protocol
						host = wsUrl.host
						port = wsUrl.port
						pathSegments = wsUrl.rawSegments
						conversationId
							?.takeIf { it > 0L }
							?.let { id ->
								parameters.append("conversation_id", id.toString())
							}
						if (nextAfterSeq > 0L) {
							parameters.append("after_seq", nextAfterSeq.toString())
						}
						if (sanitizedReplayLimit != DEFAULT_REALTIME_REPLAY_LIMIT) {
							parameters.append("replay_limit", sanitizedReplayLimit.toString())
						}
					}
				}
			} catch (throwable: Throwable) {
				logger.d(
					logScope,
					"Failed to open websocket session: ${throwable.message}"
				)
				delay(reconnectDelayMs)
				reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_REALTIME_RECONNECT_DELAY_MS)
				continue
			}

			sessionRef.set(session)
			var receivedAnyEvent = false
			try {
				for (frame in session.incoming) {
					if (frame !is Frame.Text) continue
					val event = parseRealtimeEvent(frame.readText()) ?: continue
					if (event.seq > nextAfterSeq) {
						nextAfterSeq = event.seq
					}
					receivedAnyEvent = true
					emit(event)
				}
			} catch (throwable: Throwable) {
				logger.d(
					logScope,
					"WebSocket stream error: ${throwable.message}"
				)
			} finally {
				if (sessionRef.get() === session) {
					sessionRef.set(null)
				}
				runCatching { session.outgoing.close() }
			}

			if (!currentCoroutineContext().isActive) {
				break
			}
			reconnectDelayMs = if (receivedAnyEvent) {
				INITIAL_REALTIME_RECONNECT_DELAY_MS
			} else {
				minOf(reconnectDelayMs * 2, MAX_REALTIME_RECONNECT_DELAY_MS)
			}
			delay(reconnectDelayMs)
		}
	}
	override fun sendPushAck(request: PushAckRequest): Boolean {
		val session = sessionRef.get() ?: return false
		val payload = mapOf(
			"type" to "push_ack",
			"notification_id" to request.notificationId,
			"conversation_id" to request.conversationId.toString(),
			"message_id" to request.messageId.toString(),
			"received_at_ms" to request.receivedAtMs.toString()
		)
		val json = runCatching { JsonSerializer.encodeToString(payload) }.getOrNull() ?: return false
		return runCatching { session.outgoing.trySend(Frame.Text(json)).isSuccess }.getOrDefault(false)
	}
}

private fun parseRealtimeEvent(rawText: String): ChatsRealtimeEvent? {
	val root = runCatching { JsonSerializer.parseToJsonElement(rawText).jsonObject }
		.getOrNull()
		?: return null

	val type = root.stringOrNull("type") ?: return null
	val eventId = root.stringOrNull("event_id")
	val seq = root.longOrNullFlexible("seq") ?: 0L
	val lastReadMessageId = root.longOrNullFlexible("last_read_message_id") ?: 0L
	val unreadCount = root.longOrNullFlexible("unread_count")
		?.coerceAtLeast(0L)
		?.coerceAtMost(Int.MAX_VALUE.toLong())
		?.toInt()
		?: 0
	val firstUnreadId = root.longOrNullFlexible("first_unread_id")
	val maxMessageId = root.longOrNullFlexible("max_message_id") ?: 0L

	return when (type) {
		REALTIME_EVENT_HELLO -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			ChatsRealtimeEvent.Hello(
				conversationId = conversationId,
				eventId = eventId,
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		REALTIME_EVENT_MESSAGE_CREATED -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			val message = root.objectOrNull("message")?.toChatMessageItemOrNull() ?: return null
			val isReplay = root.booleanOrNullFlexible("is_replay") ?: false
			ChatsRealtimeEvent.MessageCreated(
				conversationId = conversationId,
				eventId = eventId,
				message = message,
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId,
				isReplay = isReplay
			)
		}

		REALTIME_EVENT_READ_UP_TO_UPDATED -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			ChatsRealtimeEvent.ReadUpToUpdated(
				conversationId = conversationId,
				eventId = eventId,
				actorUserId = root.stringOrNullFlexible("actor_user_id") ?: "",
				actorLastReadMessageId = root.longOrNullFlexible("actor_last_read_message_id"),
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		REALTIME_EVENT_MESSAGE_DELETED -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			val deletedMessageId = root.longOrNullFlexible("deleted_message_id") ?: return null
			ChatsRealtimeEvent.MessageDeleted(
				conversationId = conversationId,
				eventId = eventId,
				deletedMessageId = deletedMessageId,
				actorUserId = root.stringOrNullFlexible("actor_user_id") ?: "",
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		REALTIME_EVENT_MESSAGE_UPDATED -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			val message = root.objectOrNull("message")?.toChatMessageItemOrNull() ?: return null
			ChatsRealtimeEvent.MessageUpdated(
				conversationId = conversationId,
				eventId = eventId,
				message = message,
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		REALTIME_EVENT_MESSAGE_PINNED_UPDATED -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			val message = root.objectOrNull("message")?.toChatMessageItemOrNull() ?: return null
			val pinnedMessageId = root.longOrNullFlexible("pinned_message_id") ?: message.id
			ChatsRealtimeEvent.MessagePinnedUpdated(
				conversationId = conversationId,
				eventId = eventId,
				message = message,
				pinnedMessageId = pinnedMessageId,
				isPinned = root.booleanOrNullFlexible("is_pinned") ?: message.isPinned,
				pinnedAt = root.longOrNullFlexible("pinned_at") ?: message.pinnedAt,
				pinnedByUserId = root.stringOrNullFlexible("pinned_by_user_id") ?: message.pinnedByUserId,
				actorUserId = root.stringOrNullFlexible("actor_user_id") ?: "",
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		REALTIME_EVENT_TYPING -> {
			val conversationId = root.longOrNullFlexible("conversation_id") ?: return null
			ChatsRealtimeEvent.Typing(
				conversationId = conversationId,
				eventId = eventId,
				actorUserId = root.stringOrNullFlexible("actor_user_id") ?: "",
				isTyping = root.booleanOrNullFlexible("typing") ?: true,
				typingTtlMs = root.longOrNullFlexible("typing_ttl_ms") ?: 2200L,
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		REALTIME_EVENT_RESYNC_REQUIRED -> {
			ChatsRealtimeEvent.ResyncRequired(
				conversationId = root.longOrNullFlexible("conversation_id") ?: 0L,
				eventId = eventId,
				seq = seq,
				lastReadMessageId = lastReadMessageId,
				unreadCount = unreadCount,
				firstUnreadId = firstUnreadId,
				maxMessageId = maxMessageId
			)
		}

		else -> null
	}
}

private fun buildChatsRealtimeWsUrl(apiUrl: String): Url {
	val v2Base = buildChatsApiBaseUrl(apiUrl)
	val base = Url(v2Base)
	val basePath = base.encodedPath.trimEnd('/')
	val realtimePath = "$basePath/chats/realtime/ws"
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
