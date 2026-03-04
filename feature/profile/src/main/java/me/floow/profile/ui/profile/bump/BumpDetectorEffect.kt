package me.floow.profile.ui.profile.bump

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.sqrt

private const val MIN_DYNAMIC_THRESHOLD = 5.2f
private const val MAX_DYNAMIC_THRESHOLD = 10.5f
private const val BASELINE_ALPHA = 0.08f
private const val BASELINE_MAX = 9.5f
private const val THRESHOLD_MULTIPLIER = 1.65f
private const val THRESHOLD_OFFSET = 0.8f
private const val MIN_RISE_DELTA = 0.55f
private const val SOFT_HIT_RISE_DELTA = 0.30f
private const val SOFT_HIT_FACTOR = 0.80f
private const val SOFT_HIT_WINDOW_MS = 320L
private const val BUMP_COOLDOWN_MS = 450L
private const val DETECTOR_WARMUP_MS = 140L

@Composable
fun BumpDetectorEffect(
    enabled: Boolean,
    onImpactDetected: (peak: Float) -> Unit,
) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }

    DisposableEffect(enabled, sensorManager) {
        if (!enabled || sensorManager == null) {
            onDispose { }
        } else {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            if (sensor == null) {
                onDispose { }
            } else {
                var lastTriggerAt = 0L
                var previousMagnitude = 0f
                var baseline = 1.5f
                var dynamicThreshold = MIN_DYNAMIC_THRESHOLD
                var softHitCount = 0
                var lastSoftHitAt = 0L
                val warmupEndsAt = SystemClock.elapsedRealtime() + DETECTOR_WARMUP_MS
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        val values = event?.values ?: return
                        if (values.size < 3) return

                        val x = values[0]
                        val y = values[1]
                        val z = values[2]
                        val magnitude = sqrt(x * x + y * y + z * z)
                        dynamicThreshold = (baseline * THRESHOLD_MULTIPLIER + THRESHOLD_OFFSET)
                            .coerceIn(MIN_DYNAMIC_THRESHOLD, MAX_DYNAMIC_THRESHOLD)

                        val isRisingPeak = magnitude - previousMagnitude >= MIN_RISE_DELTA
                        val isSoftRisingPeak = magnitude - previousMagnitude >= SOFT_HIT_RISE_DELTA
                        val now = SystemClock.elapsedRealtime()
                        val isReady = now >= warmupEndsAt && now - lastTriggerAt >= BUMP_COOLDOWN_MS

                        val hardHit = isRisingPeak && magnitude >= dynamicThreshold
                        val softHitThreshold = dynamicThreshold * SOFT_HIT_FACTOR
                        val softHit = isSoftRisingPeak && magnitude >= softHitThreshold

                        if (isReady && hardHit) {
                            lastTriggerAt = now
                            softHitCount = 0
                            onImpactDetected(magnitude)
                        } else if (isReady && softHit) {
                            softHitCount = if (now - lastSoftHitAt <= SOFT_HIT_WINDOW_MS) {
                                softHitCount + 1
                            } else {
                                1
                            }
                            lastSoftHitAt = now

                            if (softHitCount >= 2) {
                                lastTriggerAt = now
                                softHitCount = 0
                                onImpactDetected(magnitude)
                            }
                        } else if (now - lastSoftHitAt > SOFT_HIT_WINDOW_MS) {
                            softHitCount = 0
                        }

                        val baselineAlpha = if (magnitude < dynamicThreshold) {
                            BASELINE_ALPHA
                        } else {
                            BASELINE_ALPHA * 0.25f
                        }
                        baseline += (magnitude - baseline) * baselineAlpha
                        baseline = baseline.coerceIn(0.8f, BASELINE_MAX)
                        previousMagnitude = magnitude
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }

                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }
}
