package me.floow.uikit.viewer2

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerReducerTest {

    @Test
    fun open_moves_state_to_opening_and_freezes_origin() {
        val state = state(phase = ViewerPhase.Closed)
        val origin = SharedImageOrigin(
            sourceKey = "post:1:image:0",
            rectInWindow = Rect(10f, 20f, 100f, 180f),
            aspectRatio = 0.7f
        )

        state.reduce(FullscreenImageViewerAction.Open(page = 2, origin = origin), imageCount = 5)

        assertEquals(ViewerPhase.Opening, state.phase)
        assertTrue(state.visible)
        assertEquals(2, state.page)
        assertEquals(origin, state.openOrigin)
        assertEquals(1f, state.zoom)
        assertEquals(Offset.Zero, state.pan)
        assertEquals(0f, state.dismissOffsetY)
    }

    @Test
    fun open_animation_finished_moves_to_opened() {
        val state = state(phase = ViewerPhase.Opening)

        state.reduce(FullscreenImageViewerAction.OpenAnimationFinished, imageCount = 3)

        assertEquals(ViewerPhase.Opened, state.phase)
        assertEquals(1f, state.transitionProgress)
    }

    @Test
    fun request_close_moves_to_closing_and_sets_close_origin() {
        val state = state(
            phase = ViewerPhase.Opened,
            zoom = 2.8f,
            pan = Offset(10f, 15f),
            dismissOffsetY = 55f,
            chromeVisible = true,
            openOrigin = SharedImageOrigin("post:1:image:0", Rect(0f, 0f, 10f, 10f), 1f)
        )
        val closeOrigin = SharedImageOrigin("post:1:image:2", Rect(20f, 30f, 60f, 90f), 1.3f)

        state.reduce(
            FullscreenImageViewerAction.RequestClose(
                origin = closeOrigin,
                page = 2
            ),
            imageCount = 3
        )

        assertEquals(ViewerPhase.Closing, state.phase)
        assertEquals(2, state.page)
        assertEquals(closeOrigin, state.closeOrigin)
        assertEquals(1f, state.zoom)
        assertEquals(Offset.Zero, state.pan)
        assertEquals(0f, state.dismissOffsetY)
        assertFalse(state.chromeVisible)
    }

    @Test
    fun request_close_without_origin_does_not_fallback_to_open_origin() {
        val openOrigin = SharedImageOrigin("post:1:image:0", Rect(0f, 0f, 10f, 10f), 1f)
        val state = state(
            phase = ViewerPhase.Opened,
            page = 1,
            openOrigin = openOrigin,
            closeOrigin = null
        )

        state.reduce(
            FullscreenImageViewerAction.RequestClose(
                origin = null,
                page = 1
            ),
            imageCount = 3
        )

        assertEquals(ViewerPhase.Closing, state.phase)
        assertEquals(1, state.page)
        assertEquals(null, state.closeOrigin)
    }

    @Test
    fun request_close_uses_page_from_action() {
        val state = state(
            phase = ViewerPhase.Opened,
            page = 0
        )

        state.reduce(
            FullscreenImageViewerAction.RequestClose(
                origin = null,
                page = 2
            ),
            imageCount = 5
        )

        assertEquals(ViewerPhase.Closing, state.phase)
        assertEquals(2, state.page)
    }

    @Test
    fun request_close_resets_dismiss_offset_for_stable_closing() {
        val state = state(
            phase = ViewerPhase.Opened,
            page = 1,
            dismissOffsetY = 84f
        )

        state.reduce(
            FullscreenImageViewerAction.RequestClose(
                origin = null,
                page = 1
            ),
            imageCount = 3
        )

        assertEquals(ViewerPhase.Closing, state.phase)
        assertEquals(0f, state.dismissOffsetY)
    }

    @Test
    fun close_animation_finished_clears_state_to_closed() {
        val state = state(
            phase = ViewerPhase.Closing,
            page = 3,
            openOrigin = SharedImageOrigin("post:7:image:0", Rect(1f, 2f, 3f, 4f), 1f),
            closeOrigin = SharedImageOrigin("post:7:image:3", Rect(4f, 5f, 8f, 9f), 0.8f)
        )

        state.reduce(FullscreenImageViewerAction.CloseAnimationFinished, imageCount = 5)

        assertEquals(ViewerPhase.Closed, state.phase)
        assertFalse(state.visible)
        assertEquals(0, state.page)
        assertEquals(null, state.openOrigin)
        assertEquals(null, state.closeOrigin)
    }

    @Test
    fun opened_only_actions_are_ignored_outside_opened_phase() {
        val state = state(phase = ViewerPhase.Opening, page = 1)

        state.reduce(FullscreenImageViewerAction.PageChanged(2), imageCount = 5)
        state.reduce(FullscreenImageViewerAction.ZoomChanged(2f, Offset(2f, 3f)), imageCount = 5)
        state.reduce(FullscreenImageViewerAction.DismissDrag(30f), imageCount = 5)
        state.reduce(FullscreenImageViewerAction.ToggleChrome, imageCount = 5)

        assertEquals(1, state.page)
        assertEquals(1f, state.zoom)
        assertEquals(Offset.Zero, state.pan)
        assertEquals(0f, state.dismissOffsetY)
        assertTrue(state.chromeVisible)
    }

    private fun state(
        phase: ViewerPhase,
        page: Int = 0,
        chromeVisible: Boolean = true,
        zoom: Float = 1f,
        pan: Offset = Offset.Zero,
        dismissOffsetY: Float = 0f,
        openOrigin: SharedImageOrigin? = null,
        closeOrigin: SharedImageOrigin? = null,
        transitionProgress: Float = 0f
    ): FullscreenImageViewerState {
        return FullscreenImageViewerState(
            phase = phase,
            page = page,
            chromeVisible = chromeVisible,
            zoom = zoom,
            pan = pan,
            dismissOffsetY = dismissOffsetY,
            openOrigin = openOrigin,
            closeOrigin = closeOrigin,
            transitionProgress = transitionProgress
        )
    }
}
