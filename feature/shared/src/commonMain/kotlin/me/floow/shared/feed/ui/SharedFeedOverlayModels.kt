package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter
import me.floow.domain.models.Post
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer

internal typealias SharedFeedMovableCardContent = @Composable () -> Unit

internal enum class SharedFeedOverlayPhase {
    Opening,
    Opened,
    Closing,
    Transfer,
}

internal data class SharedFeedOverlayState(
    val post: Post,
    val launchData: SharedFeedOverlayLaunchData,
    val visible: Boolean = true,
    val phase: SharedFeedOverlayPhase = SharedFeedOverlayPhase.Opening,
    val hiddenSourceKey: String? = null,
    val detachedCount: Int = 0,
)

internal data class SharedFeedPlatformUxAdapter(
    val usesHostedFullscreenViewer: Boolean = false,
    val presentFullscreenViewer: (HostedFullscreenImageViewer?) -> Unit = {},
    val clearFullscreenViewer: () -> Unit = {},
)

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
