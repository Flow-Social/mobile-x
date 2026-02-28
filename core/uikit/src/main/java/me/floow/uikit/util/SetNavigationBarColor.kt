package me.floow.uikit.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import me.floow.uikit.theme.LocalSystemBarStyle

@Composable
fun SetNavigationBarColor(color: Color) {
    val systemBarStyle = LocalSystemBarStyle.current

    SideEffect {
        systemBarStyle.value = systemBarStyle.value.copy(navigationBarColor = color)
    }
}
