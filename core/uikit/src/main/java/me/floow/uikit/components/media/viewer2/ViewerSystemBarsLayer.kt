package me.floow.uikit.components.media.viewer2

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.floow.uikit.util.SystemBarsScrim

@Composable
fun ViewerSystemBarsLayer(visible: Boolean) {
    val scrim = Color.Black.copy(alpha = 0.6f)
    SystemBarsScrim(
        visible = visible,
        scrim = scrim,
        navigationScrim = scrim
    )
}
