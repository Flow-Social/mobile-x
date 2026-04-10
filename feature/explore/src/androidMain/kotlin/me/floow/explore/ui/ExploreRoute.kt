package me.floow.explore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.flowme.overview.R
import me.floow.uikit.util.SetNavigationBarColor

@Composable
fun ExploreRoute(
    modifier: Modifier = Modifier
) {
    SharedExploreRoute(
        strings = ExploreStrings(
            title = stringResource(R.string.explore_title),
            headline = stringResource(R.string.explore_headline),
            subtitle = stringResource(R.string.explore_subtitle),
        ),
        onNotificationsClick = {},
        topBarActionIcon = {
            NotificationBellIcon(hasBadge = false)
        },
        modifier = modifier
    )

    SetNavigationBarColor(
        MaterialTheme.colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation)
    )
}

@Composable
private fun NotificationBellIcon(
    hasBadge: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(24.dp)
    ) {
        Icon(
            painter = painterResource(me.floow.uikit.R.drawable.notification_bell),
            contentDescription = null,
            modifier = Modifier.matchParentSize()
        )

        if (hasBadge) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .size(9.dp)
                    .align(Alignment.TopEnd)
            )
        }
    }
}
