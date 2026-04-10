package me.floow.profile.ui.edit

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.uilogic.edit.ProfileEditorRepository
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.resume

@Composable
fun EditProfileRoute(
	initialData: EditProfileOverlayData? = null,
	onBackClick: () -> Unit = {},
	onDoneClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val routeInitialData = requireNotNull(initialData) {
		"EditProfileRoute requires initialData in the Android shared-owner path"
	}
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
	val snackbarHostState = remember { SnackbarHostState() }
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val imagePicker = remember { AndroidSingleImagePicker() }
	val pickImageLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickVisualMedia()
	) { uri ->
		imagePicker.onImagePicked(uri?.toString())
	}
	val stateHolder: EditProfileStateHolder = koinInject(
		parameters = { parametersOf(routeInitialData, imagePicker) }
	)
	val state by stateHolder.state.collectAsState()

	imagePicker.launchPicker = {
		pickImageLauncher.launch(
			PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
		)
	}

	LaunchedEffect(stateHolder) {
		stateHolder.events.collect { event ->
			when (event) {
				is EditProfileStateHolder.Event.Saved -> {
					focusManager.clearFocus(force = true)
					keyboardController?.hide()
					onDoneClick()
				}
				is EditProfileStateHolder.Event.ShowMessage -> {
					snackbarHostState.showSnackbar(event.message)
				}
			}
		}
	}

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons
	)

	Box(modifier = modifier.fillMaxSize()) {
		EditProfileScreen(
			state = state,
			onBackClick = {
				focusManager.clearFocus(force = true)
				keyboardController?.hide()
				onBackClick()
			},
			onDoneClick = stateHolder::save,
			onAvatarPickerClick = stateHolder::pickAvatar,
			onBackgroundPickerClick = stateHolder::pickBackground,
			onNameChange = stateHolder::updateName,
			onUsernameChange = stateHolder::updateUsername,
			onBiographyChange = stateHolder::updateBio,
			modifier = Modifier.fillMaxSize(),
		)

		SnackbarHost(
			hostState = snackbarHostState,
			modifier = Modifier.align(Alignment.BottomCenter)
		)
	}

	SetNavigationBarColor(MaterialTheme.colorScheme.background)
}

internal class AndroidSingleImagePicker : PlatformImagePicker {
	var launchPicker: (() -> Unit)? = null
	private var continuation: CancellableContinuation<PlatformPickedImage?>? = null

	override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> = emptyList()

	override suspend fun pickSingleImage(): PlatformPickedImage? {
		val launcher = checkNotNull(launchPicker) { "Single image picker launcher is not attached" }
		return suspendCancellableCoroutine { continuation ->
			this.continuation = continuation
			continuation.invokeOnCancellation {
				if (this.continuation === continuation) {
					this.continuation = null
				}
			}
			CoroutineScope(Dispatchers.Main.immediate).launch {
				launcher()
			}
		}
	}

	fun onImagePicked(uri: String?) {
		val pending = continuation ?: return
		continuation = null
		pending.resume(
			uri?.takeIf(String::isNotBlank)?.let {
				PlatformPickedImage(
					id = it,
					name = "picked_image",
					mimeType = "image/*",
					sizeBytes = 0L,
					previewUri = it,
				)
			}
		)
	}
}
