package me.floow.explore.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.floow.uikit.components.topbar.TitleTopBarWithActionButton

@Composable
internal fun ExploreScreen(
    strings: ExploreStrings,
    onNotificationsClick: () -> Unit,
    topBarActionIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            TitleTopBarWithActionButton(
                titleText = strings.title,
                onActionButtonClick = onNotificationsClick,
                icon = topBarActionIcon
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Text(
                text = strings.headline,
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(4.dp))

            Text(text = strings.subtitle)
        }
    }
}
