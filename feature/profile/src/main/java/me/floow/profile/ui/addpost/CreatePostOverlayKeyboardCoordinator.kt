package me.floow.profile.ui.addpost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.delay

@Stable
internal class CreatePostOverlayKeyboardCoordinator {
	var dialogVisible by mutableStateOf(false)
		private set

	var isInputLaidOut by mutableStateOf(false)
		private set

	var isInitialKeyboardOpened by mutableStateOf(false)
		private set

	var isAutoFocusBlocked by mutableStateOf(false)
		private set

	private var restoreRequestId by mutableIntStateOf(0)

	fun showDialog() {
		dialogVisible = true
	}

	fun markInputLaidOut() {
		isInputLaidOut = true
	}

	fun requestInputFocusRestore() {
		restoreRequestId += 1
	}

	fun markInitialKeyboardOpened() {
		isInitialKeyboardOpened = true
	}

	fun blockAutoFocus() {
		isAutoFocusBlocked = true
	}

	internal fun currentRestoreRequestId(): Int = restoreRequestId
}

@Composable
internal fun rememberCreatePostOverlayKeyboardCoordinator(
	focusRequester: FocusRequester,
	keyboardController: SoftwareKeyboardController?,
	lifecycleOwner: LifecycleOwner,
	blockAutoFocus: Boolean,
): CreatePostOverlayKeyboardCoordinator {
	val coordinator = remember { CreatePostOverlayKeyboardCoordinator() }

	fun ensureInputFocus() {
		if (coordinator.isAutoFocusBlocked) return
		runCatching { focusRequester.requestFocus() }
		keyboardController?.show()
	}

	LaunchedEffect(Unit) {
		coordinator.showDialog()
	}

	LaunchedEffect(blockAutoFocus) {
		if (blockAutoFocus) {
			coordinator.blockAutoFocus()
		}
	}

	LaunchedEffect(
		coordinator.dialogVisible,
		coordinator.isInputLaidOut,
		coordinator.isInitialKeyboardOpened,
		coordinator.isAutoFocusBlocked
	) {
		if (
			coordinator.dialogVisible &&
			coordinator.isInputLaidOut &&
			!coordinator.isInitialKeyboardOpened &&
			!coordinator.isAutoFocusBlocked
		) {
			delay(CreatePostOverlayTokens.initialFocusDelayMs)
			ensureInputFocus()
			coordinator.markInitialKeyboardOpened()
		}
	}

	LaunchedEffect(
		coordinator.currentRestoreRequestId(),
		coordinator.dialogVisible,
		coordinator.isInputLaidOut,
		coordinator.isInitialKeyboardOpened,
		coordinator.isAutoFocusBlocked
	) {
		if (
			coordinator.currentRestoreRequestId() > 0 &&
			coordinator.dialogVisible &&
			coordinator.isInputLaidOut &&
			coordinator.isInitialKeyboardOpened &&
			!coordinator.isAutoFocusBlocked
		) {
			ensureInputFocus()
			delay(CreatePostOverlayTokens.refocusDelayMs)
			ensureInputFocus()
		}
	}

	DisposableEffect(
		lifecycleOwner,
		coordinator.dialogVisible,
		coordinator.isInputLaidOut,
		coordinator.isInitialKeyboardOpened,
		coordinator.isAutoFocusBlocked
	) {
		val observer = LifecycleEventObserver { _, event ->
			if (
				event == Lifecycle.Event.ON_RESUME &&
				coordinator.dialogVisible &&
				coordinator.isInputLaidOut &&
				coordinator.isInitialKeyboardOpened &&
				!coordinator.isAutoFocusBlocked
			) {
				coordinator.requestInputFocusRestore()
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	return coordinator
}
