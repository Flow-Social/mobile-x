package me.floow.shared.chats.uilogic

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.floow.shared.login.auth.flowAuthReadLocalStorage
import me.floow.shared.profile.auth.WasmProfileRepository
import me.floow.shared.profile.auth.flowWasmApiRequest
import kotlin.coroutines.resume
import kotlin.js.JsName

internal object WasmChatsApiSupport {
	val json = Json { ignoreUnknownKeys = true }

	private const val sessionTokenKey = "flow.auth.session_token"
	private const val apiHost = "https://45.66.228.158.nip.io"

	val apiUrlV1: String = WasmProfileRepository.FLOW_API_URL
	val apiUrlV2: String = buildChatsApiBaseUrl(apiUrlV1)

	fun requireAuthToken(): String {
		return requireNotNull(
			flowAuthReadLocalStorage(sessionTokenKey)?.takeIf(String::isNotBlank)
		) { "Missing session token" }
	}

	suspend fun apiRequestV1(
		method: String,
		path: String,
		authToken: String,
		contentType: String? = null,
		body: String? = null,
	): String = apiRequest(method, path, apiUrlV1, authToken, contentType, body)

	suspend fun apiRequestV2(
		method: String,
		path: String,
		authToken: String,
		contentType: String? = null,
		body: String? = null,
	): String = apiRequest(method, path, apiUrlV2, authToken, contentType, body)

	fun toAbsoluteMediaUrl(raw: String?): String? {
		if (raw.isNullOrBlank()) return null
		return if (raw.startsWith("http")) raw else if (raw.startsWith("/")) "$apiHost$raw" else "$apiHost/$raw"
	}

	fun formatTimeLabel(epochMillis: Long?): String {
		if (epochMillis == null || epochMillis <= 0L) return ""
		val totalMinutes = epochMillis / 60_000L
		val hours = ((totalMinutes / 60L) % 24L).toInt().toString().padStart(2, '0')
		val minutes = (totalMinutes % 60L).toInt().toString().padStart(2, '0')
		return "$hours:$minutes"
	}

	private suspend fun apiRequest(
		method: String,
		path: String,
		apiUrl: String,
		authToken: String,
		contentType: String?,
		body: String?,
	): String {
		return suspendCancellableCoroutine { continuation ->
			flowWasmApiRequest(
				method = method,
				path = path,
				apiUrl = apiUrl,
				authToken = authToken,
				contentType = contentType,
				body = body,
				onSuccess = { continuation.resume(it) },
				onError = { continuation.resume("""{"error":"$it"}""") },
			)
		}.also { raw ->
			extractError(raw)?.let(::error)
		}
	}

	private fun extractError(raw: String): String? {
		val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
		return root.stringOrNull("error")
	}
}

@JsName("encodeURIComponent")
external fun encodeURIComponent(value: String): String

internal data class WasmChatUser(
	val id: String,
	val username: String?,
	val name: String?,
	val avatar: String?,
)

internal data class WasmChatMessage(
	val id: Long,
	val clientMessageId: String?,
	val conversationId: Long,
	val sender: WasmChatUser,
	val text: String,
	val replyToMessageId: Long?,
	val replyToMessageText: String?,
	val isPinned: Boolean,
	val createdAt: Long,
	val updatedAt: Long,
)

internal data class WasmChatConversation(
	val id: Long,
	val kind: String,
	val peer: WasmChatUser,
	val lastMessage: WasmChatMessage?,
	val unreadCount: Int,
	val lastReadMessageId: Long,
	val peerLastReadMessageId: Long?,
	val createdAt: Long,
	val updatedAt: Long,
)

internal data class WasmChatMessagesPage(
	val items: List<WasmChatMessage>,
	val nextBeforeId: Long?,
	val peerLastReadMessageId: Long?,
)

internal data class WasmChatMessagesAround(
	val items: List<WasmChatMessage>,
	val anchorId: Long,
	val anchorIndex: Int,
	val hasOlder: Boolean,
	val hasNewer: Boolean,
	val peerLastReadMessageId: Long?,
)

internal data class WasmPresenceItem(
	val userId: String,
	val isOnline: Boolean,
	val lastSeenAt: Long?,
)

internal data class WasmNotificationItem(
	val id: String,
	val seq: Long,
	val type: String,
	val channel: String,
	val actorId: String,
	val actorName: String?,
	val actorAvatar: String?,
	val postId: String?,
	val commentId: String?,
	val threadId: String?,
	val replyToCommentId: String?,
	val commentText: String?,
	val replyToCommentText: String?,
	val title: String,
	val body: String,
	val isRead: Boolean,
	val createdAt: Long,
)

internal data class WasmNotificationsPage(
	val items: List<WasmNotificationItem>,
	val unreadCount: Int,
	val lastReadSeq: Long,
)

internal fun parseChatConversation(raw: String): WasmChatConversation {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	return root.toWasmChatConversationOrNull() ?: error("Invalid chat conversation payload")
}

internal fun parseChatConversations(raw: String): List<WasmChatConversation> {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	return root.jsonArrayOrNull("items")
		?.mapNotNull { (it as? JsonObject)?.toWasmChatConversationOrNull() }
		.orEmpty()
}

internal fun parseChatMessagesPage(raw: String): WasmChatMessagesPage {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	val items = root.jsonArrayOrNull("items")
		?.mapNotNull { (it as? JsonObject)?.toWasmChatMessageOrNull() }
		.orEmpty()
	return WasmChatMessagesPage(
		items = items,
		nextBeforeId = root.longOrNullFlexible("next_before_id"),
		peerLastReadMessageId = root.longOrNullFlexible("peer_last_read_message_id"),
	)
}

internal fun parseChatMessage(raw: String): WasmChatMessage {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	val nested = root["message"] as? JsonObject
	return nested?.toWasmChatMessageOrNull()
		?: root.toWasmChatMessageOrNull()
		?: error("Invalid chat message payload")
}

internal fun parseSentChatMessage(raw: String): WasmChatMessage {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	val nested = root["message"] as? JsonObject
	return nested?.toWasmChatMessageOrNull()
		?: root.toWasmChatMessageOrNull()
		?: error("Invalid sent chat message payload")
}

internal fun parseChatMessagesAround(raw: String): WasmChatMessagesAround {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	return WasmChatMessagesAround(
		items = root.jsonArrayOrNull("items")
			?.mapNotNull { (it as? JsonObject)?.toWasmChatMessageOrNull() }
			.orEmpty(),
		anchorId = root.longOrNullFlexible("anchor_id") ?: error("Missing anchor_id"),
		anchorIndex = root.longOrNullFlexible("anchor_index")?.toInt() ?: -1,
		hasOlder = root.booleanOrNullFlexible("has_older") ?: false,
		hasNewer = root.booleanOrNullFlexible("has_newer") ?: false,
		peerLastReadMessageId = root.longOrNullFlexible("peer_last_read_message_id"),
	)
}

internal fun parsePresenceItems(raw: String): List<WasmPresenceItem> {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	return root.jsonArrayOrNull("items")
		?.mapNotNull { (it as? JsonObject)?.toWasmPresenceItemOrNull() }
		.orEmpty()
}

internal fun parseNotificationsPage(raw: String): WasmNotificationsPage {
	val root = WasmChatsApiSupport.json.parseToJsonElement(raw).jsonObject
	val items = root.jsonArrayOrNull("items")
		?.mapNotNull { (it as? JsonObject)?.toWasmNotificationItemOrNull() }
		.orEmpty()
	return WasmNotificationsPage(
		items = items,
		unreadCount = root.longOrNullFlexible("unread_count")
			?.coerceAtLeast(0L)
			?.coerceAtMost(Int.MAX_VALUE.toLong())
			?.toInt()
			?: items.count { !it.isRead },
		lastReadSeq = root.longOrNullFlexible("last_read_seq") ?: 0L,
	)
}

private fun buildChatsApiBaseUrl(apiUrl: String): String {
	val normalized = apiUrl.trimEnd('/')
	return when {
		normalized.endsWith("/api/v1") -> normalized.removeSuffix("/api/v1") + "/api/v2"
		normalized.endsWith("/api") -> "$normalized/v2"
		normalized.endsWith("/v1") -> normalized.removeSuffix("/v1") + "/v2"
		else -> "$normalized/api/v2"
	}
}

private fun JsonObject.toWasmChatConversationOrNull(): WasmChatConversation? {
	val id = longOrNullFlexible("id") ?: return null
	val kind = stringOrNull("kind") ?: "direct"
	val peer = objectOrNull("peer")?.toWasmChatUserOrNull() ?: return null
	val unreadCount = longOrNullFlexible("unread_count")?.toInt() ?: 0
	val lastReadMessageId = longOrNullFlexible("last_read_message_id") ?: 0L
	val createdAt = longOrNullFlexible("created_at") ?: 0L
	val updatedAt = longOrNullFlexible("updated_at") ?: createdAt
	return WasmChatConversation(
		id = id,
		kind = kind,
		peer = peer,
		lastMessage = objectOrNull("last_message")?.toWasmChatMessageOrNull(),
		unreadCount = unreadCount,
		lastReadMessageId = lastReadMessageId,
		peerLastReadMessageId = longOrNullFlexible("peer_last_read_message_id"),
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}

private fun JsonObject.toWasmChatMessageOrNull(): WasmChatMessage? {
	val id = longOrNullFlexible("id") ?: return null
	val conversationId = longOrNullFlexible("conversation_id") ?: return null
	val sender = objectOrNull("sender")?.toWasmChatUserOrNull() ?: return null
	val createdAt = longOrNullFlexible("created_at") ?: return null
	val updatedAt = longOrNullFlexible("updated_at") ?: createdAt
	return WasmChatMessage(
		id = id,
		clientMessageId = stringOrNull("client_message_id"),
		conversationId = conversationId,
		sender = sender,
		text = stringOrNull("text") ?: "",
		replyToMessageId = longOrNullFlexible("reply_to_message_id"),
		replyToMessageText = stringOrNull("reply_to_message_text"),
		isPinned = booleanOrNullFlexible("is_pinned") ?: false,
		createdAt = createdAt,
		updatedAt = updatedAt,
	)
}

private fun JsonObject.toWasmChatUserOrNull(): WasmChatUser? {
	val id = stringOrNullFlexible("id") ?: return null
	return WasmChatUser(
		id = id,
		username = stringOrNull("username"),
		name = stringOrNull("name"),
		avatar = stringOrNull("avatar"),
	)
}

private fun JsonObject.toWasmPresenceItemOrNull(): WasmPresenceItem? {
	val userId = stringOrNullFlexible("user_id") ?: return null
	return WasmPresenceItem(
		userId = userId,
		isOnline = booleanOrNullFlexible("is_online") ?: false,
		lastSeenAt = longOrNullFlexible("last_seen_at"),
	)
}

private fun JsonObject.toWasmNotificationItemOrNull(): WasmNotificationItem? {
	val actor = objectOrNull("actor") ?: return null
	val id = stringOrNull("id") ?: return null
	val type = stringOrNull("type") ?: return null
	val actorId = actor.stringOrNullFlexible("id") ?: return null
	val createdAt = longOrNullFlexible("created_at") ?: return null
	return WasmNotificationItem(
		id = id,
		seq = longOrNullFlexible("seq") ?: id.toLongOrNull() ?: return null,
		type = type,
		channel = stringOrNull("channel") ?: channelFromType(type),
		actorId = actorId,
		actorName = actor.stringOrNull("name") ?: actor.stringOrNull("username"),
		actorAvatar = actor.stringOrNull("avatar"),
		postId = stringOrNull("post_id"),
		commentId = stringOrNull("comment_id"),
		threadId = stringOrNull("thread_id") ?: stringOrNull("reply_to_comment_id") ?: stringOrNull("comment_id"),
		replyToCommentId = stringOrNull("reply_to_comment_id"),
		commentText = stringOrNull("comment_text"),
		replyToCommentText = stringOrNull("reply_to_comment_text"),
		title = stringOrNull("title") ?: "",
		body = stringOrNull("body") ?: "",
		isRead = booleanOrNullFlexible("is_read") ?: false,
		createdAt = createdAt,
	)
}

private fun channelFromType(type: String): String {
	return when (type.trim().lowercase()) {
		"comment_on_post", "comment_reply", "reply_to_comment" -> "replies"
		else -> "system"
	}
}

private fun JsonObject.stringOrNull(key: String): String? {
	return this[key]
		?.jsonPrimitive
		?.contentOrNull
		?.trim()
		?.takeIf(String::isNotEmpty)
}

private fun JsonObject.stringOrNullFlexible(key: String): String? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive ?: return null
	val content = primitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
	if (content != null) return content
	return primitive.longOrNull?.toString()
}

private fun JsonObject.longOrNullFlexible(key: String): Long? {
	val primitive = this[key] as? JsonPrimitive ?: return null
	primitive.longOrNull?.let { return it }
	return primitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.toLongOrNull()
}

private fun JsonObject.booleanOrNullFlexible(key: String): Boolean? {
	val primitive = this[key] as? JsonPrimitive ?: return null
	primitive.booleanOrNull?.let { return it }
	return primitive.contentOrNull
		?.trim()
		?.lowercase()
		?.let {
			when (it) {
				"true", "1" -> true
				"false", "0" -> false
				else -> null
			}
		}
}

private fun JsonObject.objectOrNull(key: String): JsonObject? {
	return runCatching { this[key]?.jsonObject }.getOrNull()
}

private fun JsonObject.jsonArrayOrNull(key: String): JsonArray? {
	return this[key] as? JsonArray
}
