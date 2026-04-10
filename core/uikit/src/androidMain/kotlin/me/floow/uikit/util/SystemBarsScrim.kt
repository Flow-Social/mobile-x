package me.floow.uikit.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Publishes a temporary system bar override through the central system bar controller.
 */
@Composable
fun SystemBarsScrim(
    visible: Boolean,
    scrim: Color = Color.Transparent,
    navigationScrim: Color = Color.Transparent
) {
    if (visible) {
        SetSystemBarStyle(
            statusBarColor = scrim,
            darkStatusBarIcons = false,
            navigationBarColor = navigationScrim,
            darkNavigationBarIcons = false,
            navigationBarContrastEnforced = false
        )
    }
}
