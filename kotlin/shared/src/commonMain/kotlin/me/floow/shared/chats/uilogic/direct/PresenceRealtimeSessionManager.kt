package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.floow.shared.chats.model.ChatPresenceState
import me.floow.shared.chats.model.ChatTypingState

internal class PresenceRealtimeSessionManager(
	private val transport: PresenceRealtimeTransport,
	private val authProvider: RealtimeAuthProvider,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ChatPresenceContract {
	override val connectionState: StateFlow<RealtimeConnectionState>
		get() = _connectionState

	private val _connectionState = MutableStateFlow<RealtimeConnectionState>(RealtimeConnectionState.Idle)
	private val _presences = MutableStateFlow<Map<String, ChatPresenceState>>(emptyMap())
	private val stateMutex = Mutex()
	private val ownerTargets = linkedMapOf<String, Set<String>>()
	private var desiredTargets: Set<String> = emptySet()
	private var activeTargets: Set<String> = emptySet()
	private var activeHandle: String? = null
	private var loopJob: kotlinx.coroutines.Job? = null

	override fun setTargets(owner: String, userIds: List<String>) {
		val normalizedOwner = owner.trim()
		if (normalizedOwner.isEmpty()) return
		val normalizedTargets = userIds
			.map(String::trim)
			.filter(String::isNotEmpty)
			.filter { it.toIntOrNull()?.let { id -> id > 0 } == true }
			.toSet()
		scope.launch {
			stateMutex.withLock {
				if (normalizedTargets.isEmpty()) {
					ownerTargets.remove(normalizedOwner)
				} else {
					ownerTargets[normalizedOwner] = normalizedTargets
				}
				desiredTargets = ownerTargets.values.flatten().toSet()
			}
			ensureRealtimeLoop()
			syncRealtimeTargets()
		}
	}

	override fun clearTargets(owner: String) {
		val normalizedOwner = owner.trim()
		if (normalizedOwner.isEmpty()) return
		scope.launch {
			stateMutex.withLock {
				ownerTargets.remove(normalizedOwner)
				desiredTargets = ownerTargets.values.flatten().toSet()
			}
			syncRealtimeTargets()
		}
	}

	override fun observePeerPresence(peerUserId: String): Flow<ChatPresenceState> = callbackFlow {
		val normalizedPeerId = peerUserId.trim()
		if (normalizedPeerId.isEmpty()) {
			trySend(ChatPresenceState())
			close()
			return@callbackFlow
		}
		val owner = "chat-peer-$normalizedPeerId-${nextObserverId()}"
		setTargets(owner, listOf(normalizedPeerId))
		val collector = launch {
			_presences
				.map { it[normalizedPeerId] }
				.distinctUntilChanged()
				.collectLatest { presence ->
					if (presence != null) {
						trySend(presence)
					}
				}
		}
		awaitClose {
			collector.cancel()
			clearTargets(owner)
		}
	}

	override fun observePeerTyping(peerUserId: String): Flow<ChatTypingState> = flowOf(ChatTypingState())

	private suspend fun ensureRealtimeLoop() {
		val shouldStart = stateMutex.withLock {
			desiredTargets.isNotEmpty() && (loopJob?.isActive != true)
		}
		if (!shouldStart) return
		loopJob = scope.launch {
			runLoop()
		}
	}

	private suspend fun runLoop() {
		var reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
		var reconnectAttempt = 0
		while (currentCoroutineContext().isActive) {
			val desiredSnapshot = stateMutex.withLock { desiredTargets }
			if (desiredSnapshot.isEmpty()) {
				closeSession()
				_connectionState.value = RealtimeConnectionState.Idle
				delay(300L)
				continue
			}

			val auth = authProvider.resolvePresenceAuth().getOrElse { error ->
				_connectionState.value = RealtimeConnectionState.AuthFailed(
					endpoint = PRESENCE_ENDPOINT,
					authMode = UNKNOWN_AUTH_MODE,
					message = error.message ?: "auth resolution failed",
				)
				delay(reconnectDelayMs)
				reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
				reconnectAttempt += 1
				continue
			}

			_connectionState.value = if (reconnectAttempt == 0) {
				RealtimeConnectionState.Connecting(
					endpoint = PRESENCE_ENDPOINT,
					authMode = auth.mode,
				)
			} else {
				RealtimeConnectionState.Reconnecting(
					endpoint = PRESENCE_ENDPOINT,
					authMode = auth.mode,
					attempt = reconnectAttempt,
					backoffMs = reconnectDelayMs,
				)
			}

			val closedSignal = CompletableDeferred<RealtimeSocketLifecycleInfo>()
			val openResult = transport.open(
				params = PresenceRealtimeOpenParams(auth = auth),
				listener = object : PresenceRealtimeTransportListener {
					override fun onOpen() {
						reconnectAttempt = 0
						reconnectDelayMs = INITIAL_RECONNECT_DELAY_MS
						_connectionState.value = RealtimeConnectionState.Open(
							endpoint = PRESENCE_ENDPOINT,
							authMode = auth.mode,
						)
						scope.launch {
							sendSubscribe(desiredSnapshot.toList())
						}
					}

					override fun onEvent(raw: String) {
						when (val event = parsePresenceEvent(raw)) {
							is PresenceSocketEvent.State -> {
								_presences.value = _presences.value + (event.userId to event.value)
							}
							PresenceSocketEvent.Resubscribe -> {
								_connectionState.value = RealtimeConnectionState.Resyncing(
									endpoint = PRESENCE_ENDPOINT,
									authMode = auth.mode,
								)
								scope.launch {
									val targets = stateMutex.withLock { desiredTargets }
									sendSubscribe(targets.toList())
								}
							}
							null -> Unit
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
				_connectionState.value = RealtimeSocketLifecycleInfo(
					endpoint = PRESENCE_ENDPOINT,
					authMode = auth.mode,
					message = error.message ?: "socket open failed",
				).toConnectionState()
				delay(reconnectDelayMs)
				reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
				reconnectAttempt += 1
				continue
			}
			stateMutex.withLock {
				activeHandle = handle
				activeTargets = emptySet()
			}

			try {
				val closedInfo = closedSignal.await()
				_connectionState.value = closedInfo.toConnectionState()
			} finally {
				closeSession()
			}

			if (!currentCoroutineContext().isActive) break
			delay(reconnectDelayMs)
			reconnectDelayMs = minOf(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
			reconnectAttempt += 1
		}
	}

	private suspend fun syncRealtimeTargets() {
		val handle = stateMutex.withLock { activeHandle } ?: return
		val targets = stateMutex.withLock { desiredTargets }
		val current = stateMutex.withLock { activeTargets }
		val toSubscribe = (targets - current).toList()
		val toUnsubscribe = (current - targets).toList()
		if (toUnsubscribe.isNotEmpty()) {
			val numericToUnsubscribe = toUnsubscribe.mapNotNull { it.toIntOrNull()?.takeIf { id -> id > 0 } }
			transport.send(
				handle = handle,
				payloadJson = json.encodeToString(
					PresenceSubscriptionCommand(
						type = "unsubscribe",
						userIds = numericToUnsubscribe,
					)
				),
			).onFailure {
				closeSession()
			}
		}
		if (toSubscribe.isNotEmpty()) {
			sendSubscribe(toSubscribe)
		}
		stateMutex.withLock {
			activeTargets = targets
		}
		if (targets.isEmpty()) {
			closeSession()
			_connectionState.value = RealtimeConnectionState.Idle
		}
	}

	private suspend fun sendSubscribe(userIds: List<String>) {
		val handle = stateMutex.withLock { activeHandle } ?: return
		val numericUserIds = userIds.mapNotNull { it.toIntOrNull()?.takeIf { id -> id > 0 } }
		if (numericUserIds.isEmpty()) return
		transport.send(
			handle = handle,
			payloadJson = json.encodeToString(
				PresenceSubscriptionCommand(
					type = "subscribe",
					userIds = numericUserIds,
				)
			),
		)
	}

	private fun closeSession() {
		val handle = activeHandle
		activeHandle = null
		activeTargets = emptySet()
		handle?.let(transport::close)
	}

	private fun nextObserverId(): String = buildString {
		append(observerCounter++)
	}

	private companion object {
		private const val PRESENCE_ENDPOINT = "/presence/ws"
		private const val UNKNOWN_AUTH_MODE = "unknown"
		private const val INITIAL_RECONNECT_DELAY_MS = 500L
		private const val MAX_RECONNECT_DELAY_MS = 5_000L
		private val json = Json { ignoreUnknownKeys = true }
		private var observerCounter: Long = 0L
	}
}

@Serializable
private data class PresenceSubscriptionCommand(
	val type: String,
	val userIds: List<Int>,
)

private sealed interface PresenceSocketEvent {
	data class State(val userId: String, val value: ChatPresenceState) : PresenceSocketEvent
	data object Resubscribe : PresenceSocketEvent
}

private fun parsePresenceEvent(raw: String): PresenceSocketEvent? {
	val root = runCatching { presenceRealtimeJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
	return when (root.stringOrNull("type")) {
		"presence_snapshot" -> root.arrayOrNull("items")
			?.mapNotNull { (it as? JsonObject)?.toPresenceSocketStateOrNull() }
			?.firstOrNull()
		"presence_changed" -> root.toPresenceSocketStateOrNull()
		"resync_required" -> PresenceSocketEvent.Resubscribe
		else -> null
	}
}

private fun JsonObject.toPresenceSocketStateOrNull(): PresenceSocketEvent.State? {
	val target = objectOrNull("item") ?: this
	val userId = target.stringOrNullFlexible("user_id") ?: return null
	return PresenceSocketEvent.State(
		userId = userId,
		value = ChatPresenceState(
			isOnline = target.booleanOrNullFlexible("is_online") ?: false,
			lastSeenAtMillis = target.longOrNullFlexible("last_seen_at"),
		),
	)
}

private fun RealtimeSocketLifecycleInfo.toConnectionState(): RealtimeConnectionState {
	return if (isLikelyAuthFailure()) {
		RealtimeConnectionState.AuthFailed(
			endpoint = endpoint,
			authMode = authMode,
			code = code,
			reason = reason,
			message = message,
		)
	} else {
		RealtimeConnectionState.Closed(
			endpoint = endpoint,
			authMode = authMode,
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
private fun JsonObject.arrayOrNull(key: String): JsonArray? = runCatching { this[key]?.jsonArray }.getOrNull()

private val presenceRealtimeJson = Json { ignoreUnknownKeys = true }
