package me.floow.api

import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.floow.api.chats.booleanOrNullFlexible
import me.floow.api.chats.buildChatsApiBaseUrl
import me.floow.api.chats.longOrNullFlexible
import me.floow.api.chats.objectOrNull
import me.floow.api.chats.stringOrNull
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.domain.api.CommentsRealtimeApi
import me.floow.domain.api.models.CommentItem
import me.floow.domain.api.models.CommentReplyItem
import me.floow.domain.api.models.CommentsRealtimeEvent
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

private const val COMMENTS_REALTIME_EVENT_HELLO = "hello"
private const val COMMENTS_REALTIME_EVENT_COMMENT_CREATED = "comment_created"
private const val COMMENTS_REALTIME_EVENT_COMMENT_UPDATED = "comment_updated"
private const val COMMENTS_REALTIME_EVENT_COMMENT_DELETED = "comment_deleted"
private const val COMMENTS_REALTIME_EVENT_READ_UP_TO_UPDATED = "read_up_to_updated"
private const val COMMENTS_REALTIME_EVENT_RESYNC_REQUIRED = "resync_required"

private const val DEFAULT_COMMENTS_REALTIME_REPLAY_LIMIT = 200
private const val MAX_COMMENTS_REALTIME_REPLAY_LIMIT = 500
private const val MAX_COMMENTS_REALTIME_RECONNECT_DELAY_MS = 5000L
private const val INITIAL_COMMENTS_REALTIME_RECONNECT_DELAY_MS = 500L

class CommentsRealtimeApiImpl(
	config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : CommentsRealtimeApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val wsUrl = buildCommentsRealtimeWsUrl(config.apiUrl)

	override fun subscribePostComments(
		postId: Long,
		afterSeq: Long,
		replayLimit: Int
	): Flow<CommentsRealtimeEvent> = flow {
		if (postId <= 0L) return@flow
		val authToken = authenticationManager.getAuthTokenOrNull()
			?: return@flow

		val sanitizedReplayLimit = replayLimit.coerceIn(1, MAX_COMMENTS_REALTIME_REPLAY_LIMIT)
		var nextAfterSeq = afterSeq.coerceAtLeast(0L)
		var reconnectDelayMs = INITIAL_COMMENTS_REALTIME_RECONNECT_DELAY_MS

		while (currentCoroutineContext().isActive) {
			val session = try {
				httpClient.webSocketSession {
					addAuthTokenHeader(authToken)
					url {
						protocol = wsUrl.protocol
						host = wsUrl.host
						port = wsUrl.port
						pathSegments = wsUrl.rawSegments
						parameters.append("post_id", postId.toString())
						if (nextAfterSeq > 0L) {
							parameters.append("after_seq", nextAfterSeq.toString())
						}
						if (sanitizedReplayLimit != DEFAULT_COMMENTS_REALTIME_REPLAY_LIMIT) {
							parameters.append("replay_limit", sanitizedReplayLimit.toString())
						}
					}
				}
			} catch (throwable: Throwable) {
				logger.d(
					"CommentsRealtimeApiImpl.subscribePostComments",
					"Failed to open websocket session: ${throwable.message}"
				)
				delay(reconnectDelayMs)
				reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_COMMENTS_REALTIME_RECONNECT_DELAY_MS)
				continue
			}

			var receivedAnyEvent = false
			try {
				for (frame in session.incoming) {
					if (frame !is Frame.Text) continue
					val event = parseCommentsRealtimeEvent(frame.readText()) ?: continue
					nextAfterSeq = maxOf(nextAfterSeq, event.seq, event.maxSeq)
					receivedAnyEvent = true
					emit(event)
				}
			} catch (throwable: Throwable) {
				logger.d(
					"CommentsRealtimeApiImpl.subscribePostComments",
					"WebSocket stream error: ${throwable.message}"
				)
			} finally {
				runCatching { session.outgoing.close() }
			}

			if (!currentCoroutineContext().isActive) {
				break
			}
			reconnectDelayMs = if (receivedAnyEvent) {
				INITIAL_COMMENTS_REALTIME_RECONNECT_DELAY_MS
			} else {
				minOf(reconnectDelayMs * 2, MAX_COMMENTS_REALTIME_RECONNECT_DELAY_MS)
			}
			delay(reconnectDelayMs)
		}
	}
}

private fun parseCommentsRealtimeEvent(rawText: String): CommentsRealtimeEvent? {
	val root = runCatching { JsonSerializer.parseToJsonElement(rawText).jsonObject }
		.getOrNull()
		?: return null

	val type = root.stringOrNull("type") ?: return null
	val eventId = root.stringOrNull("event_id")
	val postId = root.longOrNullFlexible("post_id") ?: return null
	val seq = root.longOrNullFlexible("seq") ?: 0L
	val lastReadSeq = root.longOrNullFlexible("last_read_seq") ?: 0L
	val unreadCount = root.longOrNullFlexible("unread_count")
		?.coerceAtLeast(0L)
		?.coerceAtMost(Int.MAX_VALUE.toLong())
		?.toInt()
		?: 0
	val firstUnreadSeq = root.longOrNullFlexible("first_unread_seq")
	val maxSeq = root.longOrNullFlexible("max_seq") ?: seq

	return when (type) {
		COMMENTS_REALTIME_EVENT_HELLO -> {
			CommentsRealtimeEvent.Hello(
				postId = postId,
				eventId = eventId,
				seq = seq,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		COMMENTS_REALTIME_EVENT_COMMENT_CREATED -> {
			val comment = root.objectOrNull("comment")?.toCommentItemOrNull() ?: return null
			CommentsRealtimeEvent.CommentCreated(
				postId = postId,
				eventId = eventId,
				comment = comment,
				seq = seq,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq,
				isReplay = root.booleanOrNullFlexible("is_replay") ?: false
			)
		}

		COMMENTS_REALTIME_EVENT_COMMENT_UPDATED -> {
			val comment = root.objectOrNull("comment")?.toCommentItemOrNull() ?: return null
			CommentsRealtimeEvent.CommentUpdated(
				postId = postId,
				eventId = eventId,
				comment = comment,
				seq = seq,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		COMMENTS_REALTIME_EVENT_COMMENT_DELETED -> {
			val deletedCommentId = root.longOrNullFlexible("deleted_comment_id") ?: return null
			CommentsRealtimeEvent.CommentDeleted(
				postId = postId,
				eventId = eventId,
				deletedCommentId = deletedCommentId,
				actorUserId = root.stringOrNullFlexible("actor_user_id"),
				seq = seq,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		COMMENTS_REALTIME_EVENT_READ_UP_TO_UPDATED -> {
			CommentsRealtimeEvent.ReadUpToUpdated(
				postId = postId,
				eventId = eventId,
				readUpToSeq = root.longOrNullFlexible("read_up_to_seq") ?: lastReadSeq,
				actorUserId = root.stringOrNullFlexible("actor_user_id"),
				seq = seq,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		COMMENTS_REALTIME_EVENT_RESYNC_REQUIRED -> {
			CommentsRealtimeEvent.ResyncRequired(
				postId = postId,
				eventId = eventId,
				seq = seq,
				lastReadSeq = lastReadSeq,
				unreadCount = unreadCount,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}

		else -> null
	}
}

private fun JsonObject.toCommentItemOrNull(): CommentItem? {
	val id = stringOrNullFlexible("id") ?: return null
	val seq = longOrNullFlexible("seq") ?: id.toLongOrNull() ?: return null
	val postId = stringOrNullFlexible("post_id") ?: return null
	val author = objectOrNull("author") ?: return null
	val authorId = author.stringOrNullFlexible("id") ?: return null
	val createdAt = longOrNullFlexible("created_at") ?: return null
	val updatedAt = longOrNullFlexible("updated_at") ?: createdAt

	val reply = objectOrNull("reply_to")?.let { replyObject ->
		val replyId = replyObject.stringOrNullFlexible("id") ?: return@let null
		val replyAuthorId = replyObject.stringOrNullFlexible("author_id") ?: ""
		CommentReplyItem(
			id = replyId,
			text = replyObject.stringOrNull("text").orEmpty(),
			authorId = replyAuthorId,
			authorName = replyObject.stringOrNull("author_name")
		)
	}

	return CommentItem(
		id = id,
		seq = seq,
		postId = postId,
		authorId = authorId,
		authorUsername = author.stringOrNull("username"),
		authorName = author.stringOrNull("name"),
		authorAvatarUrl = author.stringOrNull("avatar"),
		text = stringOrNull("text").orEmpty(),
		isRead = booleanOrNullFlexible("is_read") ?: false,
		createdAt = createdAt,
		updatedAt = updatedAt,
		replyTo = reply
	)
}

private fun JsonObject.stringOrNullFlexible(key: String): String? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive ?: return null
	return primitive.contentOrNull
		?.trim()
		?.takeIf(String::isNotEmpty)
		?: primitive.longOrNull?.toString()
}

private fun buildCommentsRealtimeWsUrl(apiUrl: String): Url {
	val v2Base = buildChatsApiBaseUrl(apiUrl)
	val base = Url(v2Base)
	val basePath = base.encodedPath.trimEnd('/')
	val realtimePath = "$basePath/comments/realtime/ws"
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
