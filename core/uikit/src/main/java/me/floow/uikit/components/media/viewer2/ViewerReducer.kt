package me.floow.uikit.components.media.viewer2

import androidx.compose.ui.geometry.Offset

fun FullscreenImageViewerState.reduce(action: FullscreenImageViewerAction, imageCount: Int) {
    when (action) {
        is FullscreenImageViewerAction.Open -> {
            phase = ViewerPhase.Opening
            page = action.page.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
            openOrigin = action.origin
            closeOrigin = null
            chromeVisible = true
            zoom = 1f
            pan = Offset.Zero
            dismissOffsetY = 0f
            transitionProgress = 0f
        }

        FullscreenImageViewerAction.OpenAnimationFinished -> {
            if (phase != ViewerPhase.Opening) return
            phase = ViewerPhase.Opened
            transitionProgress = 1f
        }

        is FullscreenImageViewerAction.RequestClose -> {
            if (phase == ViewerPhase.Closed || phase == ViewerPhase.Closing) return
            val targetPage = action.page?.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
            phase = ViewerPhase.Closing
            if (targetPage != null) {
                page = targetPage
            }
            closeOrigin = action.origin ?: closeOrigin
            chromeVisible = false
            zoom = 1f
            pan = Offset.Zero
            dismissOffsetY = 0f
            transitionProgress = 0f
        }

        FullscreenImageViewerAction.CloseAnimationFinished -> {
            phase = ViewerPhase.Closed
            page = 0
            chromeVisible = true
            zoom = 1f
            pan = Offset.Zero
            dismissOffsetY = 0f
            openOrigin = null
            closeOrigin = null
            transitionProgress = 0f
        }

        is FullscreenImageViewerAction.PageChanged -> {
            if (phase != ViewerPhase.Opened) return
            page = action.page.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
            zoom = 1f
            pan = Offset.Zero
            dismissOffsetY = 0f
        }

        FullscreenImageViewerAction.ToggleChrome -> {
            if (phase != ViewerPhase.Opened) return
            chromeVisible = !chromeVisible
        }

        is FullscreenImageViewerAction.ZoomChanged -> {
            if (phase != ViewerPhase.Opened) return
            zoom = action.scale.coerceIn(1f, 4f)
            pan = action.pan
        }

        is FullscreenImageViewerAction.DismissDrag -> {
            if (phase != ViewerPhase.Opened) return
            dismissOffsetY = action.offsetY
        }

        FullscreenImageViewerAction.MenuClick -> Unit
    }
}
