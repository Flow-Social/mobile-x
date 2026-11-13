package me.floow.shared.profile.ui.bump

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

@Composable
actual fun BumpDetectorEffect(
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
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        val values = event?.values ?: return
                        if (values.size < 3) return

                        val x = values[0]
                        val y = values[1]
                        val z = values[2]
                        val magnitude = sqrt(x * x + y * y + z * z)
                        val now = SystemClock.elapsedRealtime()
                        if (magnitude >= 6.5f && now - lastTriggerAt >= 450L) {
                            lastTriggerAt = now
                            onImpactDetected(magnitude)
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                onDispose {
                    sensorManager.unregisterListener(listener)
                }
            }
        }
    }
}
