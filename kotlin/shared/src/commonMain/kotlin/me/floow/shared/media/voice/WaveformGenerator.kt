package me.floow.shared.media.voice

object WaveformGenerator {
    const val DEFAULT_TARGET_POINTS = 48
    private const val MIN_AMPLITUDE_FLOOR = 8
    private const val MAX_AMPLITUDE_CEILING = 100
    private const val DEFAULT_FALLBACK_AMPLITUDE = 15

    /**
     * Generates a normalized waveform of [targetPoints] discrete amplitude levels (0..100)
     * suitable for visual rendering in voice message bubbles.
     */
    fun generateWaveform(
        raw: List<Float>,
        targetPoints: Int = DEFAULT_TARGET_POINTS
    ): List<Int> {
        require(targetPoints > 0) { "targetPoints must be positive" }

        if (raw.isEmpty()) {
            return List(targetPoints) { DEFAULT_FALLBACK_AMPLITUDE }
        }

        val intermediate = FloatArray(targetPoints)
        val chunkSize = raw.size.toFloat() / targetPoints
        var maxSample = 0.05f

        for (i in 0 until targetPoints) {
            val startIdx = (i * chunkSize).toInt().coerceIn(0, raw.lastIndex)
            val endIdx = ((i + 1) * chunkSize).toInt().coerceIn(startIdx + 1, raw.size)
            var sum = 0f
            var count = 0
            for (j in startIdx until endIdx) {
                sum += raw[j].coerceAtLeast(0f)
                count++
            }
            val avg = if (count > 0) sum / count else raw[startIdx].coerceAtLeast(0f)
            intermediate[i] = avg
            if (avg > maxSample) {
                maxSample = avg
            }
        }

        val result = ArrayList<Int>(targetPoints)
        val dynamicRange = (MAX_AMPLITUDE_CEILING - MIN_AMPLITUDE_FLOOR).toFloat()

        for (i in 0 until targetPoints) {
            val normalized = (intermediate[i] / maxSample).coerceIn(0f, 1f)
            val scaled = (MIN_AMPLITUDE_FLOOR + normalized * dynamicRange).toInt().coerceIn(0, 100)
            result.add(scaled)
        }

        return result
    }
}
