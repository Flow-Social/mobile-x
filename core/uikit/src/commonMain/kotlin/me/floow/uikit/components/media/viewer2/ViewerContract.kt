package me.floow.uikit.components.media.viewer2

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.painter.Painter

@Stable
enum class ViewerPhase {
    Closed,
    Opening,
    Opened,
    Closing
}

enum class SourceImageScaleMode {
    Fit,
    Crop
}

data class SharedImageOrigin(
    val sourceKey: String,
    val rectInWindow: Rect,
    val aspectRatio: Float,
    val contentRectInWindow: Rect? = null,
    val cornerRadiusPx: Float = 0f,
    val sourceScaleMode: SourceImageScaleMode = SourceImageScaleMode.Fit
)

data class FullscreenImageViewerModel(
    val images: List<String>,
    val title: String,
    val subtitleProvider: (Int) -> String,
    val openingPainter: Painter? = null,
    val originForPage: (Int) -> SharedImageOrigin? = { null }
)

@Stable
@OptIn(ExperimentalSharedTransitionApi::class)
data class FullscreenImageViewerSharedTransitionSpec(
    val scope: SharedTransitionScope,
    val keyForPage: (Int) -> Any
)

sealed interface FullscreenImageViewerAction {
    data class Open(val page: Int, val origin: SharedImageOrigin?) : FullscreenImageViewerAction
    data object OpenAnimationFinished : FullscreenImageViewerAction
    data class RequestClose(
        val origin: SharedImageOrigin? = null,
        val page: Int? = null,
        val dismissProgressAtClose: Float = 0f,
        val closeStartRect: Rect? = null
    ) : FullscreenImageViewerAction
    data object CloseAnimationFinished : FullscreenImageViewerAction
    data class PageChanged(val page: Int) : FullscreenImageViewerAction
    data object ToggleChrome : FullscreenImageViewerAction
    data class ZoomChanged(val scale: Float, val pan: Offset) : FullscreenImageViewerAction
    data class DismissDrag(val offsetY: Float) : FullscreenImageViewerAction
    data object MenuClick : FullscreenImageViewerAction
}

@Stable
class FullscreenImageViewerState internal constructor(
    phase: ViewerPhase,
    page: Int,
    chromeVisible: Boolean,
    zoom: Float,
    pan: Offset,
    dismissOffsetY: Float,
    openOrigin: SharedImageOrigin?,
    closeOrigin: SharedImageOrigin?,
    transitionProgress: Float,
    closeSceneStartProgress: Float,
    closeStartRect: Rect?
) {
    var phase by mutableStateOf(phase)
    var page by mutableIntStateOf(page)
    var chromeVisible by mutableStateOf(chromeVisible)
    var zoom by mutableFloatStateOf(zoom)
    var pan by mutableStateOf(pan)
    var dismissOffsetY by mutableFloatStateOf(dismissOffsetY)
    var openOrigin by mutableStateOf(openOrigin)
    var closeOrigin by mutableStateOf(closeOrigin)
    var transitionProgress by mutableFloatStateOf(transitionProgress)
    var closeSceneStartProgress by mutableFloatStateOf(closeSceneStartProgress)
    var closeStartRect by mutableStateOf(closeStartRect)

    val visible: Boolean
        get() = phase != ViewerPhase.Closed

    fun updateTransitionProgress(progress: Float) {
        transitionProgress = progress.coerceIn(0f, 1f)
    }
}

@Composable
fun rememberFullscreenImageViewerState(
    phase: ViewerPhase = ViewerPhase.Closed,
    page: Int = 0,
    chromeVisible: Boolean = true,
    zoom: Float = 1f,
    pan: Offset = Offset.Zero,
    dismissOffsetY: Float = 0f,
    openOrigin: SharedImageOrigin? = null,
    closeOrigin: SharedImageOrigin? = null,
    transitionProgress: Float = 0f,
    closeSceneStartProgress: Float = 1f,
    closeStartRect: Rect? = null
): FullscreenImageViewerState {
    return remember {
        FullscreenImageViewerState(
            phase = phase,
            page = page,
            chromeVisible = chromeVisible,
            zoom = zoom,
            pan = pan,
            dismissOffsetY = dismissOffsetY,
            openOrigin = openOrigin,
            closeOrigin = closeOrigin,
            transitionProgress = transitionProgress,
            closeSceneStartProgress = closeSceneStartProgress,
            closeStartRect = closeStartRect
        )
    }
}
