package me.floow.api

import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.domain.api.NotificationsRealtimeApi
import me.floow.domain.api.models.NotificationActorItem
import me.floow.domain.api.models.NotificationItem
import me.floow.domain.api.models.NotificationsRealtimeEvent
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

private const val REPLIES_CHANNEL = "replies"
private const val REALTIME_EVENT_HELLO = "hello"
private const val REALTIME_EVENT_NOTIFICATION_CREATED = "notification_created"
private const val REALTIME_EVENT_READ_STATE_UPDATED = "read_state_updated"
private const val DEFAULT_READ_COUNT = 0

class NotificationsRealtimeApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : NotificationsRealtimeApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()

	override fun subscribeReplies(afterSeq: Long): Flow<NotificationsRealtimeEvent> = flow {
		val authToken = authenticationManager.getAuthTokenOrNull()
			?: return@flow
		val sanitizedAfterSeq = afterSeq.coerceAtLeast(0L)
		val wsUrl = buildNotificationsRealtimeWsUrl(config.apiUrl)

		val session = runCatching {
			httpClient.webSocketSession {
				addAuthTokenHeader(authToken)
				url {
					protocol = wsUrl.protocol
					host = wsUrl.host
					port = wsUrl.port
					pathSegments = wsUrl.rawSegments
					parameters.append("channel", REPLIES_CHANNEL)
					if (sanitizedAfterSeq > 0L) {
						parameters.append("after_seq", sanitizedAfterSeq.toString())
					}
				}
			}
		}.getOrElse { throwable ->
			logger.d(
				"NotificationsRealtimeApiImpl.subscribeReplies",
				"Failed to open websocket session: ${throwable.message}"
			)
			return@flow
		}

		try {
			for (frame in session.incoming) {
				if (frame !is Frame.Text) continue
				val rawText = frame.readText()
				val event = parseRealtimeEvent(rawText)
				if (event != null) {
					emit(event)
				}
			}
			} catch (throwable: Throwable) {
				logger.d(
					"NotificationsRealtimeApiImpl.subscribeReplies",
					"WebSocket stream error: ${throwable.message}"
				)
			} finally {
				runCatching { session.outgoing.close() }
			}
		}
	}

private fun parseRealtimeEvent(rawText: String): NotificationsRealtimeEvent? {
	val root = runCatching { JsonSerializer.parseToJsonElement(rawText).jsonObject }
		.getOrNull()
		?: return null

	val eventType = root.stringOrNull("type") ?: return null
	val channel = normalizeNotificationChannel(root.stringOrNull("channel")) ?: REPLIES_CHANNEL
	val lastReadSeq = root.longOrNullFlexible("last_read_seq") ?: 0L
	val unreadCount = root.longOrNullFlexible("unread_count")
		?.coerceAtLeast(0L)
		?.coerceAtMost(Int.MAX_VALUE.toLong())
		?.toInt()
		?: DEFAULT_READ_COUNT
	val firstUnreadSeq = root.longOrNullFlexible("first_unread_seq")
	val maxSeq = root.longOrNullFlexible("max_seq") ?: 0L

	return when (eventType) {
		REALTIME_EVENT_HELLO -> {
			NotificationsRealtimeEvent.Hello(
				channel = channel,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		REALTIME_EVENT_NOTIFICATION_CREATED -> {
			val notification = root.objectOrNull("notification")
				?.toNotificationItemOrNull()
				?: return null
			NotificationsRealtimeEvent.NotificationCreated(
				channel = channel,
				notification = notification,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq,
				isReplay = root.booleanOrNullFlexible("is_replay") ?: false
			)
		}

		REALTIME_EVENT_READ_STATE_UPDATED -> {
			NotificationsRealtimeEvent.ReadStateUpdated(
				channel = channel,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		else -> null
	}
}

private fun buildNotificationsRealtimeWsUrl(apiUrl: String): Url {
	val base = Url(apiUrl)
	val basePath = base.encodedPath.trimEnd('/')
	val realtimePath = "$basePath/realtime/ws"
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

private fun JsonElement.toNotificationItemOrNull(): NotificationItem? {
	val item = this as? JsonObject ?: return null
	val actorObject = item.objectOrNull("actor") ?: return null

	val id = item.stringOrNull("id") ?: return null
	val seq = item.longOrNullFlexible("seq")
		?: id.toLongOrNull()
		?: return null
	val type = item.stringOrNull("type") ?: return null
	val channel = normalizeNotificationChannel(item.stringOrNull("channel"))
		?: channelFromType(type)
	val postId = item.stringOrNull("post_id") ?: return null
	val commentId = item.stringOrNull("comment_id") ?: return null
	val threadId = item.stringOrNull("thread_id")
		?: item.stringOrNull("reply_to_comment_id")
		?: commentId
	val title = item.stringOrNull("title") ?: return null
	val body = item.stringOrNull("body") ?: return null
	val isRead = item.booleanOrNullFlexible("is_read") ?: false
	val createdAt = item.longOrNullFlexible("created_at") ?: return null
	val updatedAt = item.longOrNullFlexible("updated_at") ?: return null

	val actorId = actorObject.stringOrNull("id") ?: return null
	return NotificationItem(
		id = id,
		seq = seq,
		type = type,
		channel = channel,
		actor = NotificationActorItem(
			id = actorId,
			username = actorObject.stringOrNull("username"),
			name = actorObject.stringOrNull("name"),
			avatar = actorObject.stringOrNull("avatar")
		),
		postId = postId,
		commentId = commentId,
		threadId = threadId,
		replyToCommentId = item.stringOrNull("reply_to_comment_id"),
		commentText = item.stringOrNull("comment_text"),
		replyToCommentText = item.stringOrNull("reply_to_comment_text"),
		title = title,
		body = body,
		isRead = isRead,
		readAt = item.longOrNullFlexible("read_at"),
		createdAt = createdAt,
		updatedAt = updatedAt
	)
}

private fun JsonObject.stringOrNull(key: String): String? {
	return this[key]
		?.jsonPrimitive
		?.contentOrNull
		?.trim()
		?.takeIf(String::isNotEmpty)
}

private fun JsonObject.objectOrNull(key: String): JsonObject? {
	return runCatching { this[key]?.jsonObject }.getOrNull()
}

private fun JsonObject.longOrNullFlexible(key: String): Long? {
	val element = this[key] ?: return null
	val primitive = element.jsonPrimitive
	primitive.longOrNull?.let { return it }
	primitive.contentOrNull
		?.trim()
		?.takeIf(String::isNotEmpty)
		?.toLongOrNull()
		?.let { return it }
	primitive.doubleOrNull
		?.takeIf { number -> number % 1.0 == 0.0 }
		?.toLong()
		?.let { return it }
	return null
}

private fun JsonObject.booleanOrNullFlexible(key: String): Boolean? {
	val element = this[key] ?: return null
	val primitive = element.jsonPrimitive
	primitive.booleanOrNull?.let { return it }
	return primitive.contentOrNull?.trim()?.lowercase()?.let { value ->
		when (value) {
			"true", "1" -> true
			"false", "0" -> false
			else -> null
		}
	}
}

private fun normalizeNotificationChannel(channel: String?): String? {
	val normalized = channel
		?.trim()
		?.lowercase()
		?.takeIf(String::isNotEmpty)
		?: return null
	return when (normalized) {
		"replies", "system", "campaign" -> normalized
		"all" -> null
		else -> null
	}
}

private fun channelFromType(type: String): String {
	val normalizedType = type.trim().lowercase()
	return when {
		normalizedType == "comment_on_post" || normalizedType == "reply_to_comment" || normalizedType == "comment_reply" -> "replies"
		normalizedType.startsWith("campaign_") || normalizedType.contains("campaign") -> "campaign"
		else -> "system"
	}
}
