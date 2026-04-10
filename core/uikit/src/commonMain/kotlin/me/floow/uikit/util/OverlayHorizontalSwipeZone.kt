package me.floow.uikit.util

import androidx.compose.ui.Modifier

expect fun Modifier.overlayHorizontalSwipeZone(
    zoneKey: Any,
    atStart: Boolean,
    blockOverlay: Boolean = false,
    priority: Int = 0,
): Modifier
