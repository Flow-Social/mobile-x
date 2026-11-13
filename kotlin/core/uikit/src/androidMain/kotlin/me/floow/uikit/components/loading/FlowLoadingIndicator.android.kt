package me.floow.uikit.components.loading

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LoadingIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun PlatformFlowLoadingIndicator(
    modifier: Modifier,
    color: Color,
) {
    LoadingIndicator(
        modifier = modifier
            .requiredSize(FlowLoadingIndicatorDefaults.Size)
            .testTag(FLOW_LOADING_INDICATOR_TAG),
        color = color,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal actual fun platformFlowLoadingIndicatorColor(): Color = LoadingIndicatorDefaults.indicatorColor
