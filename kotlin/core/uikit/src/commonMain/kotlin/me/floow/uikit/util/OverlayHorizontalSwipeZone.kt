package me.floow.uikit.util

import androidx.compose.ui.Modifier

fun Modifier.overlayHorizontalPagerSwipeZone(
    zoneKey: Any,
    currentPage: Int,
    blockOverlayAtStart: Boolean = false,
    priority: Int = 100,
): Modifier = overlayHorizontalSwipeZone(
    zoneKey = zoneKey,
    atStart = currentPage == 0,
    blockOverlay = blockOverlayAtStart,
    priority = priority,
)
