package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.NotificationsApi
import me.floow.domain.api.models.GetNotificationsResponse
import me.floow.domain.api.models.GetUnreadNotificationsCountResponse
import me.floow.domain.api.models.MarkAllNotificationsReadResponse
import me.floow.domain.api.models.MarkNotificationReadResponse
import me.floow.domain.api.models.MarkNotificationsReadUpToResponse
import me.floow.domain.api.models.NotificationActorItem
import me.floow.domain.api.models.NotificationItem
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class MarkNotificationReadRequest(
	@SerialName("notification_id") val notificationId: String
)

@Serializable
private data class MarkNotificationsReadUpToRequest(
	@SerialName("read_up_to_seq") val readUpToSeq: String,
	@SerialName("channel") val channel: String
)

@Serializable
private class MarkAllReadRequest

private const val MAX_NOTIFICATIONS_LIMIT = 100

class NotificationsApiImpl(
	private val config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : NotificationsApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()

	override suspend fun getNotifications(
		cursor: String?,
		limit: Int,
		types: Set<String>,
		channel: String?
	): GetNotificationsResponse {
		return safeApiCall(errorResponse = GetNotificationsResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetNotificationsResponse.Error

				val response = httpClient.get("${config.apiUrl}/notifications") {
					addAuthTokenHeader(authToken)
					url {
						parameters.append("limit", limit.coerceIn(1, MAX_NOTIFICATIONS_LIMIT).toString())
						if (!cursor.isNullOrBlank()) {
							parameters.append("cursor", cursor)
						}
						val normalizedTypes = normalizeNotificationTypes(types)
						if (normalizedTypes.isNotEmpty()) {
							parameters.append("types", normalizedTypes.joinToString(","))
						}
						normalizeNotificationChannel(channel)?.let { normalizedChannel ->
							parameters.append("channel", normalizedChannel)
						}
					}
				}

			logger.logKtorRequest("NotificationsApiImpl getNotifications", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("NotificationsApiImpl getNotifications", response.status, bodyText)
				return@safeApiCall GetNotificationsResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }
				.getOrNull() ?: return@safeApiCall GetNotificationsResponse.Error

			val items = root.jsonArrayOrNull("items")
				?.mapNotNull { element -> element.toNotificationItemOrNull() }
				.orEmpty()
			val unreadCount = root.longOrNullFlexible("unread_count")
				?.coerceAtLeast(0L)
				?.coerceAtMost(Int.MAX_VALUE.toLong())
				?.toInt()
				?: items.count { item -> !item.isRead }
			val lastReadSeq = root.longOrNullFlexible("last_read_seq") ?: 0L
			val firstUnreadSeq = root.longOrNullFlexible("first_unread_seq")
			val maxSeq = root.longOrNullFlexible("max_seq")
				?: items.maxOfOrNull(NotificationItem::seq)
				?: 0L

			GetNotificationsResponse.Success(
				items = items,
				nextCursor = root.stringOrNull("next_cursor"),
				unreadCount = unreadCount,
				lastReadSeq = lastReadSeq,
				firstUnreadSeq = firstUnreadSeq,
				maxSeq = maxSeq
			)
		}
	}

	override suspend fun getUnreadNotificationsCount(types: Set<String>, channel: String?): GetUnreadNotificationsCountResponse {
		return safeApiCall(errorResponse = GetUnreadNotificationsCountResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall GetUnreadNotificationsCountResponse.Error

				val response = httpClient.get("${config.apiUrl}/notifications/unread-count") {
					addAuthTokenHeader(authToken)
					url {
						val normalizedTypes = normalizeNotificationTypes(types)
						if (normalizedTypes.isNotEmpty()) {
							parameters.append("types", normalizedTypes.joinToString(","))
						}
						normalizeNotificationChannel(channel)?.let { normalizedChannel ->
							parameters.append("channel", normalizedChannel)
						}
					}
				}

			logger.logKtorRequest("NotificationsApiImpl getUnreadNotificationsCount", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("NotificationsApiImpl getUnreadNotificationsCount", response.status, bodyText)
				return@safeApiCall GetUnreadNotificationsCountResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }
				.getOrNull() ?: return@safeApiCall GetUnreadNotificationsCountResponse.Error

			val unreadCount = root.longOrNullFlexible("unread_count")?.coerceAtLeast(0L)?.coerceAtMost(Int.MAX_VALUE.toLong())
				?.toInt()
				?: return@safeApiCall GetUnreadNotificationsCountResponse.Error

			GetUnreadNotificationsCountResponse.Success(unreadCount = unreadCount)
		}
	}

	override suspend fun markNotificationRead(notificationId: String): MarkNotificationReadResponse {
		return safeApiCall(errorResponse = MarkNotificationReadResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall MarkNotificationReadResponse.Error

			val sanitizedId = notificationId.trim()
			if (sanitizedId.isBlank()) {
				return@safeApiCall MarkNotificationReadResponse.Error
			}

			val response = httpClient.post("${config.apiUrl}/notifications/read") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						MarkNotificationReadRequest(notificationId = sanitizedId)
					)
				)
			}

			logger.logKtorRequest("NotificationsApiImpl markNotificationRead", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall MarkNotificationReadResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("NotificationsApiImpl markNotificationRead", response.status, bodyText)
				return@safeApiCall MarkNotificationReadResponse.Error
			}

			MarkNotificationReadResponse.Success
		}
	}

	override suspend fun markAllNotificationsRead(channel: String?): MarkAllNotificationsReadResponse {
		return safeApiCall(errorResponse = MarkAllNotificationsReadResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall MarkAllNotificationsReadResponse.Error

			val response = httpClient.post("${config.apiUrl}/notifications/read-all") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(MarkAllReadRequest()))
				url {
					normalizeNotificationChannel(channel)?.let { normalizedChannel ->
						parameters.append("channel", normalizedChannel)
					}
				}
			}

			logger.logKtorRequest("NotificationsApiImpl markAllNotificationsRead", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("NotificationsApiImpl markAllNotificationsRead", response.status, bodyText)
				return@safeApiCall MarkAllNotificationsReadResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
			val updatedCount = root
				?.longOrNullFlexible("updated")
				?.coerceAtLeast(0L)
				?.coerceAtMost(Int.MAX_VALUE.toLong())
				?.toInt()
				?: 0

			MarkAllNotificationsReadResponse.Success(updatedCount = updatedCount)
		}
	}

	override suspend fun markNotificationsReadUpTo(readUpToSeq: Long, channel: String): MarkNotificationsReadUpToResponse {
		return safeApiCall(errorResponse = MarkNotificationsReadUpToResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall MarkNotificationsReadUpToResponse.Error

			val sanitizedSeq = readUpToSeq.coerceAtLeast(0L)
			if (sanitizedSeq <= 0L) {
				return@safeApiCall MarkNotificationsReadUpToResponse.Error
			}
			val normalizedChannel = normalizeNotificationChannel(channel)
				?: return@safeApiCall MarkNotificationsReadUpToResponse.Error

			val response = httpClient.post("${config.apiUrl}/notifications/read-up-to") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						MarkNotificationsReadUpToRequest(
							readUpToSeq = sanitizedSeq.toString(),
							channel = normalizedChannel
						)
					)
				)
			}

			logger.logKtorRequest("NotificationsApiImpl markNotificationsReadUpTo", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("NotificationsApiImpl markNotificationsReadUpTo", response.status, bodyText)
				return@safeApiCall MarkNotificationsReadUpToResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }
				.getOrNull() ?: return@safeApiCall MarkNotificationsReadUpToResponse.Error

			val updatedCount = root.longOrNullFlexible("updated")
				?.coerceAtLeast(0L)
				?.coerceAtMost(Int.MAX_VALUE.toLong())
				?.toInt()
				?: 0
			val lastReadSeq = root.longOrNullFlexible("last_read_seq")
				?: root.longOrNullFlexible("read_up_to_seq")
				?: sanitizedSeq

			MarkNotificationsReadUpToResponse.Success(
				updatedCount = updatedCount,
				lastReadSeq = lastReadSeq,
				channel = normalizedChannel
			)
		}
	}
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
		?.takeIf { it.isNotBlank() }
}

private fun JsonObject.longOrNullFlexible(key: String): Long? {
	val raw = this[key]
		?.jsonPrimitive
		?.contentOrNull
		?.trim()
		?.takeIf { it.isNotBlank() }
		?: return null
	return raw.toLongOrNull()
}

private fun JsonObject.booleanOrNullFlexible(key: String): Boolean? {
	val raw = this[key]
		?.jsonPrimitive
		?.contentOrNull
		?.trim()
		?.lowercase()
		?: return null
	return when (raw) {
		"true", "1" -> true
		"false", "0" -> false
		else -> null
	}
}

private fun JsonObject.objectOrNull(key: String): JsonObject? {
	return this[key] as? JsonObject
}

private fun normalizeNotificationTypes(types: Set<String>): Set<String> {
	if (types.isEmpty()) return emptySet()
	return types
		.asSequence()
		.map(String::trim)
		.filter(String::isNotEmpty)
		.map(String::lowercase)
		.toSet()
}

private fun normalizeNotificationChannel(rawChannel: String?): String? {
	val normalized = rawChannel
		?.trim()
		?.lowercase()
		?.takeIf { it.isNotEmpty() }
		?: return null
	return when (normalized) {
		"replies", "system", "campaign", "all" -> normalized
		else -> null
	}
}

private fun channelFromType(type: String): String {
	return when (type.trim().lowercase()) {
		"comment_on_post", "comment_reply", "reply_to_comment" -> "replies"
		else -> "system"
	}
}

private fun JsonObject.jsonArrayOrNull(key: String): JsonArray? {
	return this[key] as? JsonArray
}
