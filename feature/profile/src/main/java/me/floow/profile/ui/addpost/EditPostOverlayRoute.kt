package me.floow.profile.ui.addpost

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import me.floow.profile.uilogic.addpost.EditPostSaveResult
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.ui.addpost.CreatePostImageItem
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.resume

@Composable
fun EditPostOverlayRoute(
	postId: String,
	initialDescription: String?,
	initialImageUrls: List<String>,
	mediaTransferToken: String? = null,
	mediaTransferStore: PostMediaTransferStore? = null,
	onBackClick: () -> Unit,
	onOptimistic: (EditPostSaveResult) -> Unit = {},
	onSaved: (EditPostSaveResult) -> Unit,
	onSaveFailed: (EditPostSaveResult) -> Unit = {},
	onDraftChanged: (Boolean) -> Unit = {},
	modifier: Modifier = Modifier,
) {
	val handoffSnapshot = remember(mediaTransferStore, mediaTransferToken, postId) {
		val transferStore = mediaTransferStore ?: return@remember null
		mediaTransferToken
			?.let { token -> transferStore.consume(token) }
			?.takeIf { snapshot -> snapshot.postId == postId }
			?: transferStore.peek(postId)?.takeIf { snapshot -> snapshot.postId == postId }
	}
	val initialPaintersByImageId = remember(handoffSnapshot) {
		buildMap {
			handoffSnapshot
				?.paintersByIndex
				?.forEach { (index, ref) ->
					put("initial_$index", ref.painter)
				}
		}
	}
	val imagePicker = remember { AndroidEditPostImagePicker() }
	val replaceImageLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.PickVisualMedia()
	) { uri ->
		imagePicker.onImagePicked(uri?.toString())
	}
	val initialPost = remember(postId, initialDescription, initialImageUrls) {
		ProfilePostItem(
			id = postId,
			description = initialDescription,
			lqUrl = initialImageUrls.firstOrNull(),
			previewUrl = initialImageUrls.firstOrNull(),
			fullUrl = initialImageUrls.firstOrNull(),
			viewerUrls = initialImageUrls,
			previewUrls = initialImageUrls,
			likesCount = 0,
			commentsCount = 0,
		)
	}
	val stateHolder: EditPostStateHolder = koinInject(
		parameters = { parametersOf(initialPost, imagePicker) }
	)
	val uiState by stateHolder.state.collectAsState()
	var pendingRollback by remember(postId, initialDescription, initialImageUrls) {
		mutableStateOf<EditPostSaveResult?>(null)
	}

	imagePicker.launchPicker = {
		replaceImageLauncher.launch(
			PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
		)
	}

	LaunchedEffect(stateHolder) {
		stateHolder.events.collect { event ->
			when (event) {
				is EditPostStateHolder.Event.Saved -> {
					pendingRollback = null
					onSaved(
						EditPostSaveResult(
							postId = event.post.id.ifBlank { postId },
							description = event.post.description,
							imageUrls = event.post.imageUrls,
						)
					)
				}
				is EditPostStateHolder.Event.ShowMessage -> {
					pendingRollback?.let(onSaveFailed)
					pendingRollback = null
				}
			}
		}
	}
	LaunchedEffect(uiState.canPublish) {
		onDraftChanged(uiState.canPublish)
	}

	SetStatusBarStyle(
		color = Color.Transparent,
		darkIcons = false
	)

	CreatePostOverlayScreen(
		uiState = uiState,
		initialPaintersByImageId = initialPaintersByImageId,
		onCloseClick = onBackClick,
		onPickImagesClick = {},
		onRemoveImageClick = stateHolder::removeImage,
		onCommitImageOrder = stateHolder::reorderImages,
		onDescriptionChange = stateHolder::updateDescription,
		onCategoryChange = {},
		onRetryCategoriesClick = {},
		onPublishClick = {
			if (!uiState.canPublish) return@CreatePostOverlayScreen
			val optimisticResult = EditPostSaveResult(
				postId = postId,
				description = uiState.description.trim().ifBlank { null },
				imageUrls = uiState.selectedImages.map(CreatePostImageItem::uri).filter(String::isNotBlank),
			)
			val rollback = EditPostSaveResult(
				postId = postId,
				description = initialDescription,
				imageUrls = initialImageUrls,
			)
			pendingRollback = rollback
			onOptimistic(optimisticResult)
			stateHolder.save()
		},
		onImageCardClick = stateHolder::replaceImage,
		showCategorySelector = false,
		allowAddImageCard = false,
		modifier = modifier,
	)
}

private class AndroidEditPostImagePicker : PlatformImagePicker {
	var launchPicker: (() -> Unit)? = null
	private var continuation: CancellableContinuation<PlatformPickedImage?>? = null

	override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> = emptyList()

	override suspend fun pickSingleImage(): PlatformPickedImage? {
		val launcher = checkNotNull(launchPicker) { "Edit post image picker launcher is not attached" }
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
					name = "edit_post_image",
					mimeType = "image/*",
					sizeBytes = 0L,
					previewUri = it,
				)
			}
		)
	}
}
