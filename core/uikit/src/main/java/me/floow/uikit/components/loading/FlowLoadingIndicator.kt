package me.floow.uikit.components.loading

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

const val FLOW_LOADING_INDICATOR_TAG = "flow_loading_indicator"

object FlowLoadingIndicatorDefaults {
    val Size: Dp = 48.dp
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LoadingIndicatorDefaults.indicatorColor,
) {
    LoadingIndicator(
        modifier = modifier
            .requiredSize(FlowLoadingIndicatorDefaults.Size)
            .testTag(FLOW_LOADING_INDICATOR_TAG),
        color = color,
    )
}
