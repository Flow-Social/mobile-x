package me.floow.shared.profile.ui.bump

import androidx.compose.runtime.Composable

@Composable
expect fun BumpDetectorEffect(
    enabled: Boolean,
    onImpactDetected: (peak: Float) -> Unit,
)
