package me.floow.shared.media.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveformGeneratorTest {

    @Test
    fun emptyInputReturnsFallbackAmplitudes() {
        val result = WaveformGenerator.generateWaveform(emptyList(), targetPoints = 48)
        assertEquals(48, result.size)
        assertTrue(result.all { it == 15 })
    }

    @Test
    fun customTargetPointsGeneratesExactCount() {
        val raw = listOf(0.1f, 0.5f, 0.9f, 0.3f, 0.7f)
        val result = WaveformGenerator.generateWaveform(raw, targetPoints = 20)
        assertEquals(20, result.size)
        assertTrue(result.all { it in 8..100 })
    }

    @Test
    fun valuesAreClampedBetween0And100() {
        val raw = List(200) { index -> (index % 10) * 0.1f }
        val result = WaveformGenerator.generateWaveform(raw, targetPoints = 48)
        assertEquals(48, result.size)
        assertTrue(result.all { it in 0..100 })
        assertTrue(result.maxOrNull()!! <= 100)
        assertTrue(result.minOrNull()!! >= 8)
    }

    @Test
    fun allZerosProducesConsistentFloor() {
        val raw = List(100) { 0f }
        val result = WaveformGenerator.generateWaveform(raw, targetPoints = 48)
        assertEquals(48, result.size)
        assertTrue(result.all { it == 8 })
    }

    @Test
    fun peakIsMappedToMaxCeiling() {
        val raw = listOf(0.01f, 0.02f, 1.0f, 0.02f, 0.01f)
        val result = WaveformGenerator.generateWaveform(raw, targetPoints = 5)
        assertEquals(5, result.size)
        assertEquals(100, result.maxOrNull())
    }
}
