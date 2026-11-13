package me.floow.uikit.verification

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.states.TELEGRAM_CONTAINER_SLIDE_DURATION_MS
import me.floow.uikit.chat.states.TELEGRAM_CONTAINER_SLIDE_INITIAL_ALPHA
import me.floow.uikit.chat.states.TELEGRAM_CONTAINER_SLIDE_OFFSET_DP
import me.floow.uikit.chat.states.TELEGRAM_DUAL_SCROLL_THRESHOLD
import me.floow.uikit.chat.states.ChatTimelineItem
import me.floow.uikit.chat.states.findTimelineMessageIndex
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.states.findFlattenedMessageIndex
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.calculateGracefulFadeOut
import me.floow.uikit.components.media.viewer2.calculateGracefulFadeOutAlpha
import me.floow.uikit.components.media.viewer2.calculateGracefulFadeOutScale
import me.floow.uikit.components.media.viewer2.calculateViewerBackdropAlpha
import me.floow.uikit.components.media.viewer2.isOriginOnScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ChallengerVerificationTest {

    // --- 1. Dual-Mode Scroll Constants & Threshold Behavior ---

    @Test
    fun verify_telegram_scroll_constants_match_specification() {
        assertEquals("Dual-scroll threshold must be exactly 12", 12, TELEGRAM_DUAL_SCROLL_THRESHOLD)
        assertEquals("Container slide duration must be 220ms", 220, TELEGRAM_CONTAINER_SLIDE_DURATION_MS)
        assertEquals("Container slide offset must be 56dp", 56.dp, TELEGRAM_CONTAINER_SLIDE_OFFSET_DP)
        assertEquals("Container slide initial alpha must be 0.6f", 0.6f, TELEGRAM_CONTAINER_SLIDE_INITIAL_ALPHA, 0.001f)
    }

    @Test
    fun verify_dual_scroll_threshold_decision_boundary() {
        fun shouldSmoothScroll(firstVisibleItemIndex: Int): Boolean {
            return firstVisibleItemIndex <= TELEGRAM_DUAL_SCROLL_THRESHOLD
        }

        assertTrue("Index 0 should smooth scroll", shouldSmoothScroll(0))
        assertTrue("Index 6 should smooth scroll", shouldSmoothScroll(6))
        assertTrue("Index 12 (exact threshold) should smooth scroll", shouldSmoothScroll(12))
        assertFalse("Index 13 (threshold + 1) should jump + slide-in", shouldSmoothScroll(13))
        assertFalse("Index 50 should jump + slide-in", shouldSmoothScroll(50))
    }

    @Test
    fun challenge_dual_scroll_reverse_layout_vs_non_reverse_edge_case() {
        // In reverse layout (NewestAtBottom), bottom target is index 0.
        // Distance to bottom is abs(firstVisibleItemIndex - 0) = firstVisibleItemIndex.
        // So firstVisibleItemIndex <= 12 correctly represents distance <= 12.
        val isReverseLayout = true
        val totalItems = 50
        val targetIndexReverse = if (isReverseLayout) 0 else totalItems - 1
        val currentIndexScrolledDeep = 25
        val distanceReverse = abs(currentIndexScrolledDeep - targetIndexReverse)
        assertEquals(25, distanceReverse)
        assertTrue("Scrolled 25 items away in reverse layout exceeds threshold 12", distanceReverse > TELEGRAM_DUAL_SCROLL_THRESHOLD)

        // In NON-reverse layout (oldest at top), bottom target is totalItems - 1 (index 49).
        // If user is at item 2 (near top, 47 items from bottom), firstVisibleItemIndex is 2.
        // But 2 <= 12 is true! An un-adapted check of `firstVisibleItemIndex <= 12` would mistakenly smooth scroll!
        val nonReverseLayout = false
        val targetIndexNonReverse = if (nonReverseLayout) 0 else totalItems - 1
        val currentIndexAtTop = 2
        val actualDistanceNonReverse = abs(currentIndexAtTop - targetIndexNonReverse)
        assertEquals(47, actualDistanceNonReverse)
        // This empirically exposes that HasDataTimelineSupport's `firstVisibleItemIndex <= 12` relies on isReverseLayout = true
        val directThresholdCheck = currentIndexAtTop <= TELEGRAM_DUAL_SCROLL_THRESHOLD
        assertTrue("Empirical caveat: checking firstVisibleItemIndex directly is only valid when isReverseLayout is true", directThresholdCheck)
    }

    // --- 2. Viewer Backdrop Opacity Formula: alpha = max(0.5f, 1f - dragProgress) ---

    @Test
    fun verify_backdrop_opacity_formula_adheres_to_spec() {
        assertEquals(1.0f, calculateViewerBackdropAlpha(0.0f), 0.001f)
        assertEquals(0.85f, calculateViewerBackdropAlpha(0.15f), 0.001f)
        assertEquals(0.7f, calculateViewerBackdropAlpha(0.3f), 0.001f)
        assertEquals(0.5f, calculateViewerBackdropAlpha(0.5f), 0.001f)
        // Above 0.5 dragProgress, the alpha must remain clamped to the 0.5f floor
        assertEquals(0.5f, calculateViewerBackdropAlpha(0.6f), 0.001f)
        assertEquals(0.5f, calculateViewerBackdropAlpha(0.8f), 0.001f)
        assertEquals(0.5f, calculateViewerBackdropAlpha(1.0f), 0.001f)
    }

    @Test
    fun verify_backdrop_opacity_adversarial_inputs() {
        // Negative progress (dragged upward past origin) should clamp to 1.0f
        assertEquals(1.0f, calculateViewerBackdropAlpha(-0.5f), 0.001f)
        assertEquals(1.0f, calculateViewerBackdropAlpha(-100f), 0.001f)
        // Overshot progress (> 1.0) should clamp to 0.5f floor
        assertEquals(0.5f, calculateViewerBackdropAlpha(1.5f), 0.001f)
        assertEquals(0.5f, calculateViewerBackdropAlpha(100f), 0.001f)
    }

    @Test
    fun challenge_discrepancy_with_unclamped_formula() {
        // Empirically compare Telegram formula max(0.5f, 1f - p) with legacy (1f - p).coerceIn(0f, 1f)
        val progress = 0.8f
        val specAlpha = calculateViewerBackdropAlpha(progress)
        val legacyAlpha = (1f - progress).coerceIn(0f, 1f)
        assertEquals(0.5f, specAlpha, 0.001f)
        assertEquals(0.2f, legacyAlpha, 0.001f)
        // This empirically confirms that unclamped formula drops 60% below the 0.5f floor at 0.8 progress
        assertTrue(specAlpha > legacyAlpha)
    }

    // --- 3. Graceful Fade-Out Dismissal (scale: 1.0f -> 0.9f, alpha: 1.0f -> 0.0f) ---

    @Test
    fun verify_graceful_fade_out_scale_and_alpha() {
        // At progress 0 (dismiss start)
        assertEquals(1.0f, calculateGracefulFadeOutScale(0f), 0.001f)
        assertEquals(1.0f, calculateGracefulFadeOutAlpha(0f), 0.001f)

        // At progress 0.5 (midway)
        assertEquals(0.95f, calculateGracefulFadeOutScale(0.5f), 0.001f)
        assertEquals(0.5f, calculateGracefulFadeOutAlpha(0.5f), 0.001f)

        // At progress 1.0 (dismiss complete)
        assertEquals(0.9f, calculateGracefulFadeOutScale(1f), 0.001f)
        assertEquals(0.0f, calculateGracefulFadeOutAlpha(1f), 0.001f)

        // Composite transform helper
        val transformStart = calculateGracefulFadeOut(0f)
        assertEquals(1.0f, transformStart.scale, 0.001f)
        assertEquals(1.0f, transformStart.alpha, 0.001f)

        val transformEnd = calculateGracefulFadeOut(1f)
        assertEquals(0.9f, transformEnd.scale, 0.001f)
        assertEquals(0.0f, transformEnd.alpha, 0.001f)
    }

    @Test
    fun verify_is_origin_on_screen_bounds_detection() {
        val screenW = 1080f
        val screenH = 2400f

        // 1. Fully on-screen rect
        val onScreen = Rect(100f, 200f, 500f, 600f)
        assertTrue(isOriginOnScreen(onScreen, screenW, screenH))

        // 2. Null rect
        assertFalse(isOriginOnScreen(null, screenW, screenH))

        // 3. Completely above screen (scrolled up past top edge)
        val aboveScreen = Rect(100f, -500f, 500f, -10f)
        assertFalse(isOriginOnScreen(aboveScreen, screenW, screenH))

        // 4. Touching top edge boundary
        val touchTop = Rect(100f, -100f, 500f, 0f)
        assertFalse(isOriginOnScreen(touchTop, screenW, screenH))

        // 5. Partially visible at top edge (bottom > 0)
        val partialTop = Rect(100f, -50f, 500f, 50f)
        assertTrue(isOriginOnScreen(partialTop, screenW, screenH))

        // 6. Completely below screen (scrolled down past bottom edge)
        val belowScreen = Rect(100f, 2410f, 500f, 2900f)
        assertFalse(isOriginOnScreen(belowScreen, screenW, screenH))

        // 7. Partially visible at bottom edge
        val partialBottom = Rect(100f, 2350f, 500f, 2450f)
        assertTrue(isOriginOnScreen(partialBottom, screenW, screenH))

        // 8. Completely off to the left or right
        val offLeft = Rect(-300f, 200f, 0f, 600f)
        val offRight = Rect(1080f, 200f, 1500f, 600f)
        assertFalse(isOriginOnScreen(offLeft, screenW, screenH))
        assertFalse(isOriginOnScreen(offRight, screenW, screenH))
    }

    // --- 4. Dynamic originForPage Resolution ---

    @Test
    fun verify_dynamic_origin_for_page_lambda_resolution() {
        val origins = mapOf(
            0 to SharedImageOrigin("img:0", Rect(10f, 10f, 100f, 100f), 1.0f),
            1 to SharedImageOrigin("img:1", Rect(20f, 20f, 200f, 200f), 1.5f),
            2 to SharedImageOrigin("img:2", Rect(30f, 30f, 300f, 300f), 0.75f),
        )

        val model = FullscreenImageViewerModel(
            images = listOf("url0", "url1", "url2"),
            title = "Dynamic Origin Test",
            subtitleProvider = { "$it / 3" },
            originForPage = { page -> origins[page] }
        )

        // Resolve each page dynamically
        assertEquals("img:0", model.resolveOriginForPage(0)?.sourceKey)
        assertEquals("img:1", model.resolveOriginForPage(1)?.sourceKey)
        assertEquals("img:2", model.resolveOriginForPage(2)?.sourceKey)

        // Out-of-bounds page returns null safely
        assertNull(model.resolveOriginForPage(99))
        assertNull(model.resolveOriginForPage(-1))
    }

    @Test
    fun verify_default_origin_for_page_returns_null() {
        val model = FullscreenImageViewerModel(
            images = listOf("url0"),
            title = "Default Origin Test",
            subtitleProvider = { "" }
        )
        assertNull(model.resolveOriginForPage(0))
    }

    // --- 5. Timeline Index Resolution & Highlight Target ---

    @Test
    fun verify_find_timeline_message_index_with_direct_and_image_messages() {
        val sampleOutMessage = PrimaryOutMessage(
            id = 101L,
            createdAtMillis = 1000L,
            messageText = "Hello"
        )
        val imageMessage = ImageMessage(
            id = 102L,
            createdAtMillis = 2000L,
            images = listOf(
                ChatImageItem(id = 201L, remoteUrl = "https://example.com/1.jpg"),
                ChatImageItem(id = 202L, remoteUrl = "https://example.com/2.jpg")
            ),
            isOutgoing = false
        )

        val timelineItems = listOf(
            ChatTimelineItem.DateHeader(1000L),
            ChatTimelineItem.MessageRow(1000L, sampleOutMessage),
            ChatTimelineItem.MessageRow(2000L, imageMessage)
        )

        // Direct message lookup
        assertEquals(1, findTimelineMessageIndex(timelineItems, 101L))
        assertEquals(2, findTimelineMessageIndex(timelineItems, 102L))

        // Image attachment ID inside image message
        assertEquals(2, findTimelineMessageIndex(timelineItems, 201L))
        assertEquals(2, findTimelineMessageIndex(timelineItems, 202L))

        // Non-existent ID returns null
        assertNull(findTimelineMessageIndex(timelineItems, 999L))
        assertNull(findTimelineMessageIndex(timelineItems, null))
    }
}
