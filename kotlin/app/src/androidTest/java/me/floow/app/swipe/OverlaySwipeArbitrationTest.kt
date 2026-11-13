package me.floow.app.swipe

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import me.floow.uikit.util.SwipeBackOverlay
import me.floow.uikit.util.overlayHorizontalSwipeZone
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class OverlaySwipeArbitrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun overlay_swipe_inside_pager_not_start_does_not_close() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            PagerOverlayHarness(initialPage = 1, onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("pager_state").assertTextEquals("page:1")
        composeRule.onNodeWithTag("horizontal_zone").performTouchInput { swipeRight() }

        composeRule.waitUntil(2_000) {
            try {
                composeRule.onNodeWithTag("pager_state").assertTextEquals("page:0")
                true
            } catch (_: Throwable) {
                false
            }
        }
        assertFalse(closed.get())
    }

    @Test
    fun overlay_swipe_inside_pager_at_start_closes() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            PagerOverlayHarness(initialPage = 0, onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("horizontal_zone").performTouchInput { swipeRight() }

        composeRule.waitUntil(4_000) { closed.get() }
    }

    @Test
    fun overlay_swipe_outside_pager_closes_even_if_pager_not_start() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            PagerOverlayHarness(initialPage = 1, onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("outside_swipe_area").performTouchInput { swipeRight() }

        composeRule.waitUntil(4_000) { closed.get() }
    }

    @Test
    fun overlay_swipe_inside_lazyrow_not_start_does_not_close() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            LazyRowOverlayHarness(initialIndex = 2, onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("lazy_state").assertTextEquals("index:2")
        composeRule.onNodeWithTag("horizontal_zone").performTouchInput { swipeRight() }

        composeRule.waitUntil(2_000) {
            try {
                composeRule.onNodeWithTag("lazy_state").assertTextEquals("index:1")
                true
            } catch (_: Throwable) {
                false
            }
        }
        assertFalse(closed.get())
    }

    @Test
    fun overlay_swipe_inside_lazyrow_at_start_closes() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            LazyRowOverlayHarness(initialIndex = 0, onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("horizontal_zone").performTouchInput { swipeRight() }

        composeRule.waitUntil(4_000) { closed.get() }
    }

    @Test
    fun overlay_swipe_prefers_higher_priority_zone_on_overlap() {
        val closed = AtomicBoolean(false)

        composeRule.setContent {
            PriorityOverlapHarness(onClose = { closed.set(true) })
        }

        composeRule.onNodeWithTag("priority_overlap_area").performTouchInput { swipeRight() }

        composeRule.waitForIdle()
        assertFalse(closed.get())
    }
}

@Composable
private fun PagerOverlayHarness(
    initialPage: Int,
    onClose: () -> Unit,
    blockOverlay: Boolean = false
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 3 })
    val zoneKey = remember { "arbitration_pager_zone" }

    SwipeBackOverlay(
        onClose = onClose,
        threshold = 48.dp,
        animateIn = false,
        scrimMaxAlpha = 0f
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .testTag("outside_swipe_area")
            )

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.Center)
                    .testTag("horizontal_zone")
                    .overlayHorizontalSwipeZone(
                        zoneKey = zoneKey,
                        atStart = pagerState.currentPage == 0,
                        blockOverlay = blockOverlay
                    )
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (page % 2 == 0) Color(0xFFE5E5E5) else Color(0xFFC4D8FF))
                )
            }

            Text(
                text = "page:${pagerState.currentPage}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("pager_state")
            )
        }
    }
}

@Composable
private fun LazyRowOverlayHarness(
    initialIndex: Int,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val zoneKey = remember { "arbitration_lazy_zone" }

    SwipeBackOverlay(
        onClose = onClose,
        threshold = 48.dp,
        animateIn = false,
        scrimMaxAlpha = 0f
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .align(Alignment.TopCenter)
                    .testTag("outside_swipe_area")
            )

            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .align(Alignment.Center)
                    .testTag("horizontal_zone")
                    .overlayHorizontalSwipeZone(
                        zoneKey = zoneKey,
                        atStart = listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
                    )
            ) {
                items((0..8).toList()) { index ->
                    Box(
                        modifier = Modifier
                            .size(220.dp, 160.dp)
                            .background(if (index % 2 == 0) Color(0xFFD7FFD9) else Color(0xFFFFF1C7))
                    )
                }
            }

            Text(
                text = "index:${listState.firstVisibleItemIndex}",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .testTag("lazy_state")
            )
        }
    }
}

@Composable
private fun PriorityOverlapHarness(
    onClose: () -> Unit
) {
    val highPriorityKey = remember { "priority_overlap_high" }
    val lowPriorityKey = remember { "priority_overlap_low" }

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
                .overlayHorizontalSwipeZone(
                    zoneKey = highPriorityKey,
                    atStart = false,
                    blockOverlay = false,
                    priority = 100
                )
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.Center)
                    .testTag("priority_overlap_area")
                    .overlayHorizontalSwipeZone(
                        zoneKey = lowPriorityKey,
                        atStart = true,
                        blockOverlay = false,
                        priority = 0
                    )
            )
        }
    }
}
