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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
import me.floow.domain.api.models.ChatsGetConversationsResponse
import me.floow.domain.api.models.ChatsGetMessagesResponse
import me.floow.domain.api.models.ChatsGetMessagesAroundResponse
import me.floow.domain.api.models.ChatsGetOrCreateDirectResponse
import me.floow.domain.api.models.ChatsDeleteMessageResponse
import me.floow.domain.api.models.ChatsGetPinnedMessagesResponse
import me.floow.domain.api.models.ChatsMarkReadUpToResponse
import me.floow.domain.api.models.ChatsPinMessageResponse
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
	@SerialName("content_type") val contentType: String? = null,
	val media: SendChatMessageMediaRequest? = null,
	@SerialName("idempotency_key") val clientMessageId: String? = null,
	@SerialName("reply_to_message_id") val replyToMessageId: String? = null
)

@Serializable
private data class SendChatMessageMediaRequest(
	@SerialName("url") val url: String,
	@SerialName("object_key") val objectKey: String,
	@SerialName("mime_type") val mimeType: String,
	@SerialName("size_bytes") val sizeBytes: Long,
	@SerialName("duration_ms") val durationMs: Long,
	@SerialName("width") val width: Int? = null,
	@SerialName("height") val height: Int? = null,
)

@Serializable
private data class MarkReadUpToRequest(
	@SerialName("message_id") val messageId: String
)

@Serializable
private data class SendTypingRequest(
	val typing: Boolean
)

@Serializable
private data class UpdateChatMessageRequest(
	val text: String
)

private const val MAX_CHAT_CONVERSATIONS_LIMIT = 100
private const val MAX_CHAT_MESSAGES_LIMIT = 100
private const val MAX_CHAT_PINS_LIMIT = 50

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
				return@safeApiCall ChatsSendMessageResponse.Conflict
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

	override suspend fun sendVideoCircleMessage(
		conversationId: Long,
		clientMessageId: String?,
		replyToMessageId: Long?,
		mediaUrl: String,
		objectKey: String,
		mimeType: String,
		sizeBytes: Long,
		durationMs: Long,
		width: Int?,
		height: Int?,
	): ChatsSendMessageResponse {
		return safeApiCall(errorResponse = ChatsSendMessageResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsSendMessageResponse.Error
			if (
				conversationId <= 0L ||
				mediaUrl.isBlank() ||
				objectKey.isBlank() ||
				mimeType.isBlank() ||
				sizeBytes <= 0L ||
				durationMs <= 0L
			) {
				return@safeApiCall ChatsSendMessageResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/messages") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(
					JsonSerializer.encodeToString(
						SendChatMessageRequest(
							text = "Видеосообщение",
							contentType = "video_circle",
							media = SendChatMessageMediaRequest(
								url = mediaUrl.trim(),
								objectKey = objectKey.trim(),
								mimeType = mimeType.trim(),
								sizeBytes = sizeBytes,
								durationMs = durationMs,
								width = width?.takeIf { it > 0 },
								height = height?.takeIf { it > 0 },
							),
							clientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
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
				return@safeApiCall ChatsSendMessageResponse.Conflict
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

	override suspend fun sendTyping(conversationId: Long, isTyping: Boolean): ChatsTypingResponse {
		return safeApiCall(errorResponse = ChatsTypingResponse.Error) {
			val authToken = authenticationManager.getAuthTokenOrNull()
				?: return@safeApiCall ChatsTypingResponse.Error
			if (conversationId <= 0L) {
				return@safeApiCall ChatsTypingResponse.Error
			}

			val response = httpClient.post("$baseUrl/chats/conversations/$conversationId/typing") {
				addAuthTokenHeader(authToken)
				contentType(ContentType.Application.Json)
				setBody(JsonSerializer.encodeToString(SendTypingRequest(typing = isTyping)))
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
	return ChatReadStateItem(
		conversationId = conversationId,
		lastReadMessageId = lastReadMessageId,
		unreadCount = unreadCount,
		firstUnreadId = firstUnreadId,
		maxMessageId = maxMessageId
	)
}
