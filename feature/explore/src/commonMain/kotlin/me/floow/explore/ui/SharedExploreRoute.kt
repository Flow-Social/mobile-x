package me.floow.explore.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SharedExploreRoute(
    strings: ExploreStrings,
    onNotificationsClick: () -> Unit,
    topBarActionIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    ExploreScreen(
        strings = strings,
        onNotificationsClick = onNotificationsClick,
        topBarActionIcon = topBarActionIcon,
        modifier = modifier
    )
}
