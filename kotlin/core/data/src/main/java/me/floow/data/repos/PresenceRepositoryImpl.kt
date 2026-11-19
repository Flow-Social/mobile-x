package me.floow.data.repos

import kotlin.random.Random
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.floow.domain.api.PresenceApi
import me.floow.domain.api.PresenceRealtimeApi
import me.floow.domain.api.models.PresenceGetResponse
import me.floow.domain.api.models.PresenceItem
import me.floow.domain.api.models.PresenceRealtimeEvent
import me.floow.domain.api.models.PresenceUpdateResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.repos.PresenceRepository
import me.floow.domain.data.repos.PresenceSessionStore
import me.floow.domain.lifecycle.AppLifecycleBridge
import me.floow.domain.models.UserPresence
import me.floow.domain.utils.Logger

private const val MAX_TARGETS = 200
private const val HEARTBEAT_INTERVAL_MS = 35_000L
private const val HEARTBEAT_RETRY_MS = 5_000L
private const val REFRESH_INTERVAL_MS = 10_000L
private const val LEASE_FALLBACK_MS = 90_000L

class PresenceRepositoryImpl(
    private val logger: Logger,
    private val presenceApi: PresenceApi,
    private val presenceRealtimeApi: PresenceRealtimeApi,
    private val authenticationManager: AuthenticationManager,
    private val sessionStore: PresenceSessionStore,
    foreground: StateFlow<Boolean> = AppLifecycleBridge.isForeground,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val clock: TimeSource = TimeSource.Monotonic,
) : PresenceRepository {
    private data class Targets(val ids: List<String>, val priority: Boolean)
    private data class CachedPresence(val presence: UserPresence, val expiry: TimeMark?, val refreshAt: TimeMark)
    private data class ForegroundSession(val account: String, val id: String)

    private val owners = MutableStateFlow<Map<String, Targets>>(emptyMap())
    private val cached = MutableStateFlow<Map<String, CachedPresence>>(emptyMap())
    override val presences = cached.map { entries -> entries.mapValues { it.value.presence } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    init {
        scope.launch {
            var running: ForegroundSession? = null
            combine(foreground, authenticationManager.authenticationStateFlow) { isForeground, _ ->
                if (isForeground && authenticationManager.isSignedIn()) authenticationManager.getSelfUserIdOrNull() else null
            }.distinctUntilChanged().collectLatest { account ->
                val previous = running
                running = null
                cached.value = emptyMap()
                if (previous != null && previous.account == authenticationManager.getSelfUserIdOrNull()) {
                    withTimeoutOrNull(1_500L) { presenceApi.offline(previous.id) }
                }
                if (account == null) return@collectLatest
                // A late offline request from the previous foreground cannot remove this lease.
                val id = "${sessionStore.getOrCreateSessionId()}:${Random.nextLong().toString(16)}"
                running = ForegroundSession(account, id)
                coroutineScope {
                    launch { heartbeat(id) }
                    launch { watchPresence() }
                    launch { refreshStalePresence() }
                }
            }
        }
    }

    override fun setTargets(owner: String, userIds: List<String>, priority: Boolean) {
        val key = owner.trim()
        if (key.isEmpty()) return
        val ids = userIds.mapNotNull { it.trim().toIntOrNull()?.takeIf { id -> id > 0 }?.toString() }.distinct()
        owners.update { if (ids.isEmpty()) it - key else it + (key to Targets(ids, priority)) }
    }

    override fun clearTargets(owner: String) {
        owners.update { it - owner.trim() }
    }

    private fun targets(owners: Map<String, Targets> = this.owners.value): Set<String> =
        owners.values.sortedByDescending { it.priority }.asSequence().flatMap { it.ids.asSequence() }
            .distinct().take(MAX_TARGETS).toCollection(LinkedHashSet())

    private suspend fun heartbeat(sessionId: String) {
        while (kotlin.coroutines.coroutineContext.isActive) {
            val response = presenceApi.heartbeat(sessionId)
            if (response is PresenceUpdateResponse.Error) logger.d("Presence", "heartbeat failed")
            delay(if (response is PresenceUpdateResponse.Success) HEARTBEAT_INTERVAL_MS else HEARTBEAT_RETRY_MS)
        }
    }

    private suspend fun watchPresence() {
        var retryMs = 1_000L
        val desired = owners.map { targets(it) }.distinctUntilChanged()
        while (kotlin.coroutines.coroutineContext.isActive) {
            desired.first { it.isNotEmpty() }
            val session = presenceRealtimeApi.openSession()
            if (session != null) {
                try {
                    coroutineScope {
                        val commands = launch {
                            var active = emptySet<String>()
                            desired.collect { next ->
                                if (next.isEmpty()) {
                                    session.close()
                                } else {
                                    val removals = active - next
                                    if (removals.isNotEmpty()) session.unsubscribe(removals.toList())
                                    val additions = next - active
                                    if (additions.isNotEmpty()) session.subscribe(additions.toList())
                                    active = next
                                }
                            }
                        }
                        try {
                            session.events.collect { event ->
                                retryMs = 1_000L
                                when (event) {
                                    is PresenceRealtimeEvent.Snapshot -> applyItems(event.items)
                                    is PresenceRealtimeEvent.PresenceChanged -> applyItems(listOf(event.item))
                                    is PresenceRealtimeEvent.ResyncRequired -> refresh(targets())
                                }
                            }
                        } finally {
                            commands.cancel()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    logger.d("Presence", "realtime interrupted: ${failure.message}")
                } finally {
                    withContext(NonCancellable) { withTimeoutOrNull(1_000L) { session.close() } }
                }
            }
            delay(retryMs)
            retryMs = (retryMs * 2).coerceAtMost(10_000L)
        }
    }

    private suspend fun refreshStalePresence() {
        while (kotlin.coroutines.coroutineContext.isActive) {
            delay(REFRESH_INTERVAL_MS)
            val desired = targets()
            val stale = desired.filter { cached.value[it]?.refreshAt?.hasPassedNow() != false }
            refresh(stale)
            val current = targets()
            cached.update { values ->
                values.filter { (id, value) -> id in current && value.expiry?.hasPassedNow() != true }
            }
        }
    }

    private suspend fun refresh(userIds: Collection<String>) {
        if (userIds.isEmpty()) return
        when (val response = withTimeoutOrNull(3_000L) { presenceApi.getPresence(userIds.toList()) }) {
            is PresenceGetResponse.Success -> applyItems(response.items)
            is PresenceGetResponse.Error, null -> logger.d("Presence", "snapshot failed")
        }
    }

    private fun applyItems(items: List<PresenceItem>) {
        val now = clock.markNow()
        val desired = targets()
        val updates = items.filter { it.userId in desired }.associate { item ->
            val leaseMs = item.expiresAtMillis?.let { (it - item.serverTimestampMillis).coerceIn(0, LEASE_FALLBACK_MS) }
                ?: LEASE_FALLBACK_MS
            val expiry = if (item.isOnline) now + leaseMs.milliseconds else null
            item.userId to CachedPresence(
                UserPresence(item.userId, item.isOnline, item.lastSeenAtMillis, item.serverTimestampMillis),
                expiry,
                if (item.isOnline) now + (leaseMs - REFRESH_INTERVAL_MS).coerceAtLeast(0L).milliseconds
                else now + LEASE_FALLBACK_MS.milliseconds,
            )
        }
        cached.update { values ->
            values + updates.filter { (id, next) ->
                val old = values[id]?.presence
                old == null || old.serverTimestampMillis < next.presence.serverTimestampMillis ||
                    (old.serverTimestampMillis == next.presence.serverTimestampMillis && (!next.presence.isOnline || old.isOnline))
            }
        }
    }
}
