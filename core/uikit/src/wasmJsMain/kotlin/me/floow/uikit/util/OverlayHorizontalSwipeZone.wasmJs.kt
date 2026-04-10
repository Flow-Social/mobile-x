package me.floow.uikit.util

import androidx.compose.ui.Modifier

actual fun Modifier.overlayHorizontalSwipeZone(
    zoneKey: Any,
    atStart: Boolean,
    blockOverlay: Boolean,
    priority: Int,
): Modifier = this
