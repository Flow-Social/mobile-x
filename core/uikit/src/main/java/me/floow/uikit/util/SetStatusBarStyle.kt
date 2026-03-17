package me.floow.uikit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import me.floow.uikit.theme.LocalSystemBarStyle
import me.floow.uikit.theme.SystemBarStyle

@Composable
fun SetSystemBarStyle(
    statusBarColor: Color? = null,
    darkStatusBarIcons: Boolean? = null,
    navigationBarColor: Color? = null,
    darkNavigationBarIcons: Boolean? = null,
    navigationBarContrastEnforced: Boolean? = null
) {
    val controller = LocalSystemBarStyle.current
    val token = remember { Any() }
    val style = SystemBarStyle(
        statusBarColor = statusBarColor,
        useDarkStatusBarIcons = darkStatusBarIcons,
        navigationBarColor = navigationBarColor,
        useDarkNavigationBarIcons = darkNavigationBarIcons,
        isNavigationBarContrastEnforced = navigationBarContrastEnforced
    )

    SideEffect {
        controller.update(token, style)
    }

    DisposableEffect(controller, token) {
        onDispose {
            controller.remove(token)
        }
    }
}

@Composable
fun SetStatusBarStyle(
    color: Color,
    darkIcons: Boolean
) {
    SetSystemBarStyle(
        statusBarColor = color,
        darkStatusBarIcons = darkIcons
    )
}
