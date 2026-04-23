package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter

internal typealias SharedFeedMovableCardContent = @Composable () -> Unit

internal data class SharedFeedCardPose(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val rotation: Float,
)

internal data class SharedFeedOverlayLaunchData(
    val buttonRect: Rect?,
    val cardRects: List<Rect?>,
    val cardPoses: List<SharedFeedCardPose?> = emptyList(),
    val cardContents: List<SharedFeedMovableCardContent?> = emptyList(),
    val cardPainters: List<Painter?> = emptyList(),
)
