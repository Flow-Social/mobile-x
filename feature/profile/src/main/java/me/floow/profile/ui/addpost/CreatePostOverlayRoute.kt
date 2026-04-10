package me.floow.profile.ui.addpost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.floow.profile.uilogic.addpost.AddPostVmState
import me.floow.profile.uilogic.addpost.AddPostViewModel
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.uilogic.addpost.CreatePostStateHolder
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.resume

@Composable
fun CreatePostOverlayRoute(
	onBackClick: () -> Unit,
	onPublished: () -> Unit,
	isMockBuild: Boolean,
	modifier: Modifier = Modifier,
	viewModel: AddPostViewModel,
) {
	val state by viewModel.state.collectAsState()
	val uiState = remember(state, isMockBuild) {
		state.toCreatePostUiState(allowManualUrls = isMockBuild)
	}
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val lifecycleOwner = LocalLifecycleOwner.current
	var isClosing by remember { mutableStateOf(false) }
	var resumeSignal by remember { mutableIntStateOf(0) }
	val pickImagesLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = AddPostVmState.MAX_IMAGES)
	) { uris ->
		viewModel.addLocalImageUris(uris.map { it.toString() })
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				resumeSignal += 1
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	SetStatusBarStyle(
		color = Color.Transparent,
		darkIcons = false
	)

	CreatePostOverlayScreen(
		uiState = uiState,
		onCloseClick = {
			isClosing = true
			focusManager.clearFocus(force = true)
			keyboardController?.hide()
			onBackClick()
		},
		onPickImagesClick = {
			pickImagesLauncher.launch(
				PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
			)
		},
		onRemoveImageClick = viewModel::removeLocalImageByUri,
		onCommitImageOrder = viewModel::setLocalImageOrder,
		onDescriptionChange = viewModel::updateDescription,
		onCategoryChange = viewModel::updateCategory,
		onRetryCategoriesClick = viewModel::retryLoadCategories,
		onPublishClick = {
			viewModel.publish(
				allowManualUrls = isMockBuild,
				onSuccess = {
					isClosing = true
					focusManager.clearFocus(force = true)
					keyboardController?.hide()
					onPublished()
				}
			)
		},
		resumeSignal = resumeSignal,
		blockAutoFocus = isClosing,
		modifier = modifier,
	)
}

@Composable
fun CreatePostOverlayRoute(
	onBackClick: () -> Unit,
	onPublished: () -> Unit,
	onDraftChanged: (Boolean) -> Unit,
	modifier: Modifier = Modifier,
) {
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val lifecycleOwner = LocalLifecycleOwner.current
	var isClosing by remember { mutableStateOf(false) }
	var resumeSignal by remember { mutableIntStateOf(0) }
	val platformImagePicker = remember { AndroidCreatePostPlatformImagePicker() }
	val pickImagesLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = AddPostVmState.MAX_IMAGES)
	) { uris ->
		platformImagePicker.onPostImagesPicked(uris.map { it.toString() })
	}
	val stateHolder: CreatePostStateHolder = koinInject(
		parameters = { parametersOf(platformImagePicker) }
	)
	val uiState by stateHolder.state.collectAsState()

	platformImagePicker.pickPostImagesLauncher = { maxItems ->
		pickImagesLauncher.launch(
			PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
		)
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				resumeSignal += 1
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	LaunchedEffect(stateHolder) {
		stateHolder.events.collect { event ->
			when (event) {
				is CreatePostStateHolder.Event.Created -> {
					isClosing = true
					focusManager.clearFocus(force = true)
					keyboardController?.hide()
					onPublished()
				}
				is CreatePostStateHolder.Event.ShowMessage -> Unit
			}
		}
	}
	LaunchedEffect(uiState.hasDraft) {
		onDraftChanged(uiState.hasDraft)
	}

	SetStatusBarStyle(
		color = Color.Transparent,
		darkIcons = false
	)

	CreatePostOverlayScreen(
		uiState = uiState,
		onCloseClick = {
			isClosing = true
			focusManager.clearFocus(force = true)
			keyboardController?.hide()
			onBackClick()
		},
		onPickImagesClick = stateHolder::pickImages,
		onRemoveImageClick = stateHolder::removeImage,
		onCommitImageOrder = stateHolder::reorderImages,
		onDescriptionChange = stateHolder::updateDescription,
		onCategoryChange = stateHolder::updateCategory,
		onRetryCategoriesClick = stateHolder::reloadCategories,
		onPublishClick = stateHolder::publish,
		resumeSignal = resumeSignal,
		blockAutoFocus = isClosing,
		modifier = modifier,
	)
}

private class AndroidCreatePostPlatformImagePicker : PlatformImagePicker {
	var pickPostImagesLauncher: ((Int) -> Unit)? = null
	private var continuation: CancellableContinuation<List<PlatformPickedImage>>? = null

	override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> {
		val launcher = checkNotNull(pickPostImagesLauncher) { "Post image picker launcher is not attached" }
		return suspendCancellableCoroutine { continuation ->
			this.continuation = continuation
			continuation.invokeOnCancellation {
				if (this.continuation === continuation) {
					this.continuation = null
				}
			}
			CoroutineScope(Dispatchers.Main.immediate).launch {
				launcher(maxItems)
			}
		}
	}

	override suspend fun pickSingleImage(): PlatformPickedImage? = null

	fun onPostImagesPicked(uris: List<String>) {
		val pending = continuation ?: return
		continuation = null
		pending.resume(
			uris.mapIndexed { index, uri ->
				PlatformPickedImage(
					id = uri,
					name = "picked_image_$index",
					mimeType = "image/*",
					sizeBytes = 0L,
					previewUri = uri,
				)
			}
		)
	}
}
