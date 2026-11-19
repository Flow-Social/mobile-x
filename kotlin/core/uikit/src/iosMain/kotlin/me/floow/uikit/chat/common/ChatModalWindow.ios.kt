package me.floow.uikit.chat.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.ExperimentalForeignApi
import me.floow.uikit.theme.LocalColorScheme
import me.floow.uikit.theme.LocalTypography
import platform.UIKit.UIColor
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelAlert

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun ChatModalWindow(
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
) {
	val parent = LocalUIViewController.current
	val scene = parent.view.window?.windowScene
	if (scene == null) {
		content()
		return
	}
	val currentContent = rememberUpdatedState(content)
	val colors = rememberUpdatedState(MaterialTheme.colorScheme)
	val typography = rememberUpdatedState(MaterialTheme.typography)
	val shapes = rememberUpdatedState(MaterialTheme.shapes)
	val flowColors = rememberUpdatedState(LocalColorScheme.current)
	val flowTypography = rememberUpdatedState(LocalTypography.current)
	DisposableEffect(scene) {
		val controller = ComposeUIViewController(configure = { opaque = false }) {
			MaterialTheme(colorScheme = colors.value, typography = typography.value, shapes = shapes.value) {
				CompositionLocalProvider(
					LocalColorScheme provides flowColors.value,
					LocalTypography provides flowTypography.value,
				) {
					currentContent.value()
				}
			}
		}
		val window = UIWindow(windowScene = scene).apply {
			backgroundColor = UIColor.clearColor
			windowLevel = UIWindowLevelAlert + 1.0
			rootViewController = controller
			// Keep the composer in the key window so the keyboard remains open.
			hidden = false
		}
		onDispose {
			window.hidden = true
			window.rootViewController = null
		}
	}
}
