package me.floow.domain.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.floow.domain.lifecycle.AppLifecycleBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffWithJitterTest {

	@Test
	fun `zero delay resets to min delay with zero jitter`() {
		val nextDelay = computeExponentialBackoffWithJitter(
			currentDelayMs = 0L,
			minDelayMs = 1000L,
			maxDelayMs = 30000L,
			jitterMaxMs = 0L,
		)
		assertEquals(1000L, nextDelay)
	}

	@Test
	fun `backoff doubles each step without jitter until capped`() {
		var delay = 0L

		val expectedDelays = listOf(1000L, 2000L, 4000L, 8000L, 16000L, 30000L, 30000L)
		for (expected in expectedDelays) {
			delay = computeExponentialBackoffWithJitter(
				currentDelayMs = delay,
				minDelayMs = 1000L,
				maxDelayMs = 30000L,
				jitterMaxMs = 0L,
			)
			assertEquals(expected, delay)
		}
	}

	@Test
	fun `jitter is strictly within range`() {
		val minDelay = 1000L
		val maxDelay = 30000L
		val jitterMax = 1000L

		var delay = 0L
		for (i in 1..20) {
			delay = computeExponentialBackoffWithJitter(
				currentDelayMs = delay,
				minDelayMs = minDelay,
				maxDelayMs = maxDelay,
				jitterMaxMs = jitterMax,
			)
			assertTrue("Delay $delay should be >= minDelay $minDelay", delay >= minDelay)
			assertTrue("Delay $delay should be <= maxDelay + jitterMax ${maxDelay + jitterMax}", delay <= maxDelay + jitterMax)
		}
	}

	@Test
	fun `app lifecycle bridge updates foreground state and wakes up delay`() = runBlocking {
		AppLifecycleBridge.onAppForeground()
		assertTrue(AppLifecycleBridge.isForeground.value)

		AppLifecycleBridge.onAppBackground()
		assertFalse(AppLifecycleBridge.isForeground.value)

		// delayWithForegroundWakeup should suspend while in background and resume on foreground
		val job = launch(Dispatchers.Default) {
			AppLifecycleBridge.delayWithForegroundWakeup(60_000L)
		}
		Thread.sleep(100)
		assertTrue(job.isActive)

		AppLifecycleBridge.onAppForeground()
		job.join()
		assertFalse(job.isActive)
	}
}
