package me.floow.uikit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

@Composable
expect fun PlatformSystemAppearance(
    colorScheme: ColorScheme,
    systemBarStyle: SystemBarStyle
)
