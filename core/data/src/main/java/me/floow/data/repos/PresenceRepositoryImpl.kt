package me.floow.data.repos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.floow.domain.api.PresenceApi
import me.floow.domain.api.PresenceRealtimeApi
import me.floow.domain.api.models.PresenceGetResponse
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
private const val OFFLINE_DEBOUNCE_MS = 5_000L

class PresenceRepositoryImpl(
	private val logger: Logger,
	private val presenceApi: PresenceApi,
	private val presenceRealtimeApi: PresenceRealtimeApi,
	private val authenticationManager: AuthenticationManager,
	private val sessionStore: PresenceSessionStore
) : PresenceRepository {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
	private val _presences = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
	private var chatListUserIds: Set<String> = emptySet()
	private var focusUserIds: Set<String> = emptySet()
	private var realtimeJob: Job? = null
	private var heartbeatJob: Job? = null
	private var offlineJob: Job? = null
	@Volatile
	private var isForeground: Boolean = false

	override val presences: StateFlow<Map<String, UserPresence>> = _presences

	override fun updateChatListUserIds(userIds: List<String>) {
		val normalized = normalizeUserIds(userIds)
		if (normalized == chatListUserIds) return
		chatListUserIds = normalized
		refreshSubscriptions()
	}

	override fun updateFocusUserIds(userIds: List<String>) {
		val normalized = normalizeUserIds(userIds)
		if (normalized == focusUserIds) return
		focusUserIds = normalized
		refreshSubscriptions()
	}

	override fun onAppForeground() {
		isForeground = true
		offlineJob?.cancel()
		offlineJob = null
		if (heartbeatJob?.isActive == true) return
		heartbeatJob = scope.launch {
			val sessionId = sessionStore.getOrCreateSessionId()
			while (isActive) {
				if (!authenticationManager.isSignedIn()) {
					delay(HEARTBEAT_INTERVAL_MS)
					continue
				}
				when (val response = presenceApi.heartbeat(sessionId)) {
					is PresenceUpdateResponse.Success -> {
						applyPresenceItem(response.item)
					}
					is PresenceUpdateResponse.Error -> {
						logger.d("PresenceRepositoryImpl.onAppForeground", "heartbeat failed")
					}
				}
				delay(HEARTBEAT_INTERVAL_MS)
			}
		}
	}

	override fun onAppBackground() {
		isForeground = false
		heartbeatJob?.cancel()
		heartbeatJob = null
		offlineJob?.cancel()
		offlineJob = scope.launch {
			delay(OFFLINE_DEBOUNCE_MS)
			if (isForeground || !authenticationManager.isSignedIn()) return@launch
			val sessionId = sessionStore.getOrCreateSessionId()
			when (val response = presenceApi.offline(sessionId)) {
				is PresenceUpdateResponse.Success -> applyPresenceItem(response.item)
				is PresenceUpdateResponse.Error -> logger.d("PresenceRepositoryImpl.onAppBackground", "offline failed")
			}
		}
	}

	private fun refreshSubscriptions() {
		val combined = (focusUserIds + chatListUserIds).toList()
		val limited = combined.take(MAX_PRESENCE_TARGETS)
		realtimeJob?.cancel()
		if (limited.isEmpty()) return
		scope.launch {
			refreshPresenceSnapshot(limited)
		}
		realtimeJob = scope.launch {
			var backoff = REALTIME_RECONNECT_MIN_MS
			while (isActive) {
				val result = runCatching {
					presenceRealtimeApi.subscribe(limited).collect { event ->
						applyPresenceRealtimeEvent(event)
					}
				}
				if (result.isSuccess) {
					backoff = REALTIME_RECONNECT_MIN_MS
				} else {
					logger.d("PresenceRepositoryImpl.refreshSubscriptions", "realtime error: ${result.exceptionOrNull()?.message}")
				}
				delay(backoff)
				backoff = (backoff * 2).coerceAtMost(REALTIME_RECONNECT_MAX_MS)
			}
		}
	}

	private suspend fun refreshPresenceSnapshot(userIds: List<String>) {
		when (val response = presenceApi.getPresence(userIds)) {
			is PresenceGetResponse.Success -> {
				response.items.forEach { item ->
					applyPresenceItem(item)
				}
			}
			is PresenceGetResponse.Error -> {
				logger.d("PresenceRepositoryImpl.refreshPresenceSnapshot", "snapshot failed")
			}
		}
	}

	private fun applyPresenceRealtimeEvent(event: PresenceRealtimeEvent) {
		when (event) {
			is PresenceRealtimeEvent.PresenceChanged -> applyPresenceItem(event.item)
		}
	}

	private fun applyPresenceItem(item: me.floow.domain.api.models.PresenceItem) {
		val presence = UserPresence(
			userId = item.userId,
			isOnline = item.isOnline,
			lastSeenAtMillis = item.lastSeenAtMillis
		)
		_presences.update { current ->
			current + (item.userId to presence)
		}
	}

	private fun normalizeUserIds(userIds: List<String>): Set<String> {
		return userIds
			.map { it.trim() }
			.filter { it.isNotEmpty() && it.toLongOrNull() != null }
			.distinct()
			.toSet()
	}
}
