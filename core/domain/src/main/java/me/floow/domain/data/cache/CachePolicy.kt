package me.floow.domain.data.cache

data class CachePolicy(
	val maxAgeMs: Long,
	val staleWhileRevalidateMs: Long
) {
	init {
		require(maxAgeMs >= 0) { "maxAgeMs must be >= 0" }
		require(staleWhileRevalidateMs >= 0) { "staleWhileRevalidateMs must be >= 0" }
	}

	fun ageMs(lastUpdatedAtMs: Long?, nowMs: Long = System.currentTimeMillis()): Long {
		val lastUpdated = lastUpdatedAtMs ?: return Long.MAX_VALUE
		return (nowMs - lastUpdated).coerceAtLeast(0L)
	}

	fun isFresh(ageMs: Long): Boolean = ageMs <= maxAgeMs

	fun canServeStale(ageMs: Long): Boolean = ageMs <= maxAgeMs + staleWhileRevalidateMs

	fun evaluate(
		hasCachedData: Boolean,
		lastUpdatedAtMs: Long?,
		nowMs: Long = System.currentTimeMillis()
	): CacheEvaluation {
		if (!hasCachedData) {
			return CacheEvaluation(
				ageMs = Long.MAX_VALUE,
				state = CacheState.Empty
			)
		}

		val ageMs = ageMs(lastUpdatedAtMs = lastUpdatedAtMs, nowMs = nowMs)
		val state = when {
			isFresh(ageMs) -> CacheState.Fresh
			canServeStale(ageMs) -> CacheState.Stale
			else -> CacheState.Expired
		}
		return CacheEvaluation(ageMs = ageMs, state = state)
	}
}

data class CacheEvaluation(
	val ageMs: Long,
	val state: CacheState
)

enum class CacheState {
	Empty,
	Fresh,
	Stale,
	Expired
}
