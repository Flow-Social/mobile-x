package me.floow.shared.profile.ui.segments.summary

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import me.floow.uikit.util.overlayHorizontalPagerSwipeZone

@Composable
fun ProfileSummarySegment(
    profileAvatarUri: String?,
    avatarThumb: String? = null,
    displayName: String?,
    description: String?,
    totalLikesReceived: Int,
    statusLabel: String?,
    modifier: Modifier = Modifier
) {
    val pageCount = 2
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pageCount })
    val pagerZoneKey = remember { "profile_summary_pager_zone" }

    Box(modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("profile_summary_pager")
                .overlayHorizontalPagerSwipeZone(
                    zoneKey = pagerZoneKey,
                    currentPage = pagerState.currentPage,
                )
        ) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                when (page) {
                    0 -> AvatarUsernameProfileSummaryPage(
                        profileAvatarUri = profileAvatarUri,
                        avatarThumb = avatarThumb,
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
                .padding(bottom = 10.dp),
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
