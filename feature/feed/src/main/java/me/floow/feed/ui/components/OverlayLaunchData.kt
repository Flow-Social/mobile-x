package me.floow.feed.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect

internal typealias MovableCardContent = @Composable () -> Unit

internal data class CardPose(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float
)

internal data class OverlayLaunchData(
    val buttonRect: Rect?,
    val cardRects: List<Rect?>,
    val cardPoses: List<CardPose?> = emptyList(),
    val cardContents: List<MovableCardContent?> = emptyList()
)
