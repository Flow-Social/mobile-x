package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.floow.shared.chats.model.ChatMessageItemModel
import kotlinx.coroutines.flow.collectLatest

internal class ChatRealtimeSessionManager(
	private val transport: ChatRealtimeTransport,
	private val authProvider: RealtimeAuthProvider,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ChatRealtimeContract {
	override val connectionState: StateFlow<RealtimeConnectionState>
		get() = _connectionState

	private val _connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Idle)
	private val sessionsMutex = Mutex()
	private val sessions = linkedMapOf<Long, ConversationSession>()

	override fun observeConversation(conversationId: Long, afterSeq: Long): Flow<ChatRealtimeEvent> = callbackFlow {
		if (conversationId <= 0L) {
			close()
			return@callbackFlow
		}
		val session = sessionsMutex.withLock {
			sessions.getOrPut(conversationId) {
				ConversationSession(
					conversationId = conversationId,
					nextAfterSeq = afterSeq.coerceAtLeast(0L),
				)
			}.also { existing ->
				existing.subscribers += 1
				if (existing.subscribers == 1) {
					existing.nextAfterSeq = afterSeq.coerceAtLeast(0L)
					existing.loopJob = scope.launch {
						runConversationLoop(existing)
					}
				}
			}
		}
		val collector = launch {
			session.events.collectLatest { trySend(it) }
		}
		awaitClose {
			collector.cancel()
			scope.launch {
				sessionsMutex.withLock {
					val current = sessions[conversationId] ?: return@withLock
					current.subscribers = (current.subscribers - 1).coerceAtLeast(0)
					if (current.subscribers == 0) {
						current.loopJob?.cancel()
						current.activeHandle?.let(transport::close)
						sessions.remove(conversationId)
						_connectionState.value = RealtimeConnectionState.Idle
					}
				}
			}
		}
	}

	override suspend fun setTyping(conversationId: Long, isTyping: Boolean): Result<Unit> {
		val handle = sessionsMutex.withLock { sessions[conversationId]?.activeHandle }
			?: return Result.failure(IllegalStateException("Chats realtime socket is not active"))
		return transport.send(
			handle = handle,
			payloadJson = json.encodeToString(
				WasmTypingCommand(
					type = "typing",
					typing = isTyping,
				)
			),
		)
	}

	private suspend fun runConversationLoop(session: ConversationSession) {
		var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
		var reconnectAttempt = 0
		while (currentCoroutineContext().isActive) {
			val auth = authProvider.resolveChatAuth().getOrElse { error ->
				_connectionState.value = RealtimeConnectionState.AuthFailed(
					endpoint = CHAT_ENDPOINT,
					authMode = UNKNOWN_AUTH_MODE,
					conversationId = session.conversationId,
					message = error.message ?: "auth resolution failed",
				)
				delay(reconnectDelayMs)
				reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
				reconnectAttempt += 1
				continue
			}

			_connectionState.value = if (reconnectAttempt == 0) {
				RealtimeConnectionState.Connecting(
					endpoint = CHAT_ENDPOINT,
					authMode = auth.mode,
					conversationId = session.conversationId,
				)
			} else {
				RealtimeConnectionState.Reconnecting(
					endpoint = CHAT_ENDPOINT,
					authMode = auth.mode,
					conversationId = session.conversationId,
					attempt = reconnectAttempt,
					backoffMs = reconnectDelayMs,
				)
			}

			val closedSignal = CompletableDeferred<RealtimeSocketLifecycleInfo>()
			val openResult = transport.open(
				params = ChatRealtimeOpenParams(
					conversationId = session.conversationId,
					afterSeq = session.nextAfterSeq,
					replayLimit = DEFAULT_REPLAY_LIMIT,
					auth = auth,
				),
				listener = object : ChatRealtimeTransportListener {
					override fun onOpen() {
						reconnectAttempt = 0
						reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
						_connectionState.value = RealtimeConnectionState.Open(
							endpoint = CHAT_ENDPOINT,
							authMode = auth.mode,
							conversationId = session.conversationId,
						)
					}

					override fun onEvent(raw: String) {
						parseRealtimeEvent(raw)?.let { parsed ->
							if (parsed.seq > session.nextAfterSeq) {
								session.nextAfterSeq = parsed.seq
							}
							if (parsed.event is ChatRealtimeEvent.ResyncRequired) {
								_connectionState.value = RealtimeConnectionState.Resyncing(
									endpoint = CHAT_ENDPOINT,
									authMode = auth.mode,
									conversationId = session.conversationId,
								)
							}
							parsed.ackMessageId?.takeIf { it > 0L }?.let { messageId ->
								scope.launch {
									transport.send(
										handle = session.activeHandle ?: return@launch,
										payloadJson = json.encodeToString(
											WasmPushAckCommand(
												type = "push_ack",
												messageId = messageId,
											)
										),
									)
								}
							}
							session.events.tryEmit(parsed.event)
						}
					}

					override fun onClosed(info: RealtimeSocketLifecycleInfo) {
						if (!closedSignal.isCompleted) {
							closedSignal.complete(info)
						}
					}

					override fun onError(info: RealtimeSocketLifecycleInfo) {
						if (!closedSignal.isCompleted) {
							closedSignal.complete(info)
						}
					}
				},
			)

			val handle = openResult.getOrElse { error ->
				val info = RealtimeSocketLifecycleInfo(
					endpoint = CHAT_ENDPOINT,
					authMode = auth.mode,
					conversationId = session.conversationId,
					message = error.message ?: "socket open failed",
				)
				_connectionState.value = info.toConnectionState()
				delay(reconnectDelayMs)
				reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
				reconnectAttempt += 1
				continue
			}

			session.activeHandle = handle
			try {
				val closedInfo = closedSignal.await()
				_connectionState.value = closedInfo.toConnectionState()
			} finally {
				session.activeHandle = null
				transport.close(handle)
			}

			if (!currentCoroutineContext().isActive) break
			delay(reconnectDelayMs)
			reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
			reconnectAttempt += 1
		}
	}

	private data class ConversationSession(
		val conversationId: Long,
		val events: MutableSharedFlow<ChatRealtimeEvent> = MutableSharedFlow(extraBufferCapacity = 64),
		var nextAfterSeq: Long,
		var subscribers: Int = 0,
		var activeHandle: String? = null,
		var loopJob: kotlinx.coroutines.Job? = null,
	)

	private companion object {
		private const val CHAT_ENDPOINT = "/chats/realtime/ws"
		private const val UNKNOWN_AUTH_MODE = "unknown"
		private const val DEFAULT_REPLAY_LIMIT = 200
		private const val INITIAL_RECONNECT_DELAY_MS = 500L
		private const val MAX_RECONNECT_DELAY_MS = 5_000L
		private val json = Json { ignoreUnknownKeys = true }
	}
}

@Serializable
private data class WasmTypingCommand(
	val type: String,
	val typing: Boolean,
)

@Serializable
private data class WasmPushAckCommand(
	val type: String,
	val messageId: Long,
)

private data class ParsedRealtimeEvent(
	val seq: Long,
	val event: ChatRealtimeEvent,
	val ackMessageId: Long? = null,
)

private fun parseRealtimeEvent(raw: String): ParsedRealtimeEvent? {
	val root = runCatching { chatRealtimeJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
	val type = root.stringOrNull("type") ?: return null
	val seq = root.longOrNullFlexible("seq") ?: 0L
	return when (type) {
		"hello" -> root.longOrNullFlexible("last_read_message_id")
			?.takeIf { it > 0L }
			?.let { ParsedRealtimeEvent(seq = seq, event = ChatRealtimeEvent.PeerReadUpdated(it)) }
		"message_created", "message_updated" -> {
			val peerLastReadMessageId = root.longOrNullFlexible("peer_last_read_message_id")
			val message = root.objectOrNull("message")?.toRealtimeMessageItemOrNull() ?: return null
			ParsedRealtimeEvent(
				seq = seq,
				event = ChatRealtimeEvent.MessageUpserted(
					message = message,
					peerLastReadMessageId = peerLastReadMessageId,
				),
				ackMessageId = message.id,
			)
		}
		"message_deleted" -> {
			val messageId = root.longOrNullFlexible("deleted_message_id") ?: return null
			ParsedRealtimeEvent(seq = seq, event = ChatRealtimeEvent.MessageDeleted(messageId), ackMessageId = messageId)
		}
		"message_pinned_updated" -> {
			val messageId = root.longOrNullFlexible("pinned_message_id")
				?: root.objectOrNull("message")?.longOrNullFlexible("id")
				?: return null
			val isPinned = root.booleanOrNullFlexible("is_pinned")
				?: root.objectOrNull("message")?.booleanOrNullFlexible("is_pinned")
				?: false
			ParsedRealtimeEvent(
				seq = seq,
				event = ChatRealtimeEvent.MessagePinned(messageId = messageId, isPinned = isPinned),
				ackMessageId = messageId,
			)
		}
		"read_up_to_updated" -> {
			val messageId = root.longOrNullFlexible("actor_last_read_message_id") ?: return null
			ParsedRealtimeEvent(seq = seq, event = ChatRealtimeEvent.PeerReadUpdated(messageId), ackMessageId = messageId)
		}
		"typing" -> ParsedRealtimeEvent(
			seq = seq,
			event = ChatRealtimeEvent.TypingUpdated(
				actorUserId = root.stringOrNullFlexible("actor_user_id").orEmpty(),
				displayName = root.stringOrNullFlexible("actor_name").orEmpty(),
				isTyping = root.booleanOrNullFlexible("typing") ?: true,
				typingTtlMs = root.longOrNullFlexible("typing_ttl_ms") ?: 2_200L,
			),
		)
		"resync_required" -> ParsedRealtimeEvent(seq = seq, event = ChatRealtimeEvent.ResyncRequired)
		else -> null
	}
}

private val chatRealtimeJson = Json { ignoreUnknownKeys = true }

private fun JsonObject.toRealtimeMessageItemOrNull(): ChatMessageItemModel? {
	val id = longOrNullFlexible("id") ?: return null
	val sender = objectOrNull("sender")
	return ChatMessageItemModel(
		id = id,
		clientMessageId = stringOrNullFlexible("client_message_id"),
		senderUserId = sender?.stringOrNullFlexible("id"),
		senderDisplayName = sender?.stringOrNullFlexible("name") ?: sender?.stringOrNullFlexible("username"),
		text = stringOrNullFlexible("text").orEmpty(),
		createdAtMillis = longOrNullFlexible("created_at") ?: 0L,
		isOutgoing = false,
		replyToMessageId = longOrNullFlexible("reply_to_message_id"),
		replyToMessageText = stringOrNullFlexible("reply_to_message_text"),
		isPinned = booleanOrNullFlexible("is_pinned") ?: false,
		deliveryState = null,
	)
}

private fun RealtimeSocketLifecycleInfo.toConnectionState(): RealtimeConnectionState {
	return if (isLikelyAuthFailure()) {
		RealtimeConnectionState.AuthFailed(
			endpoint = endpoint,
			authMode = authMode,
			conversationId = conversationId,
			code = code,
			reason = reason,
			message = message,
		)
	} else {
		RealtimeConnectionState.Closed(
			endpoint = endpoint,
			authMode = authMode,
			conversationId = conversationId,
			code = code,
			reason = reason,
			wasClean = wasClean,
			message = message,
		)
	}
}

private fun RealtimeSocketLifecycleInfo.isLikelyAuthFailure(): Boolean {
	return code == 401 || code == 403 || code == 4401 || code == 4403 ||
		reason?.contains("auth", ignoreCase = true) == true ||
		message?.contains("auth", ignoreCase = true) == true ||
		message?.contains("token", ignoreCase = true) == true
}

private fun JsonObject.stringOrNull(key: String): String? =
	this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

private fun JsonObject.stringOrNullFlexible(key: String): String? {
	val primitive = this[key] as? JsonPrimitive ?: return null
	primitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
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

private fun JsonObject.objectOrNull(key: String): JsonObject? = runCatching { this[key]?.jsonObject }.getOrNull()
