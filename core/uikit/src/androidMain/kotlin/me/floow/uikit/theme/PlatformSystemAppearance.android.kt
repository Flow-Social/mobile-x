package me.floow.uikit.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

@Composable
actual fun PlatformSystemAppearance(
    colorScheme: ColorScheme,
    systemBarStyle: SystemBarStyle
) {
    val view = LocalView.current
    val defaultNavigationBarContrastEnforced = remember(view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !view.isInEditMode) {
            (view.context as Activity).window.isNavigationBarContrastEnforced
        } else {
            false
        }
    }

    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as Activity).window
        window.setBackgroundDrawable(ColorDrawable(colorScheme.surfaceContainer.toArgb()))

        val appliedStatusBarColor = systemBarStyle.statusBarColor ?: colorScheme.background
        val appliedNavigationBarColor = systemBarStyle.navigationBarColor
            ?: colorScheme.surfaceContainer

        window.statusBarColor = appliedStatusBarColor.toArgb()
        window.navigationBarColor = appliedNavigationBarColor.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced =
                systemBarStyle.isNavigationBarContrastEnforced ?: defaultNavigationBarContrastEnforced
        }

        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
            systemBarStyle.useDarkStatusBarIcons ?: (appliedStatusBarColor.luminance() > 0.5f)
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
            systemBarStyle.useDarkNavigationBarIcons ?: (appliedNavigationBarColor.luminance() > 0.5f)
    }
}
