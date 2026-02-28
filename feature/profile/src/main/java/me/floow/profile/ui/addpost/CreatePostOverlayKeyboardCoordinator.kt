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

	internal fun currentRestoreRequestId(): Int = restoreRequestId
}

@Composable
internal fun rememberCreatePostOverlayKeyboardCoordinator(
	focusRequester: FocusRequester,
	keyboardController: SoftwareKeyboardController?,
	lifecycleOwner: LifecycleOwner,
): CreatePostOverlayKeyboardCoordinator {
	val coordinator = remember { CreatePostOverlayKeyboardCoordinator() }

	fun ensureInputFocus() {
		runCatching { focusRequester.requestFocus() }
		keyboardController?.show()
	}

	LaunchedEffect(Unit) {
		coordinator.showDialog()
	}

	LaunchedEffect(
		coordinator.dialogVisible,
		coordinator.isInputLaidOut,
		coordinator.isInitialKeyboardOpened
	) {
		if (coordinator.dialogVisible && coordinator.isInputLaidOut && !coordinator.isInitialKeyboardOpened) {
			delay(CreatePostOverlayTokens.initialFocusDelayMs)
			ensureInputFocus()
			coordinator.markInitialKeyboardOpened()
		}
	}

	LaunchedEffect(
		coordinator.currentRestoreRequestId(),
		coordinator.dialogVisible,
		coordinator.isInputLaidOut,
		coordinator.isInitialKeyboardOpened
	) {
		if (
			coordinator.currentRestoreRequestId() > 0 &&
			coordinator.dialogVisible &&
			coordinator.isInputLaidOut &&
			coordinator.isInitialKeyboardOpened
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
		coordinator.isInitialKeyboardOpened
	) {
		val observer = LifecycleEventObserver { _, event ->
			if (
				event == Lifecycle.Event.ON_RESUME &&
				coordinator.dialogVisible &&
				coordinator.isInputLaidOut &&
				coordinator.isInitialKeyboardOpened
			) {
				coordinator.requestInputFocusRestore()
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	return coordinator
}
