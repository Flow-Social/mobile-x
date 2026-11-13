package me.floow.domain.utils

import kotlin.random.Random

const val DEFAULT_BACKOFF_MIN_MS = 1000L
const val DEFAULT_BACKOFF_MAX_MS = 30000L
const val DEFAULT_JITTER_MAX_MS = 1000L

/**
 * Computes exponential backoff with randomized jitter:
 * base = (currentDelay * 2).coerceIn(minDelayMs, maxDelayMs)
 * result = base + random(0..jitterMaxMs)
 */
fun computeExponentialBackoffWithJitter(
	currentDelayMs: Long,
	minDelayMs: Long = DEFAULT_BACKOFF_MIN_MS,
	maxDelayMs: Long = DEFAULT_BACKOFF_MAX_MS,
	jitterMaxMs: Long = DEFAULT_JITTER_MAX_MS
): Long {
	val base = if (currentDelayMs < minDelayMs) {
		minDelayMs
	} else {
		(currentDelayMs * 2).coerceAtMost(maxDelayMs)
	}
	val jitter = if (jitterMaxMs > 0L) Random.nextLong(0L, jitterMaxMs + 1L) else 0L
	return (base + jitter).coerceAtMost(maxDelayMs + jitterMaxMs)
}
