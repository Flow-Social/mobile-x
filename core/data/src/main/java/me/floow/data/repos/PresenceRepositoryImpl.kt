package me.floow.data.repos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.floow.domain.api.PresenceApi
import me.floow.domain.api.PresenceRealtimeApi
import me.floow.domain.api.PresenceRealtimeSession
import me.floow.domain.api.models.PresenceGetResponse
import me.floow.domain.api.models.PresenceItem
import me.floow.domain.api.models.PresenceRealtimeEvent
import me.floow.domain.api.models.PresenceUpdateResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.PresenceSessionStore
import me.floow.domain.models.UserPresence
import me.floow.domain.utils.Logger

private const val MAX_PRESENCE_TARGETS = 200
private const val HEARTBEAT_INTERVAL_MS = 35_000L
private const val REALTIME_RECONNECT_MIN_MS = 1_000L
private const val REALTIME_RECONNECT_MAX_MS = 10_000L

class PresenceRepositoryImpl(
	private val logger: Logger,
	private val presenceApi: PresenceApi,
	private val presenceRealtimeApi: PresenceRealtimeApi,
	private val authenticationManager: AuthenticationManager,
	private val sessionStore: PresenceSessionStore
) : PresenceRepository {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val stateMutex = Mutex()
	private val _presences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
	private val ownerTargets = linkedMapOf<String, Set<String>>()
	private var desiredTargets: Set<String> = emptySet()
	private var activeTargets: Set<String> = emptySet()
	private var realtimeSession: PresenceRealtimeSession? = null
	private var realtimeJob: Job? = null
	private var heartbeatJob: Job? = null

	@Volatile
	private var isForeground: Boolean = false

	override val presences: StateFlow<Map<String, UserPresence>> = _presences

	override fun setTargets(owner: String, userIds: List<String>) {
		val ownerKey = owner.trim()
		if (ownerKey.isEmpty()) return
		val normalized = normalizeUserIds(userIds)
		scope.launch {
			var desiredSnapshot: Set<String>? = null
			stateMutex.withLock {
				if (normalized.isEmpty()) {
					ownerTargets.remove(ownerKey)
				} else {
					if (ownerTargets[ownerKey] == normalized) return@launch
					ownerTargets[ownerKey] = normalized
				}
				desiredTargets = computeDesiredTargetsLocked()
				desiredSnapshot = desiredTargets
			}
			syncRealtimeTargets(desiredSnapshot.orEmpty())
		}
	}

	override fun clearTargets(owner: String) {
		val ownerKey = owner.trim()
		if (ownerKey.isEmpty()) return
		scope.launch {
			var desiredSnapshot: Set<String>? = null
			stateMutex.withLock {
				if (ownerTargets.remove(ownerKey) == null) return@launch
				desiredTargets = computeDesiredTargetsLocked()
				desiredSnapshot = desiredTargets
			}
			syncRealtimeTargets(desiredSnapshot.orEmpty())
		}
	}

	override fun onAppForeground() {
		isForeground = true
		ensureRealtimeLoop()
		if (heartbeatJob?.isActive == true) return
		heartbeatJob = scope.launch {
			val sessionId = sessionStore.getOrCreateSessionId()
			while (isActive) {
				if (!authenticationManager.isSignedIn()) {
					delay(HEARTBEAT_INTERVAL_MS)
					continue
				}
				when (val response = presenceApi.heartbeat(sessionId)) {
					is PresenceUpdateResponse.Success -> applyPresenceItem(response.item)
					is PresenceUpdateResponse.Error -> logger.d("PresenceRepositoryImpl.onAppForeground", "heartbeat failed")
				}
				delay(HEARTBEAT_INTERVAL_MS)
			}
		}
	}

	override fun onAppBackground() {
		isForeground = false
		heartbeatJob?.cancel()
		heartbeatJob = null
		realtimeJob?.cancel()
		realtimeJob = null
		scope.launch { closeRealtimeSession() }
		scope.launch {
			if (isForeground || !authenticationManager.isSignedIn()) return@launch
			val sessionId = sessionStore.getOrCreateSessionId()
			when (val response = presenceApi.offline(sessionId)) {
				is PresenceUpdateResponse.Success -> applyPresenceItem(response.item)
				is PresenceUpdateResponse.Error -> logger.d("PresenceRepositoryImpl.onAppBackground", "offline failed")
			}
		}
	}

	private fun ensureRealtimeLoop() {
		if (!isForeground || realtimeJob?.isActive == true) return
		realtimeJob = scope.launch {
			var backoffMs = REALTIME_RECONNECT_MIN_MS
			while (isActive && isForeground) {
				val desiredSnapshot = stateMutex.withLock { desiredTargets }
				if (desiredSnapshot.isEmpty()) {
					closeRealtimeSession()
					backoffMs = REALTIME_RECONNECT_MIN_MS
					delay(300L)
					continue
				}
				if (!authenticationManager.isSignedIn()) {
					delay(backoffMs)
					backoffMs = (backoffMs * 2).coerceAtMost(REALTIME_RECONNECT_MAX_MS)
					continue
				}

				val session = presenceRealtimeApi.openSession()
				if (session == null) {
					delay(backoffMs)
					backoffMs = (backoffMs * 2).coerceAtMost(REALTIME_RECONNECT_MAX_MS)
					continue
				}

				stateMutex.withLock {
					realtimeSession = session
					activeTargets = emptySet()
				}

				val connected = runCatching {
					session.subscribe(desiredSnapshot.toList())
					stateMutex.withLock {
						activeTargets = desiredSnapshot
					}
					session.events.collectLatest { event ->
						applyPresenceRealtimeEvent(event)
					}
				}
				if (connected.isFailure) {
					logger.d(
						"PresenceRepositoryImpl.ensureRealtimeLoop",
						"realtime error: ${connected.exceptionOrNull()?.message}"
					)
				}
				closeRealtimeSession()
				if (!isForeground) break
				delay(backoffMs)
				backoffMs = (backoffMs * 2).coerceAtMost(REALTIME_RECONNECT_MAX_MS)
			}
		}
	}

	private suspend fun syncRealtimeTargets(targets: Set<String>) {
		if (!isForeground) return
		ensureRealtimeLoop()
		val session = stateMutex.withLock { realtimeSession }
		if (session == null) return
		val currentlyActive = stateMutex.withLock { activeTargets }
		val toSubscribe = (targets - currentlyActive).toList()
		val toUnsubscribe = (currentlyActive - targets).toList()
		val commandResult = runCatching {
			if (toUnsubscribe.isNotEmpty()) {
				session.unsubscribe(toUnsubscribe)
			}
			if (toSubscribe.isNotEmpty()) {
				session.subscribe(toSubscribe)
			}
		}
		commandResult.onFailure { throwable ->
			logger.d("PresenceRepositoryImpl.syncRealtimeTargets", "command failed: ${throwable.message}")
			closeRealtimeSession()
		}
		if (commandResult.isSuccess) {
			stateMutex.withLock {
				activeTargets = targets
			}
		}
	}

	private suspend fun closeRealtimeSession() {
		val session = stateMutex.withLock {
			val current = realtimeSession
			realtimeSession = null
			activeTargets = emptySet()
			current
		}
		runCatching { session?.close() }
	}

	private suspend fun refreshPresenceSnapshot(userIds: List<String>) {
		when (val response = presenceApi.getPresence(userIds)) {
			is PresenceGetResponse.Success -> response.items.forEach(::applyPresenceItem)
			is PresenceGetResponse.Error -> logger.d("PresenceRepositoryImpl.refreshPresenceSnapshot", "snapshot failed")
		}
	}

	private suspend fun applyPresenceRealtimeEvent(event: PresenceRealtimeEvent) {
		when (event) {
			is PresenceRealtimeEvent.Snapshot -> event.items.forEach(::applyPresenceItem)
			is PresenceRealtimeEvent.PresenceChanged -> applyPresenceItem(event.item)
			is PresenceRealtimeEvent.ResyncRequired -> {
				val targets = stateMutex.withLock { desiredTargets.toList() }
				if (targets.isNotEmpty()) {
					refreshPresenceSnapshot(targets)
				}
			}
		}
	}

	private fun applyPresenceItem(item: PresenceItem) {
		val presence = UserPresence(
			userId = item.userId,
			isOnline = item.isOnline,
			lastSeenAtMillis = item.lastSeenAtMillis,
			serverTimestampMillis = item.serverTimestampMillis
		)
		_presences.update { current ->
			val existing = current[item.userId]
			if (existing != null && existing.serverTimestampMillis > presence.serverTimestampMillis) {
				return@update current
			}
			current + (item.userId to presence)
		}
	}

	private fun computeDesiredTargetsLocked(): Set<String> {
		// Keep subscription order stable to avoid random evictions when we hit MAX_PRESENCE_TARGETS.
		// Owners are ordered (linkedMapOf), and each owner's targets are kept as LinkedHashSet.
		val out = LinkedHashSet<String>(MAX_PRESENCE_TARGETS)
		for (targets in ownerTargets.values) {
			for (userId in targets) {
				out.add(userId)
				if (out.size >= MAX_PRESENCE_TARGETS) return out
			}
		}
		return out
	}

	private fun normalizeUserIds(userIds: List<String>): Set<String> {
		// Use LinkedHashSet to preserve caller order (stable diffs + stable eviction under limit).
		val out = LinkedHashSet<String>(userIds.size)
		for (raw in userIds) {
			val trimmed = raw.trim()
			if (trimmed.isEmpty()) continue
			// Backend presence subscribe expects numeric user_ids.
			if (trimmed.toLongOrNull() == null) continue
			out.add(trimmed)
		}
		return out
	}
}
