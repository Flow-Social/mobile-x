package me.floow.app.swipe

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import me.floow.shared.profile.ui.segments.summary.ProfileSummarySegment
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerV2
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.util.SwipeBackOverlay
import me.floow.uikit.util.overlayHorizontalSwipeZone
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.launch

class OverlaySwipeScenariosTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun image_preview_page2_swipe_right_goes_page1_not_close() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ImagePreviewHarness(
                initialPage = 1,
                onClose = { closed.set(true) }
            )
        }

        composeRule.onNodeWithTag("image_preview_state").assertTextEquals("page:1")
        composeRule.onNodeWithTag("image_preview_pager").performTouchInput { swipeRight() }

        composeRule.waitUntil(2_000) {
            try {
                composeRule.onNodeWithTag("image_preview_state").assertTextEquals("page:0")
                true
            } catch (_: Throwable) {
                false
            }
        }
        assertFalse(closed.get())
    }

    @Test
    fun image_preview_page1_swipe_right_does_not_close() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ImagePreviewHarness(
                initialPage = 0,
                onClose = { closed.set(true) }
            )
        }

        composeRule.onNodeWithTag("image_preview_pager").performTouchInput { swipeRight() }

        composeRule.waitForIdle()
        assertFalse(closed.get())
    }

    @Test
    fun image_preview_swipe_right_anywhere_does_not_close_overlay() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ImagePreviewHarness(
                initialPage = 0,
                onClose = { closed.set(true) }
            )
        }

        composeRule.onNodeWithTag("image_preview_container").performTouchInput { swipeRight() }

        composeRule.waitForIdle()
        assertFalse(closed.get())
    }

    @Test
    fun image_preview_vertical_swipe_dismisses_overlay() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ImagePreviewHarness(
                initialPage = 0,
                dismissThresholdDp = 72.dp,
                onClose = { closed.set(true) }
            )
        }

        composeRule.onNodeWithTag("image_preview_pager").performTouchInput { swipeUp() }

        composeRule.waitUntil(4_000) { closed.get() }
    }

    @Test
    fun image_preview_vertical_drag_reveals_background() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ImagePreviewHarness(
                initialPage = 0,
                dismissThresholdDp = 280.dp,
                onClose = { closed.set(true) }
            )
        }

        composeRule.onNodeWithTag("image_preview_pager").performTouchInput {
            down(center)
            moveBy(Offset(0f, 180f))
            up()
        }

        composeRule.waitForIdle()
        val progressText = composeRule
            .onNodeWithTag("image_preview_dismiss_progress")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
        val progressValue = progressText.substringAfter("progress:").toIntOrNull() ?: 0
        assertTrue(progressValue > 0)
        assertFalse(closed.get())
    }

    @Test
    fun image_preview_double_tap_zooms_in_and_out() {
        composeRule.setContent {
            RealViewerHarness(openOnStart = false)
        }

        composeRule.onNodeWithTag("fullscreen_viewer_pager").performTouchInput { doubleClick() }
        composeRule.waitUntil(3_000) {
            readIntFromNode(tag = "real_viewer_zoom_state", prefix = "zoom:") > 100
        }

        composeRule.onNodeWithTag("fullscreen_viewer_pager").performTouchInput { doubleClick() }
        composeRule.waitUntil(3_000) {
            readIntFromNode(tag = "real_viewer_zoom_state", prefix = "zoom:") <= 105
        }
    }

    @Test
    fun image_preview_open_has_transition_actor_or_fallback() {
        composeRule.setContent {
            RealViewerHarness(openOnStart = true)
        }

        composeRule.waitUntil(3_000) {
            val progress = readIntFromNode(tag = "real_viewer_progress_state", prefix = "progress:")
            progress > 0
        }

        composeRule.waitUntil(3_000) {
            runCatching {
                composeRule.onNodeWithTag("viewer_transition_layer").fetchSemanticsNode()
            }.isSuccess || runCatching {
                composeRule.onNodeWithTag("viewer_content_layer").fetchSemanticsNode()
            }.isSuccess
        }
    }

    @Test
    fun profile_header_page2_swipe_right_goes_page1_not_close() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ProfileHeaderHarness(onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("profile_summary_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("profile_summary_pager").performTouchInput { swipeRight() }

        composeRule.waitForIdle()
        assertFalse(closed.get())
    }

    @Test
    fun profile_header_page1_swipe_right_closes() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            ProfileHeaderHarness(onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("profile_summary_pager").performTouchInput { swipeRight() }

        composeRule.waitUntil(4_000) { closed.get() }
    }

    @Test
    fun postscreen_preview_page2_close_returns_to_page2_not_page1() {
        composeRule.setContent {
            PostscreenPreviewCloseSyncHarness()
        }

        composeRule.onNodeWithTag("postscreen_preview_pager").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("postscreen_preview_pager").performTouchInput { swipeUp() }

        composeRule.waitUntil(4_000) {
            try {
                composeRule.onNodeWithTag("postscreen_source_page_state").assertTextEquals("sourcePage:1")
                composeRule.onNodeWithTag("postscreen_close_target_state")
                    .assertTextEquals("target:post:1:image:1")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun readIntFromNode(tag: String, prefix: String): Int {
        val text = composeRule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString(separator = "") { it.text }
        return text.substringAfter(prefix).toIntOrNull() ?: 0
    }
}

@Composable
private fun ImagePreviewHarness(
    initialPage: Int,
    dismissThresholdDp: androidx.compose.ui.unit.Dp = 120.dp,
    onClose: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
    val zoneKey = remember { "image_preview_harness_zone" }
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val dismissThresholdPx = with(LocalDensity.current) { dismissThresholdDp.toPx() }
    var maxDismissProgress by remember { mutableFloatStateOf(0f) }

    SwipeBackOverlay(
        onClose = onClose,
        threshold = 48.dp,
        animateIn = false,
        scrimMaxAlpha = 0f
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .testTag("image_preview_container")
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("image_preview_pager")
                    .imagePreviewVerticalDismissHarness(
                        touchSlopPx = touchSlopPx,
                        thresholdPx = dismissThresholdPx,
                        onDismissRequest = onClose,
                        onProgress = { progress ->
                            if (progress > maxDismissProgress) {
                                maxDismissProgress = progress
                            }
                        }
                    )
                    .overlayHorizontalSwipeZone(
                        zoneKey = zoneKey,
                        atStart = pagerState.currentPage == 0,
                        blockOverlay = true,
                        priority = 100
                    )
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (page % 2 == 0) Color(0xFFD8E6FF) else Color(0xFFFFE5D8))
                )
            }

            Text(
                text = "page:${pagerState.currentPage}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("image_preview_state")
            )

            Text(
                text = "progress:${(maxDismissProgress * 100f).roundToInt()}",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("image_preview_dismiss_progress")
            )
        }
    }
}

@Composable
private fun RealViewerHarness(
    openOnStart: Boolean
) {
    val images = remember {
        listOf(
            "https://example.com/image-1.jpg",
            "https://example.com/image-2.jpg"
        )
    }
    val state = rememberFullscreenImageViewerState(
        phase = if (openOnStart) me.floow.uikit.components.media.viewer2.ViewerPhase.Closed
        else me.floow.uikit.components.media.viewer2.ViewerPhase.Opened,
        page = 0
    )
    val model = remember(images) {
        FullscreenImageViewerModel(
            images = images,
            title = "Preview",
            subtitleProvider = { "item:$it" },
            originForPage = { page ->
                SharedImageOrigin(
                    sourceKey = "test:image:$page",
                    rectInWindow = Rect(48f, 220f, 360f, 640f),
                    aspectRatio = 3f / 4f
                )
            }
        )
    }

    LaunchedEffect(openOnStart) {
        if (openOnStart) {
            state.reduce(
                FullscreenImageViewerAction.Open(
                    page = 0,
                    origin = model.originForPage(0)
                ),
                images.size
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        FullscreenImageViewerV2(
            model = model,
            state = state,
            onAction = { action ->
                state.reduce(action, images.size)
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("real_viewer_root")
        )

        Text(
            text = "zoom:${(state.zoom * 100f).roundToInt()}",
            modifier = Modifier
                .align(Alignment.TopStart)
                .testTag("real_viewer_zoom_state")
        )
        Text(
            text = "progress:${(state.transitionProgress * 100f).roundToInt()}",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .testTag("real_viewer_progress_state")
        )
    }
}

private fun Modifier.imagePreviewVerticalDismissHarness(
    touchSlopPx: Float,
    thresholdPx: Float,
    onDismissRequest: () -> Unit,
    onProgress: (Float) -> Unit
): Modifier {
    return pointerInput(touchSlopPx, thresholdPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var totalDx = 0f
            var totalDy = 0f
            var isDragging = false
            var offsetY = 0f

            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break

                val dx = change.position.x - change.previousPosition.x
                val dy = change.position.y - change.previousPosition.y
                totalDx += dx
                totalDy += dy

                if (!isDragging && abs(totalDy) > touchSlopPx && abs(totalDy) > abs(totalDx)) {
                    isDragging = true
                }

                if (isDragging) {
                    change.consume()
                    offsetY += dy
                    onProgress((abs(offsetY) / thresholdPx).coerceIn(0f, 1f))
                }
            }

            if (isDragging) {
                if (abs(offsetY) >= thresholdPx) {
                    onDismissRequest()
                }
                onProgress(0f)
            }
        }
    }
}

@Composable
private fun ProfileHeaderHarness(
    onClose: () -> Unit
) {
    SwipeBackOverlay(
        onClose = onClose,
        threshold = 48.dp,
        animateIn = false,
        scrimMaxAlpha = 0f
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            ProfileSummarySegment(
                profileAvatarUri = "https://example.com/avatar.png",
                displayName = "test_user",
                description = "About test user",
                totalLikesReceived = 123,
                statusLabel = "online",
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun PostscreenPreviewCloseSyncHarness() {
    val sourcePagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val previewPagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var previewVisible by remember { mutableStateOf(true) }
    var closeTargetKey by remember { mutableStateOf("target:none") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        HorizontalPager(
            state = sourcePagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (page % 2 == 0) Color(0xFFE6F4EA) else Color(0xFFE8F0FE))
            )
        }

        Text(
            text = "sourcePage:${sourcePagerState.currentPage}",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("postscreen_source_page_state")
        )

        Text(
            text = closeTargetKey,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .testTag("postscreen_close_target_state")
        )

        if (previewVisible) {
            HorizontalPager(
                state = previewPagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("postscreen_preview_pager")
                    .imagePreviewVerticalDismissHarness(
                        touchSlopPx = LocalViewConfiguration.current.touchSlop,
                        thresholdPx = with(LocalDensity.current) { 72.dp.toPx() },
                        onDismissRequest = {
                            val closePage = if (previewPagerState.isScrollInProgress) {
                                previewPagerState.targetPage
                            } else {
                                previewPagerState.currentPage
                            }
                            scope.launch {
                                sourcePagerState.scrollToPage(closePage)
                                withFrameNanos { }
                                closeTargetKey = "target:post:1:image:$closePage"
                                previewVisible = false
                            }
                        },
                        onProgress = {}
                    )
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (page % 2 == 0) Color(0xFF4A4A4A) else Color(0xFF1E1E1E))
                )
            }
        }
    }
}
