package me.floow.profile.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

@Composable
internal fun SheetStatusBarStyle(
    appearance: StatusBarAppearance,
) {
    val view = LocalView.current
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window ?: return

    SideEffect {
        @Suppress("DEPRECATION")
        dialogWindow.statusBarColor = appearance.color.toArgb()
        WindowCompat.getInsetsController(dialogWindow, view).isAppearanceLightStatusBars =
            appearance.darkIcons
    }
}
