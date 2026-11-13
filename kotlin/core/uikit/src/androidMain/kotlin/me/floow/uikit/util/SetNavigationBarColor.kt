package me.floow.uikit.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SetNavigationBarColor(color: Color) {
    SetSystemBarStyle(navigationBarColor = color)
}
