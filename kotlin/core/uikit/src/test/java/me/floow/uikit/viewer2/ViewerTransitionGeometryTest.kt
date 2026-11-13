package me.floow.uikit.viewer2

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import me.floow.uikit.components.media.viewer2.computeFitRect
import me.floow.uikit.components.media.viewer2.computeCropRectInBounds
import me.floow.uikit.components.media.viewer2.lerpRect
import me.floow.uikit.components.media.viewer2.viewerImageGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ViewerTransitionGeometryTest {

    @Test
    fun documents_old_late_resize_when_transition_and_content_use_different_aspects() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val fallbackAspect = containerWidth / containerHeight
        val realImageAspect = 4f / 3f
        val sourceRect = Rect(80f, 420f, 420f, 760f)

        val transitionEndRect = computeFitRect(containerWidth, containerHeight, fallbackAspect)
        val finalSettledRect = computeFitRect(containerWidth, containerHeight, realImageAspect)
        val animatedRect = lerpRect(sourceRect, transitionEndRect, 1f)

        val transitionGeometry = viewerImageGeometry(
            frameRect = animatedRect,
            bitmapAspect = realImageAspect,
            fitBlend = 1f
        )
        val settledGeometry = viewerImageGeometry(
            frameRect = finalSettledRect,
            bitmapAspect = realImageAspect,
            fitBlend = 1f
        )

        println(transitionGeometry.debugLine("transition/open", 1f, "legacy-transition", "post-1:image-0"))
        println(settledGeometry.debugLine("settled", 1f, "legacy-content", "post-1:image-0"))

        assertNotEquals(transitionEndRect, finalSettledRect)
        assertNotEquals(transitionGeometry, settledGeometry)
    }

    @Test
    fun transition_progress_one_matches_settled_progress_one() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val frozenAspect = 4f / 3f
        val sourceRect = Rect(80f, 420f, 420f, 760f)

        val transitionEndRect = computeFitRect(containerWidth, containerHeight, frozenAspect)
        val finalSettledRect = computeFitRect(containerWidth, containerHeight, frozenAspect)
        val animatedRect = lerpRect(sourceRect, transitionEndRect, 1f)

        val transitionGeometry = viewerImageGeometry(
            frameRect = animatedRect,
            bitmapAspect = frozenAspect,
            fitBlend = 1f
        )
        val settledGeometry = viewerImageGeometry(
            frameRect = finalSettledRect,
            bitmapAspect = frozenAspect,
            fitBlend = 1f
        )

        println(transitionGeometry.debugLine("transition/open", 1f, "ViewerImageRenderer", "post-1:image-0"))
        println(settledGeometry.debugLine("settled", 1f, "ViewerImageRenderer", "post-1:image-0"))

        assertEquals(transitionGeometry, settledGeometry)
    }

    @Test
    fun open_interpolates_outer_rect_and_image_crop_with_same_progress() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val bitmapAspect = 16f / 9f
        val sourceRect = Rect(80f, 420f, 420f, 760f)
        val targetRect = computeFitRect(containerWidth, containerHeight, bitmapAspect)

        val openStart = transitionGeometry(sourceRect, targetRect, bitmapAspect, progress = 0f)
        val openMiddle = transitionGeometry(sourceRect, targetRect, bitmapAspect, progress = 0.5f)
        val openEnd = transitionGeometry(sourceRect, targetRect, bitmapAspect, progress = 1f)
        val settledFullscreen = viewerImageGeometry(
            frameRect = targetRect,
            bitmapAspect = bitmapAspect,
            fitBlend = 1f
        )

        println(openStart.motionDebugLine("open", 0f))
        println(openMiddle.motionDebugLine("open", 0.5f))
        println(openEnd.motionDebugLine("open", 1f))
        println(settledFullscreen.motionDebugLine("settled fullscreen", 1f))

        assertEquals(viewerImageGeometry(sourceRect, bitmapAspect, fitBlend = 0f), openStart)
        assertNotEquals(openStart.drawRect, openMiddle.drawRect)
        assertNotEquals(openMiddle.drawRect, openEnd.drawRect)
        assertEquals(settledFullscreen, openEnd)
    }

    @Test
    fun back_start_and_end_match_fullscreen_and_source_geometry() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val frozenAspect = 3f / 4f
        val sourceRect = Rect(120f, 360f, 480f, 840f)
        val targetRect = computeFitRect(containerWidth, containerHeight, frozenAspect)

        val settledGeometry = viewerImageGeometry(
            frameRect = targetRect,
            bitmapAspect = frozenAspect,
            fitBlend = 1f
        )
        val backStartGeometry = viewerImageGeometry(
            frameRect = lerpRect(targetRect, sourceRect, 0f),
            bitmapAspect = frozenAspect,
            fitBlend = 1f
        )
        val backEndGeometry = viewerImageGeometry(
            frameRect = lerpRect(targetRect, sourceRect, 1f),
            bitmapAspect = frozenAspect,
            fitBlend = 0f
        )
        val sourceCardGeometry = viewerImageGeometry(
            frameRect = sourceRect,
            bitmapAspect = frozenAspect,
            fitBlend = 0f
        )

        println(settledGeometry.debugLine("settled", 1f, "ViewerImageRenderer", "post-1:image-0"))
        println(backStartGeometry.debugLine("transition/back", 1f, "ViewerImageRenderer", "post-1:image-0"))
        println(backEndGeometry.debugLine("transition/back", 0f, "ViewerImageRenderer", "post-1:image-0"))

        assertEquals(settledGeometry, backStartGeometry)
        assertEquals(sourceCardGeometry, backEndGeometry)
    }

    @Test
    fun back_interpolates_outer_rect_and_image_crop_with_same_progress() {
        val containerWidth = 1080f
        val containerHeight = 2400f
        val bitmapAspect = 16f / 9f
        val sourceRect = Rect(80f, 420f, 420f, 760f)
        val targetRect = computeFitRect(containerWidth, containerHeight, bitmapAspect)

        val backStart = transitionGeometry(sourceRect, targetRect, bitmapAspect, progress = 1f)
        val backMiddle = transitionGeometry(sourceRect, targetRect, bitmapAspect, progress = 0.5f)
        val backEnd = transitionGeometry(sourceRect, targetRect, bitmapAspect, progress = 0f)
        val settledSource = viewerImageGeometry(
            frameRect = sourceRect,
            bitmapAspect = bitmapAspect,
            fitBlend = 0f
        )

        println(backStart.motionDebugLine("back", 1f))
        println(backMiddle.motionDebugLine("back", 0.5f))
        println(backEnd.motionDebugLine("back", 0f))
        println(settledSource.motionDebugLine("after return settled source", 0f))

        assertEquals(viewerImageGeometry(targetRect, bitmapAspect, fitBlend = 1f), backStart)
        assertNotEquals(backStart.drawRect, backMiddle.drawRect)
        assertNotEquals(backMiddle.drawRect, backEnd.drawRect)
        assertEquals(settledSource, backEnd)
    }

    @Test
    fun source_crop_geometry_uses_clip_separately_from_draw_rect() {
        val sourceRect = Rect(120f, 360f, 480f, 840f)
        val bitmapAspect = 16f / 9f
        val geometry = viewerImageGeometry(
            frameRect = sourceRect,
            bitmapAspect = bitmapAspect,
            fitBlend = 0f
        )

        assertEquals(sourceRect, geometry.layoutRect)
        assertEquals(sourceRect, geometry.clipRect)
        assertEquals(computeCropRectInBounds(sourceRect, bitmapAspect), geometry.drawRect)
        assertEquals(ContentScale.Fit, geometry.contentScale)
    }

    private fun me.floow.uikit.components.media.viewer2.ViewerImageGeometry.debugLine(
        phase: String,
        progress: Float,
        renderMode: String,
        mediaId: String
    ): String {
        val scaleName = if (contentScale == ContentScale.Fit) "Fit" else contentScale.toString()
        return "phase=$phase mediaId=$mediaId pageIndex=0 progress=$progress " +
            "renderMode=$renderMode layoutRect=$layoutRect drawRect=$drawRect clipRect=$clipRect " +
            "contentScale=$scaleName visibleBitmapRect=$visibleBitmapRect bitmapAspect=$bitmapAspect " +
            "originStillRegistered=true"
    }

    private fun transitionGeometry(
        sourceRect: Rect,
        targetRect: Rect,
        bitmapAspect: Float,
        progress: Float
    ): me.floow.uikit.components.media.viewer2.ViewerImageGeometry {
        return viewerImageGeometry(
            frameRect = lerpRect(sourceRect, targetRect, progress),
            bitmapAspect = bitmapAspect,
            fitBlend = progress
        )
    }

    private fun me.floow.uikit.components.media.viewer2.ViewerImageGeometry.motionDebugLine(
        phase: String,
        progress: Float
    ): String {
        val imageScale = drawRect.width / layoutRect.width
        val imageTranslationX = drawRect.left - layoutRect.left
        val imageTranslationY = drawRect.top - layoutRect.top
        return "phase=$phase progress=$progress outerRect=$layoutRect " +
            "imageScale=$imageScale imageTranslation=($imageTranslationX,$imageTranslationY) " +
            "clipRect=$clipRect visibleBitmapRect=$visibleBitmapRect"
    }
}
