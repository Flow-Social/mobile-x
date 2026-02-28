package me.floow.uikit.util

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import me.floow.uikit.theme.LocalSystemBarStyle

/**
 * Applies system bar colors directly to the window, bypassing FlowTheme state updates
 * to avoid full-app recomposition lag.
 *
 * @param visible If true, applies [scrim] and [navigationScrim]. If false, restores defaults.
 * @param scrim Color for status bar suitable for viewer overlay (usually transparent).
 * @param navigationScrim Color for nav bar suitable for viewer overlay (usually transparent).
 */
@Composable
fun SystemBarsScrim(
    visible: Boolean,
    scrim: Color = Color.Transparent,
    navigationScrim: Color = Color.Transparent
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val activity = view.context as? Activity ?: return
    val window = activity.window
    val insetsController = WindowCompat.getInsetsController(window, view)
    val defaultNavBarContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced
    } else {
        false
    }

    // Capture default styles to restore later
    val defaultStyle = LocalSystemBarStyle.current
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()

    // 1. Apply overrides when visible
    // We use SideEffect to ensure it persists if recomposition happens (though ideally minimized)
    // But to avoid fighting FlowTheme, we rely on FlowTheme NOT recomposing since we don't touch its state.
    if (visible) {
        SideEffect {
            window.statusBarColor = scrim.toArgb()
            window.navigationBarColor = navigationScrim.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            insetsController.isAppearanceLightStatusBars = false // Always light content (white icons) on dark viewer
            insetsController.isAppearanceLightNavigationBars = false // Always light content (white icons) on dark viewer
        }
    }

    // 2. Restore defaults when leaving composition or visible becomes false
    DisposableEffect(visible) {
        onDispose {
            if (visible) {
                // Restore logic mimicking FlowTheme
                val style = defaultStyle.value
                val defaultStatusBarColor = style.statusBarColor ?: colorScheme.background
                val defaultNavBarColor = style.navigationBarColor 
                    ?: colorScheme.surfaceColorAtElevation(NavigationBarDefaults.Elevation)

                window.statusBarColor = defaultStatusBarColor.toArgb()
                window.navigationBarColor = defaultNavBarColor.toArgb()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = defaultNavBarContrastEnforced
                }

                insetsController.isAppearanceLightStatusBars = 
                    style.useDarkStatusBarIcons ?: !darkTheme
                insetsController.isAppearanceLightNavigationBars = 
                    style.useDarkNavigationBarIcons ?: !darkTheme
            }
        }
    }
}
