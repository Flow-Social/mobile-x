package me.floow.shared.profile.ui.segments.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ProfileSummarySegment(
    profileAvatarUri: String?,
    displayName: String?,
    description: String?,
    totalLikesReceived: Int,
    statusLabel: String?,
    modifier: Modifier = Modifier
) {
    val pageCount = 2
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val scope = rememberCoroutineScope()

    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .height(390.dp)
                .pointerInput(pagerState, pageCount) {
                    val dragThresholdPx = 48.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (down.type != PointerType.Mouse) {
                            return@awaitEachGesture
                        }

                        var totalDragX = 0f
                        val dragChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                            if (abs(over.x) <= abs(over.y)) {
                                return@awaitTouchSlopOrCancellation
                            }
                            change.consume()
                            totalDragX += over.x
                        }

                        if (dragChange == null) {
                            return@awaitEachGesture
                        }

                        val completed = drag(dragChange.id) { change ->
                            val dragAmount = change.positionChange()
                            change.consume()
                            totalDragX += dragAmount.x
                        }

                        if (!completed) {
                            return@awaitEachGesture
                        }

                        val targetPage = when {
                            totalDragX <= -dragThresholdPx -> (pagerState.currentPage + 1).coerceAtMost(pageCount - 1)
                            totalDragX >= dragThresholdPx -> (pagerState.currentPage - 1).coerceAtLeast(0)
                            else -> pagerState.currentPage
                        }

                        if (targetPage != pagerState.currentPage) {
                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                        }
                    }
                }
                .testTag("profile_summary_pager")
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    0 -> AvatarUsernameProfileSummaryPage(
                        profileAvatarUri = profileAvatarUri,
                        displayName = displayName,
                        totalLikesReceived = totalLikesReceived,
                        statusLabel = statusLabel,
                        modifier = Modifier.fillMaxSize()
                    )

                    1 -> AboutMeProfileSummaryPage(
                        description = description,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(pageCount) { page ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (pagerState.currentPage == page) Color.White else Color.LightGray)
                        .size(6.dp)
                )
            }
        }
    }
}
