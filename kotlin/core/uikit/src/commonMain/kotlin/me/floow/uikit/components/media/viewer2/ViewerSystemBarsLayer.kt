package me.floow.uikit.components.media.viewer2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import me.floow.uikit.theme.LocalSystemBarStyle
import me.floow.uikit.theme.SystemBarStyle

@Composable
fun ViewerSystemBarsLayer(visible: Boolean) {
    if (visible) {
        val controller = LocalSystemBarStyle.current
        val token = remember { Any() }
        val style = remember {
            SystemBarStyle(
                statusBarColor = Color.Transparent,
                useDarkStatusBarIcons = false,
                navigationBarColor = Color.Transparent,
                useDarkNavigationBarIcons = false,
                isNavigationBarContrastEnforced = false
            )
        }
        SideEffect {
            controller.update(token, style)
        }
        DisposableEffect(controller, token) {
            onDispose {
                controller.remove(token)
            }
        }
    }
}
