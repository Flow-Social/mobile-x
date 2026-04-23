package me.floow.uikit.components.loading

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

@Composable
internal actual fun PlatformFlowLoadingIndicator(
    modifier: Modifier,
    color: Color,
) {
    CircularProgressIndicator(
        modifier = modifier
            .requiredSize(FlowLoadingIndicatorDefaults.Size)
            .testTag(FLOW_LOADING_INDICATOR_TAG),
        color = color,
    )
}

@Composable
internal actual fun platformFlowLoadingIndicatorColor(): Color = MaterialTheme.colorScheme.primary
