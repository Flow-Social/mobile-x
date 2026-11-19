package me.floow.uikit.chat.common

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun ChatModalWindow(
	onDismissRequest: () -> Unit,
	content: @Composable () -> Unit,
) {
	Dialog(
		onDismissRequest = onDismissRequest,
		properties = DialogProperties(
			usePlatformDefaultWidth = false,
			decorFitsSystemWindows = false,
			dismissOnClickOutside = false,
		),
	) {
		val view = LocalView.current
		remember(view) {
			// Only touch the dialog's window. The activity still owns the input method.
			var parent = view.parent
			while (parent != null && parent !is DialogWindowProvider) {
				parent = (parent as? View)?.parent
			}
			(parent as? DialogWindowProvider)?.window
		}?.let { window ->
			DisposableEffect(window) {
				window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
				window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
				window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
				window.setDimAmount(0f)
				window.setWindowAnimations(0)
				onDispose { }
			}
		}
		content()
	}
}
