package me.floow.uikit.components.loading

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val FLOW_LOADING_INDICATOR_TAG = "flow_loading_indicator"

object FlowLoadingIndicatorDefaults {
    val Size: Dp = 48.dp
}

@Composable
fun FlowLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = platformFlowLoadingIndicatorColor(),
) {
    PlatformFlowLoadingIndicator(
        modifier = modifier,
        color = color,
    )
}

@Composable
internal expect fun PlatformFlowLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color,
)

@Composable
internal expect fun platformFlowLoadingIndicatorColor(): Color
