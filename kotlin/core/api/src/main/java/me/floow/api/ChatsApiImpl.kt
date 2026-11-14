package me.floow.api

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import me.floow.api.chats.booleanOrNullFlexible
import me.floow.api.chats.buildChatsApiBaseUrl
import me.floow.api.chats.jsonArrayOrNull
import me.floow.api.chats.longOrNullFlexible
import me.floow.api.chats.objectOrNull
import me.floow.api.chats.stringOrNull
import me.floow.api.chats.stringOrNullFlexible
import me.floow.api.chats.toChatMessageItemOrNull
import me.floow.api.chats.toChatUserItemOrNull
import me.floow.api.util.ApiConfig
import me.floow.api.util.HttpClientProvider
import me.floow.api.util.JsonSerializer
import me.floow.api.util.extensions.addAuthTokenHeader
import me.floow.api.util.extensions.logFailureResponse
import me.floow.api.util.extensions.logKtorRequest
import me.floow.api.util.safeApiCall
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.models.ChatConversationItem
import me.floow.domain.api.models.ChatReadStateItem
import me.floow.domain.api.models.ChatsGetConversationResponse
import me.floow.domain.api.models.ChatsGetConversationInitialResponse
import me.floow.domain.api.models.ChatsGetConversationsResponse
import me.floow.domain.api.models.ChatsGetMessagesResponse
import me.floow.domain.api.models.ChatsGetMessagesAroundResponse
import me.floow.domain.api.models.ChatsGetMessageByClientMessageIdResponse
import me.floow.domain.api.models.ChatsGetOrCreateDirectResponse
import me.floow.domain.api.models.ChatsDeleteConversationResponse
import me.floow.domain.api.models.ChatsDeleteMessageResponse
import me.floow.domain.api.models.ChatsGetPinnedMessagesResponse
import me.floow.domain.api.models.ChatsMarkReadUpToResponse
import me.floow.domain.api.models.ChatsPinMessageResponse
import me.floow.domain.api.models.ChatAlbumItemPayload
import me.floow.domain.api.models.ChatsSendAlbumMessageResponse
import me.floow.domain.api.models.ChatsSendMessageResponse
import me.floow.domain.api.models.ChatsTypingResponse
import me.floow.domain.api.models.ChatsUnpinMessageResponse
import me.floow.domain.api.models.ChatsUpdateMessageResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger

@Serializable
private data class CreateDirectChatRequest(
	@SerialName("user_id") val userId: String
)

@Serializable
private data class SendChatMessageRequest(
	val text: String,
	@SerialName("client_message_id") val clientMessageId: String? = null,
	@SerialName("idempotency_key") val idempotencyKey: String? = null,
	@SerialName("reply_to_message_id") val replyToMessageId: String? = null
)

@Serializable
private data class SendMediaPayload(
	val url: String,
	@SerialName("object_key") val objectKey: String,
	@SerialName("mime_type") val mimeType: String,
	@SerialName("size_bytes") val sizeBytes: Long,
	@SerialName("duration_ms") val durationMs: Long? = null,
	val width: Int? = null,
	val height: Int? = null,
	@SerialName("thumbnail_url") val thumbnailUrl: String? = null,
	val waveform: List<Int>? = null,
	@SerialName("group_id") val groupId: String? = null,
	val position: Int? = null,
)

@Serializable
private data class SendVideoCircleMessageRequest(
	val text: String,
	@SerialName("client_message_id") val clientMessageId: String? = null,
	@SerialName("idempotency_key") val idempotencyKey: String? = null,
	@SerialName("content_type") val contentType: String,
	val media: SendMediaPayload? = null,
	@SerialName("media_url") val mediaUrl: String,
	@SerialName("media_object_key") val mediaObjectKey: String,
	@SerialName("media_mime_type") val mediaMimeType: String = "video/mp4",
	@SerialName("media_size_bytes") val mediaSizeBytes: Long,
	@SerialName("media_duration_ms") val mediaDurationMs: Long,
	@SerialName("media_width") val mediaWidth: Int,
	@SerialName("media_height") val mediaHeight: Int,
	@SerialName("media_thumbnail_url") val mediaThumbnailUrl: String? = null,
	@SerialName("reply_to_message_id") val replyToMessageId: String? = null
)

@Serializable
private data class SendVoiceMessageRequest(
	val text: String,
	@SerialName("client_message_id") val clientMessageId: String? = null,
	@SerialName("idempotency_key") val idempotencyKey: String? = null,
	@SerialName("content_type") val contentType: String,
	val media: SendMediaPayload? = null,
	@SerialName("media_url") val mediaUrl: String,
	@SerialName("media_object_key") val mediaObjectKey: String,
	@SerialName("media_mime_type") val mediaMimeType: String = "audio/ogg",
	@SerialName("media_size_bytes") val mediaSizeBytes: Long,
	@SerialName("media_duration_ms") val mediaDurationMs: Long,
	@SerialName("reply_to_message_id") val replyToMessageId: String? = null
)

@Serializable
private data class SendImageMessageRequest(
	val text: String,
	@SerialName("client_message_id") val clientMessageId: String? = null,
	@SerialName("idempotency_key") val idempotencyKey: String? = null,
	@SerialName("content_type") val contentType: String,
	@SerialName("group_id") val groupId: String? = null,
	val position: Int = 0,
	val media: SendMediaPayload? = null,
	@SerialName("media_url") val mediaUrl: String,
	@SerialName("media_object_key") val mediaObjectKey: String,
	@SerialName("media_mime_type") val mediaMimeType: String = "image/jpeg",
	@SerialName("media_size_bytes") val mediaSizeBytes: Long,
	@SerialName("media_width") val mediaWidth: Int,
	@SerialName("media_height") val mediaHeight: Int,
	@SerialName("reply_to_message_id") val replyToMessageId: String? = null
)

@Serializable
private data class SendAlbumItemPayload(
	val media: SendMediaPayload,
	@SerialName("client_message_id") val clientMessageId: String? = null,
)

@Serializable
private data class SendAlbumMessageRequest(
	val items: List<SendAlbumItemPayload>,
	val caption: String? = null,
	@SerialName("reply_to_message_id") val replyToMessageId: Long? = null,
	@SerialName("idempotency_key") val idempotencyKey: String,
)

@Serializable
private data class MarkReadUpToRequest(
	@SerialName("message_id") val messageId: String
)

@Serializable
private data class SendTypingRequest(
	val typing: Boolean,
	val action: String = "typing"
)

@Serializable
private data class UpdateChatMessageRequest(
	val text: String
)

@Serializable
private data class PinConversationRequest(
	val pinned: Boolean
)

private const val MAX_CHAT_CONVERSATIONS_LIMIT = 100
private const val MAX_CHAT_MESSAGES_LIMIT = 100
private const val MAX_CHAT_PINS_LIMIT = 50

/**
 * Legacy Android/shared implementation of [ChatsApi].
 *
 * ATTENTION:
 * Registered in `SharedApiModule.kt` (`:shared`) and `apiModule.kt` (`:app`).
 * This class serves as the transitional implementation for chat network calls in the current KMP graph.
 * DO NOT delete or rename this class without migrating all dependency injection definitions and callers
 * to the unified common KMP HTTP networking layer.
 */
@Deprecated(
	message = "Transitional legacy ChatsApi implementation. Registered in SharedApiModule and apiModule. Do not delete until full migration to unified KMP client.",
	level = DeprecationLevel.WARNING
)
class ChatsApiImpl(
	config: ApiConfig,
	private val logger: Logger,
	private val authenticationManager: AuthenticationManager,
	httpClientProvider: HttpClientProvider
) : ChatsApi {
	private val httpClient = httpClientProvider.getClientWithoutHttpCache()
	private val baseUrl = buildChatsApiBaseUrl(config.apiUrl)

	override suspend fun getOrCreateSavedMessagesConversation(): ChatsGetOrCreateDirectResponse {
		return safeApiCall(errorResponse = ChatsGetOrCreateDirectResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetOrCreateDirectResponse.Error

			val response = httpClient.post("$baseUrl/chats/saved") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ChatsApiImpl.getOrCreateSavedMessagesConversation", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getOrCreateSavedMessagesConversation", response.status, bodyText)
				return@safeApiCall ChatsGetOrCreateDirectResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetOrCreateDirectResponse.Error
			val conversation = root.toChatConversationItemOrNull()
				?: return@safeApiCall ChatsGetOrCreateDirectResponse.Error

			ChatsGetOrCreateDirectResponse.Success(conversation = conversation)
		}
	}

	override suspend fun getOrCreateDirectConversation(peerUserId: String): ChatsGetOrCreateDirectResponse {
		return safeApiCall(errorResponse = ChatsGetOrCreateDirectResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetOrCreateDirectResponse.Error
			val sanitizedPeerUserId = peerUserId.trim()
			if (sanitizedPeerUserId.isEmpty()) {
				return@safeApiCall ChatsGetOrCreateDirectResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/direct") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(CreateDirectChatRequest(userId = sanitizedPeerUserId)))
			}

			logger.logKtorRequest("ChatsApiImpl.getOrCreateDirectConversation", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getOrCreateDirectConversation", response.status, bodyText)
				return@safeApiCall ChatsGetOrCreateDirectResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetOrCreateDirectResponse.Error
			val conversation = root.toChatConversationItemOrNull()
				?: return@safeApiCall ChatsGetOrCreateDirectResponse.Error

			ChatsGetOrCreateDirectResponse.Success(conversation = conversation)
		}
	}

	override suspend fun getConversations(limit: Int, cursor: String?): ChatsGetConversationsResponse {
		return safeApiCall(errorResponse = ChatsGetConversationsResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetConversationsResponse.Error

			val response = httpClient.get("$baseUrl/chats/conversations") {
				addAuthTokenHeader(authToken)
				url {
					parameters.append("limit", limit.coerceIn(1, MAX_CHAT_CONVERSATIONS_LIMIT).toString())
					if (!cursor.isNullOrBlank()) {
						parameters.append("cursor", cursor)
					}
				}
			}

			logger.logKtorRequest("ChatsApiImpl.getConversations", response.call.request)
			val bodyText = response.bodyAsText()
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getConversations", response.status, bodyText)
				return@safeApiCall ChatsGetConversationsResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetConversationsResponse.Error
			val items = root.jsonArrayOrNull("items")
				?.mapNotNull { item -> (item as? JsonObject)?.toChatConversationItemOrNull() }
				.orEmpty()
			val nextCursor = root.stringOrNull("next_cursor")
			val hasMore = root.booleanOrNullFlexible("has_more") ?: false

			ChatsGetConversationsResponse.Success(
				items = items,
				nextCursor = nextCursor,
				hasMore = hasMore
			)
		}
	}

	override suspend fun getConversation(conversationId: Long): ChatsGetConversationResponse {
		return safeApiCall(errorResponse = ChatsGetConversationResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetConversationResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsGetConversationResponse.Error
			}

			val response = httpClient.get("$baseUrl/chats/conversations/$conversationId") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ChatsApiImpl.getConversation", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetConversationResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getConversation", response.status, bodyText)
				return@safeApiCall ChatsGetConversationResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetConversationResponse.Error
			val conversation = root.toChatConversationItemOrNull()
				?: return@safeApiCall ChatsGetConversationResponse.Error

			ChatsGetConversationResponse.Success(conversation = conversation)
		}
	}

	override suspend fun setConversationPinned(
		conversationId: Long,
		isPinned: Boolean
	): ChatsGetConversationResponse {
		return safeApiCall(errorResponse = ChatsGetConversationResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetConversationResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsGetConversationResponse.Error
			}

			val response = if (isPinned) {
				httpClient.post("$baseUrl/chats/conversations/$conversationId/pin") {
					addAuthTokenHeader(authToken)
					contentType(ContentType.Application.Json)
					setBody(JsonSerializer.encodeToString(PinConversationRequest(pinned = true)))
				}
			} else {
				httpClient.delete("$baseUrl/chats/conversations/$conversationId/pin") {
					addAuthTokenHeader(authToken)
				}
			}
			logger.logKtorRequest("ChatsApiImpl.setConversationPinned", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetConversationResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.setConversationPinned", response.status, bodyText)
				return@safeApiCall ChatsGetConversationResponse.Error
			}
			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetConversationResponse.Error
			val conversation = root.toChatConversationItemOrNull()
				?: return@safeApiCall ChatsGetConversationResponse.Error
			ChatsGetConversationResponse.Success(conversation)
		}
	}

	override suspend fun getConversationInitial(
		conversationId: Long,
		messageLimit: Int,
		includePins: Boolean
	): ChatsGetConversationInitialResponse {
		return safeApiCall(errorResponse = ChatsGetConversationInitialResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetConversationInitialResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsGetConversationInitialResponse.Error
			}

			val response = httpClient.get("$baseUrl/chats/conversations/$conversationId/initial") {
				addAuthTokenHeader(authToken)
				url {
					parameters.append("message_limit", messageLimit.coerceIn(1, MAX_CHAT_MESSAGES_LIMIT).toString())
					parameters.append("include_pins", includePins.toString())
				}
			}

			logger.logKtorRequest("ChatsApiImpl.getConversationInitial", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetConversationInitialResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getConversationInitial", response.status, bodyText)
				return@safeApiCall ChatsGetConversationInitialResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetConversationInitialResponse.Error
			val conversationObject = root.objectOrNull("conversation") ?: root
			val conversation = conversationObject.toChatConversationItemOrNull()
				?: return@safeApiCall ChatsGetConversationInitialResponse.Error
			val messagesObject = root.objectOrNull("messages") ?: root
			val items = messagesObject.jsonArrayOrNull("items")
				?.mapNotNull { item -> (item as? JsonObject)?.toChatMessageItemOrNull() }
				.orEmpty()
			val pinnedItems = root.jsonArrayOrNull("pinned_messages")
				?.mapNotNull { item -> (item as? JsonObject)?.toChatMessageItemOrNull() }
				.orEmpty()
			val peerLastReadMessageId = messagesObject.longOrNullFlexible("peer_last_read_message_id")
				?: root.longOrNullFlexible("peer_last_read_message_id")
			val readStateVersion = root.longOrNullFlexible("read_state_version")
				?: root.objectOrNull("read_state")?.longOrNullFlexible("read_state_version")
				?: 0L

			ChatsGetConversationInitialResponse.Success(
				conversation = conversation,
				items = items,
				nextBeforeId = messagesObject.longOrNullFlexible("next_before_id")
					?: root.longOrNullFlexible("next_before_id"),
				peerLastReadMessageId = peerLastReadMessageId,
				readStateVersion = readStateVersion,
				pinnedItems = pinnedItems,
			)
		}
	}

	override suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): ChatsGetMessagesResponse {
		return safeApiCall(errorResponse = ChatsGetMessagesResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetMessagesResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsGetMessagesResponse.Error
			}

			val response = httpClient.get("$baseUrl/chats/conversations/$conversationId/messages") {
				addAuthTokenHeader(authToken)
				url {
					parameters.append("limit", limit.coerceIn(1, MAX_CHAT_MESSAGES_LIMIT).toString())
					if (beforeId != null && beforeId > 0L) {
						parameters.append("before_id", beforeId.toString())
					}
				}
			}

			logger.logKtorRequest("ChatsApiImpl.getMessages", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetMessagesResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getMessages", response.status, bodyText)
				return@safeApiCall ChatsGetMessagesResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetMessagesResponse.Error
			val items = root.jsonArrayOrNull("items")
				?.mapNotNull { item -> (item as? JsonObject)?.toChatMessageItemOrNull() }
				.orEmpty()
			val nextBeforeId = root.longOrNullFlexible("next_before_id")
			val peerLastReadMessageId = root.longOrNullFlexible("peer_last_read_message_id")

			ChatsGetMessagesResponse.Success(
				items = items,
				nextBeforeId = nextBeforeId,
				peerLastReadMessageId = peerLastReadMessageId
			)
		}
	}

	override suspend fun getMessagesAround(
		conversationId: Long,
		anchorId: Long,
		olderLimit: Int,
		newerLimit: Int
	): ChatsGetMessagesAroundResponse {
		return safeApiCall(errorResponse = ChatsGetMessagesAroundResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetMessagesAroundResponse.Error
			if (conversationId <= 0L || anchorId <= 0L) {
				return@safeApiCall ChatsGetMessagesAroundResponse.Error
			}

			val response = httpClient.get("$baseUrl/chats/conversations/$conversationId/messages/around") {
				addAuthTokenHeader(authToken)
				url {
					parameters.append("anchor_id", anchorId.toString())
					parameters.append("older", olderLimit.coerceAtLeast(0).coerceAtMost(MAX_CHAT_MESSAGES_LIMIT).toString())
					parameters.append("newer", newerLimit.coerceAtLeast(0).coerceAtMost(MAX_CHAT_MESSAGES_LIMIT).toString())
				}
			}

			logger.logKtorRequest("ChatsApiImpl.getMessagesAround", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetMessagesAroundResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getMessagesAround", response.status, bodyText)
				return@safeApiCall ChatsGetMessagesAroundResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetMessagesAroundResponse.Error
			val items = root.jsonArrayOrNull("items")
				?.mapNotNull { item -> (item as? JsonObject)?.toChatMessageItemOrNull() }
				.orEmpty()
			val anchor = root.longOrNullFlexible("anchor_id") ?: return@safeApiCall ChatsGetMessagesAroundResponse.Error
			val anchorIndex = root.longOrNullFlexible("anchor_index")?.toInt() ?: -1
			val hasOlder = root.booleanOrNullFlexible("has_older") ?: false
			val hasNewer = root.booleanOrNullFlexible("has_newer") ?: false
			val peerLastReadMessageId = root.longOrNullFlexible("peer_last_read_message_id")

			ChatsGetMessagesAroundResponse.Success(
				items = items,
				anchorId = anchor,
				anchorIndex = anchorIndex,
				hasOlder = hasOlder,
				hasNewer = hasNewer,
				peerLastReadMessageId = peerLastReadMessageId
			)
		}
	}

	override suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?
	): ChatsSendMessageResponse {
		return safeApiCall(errorResponse = ChatsSendMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			if (conversationId <= 0L || text.isBlank()) {
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						SendChatMessageRequest(
							text = text.trim(),
							clientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							replyToMessageId = replyToMessageId?.takeIf { it > 0L }?.toString()
						)
					)
				)
			}

			logger.logKtorRequest("ChatsApiImpl.sendMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsSendMessageResponse.NotFound
			}
			if (response.status == HttpStatusCode.Conflict) {
				val existingMessage = runCatching {
					JsonSerializer.parseToJsonElement(bodyText).jsonObject
						.objectOrNull("message")
						?.toChatMessageItemOrNull()
				}.getOrNull()
				return@safeApiCall ChatsSendMessageResponse.Conflict(existingMessage)
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.sendMessage", response.status, bodyText)
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val deduped = root.booleanOrNullFlexible("deduped") ?: false

			ChatsSendMessageResponse.Success(
				message = message,
				deduped = deduped
			)
		}
	}

	override suspend fun getMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	): ChatsGetMessageByClientMessageIdResponse {
		return safeApiCall(errorResponse = ChatsGetMessageByClientMessageIdResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetMessageByClientMessageIdResponse.Error
			val normalizedClientMessageId = clientMessageId.trim()
			if (conversationId <= 0L || normalizedClientMessageId.isEmpty()) {
				return@safeApiCall ChatsGetMessageByClientMessageIdResponse.Error
			}

			val response = httpClient.get(
				"$baseUrl/chats/conversations/$conversationId/messages/by-client-id/$normalizedClientMessageId"
			) {
				addAuthTokenHeader(authToken)
			}
			logger.logKtorRequest("ChatsApiImpl.getMessageByClientMessageId", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetMessageByClientMessageIdResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getMessageByClientMessageId", response.status, bodyText)
				return@safeApiCall ChatsGetMessageByClientMessageIdResponse.Error
			}
			val message = runCatching {
				JsonSerializer.parseToJsonElement(bodyText).jsonObject
					.objectOrNull("message")
					?.toChatMessageItemOrNull()
			}.getOrNull() ?: return@safeApiCall ChatsGetMessageByClientMessageIdResponse.Error
			ChatsGetMessageByClientMessageIdResponse.Success(message)
		}
	}

	override suspend fun sendVideoCircleMessage(
		conversationId: Long,
		clientMessageId: String?,
		mediaUrl: String,
		mediaObjectKey: String,
		mediaSizeBytes: Long,
		mediaDurationMs: Long,
		mediaWidth: Int,
		mediaHeight: Int,
		thumbnailUrl: String?,
		replyToMessageId: Long?
	): ChatsSendMessageResponse {
		return safeApiCall(errorResponse = ChatsSendMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			if (
				conversationId <= 0L ||
				mediaUrl.isBlank() ||
				mediaObjectKey.isBlank() ||
				mediaSizeBytes <= 0L ||
				mediaDurationMs <= 0L
			) {
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						SendVideoCircleMessageRequest(
							text = "Видеосообщение",
							contentType = "video_circle",
							media = SendMediaPayload(
								url = mediaUrl.trim(),
								objectKey = mediaObjectKey.trim(),
								mimeType = "video/mp4",
								sizeBytes = mediaSizeBytes,
								durationMs = mediaDurationMs,
								width = mediaWidth,
								height = mediaHeight,
								thumbnailUrl = thumbnailUrl?.trim()?.takeIf(String::isNotEmpty),
							),
							mediaUrl = mediaUrl.trim(),
							mediaObjectKey = mediaObjectKey.trim(),
							mediaMimeType = "video/mp4",
							mediaSizeBytes = mediaSizeBytes,
							mediaDurationMs = mediaDurationMs,
							mediaWidth = mediaWidth,
							mediaHeight = mediaHeight,
							mediaThumbnailUrl = thumbnailUrl?.trim()?.takeIf(String::isNotEmpty),
							clientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							idempotencyKey = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							replyToMessageId = replyToMessageId?.takeIf { it > 0L }?.toString(),
						)
					)
				)
			}

			logger.logKtorRequest("ChatsApiImpl.sendVideoCircleMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsSendMessageResponse.NotFound
			}
			if (response.status == HttpStatusCode.Conflict) {
				val existingMessage = runCatching {
					JsonSerializer.parseToJsonElement(bodyText).jsonObject
						.objectOrNull("message")
						?.toChatMessageItemOrNull()
				}.getOrNull()
				return@safeApiCall ChatsSendMessageResponse.Conflict(existingMessage)
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.sendVideoCircleMessage", response.status, bodyText)
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val deduped = root.booleanOrNullFlexible("deduped") ?: false

			ChatsSendMessageResponse.Success(
				message = message,
				deduped = deduped,
			)
		}
	}

	override suspend fun sendVoiceMessage(
		conversationId: Long,
		clientMessageId: String?,
		mediaUrl: String,
		mediaObjectKey: String,
		mediaSizeBytes: Long,
		mediaDurationMs: Long,
		waveform: List<Int>,
		replyToMessageId: Long?
	): ChatsSendMessageResponse {
		return safeApiCall(errorResponse = ChatsSendMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			if (
				conversationId <= 0L ||
				mediaUrl.isBlank() ||
				mediaObjectKey.isBlank() ||
				mediaSizeBytes <= 0L ||
				mediaDurationMs <= 0L
			) {
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val mimeType = if (mediaUrl.endsWith(".m4a", ignoreCase = true) || mediaUrl.endsWith(".mp4", ignoreCase = true)) {
				"audio/mp4"
			} else {
				"audio/ogg"
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						SendVoiceMessageRequest(
							text = "Голосовое сообщение",
							contentType = "voice",
							media = SendMediaPayload(
								url = mediaUrl.trim(),
								objectKey = mediaObjectKey.trim(),
								mimeType = mimeType,
								sizeBytes = mediaSizeBytes,
								durationMs = mediaDurationMs,
								waveform = waveform,
							),
							mediaUrl = mediaUrl.trim(),
							mediaObjectKey = mediaObjectKey.trim(),
							mediaMimeType = mimeType,
							mediaSizeBytes = mediaSizeBytes,
							mediaDurationMs = mediaDurationMs,
							clientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							idempotencyKey = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							replyToMessageId = replyToMessageId?.takeIf { it > 0L }?.toString(),
						)
					)
				)
			}

			logger.logKtorRequest("ChatsApiImpl.sendVoiceMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsSendMessageResponse.NotFound
			}
			if (response.status == HttpStatusCode.Conflict) {
				val existingMessage = runCatching {
					JsonSerializer.parseToJsonElement(bodyText).jsonObject
						.objectOrNull("message")
						?.toChatMessageItemOrNull()
				}.getOrNull()
				return@safeApiCall ChatsSendMessageResponse.Conflict(existingMessage)
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.sendVoiceMessage", response.status, bodyText)
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val deduped = root.booleanOrNullFlexible("deduped") ?: false

			ChatsSendMessageResponse.Success(
				message = message,
				deduped = deduped,
			)
		}
	}

	override suspend fun sendImageMessage(
		conversationId: Long,
		clientMessageId: String?,
		mediaUrl: String,
		mediaObjectKey: String,
		mediaSizeBytes: Long,
		mediaWidth: Int,
		mediaHeight: Int,
		caption: String?,
		groupId: String?,
		position: Int,
		replyToMessageId: Long?,
		thumbnailUrl: String?
	): ChatsSendMessageResponse {
		return safeApiCall(errorResponse = ChatsSendMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			if (
				conversationId <= 0L ||
				mediaUrl.isBlank() ||
				mediaObjectKey.isBlank() ||
				mediaSizeBytes <= 0L
			) {
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val mimeType = if (mediaUrl.endsWith(".webp", ignoreCase = true)) {
				"image/webp"
			} else if (mediaUrl.endsWith(".png", ignoreCase = true)) {
				"image/png"
			} else {
				"image/jpeg"
			}

			val textToSend = caption?.trim()?.takeIf { it.isNotEmpty() } ?: "Фотография"

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						SendImageMessageRequest(
							text = textToSend,
							clientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							idempotencyKey = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
							contentType = "image",
							groupId = groupId?.trim()?.takeIf(String::isNotEmpty),
							position = position,
							media = SendMediaPayload(
								url = mediaUrl.trim(),
								objectKey = mediaObjectKey.trim(),
								mimeType = mimeType,
								sizeBytes = mediaSizeBytes,
								width = mediaWidth,
								height = mediaHeight,
								thumbnailUrl = thumbnailUrl?.trim()?.takeIf(String::isNotEmpty),
								groupId = groupId?.trim()?.takeIf(String::isNotEmpty),
								position = position,
							),
							mediaUrl = mediaUrl.trim(),
							mediaObjectKey = mediaObjectKey.trim(),
							mediaMimeType = mimeType,
							mediaSizeBytes = mediaSizeBytes,
							mediaWidth = mediaWidth,
							mediaHeight = mediaHeight,
							replyToMessageId = replyToMessageId?.takeIf { it > 0L }?.toString(),
						)
					)
				)
			}

			logger.logKtorRequest("ChatsApiImpl.sendImageMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsSendMessageResponse.NotFound
			}
			if (response.status == HttpStatusCode.Conflict) {
				val existingMessage = runCatching {
					JsonSerializer.parseToJsonElement(bodyText).jsonObject
						.objectOrNull("message")
						?.toChatMessageItemOrNull()
				}.getOrNull()
				return@safeApiCall ChatsSendMessageResponse.Conflict(existingMessage)
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.sendImageMessage", response.status, bodyText)
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			val deduped = root.booleanOrNullFlexible("deduped") ?: false

			ChatsSendMessageResponse.Success(
				message = message,
				deduped = deduped,
			)
		}
	}

	override suspend fun sendAlbumMessage(
		conversationId: Long,
		items: List<ChatAlbumItemPayload>,
		caption: String?,
		replyToMessageId: Long?,
		idempotencyKey: String,
	): ChatsSendAlbumMessageResponse {
		return safeApiCall(errorResponse = ChatsSendAlbumMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsSendAlbumMessageResponse.Error
			if (conversationId <= 0L || items.isEmpty() || idempotencyKey.isBlank()) {
				return@safeApiCall ChatsSendAlbumMessageResponse.Error
			}

			val requestPayload = SendAlbumMessageRequest(
				items = items.mapIndexed { index, item ->
					val mimeType = if (item.mediaUrl.endsWith(".webp", ignoreCase = true)) {
						"image/webp"
					} else if (item.mediaUrl.endsWith(".png", ignoreCase = true)) {
						"image/png"
					} else {
						"image/jpeg"
					}
					SendAlbumItemPayload(
						media = SendMediaPayload(
							url = item.mediaUrl.trim(),
							objectKey = item.mediaObjectKey.trim(),
							mimeType = mimeType,
							sizeBytes = item.mediaSizeBytes,
							width = item.mediaWidth,
							height = item.mediaHeight,
							thumbnailUrl = item.mediaThumbnailUrl?.trim()?.takeIf(String::isNotEmpty),
							position = index,
						),
						clientMessageId = item.clientMessageId?.trim()?.takeIf(String::isNotEmpty),
					)
				},
				caption = caption?.trim()?.takeIf(String::isNotEmpty),
				replyToMessageId = replyToMessageId?.takeIf { it > 0L },
				idempotencyKey = idempotencyKey.trim(),
			)

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages/album") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(requestPayload))
			}

			logger.logKtorRequest("ChatsApiImpl.sendAlbumMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsSendAlbumMessageResponse.NotFound
			}
			if (response.status == HttpStatusCode.Conflict) {
				val existingMessages = runCatching {
					JsonSerializer.parseToJsonElement(bodyText).jsonObject
						.jsonArrayOrNull("messages")
						?.mapNotNull { (it as? JsonObject)?.toChatMessageItemOrNull() }
				}.getOrNull().orEmpty()
				return@safeApiCall ChatsSendAlbumMessageResponse.Conflict(existingMessages)
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.sendAlbumMessage", response.status, bodyText)
				return@safeApiCall ChatsSendAlbumMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsSendAlbumMessageResponse.Error
			val messagesArray = root.jsonArrayOrNull("messages")
				?: return@safeApiCall ChatsSendAlbumMessageResponse.Error
			val messages = messagesArray.mapNotNull { (it as? JsonObject)?.toChatMessageItemOrNull() }
			val deduped = root.booleanOrNullFlexible("deduped") ?: false

			ChatsSendAlbumMessageResponse.Success(
				messages = messages,
				deduped = deduped,
			)
		}
	}

	override suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	): ChatsDeleteMessageResponse {
		return safeApiCall(errorResponse = ChatsDeleteMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsDeleteMessageResponse.Error
			if (conversationId <= 0L || messageId <= 0L) {
				return@safeApiCall ChatsDeleteMessageResponse.Error
			}

			val response = httpClient.delete("$baseUrl/chats/conversations/$conversationId/messages/$messageId") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ChatsApiImpl.deleteMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.Forbidden) {
				return@safeApiCall ChatsDeleteMessageResponse.Forbidden
			}
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsDeleteMessageResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.deleteMessage", response.status, bodyText)
				return@safeApiCall ChatsDeleteMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsDeleteMessageResponse.Error
			val deletedMessageId = root.longOrNullFlexible("deleted_message_id") ?: messageId
			val readState = root.objectOrNull("read_state")?.toChatReadStateItemOrNull()
			ChatsDeleteMessageResponse.Success(
				deletedMessageId = deletedMessageId,
				readState = readState
			)
		}
	}

	override suspend fun deleteMessagesBatch(
		conversationId: Long,
		messageIds: List<Long>
	): me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse {
		return safeApiCall(errorResponse = me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Error
			val validIds = messageIds.filter { it > 0L }
			if (conversationId <= 0L || validIds.isEmpty()) {
				return@safeApiCall me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Error
			}

			val payload = kotlinx.serialization.json.buildJsonObject {
				put("message_ids", kotlinx.serialization.json.JsonArray(validIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages/delete-batch") {
				addAuthTokenHeader(authToken)
				contentType(io.ktor.http.ContentType.Application.Json)
				setBody(payload.toString())
			}

			logger.logKtorRequest("ChatsApiImpl.deleteMessagesBatch", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.Forbidden) {
				return@safeApiCall me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Forbidden
			}
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.deleteMessagesBatch", response.status, bodyText)
				return@safeApiCall me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Error
			val deletedIds = root["deleted_message_ids"]?.let { array ->
				if (array is JsonArray) {
					array.mapNotNull { elem -> (elem as? JsonPrimitive)?.content?.toLongOrNull() }
				} else null
			} ?: validIds
			val readState = root.objectOrNull("read_state")?.toChatReadStateItemOrNull()
			me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse.Success(
				deletedMessageIds = deletedIds,
				readState = readState
			)
		}
	}

	override suspend fun updateMessage(
		conversationId: Long,
		messageId: Long,
		text: String
	): ChatsUpdateMessageResponse {
		return safeApiCall(errorResponse = ChatsUpdateMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsUpdateMessageResponse.Error
			if (conversationId <= 0L || messageId <= 0L || text.isBlank()) {
				return@safeApiCall ChatsUpdateMessageResponse.Error
			}

			val response = httpClient.patch("$baseUrl/chats/conversations/$conversationId/messages/$messageId") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(UpdateChatMessageRequest(text = text.trim())))
			}

			logger.logKtorRequest("ChatsApiImpl.updateMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.Forbidden) {
				return@safeApiCall ChatsUpdateMessageResponse.Forbidden
			}
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsUpdateMessageResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.updateMessage", response.status, bodyText)
				return@safeApiCall ChatsUpdateMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsUpdateMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsUpdateMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsUpdateMessageResponse.Error
			ChatsUpdateMessageResponse.Success(message = message)
		}
	}

	override suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): ChatsGetPinnedMessagesResponse {
		return safeApiCall(errorResponse = ChatsGetPinnedMessagesResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsGetPinnedMessagesResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsGetPinnedMessagesResponse.Error
			}

			val response = httpClient.get("$baseUrl/chats/conversations/$conversationId/pins") {
				addAuthTokenHeader(authToken)
				url {
					parameters.append("limit", limit.coerceIn(1, MAX_CHAT_PINS_LIMIT).toString())
				}
			}

			logger.logKtorRequest("ChatsApiImpl.getPinnedMessages", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsGetPinnedMessagesResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.getPinnedMessages", response.status, bodyText)
				return@safeApiCall ChatsGetPinnedMessagesResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsGetPinnedMessagesResponse.Error
			val items = root.jsonArrayOrNull("items")
				?.mapNotNull { item -> (item as? JsonObject)?.toChatMessageItemOrNull() }
				.orEmpty()
			ChatsGetPinnedMessagesResponse.Success(items = items)
		}
	}

	override suspend fun pinMessage(conversationId: Long, messageId: Long): ChatsPinMessageResponse {
		return safeApiCall(errorResponse = ChatsPinMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsPinMessageResponse.Error
			if (conversationId <= 0L || messageId <= 0L) {
				return@safeApiCall ChatsPinMessageResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages/$messageId/pin") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ChatsApiImpl.pinMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsPinMessageResponse.NotFound
			}
			if (response.status == HttpStatusCode.Conflict) {
				return@safeApiCall ChatsPinMessageResponse.Conflict
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.pinMessage", response.status, bodyText)
				return@safeApiCall ChatsPinMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsPinMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsPinMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsPinMessageResponse.Error
			val pinned = root.booleanOrNullFlexible("pinned") ?: message.isPinned
			ChatsPinMessageResponse.Success(
				message = message,
				pinned = pinned
			)
		}
	}

	override suspend fun unpinMessage(conversationId: Long, messageId: Long): ChatsUnpinMessageResponse {
		return safeApiCall(errorResponse = ChatsUnpinMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsUnpinMessageResponse.Error
			if (conversationId <= 0L || messageId <= 0L) {
				return@safeApiCall ChatsUnpinMessageResponse.Error
			}

			val response = httpClient.delete("$baseUrl/chats/conversations/$conversationId/messages/$messageId/pin") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ChatsApiImpl.unpinMessage", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsUnpinMessageResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.unpinMessage", response.status, bodyText)
				return@safeApiCall ChatsUnpinMessageResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsUnpinMessageResponse.Error
			val messageObject = root.objectOrNull("message")
				?: return@safeApiCall ChatsUnpinMessageResponse.Error
			val message = messageObject.toChatMessageItemOrNull()
				?: return@safeApiCall ChatsUnpinMessageResponse.Error
			val pinned = root.booleanOrNullFlexible("pinned") ?: message.isPinned
			ChatsUnpinMessageResponse.Success(
				message = message,
				pinned = pinned
			)
		}
	}

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long): ChatsMarkReadUpToResponse {
		return safeApiCall(errorResponse = ChatsMarkReadUpToResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsMarkReadUpToResponse.Error
			if (conversationId <= 0L || messageId <= 0L) {
				return@safeApiCall ChatsMarkReadUpToResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/read-up-to") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(MarkReadUpToRequest(messageId = messageId.toString())))
			}

			logger.logKtorRequest("ChatsApiImpl.markReadUpTo", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsMarkReadUpToResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.markReadUpTo", response.status, bodyText)
				return@safeApiCall ChatsMarkReadUpToResponse.Error
			}

			val root = runCatching { JsonSerializer.parseToJsonElement(bodyText).jsonObject }.getOrNull()
				?: return@safeApiCall ChatsMarkReadUpToResponse.Error
			val readStateObject = root.objectOrNull("read_state")
				?: return@safeApiCall ChatsMarkReadUpToResponse.Error
			val readState = readStateObject.toChatReadStateItemOrNull()
				?: return@safeApiCall ChatsMarkReadUpToResponse.Error

			ChatsMarkReadUpToResponse.Success(readState = readState)
		}
	}

	override suspend fun deleteConversation(
		conversationId: Long,
		forEveryone: Boolean
	): ChatsDeleteConversationResponse {
		return safeApiCall(errorResponse = ChatsDeleteConversationResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsDeleteConversationResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsDeleteConversationResponse.Error
			}

			val response = httpClient.delete("$baseUrl/chats/conversations/$conversationId?for_everyone=$forEveryone") {
				addAuthTokenHeader(authToken)
			}

			logger.logKtorRequest("ChatsApiImpl.deleteConversation", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsDeleteConversationResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.deleteConversation", response.status, bodyText)
				return@safeApiCall ChatsDeleteConversationResponse.Error
			}
			ChatsDeleteConversationResponse.Success
		}
	}

	override suspend fun sendTyping(
		conversationId: Long,
		isTyping: Boolean,
		action: String
	): ChatsTypingResponse {
		return safeApiCall(errorResponse = ChatsTypingResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsTypingResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsTypingResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/typing") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(SendTypingRequest(typing = isTyping, action = action)))
			}

			logger.logKtorRequest("ChatsApiImpl.sendTyping", response.call.request)
			val bodyText = response.bodyAsText()
			if (response.status == HttpStatusCode.NotFound) {
				return@safeApiCall ChatsTypingResponse.NotFound
			}
			if (!response.status.isSuccess()) {
				logger.logFailureResponse("ChatsApiImpl.sendTyping", response.status, bodyText)
				return@safeApiCall ChatsTypingResponse.Error
			}
			ChatsTypingResponse.Success
		}
	}
}

private fun JsonObject.toChatConversationItemOrNull(): ChatConversationItem? {
	val id = longOrNullFlexible("id") ?: return null
	val kind = stringOrNull("kind") ?: "direct"
	val peer = objectOrNull("peer")?.toChatUserItemOrNull() ?: return null
	val lastMessage = objectOrNull("last_message")?.toChatMessageItemOrNull()
	val unreadCount = longOrNullFlexible("unread_count")
		?.coerceAtLeast(0L)
		?.coerceAtMost(Int.MAX_VALUE.toLong())
		?.toInt()
		?: 0
	val lastReadMessageId = longOrNullFlexible("last_read_message_id") ?: 0L
	val peerLastReadMessageId = longOrNullFlexible("peer_last_read_message_id")
	val isPinned = booleanOrNullFlexible("is_pinned")
		?: booleanOrNullFlexible("pinned")
		?: false
	val pinnedAt = longOrNullFlexible("pinned_at")
	val createdAt = longOrNullFlexible("created_at") ?: 0L
	val updatedAt = longOrNullFlexible("updated_at") ?: 0L
	return ChatConversationItem(
		id = id,
		kind = kind,
		peer = peer,
		lastMessage = lastMessage,
		unreadCount = unreadCount,
		lastReadMessageId = lastReadMessageId,
		peerLastReadMessageId = peerLastReadMessageId,
		isPinned = isPinned,
		pinnedAt = pinnedAt,
		createdAt = createdAt,
		updatedAt = updatedAt
	)
}

private fun JsonObject.toChatReadStateItemOrNull(): ChatReadStateItem? {
	val conversationId = longOrNullFlexible("conversation_id") ?: return null
	val lastReadMessageId = longOrNullFlexible("last_read_message_id") ?: 0L
	val unreadCount = longOrNullFlexible("unread_count")
		?.coerceAtLeast(0L)
		?.coerceAtMost(Int.MAX_VALUE.toLong())
		?.toInt()
		?: 0
	val firstUnreadId = longOrNullFlexible("first_unread_id")
	val maxMessageId = longOrNullFlexible("max_message_id") ?: 0L
	val readStateVersion = longOrNullFlexible("read_state_version") ?: 0L
	return ChatReadStateItem(
		conversationId = conversationId,
		lastReadMessageId = lastReadMessageId,
		unreadCount = unreadCount,
		firstUnreadId = firstUnreadId,
		maxMessageId = maxMessageId,
		readStateVersion = readStateVersion,
	)
}
