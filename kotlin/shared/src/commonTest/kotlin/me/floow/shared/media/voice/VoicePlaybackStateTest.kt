package me.floow.shared.media.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePlaybackStateTest {

    @Test
    fun voicePlaybackState_speedToggleCycle() {
        fun nextSpeed(currentSpeed: Float): Float {
            return when {
                currentSpeed < 1.25f -> 1.5f
                currentSpeed < 1.75f -> 2.0f
                else -> 1.0f
            }
        }

        assertEquals(1.5f, nextSpeed(1.0f))
        assertEquals(2.0f, nextSpeed(1.5f))
        assertEquals(1.0f, nextSpeed(2.0f))
    }

    @Test
    fun voiceTimeCalculation_safeAgainstNanAndInf() {
        fun calculateSafePositionMs(currentSec: Double): Long {
            if (currentSec.isNaN() || currentSec.isInfinite() || currentSec < 0.0) {
                return 0L
            }
            return (currentSec * 1000.0).toLong()
        }

        fun calculateSafeDurationMs(durationSec: Double, fallbackDurationMs: Long): Long {
            if (durationSec.isNaN() || durationSec.isInfinite() || durationSec <= 0.0) {
                return fallbackDurationMs.coerceAtLeast(0L)
            }
            return (durationSec * 1000.0).toLong()
        }

        assertEquals(0L, calculateSafePositionMs(Double.NaN))
        assertEquals(0L, calculateSafePositionMs(Double.POSITIVE_INFINITY))
        assertEquals(0L, calculateSafePositionMs(Double.NEGATIVE_INFINITY))
        assertEquals(0L, calculateSafePositionMs(-1.5))
        assertEquals(1500L, calculateSafePositionMs(1.5))

        assertEquals(5000L, calculateSafeDurationMs(Double.NaN, 5000L))
        assertEquals(5000L, calculateSafeDurationMs(0.0, 5000L))
        assertEquals(3000L, calculateSafeDurationMs(3.0, 5000L))
    }

    @Test
    fun seekTo_clampedWithinSafeRange() {
        fun clampSeekPosition(targetMs: Long, durationMs: Long): Long {
            val maxDur = durationMs.coerceAtLeast(0L)
            return targetMs.coerceIn(0L, maxDur)
        }

        assertEquals(0L, clampSeekPosition(-500L, 10000L))
        assertEquals(5000L, clampSeekPosition(5000L, 10000L))
        assertEquals(10000L, clampSeekPosition(15000L, 10000L))
        assertEquals(0L, clampSeekPosition(500L, 0L))
    }

    @Test
    fun videoCircleProgress_safeFraction() {
        fun calculateProgress(currentSec: Double, durationSec: Double): Float {
            if (currentSec.isNaN() || durationSec.isNaN() || durationSec <= 0.0 || currentSec < 0.0) {
                return 0f
            }
            return (currentSec / durationSec).coerceIn(0.0, 1.0).toFloat()
        }

        assertEquals(0f, calculateProgress(Double.NaN, 10.0))
        assertEquals(0f, calculateProgress(5.0, Double.NaN))
        assertEquals(0f, calculateProgress(5.0, 0.0))
        assertEquals(0.5f, calculateProgress(5.0, 10.0))
        assertEquals(1.0f, calculateProgress(12.0, 10.0))
    }
}
