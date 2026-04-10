package me.floow.domain.utils

import me.floow.domain.data.cache.CacheEvaluation
import me.floow.domain.data.cache.CacheState

enum class TelemetryOperation(val key: String) {
	FeedInitialLoad("feed_initial_load"),
	FeedSoftRevalidate("feed_soft_revalidate"),
	ProfileLoad("profile_load"),
	CategoriesNetworkRefresh("categories_network_refresh")
}

enum class TelemetryScope(val key: String) {
	FeedStack("feed_stack"),
	ProfileScreen("profile_screen"),
	Categories("categories")
}

enum class CacheDecision(val key: String) {
	ColdStart("cold_start"),
	FreshHit("fresh_hit"),
	StaleHitRevalidate("stale_hit_revalidate"),
	ExpiredRevalidate("expired_revalidate"),
	ExpiredRefresh("expired_refresh"),
	BackgroundRevalidateScheduled("background_revalidate_scheduled")
}

fun Logger.logLoadTiming(
	tag: String,
	operation: TelemetryOperation,
	startedAtMs: Long,
	nowMs: Long = currentTimeMillis(),
	extras: String = ""
) {
	val durationMs = (nowMs - startedAtMs).coerceAtLeast(0L)
	val details = if (extras.isBlank()) "" else " $extras"
	d(tag, "[telemetry][timing] op=${operation.key} durationMs=$durationMs$details")
}

fun Logger.logCacheDecision(
	tag: String,
	scope: TelemetryScope,
	decision: CacheDecision,
	ageMs: Long? = null,
	extras: String = ""
) {
	val age = ageMs?.toString() ?: "na"
	val details = if (extras.isBlank()) "" else " $extras"
	d(tag, "[telemetry][cache] scope=${scope.key} decision=${decision.key} ageMs=$age$details")
}

fun Logger.logCacheState(
	tag: String,
	scope: TelemetryScope,
	cache: CacheEvaluation,
	extras: String = "",
	emptyDecision: CacheDecision = CacheDecision.ColdStart,
	freshDecision: CacheDecision = CacheDecision.FreshHit,
	staleDecision: CacheDecision = CacheDecision.StaleHitRevalidate,
	expiredDecision: CacheDecision = CacheDecision.ExpiredRevalidate
): CacheDecision {
	val decision = when (cache.state) {
		CacheState.Empty -> emptyDecision
		CacheState.Fresh -> freshDecision
		CacheState.Stale -> staleDecision
		CacheState.Expired -> expiredDecision
	}
	logCacheDecision(
		tag = tag,
		scope = scope,
		decision = decision,
		ageMs = if (cache.state == CacheState.Empty) null else cache.ageMs,
		extras = extras
	)
	return decision
}
