package me.floow.shared.profile.ui.bump

import androidx.compose.runtime.Composable

@Composable
actual fun BumpDetectorEffect(
    enabled: Boolean,
    onImpactDetected: (peak: Float) -> Unit,
) {
    // iOS accelerometer / motion detection hook
}
