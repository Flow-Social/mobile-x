package me.floow.api.chats

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.floow.domain.api.models.ChatMessageItem
import me.floow.domain.api.models.ChatMessageMediaItem
import me.floow.domain.api.models.ChatUserItem

internal fun buildChatsApiBaseUrl(apiUrl: String): String {
	val normalized = apiUrl.trimEnd('/')
	return when {
		normalized.endsWith("/api/v1") -> normalized.removeSuffix("/api/v1") + "/api/v2"
		normalized.endsWith("/api") -> "$normalized/v2"
		normalized.endsWith("/v1") -> normalized.removeSuffix("/v1") + "/v2"
		else -> "$normalized/api/v2"
	}
}

internal fun JsonObject.toChatMessageItemOrNull(): ChatMessageItem? {
	val id = longOrNullFlexible("id") ?: return null
	val conversationId = longOrNullFlexible("conversation_id") ?: return null
	val sender = objectOrNull("sender")?.toChatUserItemOrNull() ?: return null
	val text = stringOrNull("text") ?: ""
	val contentType = stringOrNull("content_type")
	val media = objectOrNull("media")?.toChatMessageMediaItemOrNull()
	val clientMessageId = stringOrNullFlexible("idempotency_key")
	val replyToMessageId = longOrNullFlexible("reply_to_message_id")
	val replyToMessageText = stringOrNull("reply_to_message_text")
	val isPinned = booleanOrNullFlexible("is_pinned") ?: false
	val pinnedAt = longOrNullFlexible("pinned_at")
	val pinnedByUserId = stringOrNullFlexible("pinned_by_user_id")
	val createdAt = longOrNullFlexible("created_at") ?: return null
	val updatedAt = longOrNullFlexible("updated_at") ?: createdAt
	return ChatMessageItem(
		id = id,
		conversationId = conversationId,
		sender = sender,
		text = text,
		contentType = contentType,
		media = media,
		clientMessageId = clientMessageId,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		pinnedAt = pinnedAt,
		pinnedByUserId = pinnedByUserId,
		createdAt = createdAt,
		updatedAt = updatedAt
	)
}

internal fun JsonObject.toChatMessageMediaItemOrNull(): ChatMessageMediaItem? {
	val url = stringOrNull("url") ?: return null
	val objectKey = stringOrNull("object_key") ?: return null
	val mimeType = stringOrNull("mime_type") ?: return null
	val sizeBytes = longOrNullFlexible("size_bytes") ?: return null
	val durationMs = longOrNullFlexible("duration_ms") ?: return null
	return ChatMessageMediaItem(
		url = url,
		objectKey = objectKey,
		mimeType = mimeType,
		sizeBytes = sizeBytes,
		durationMs = durationMs,
		width = longOrNullFlexible("width")?.toInt(),
		height = longOrNullFlexible("height")?.toInt(),
	)
}

internal fun JsonObject.toChatUserItemOrNull(): ChatUserItem? {
	val id = stringOrNullFlexible("id") ?: return null
	return ChatUserItem(
		id = id,
		username = stringOrNull("username"),
		name = stringOrNull("name"),
		avatar = stringOrNull("avatar")
	)
}

internal fun JsonObject.stringOrNull(key: String): String? {
	return this[key]
		?.jsonPrimitive
		?.contentOrNull
		?.trim()
		?.takeIf(String::isNotEmpty)
}

internal fun JsonObject.stringOrNullFlexible(key: String): String? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive ?: return null
	val content = primitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
	if (content != null) {
		return content
	}
	primitive.longOrNull?.let { value ->
		return value.toString()
	}
	return null
}

internal fun JsonObject.longOrNullFlexible(key: String): Long? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive ?: return null
	primitive.longOrNull?.let { value ->
		return value
	}
	return primitive.contentOrNull
		?.trim()
		?.takeIf(String::isNotEmpty)
		?.toLongOrNull()
}

internal fun JsonObject.booleanOrNullFlexible(key: String): Boolean? {
	val element = this[key] ?: return null
	val primitive = element as? JsonPrimitive ?: return null
	primitive.booleanOrNull?.let { value ->
		return value
	}
	return primitive.contentOrNull
		?.trim()
		?.lowercase()
		?.let { value ->
			when (value) {
				"true", "1" -> true
				"false", "0" -> false
				else -> null
			}
		}
}

internal fun JsonObject.objectOrNull(key: String): JsonObject? {
	return runCatching { this[key]?.jsonObject }.getOrNull()
}

internal fun JsonObject.jsonArrayOrNull(key: String): List<JsonElement>? {
	val element = this[key] ?: return null
	return runCatching {
		(element as? kotlinx.serialization.json.JsonArray)?.toList()
	}.getOrNull()
}
