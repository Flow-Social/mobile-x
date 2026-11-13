package me.floow.shared.feed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import me.floow.uikit.util.SystemBarsScrim
import androidx.compose.ui.graphics.Color

@Composable
internal actual fun FeedOverlayDialogHost(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        DisposableEffect(dialogWindowProvider) {
            dialogWindowProvider?.window?.let { window ->
                window.setDimAmount(0f)
                window.setWindowAnimations(0)
                window.setBackgroundDrawableResource(android.R.color.transparent)
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
            onDispose { }
        }
        content()
    }
}
