package me.floow.app.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.floow.shared.profile.ui.addpost.CreatePostSheetRoute
import me.floow.shared.profile.ui.addpost.EditPostSheetRoute
import me.floow.shared.profile.ui.edit.EditProfileBottomSheet
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.addpost.EditPostStateHolder
import me.floow.shared.profile.uilogic.compose.NoOpPlatformImagePicker
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.uikit.components.sheet.FlowModalSheetOverlay
import me.floow.shared.post.ui.PostScreenModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.shared.chats.media.attachment.ChatAttachmentPickerSheet
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

private enum class ProfilePickerTarget {
    AVATAR,
    BACKGROUND,
}

sealed interface FlowOverlay {
    data object CreatePost : FlowOverlay
    data class EditProfile(val data: EditProfileOverlayData) : FlowOverlay
    data class EditPost(val post: ProfilePostItem) : FlowOverlay
    data class ViewPost(val model: PostScreenModel) : FlowOverlay
}

@Stable
class FlowOverlayState {
    private val _stack = mutableStateListOf<FlowOverlay>()
    val current: FlowOverlay? get() = _stack.lastOrNull()
    val isVisible: Boolean get() = _stack.isNotEmpty()
    var revealProgress by mutableFloatStateOf(0f)

    fun show(overlay: FlowOverlay) {
        _stack.add(overlay)
    }

    fun dismiss() {
        if (_stack.isNotEmpty()) {
            _stack.removeAt(_stack.lastIndex)
        }
        if (_stack.isEmpty()) {
            revealProgress = 0f
        }
    }

    fun clear() {
        _stack.clear()
        revealProgress = 0f
    }
}

@Composable
fun rememberFlowOverlayState(): FlowOverlayState {
    return remember { FlowOverlayState() }
}

@Composable
fun FlowOverlayHost(
    overlayState: FlowOverlayState,
    onNavigateToProfile: (userId: String) -> Unit,
    profileStateHolder: ProfileStateHolder? = null,
    onNavigateToComments: (postId: String) -> Unit = {},
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val currentOverlay = overlayState.current ?: return
    var createPostExpandRequest by remember { mutableStateOf<Int?>(null) }
    var isPostScrollAtTop by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val entryId = remember(currentOverlay) {
        when (currentOverlay) {
            is FlowOverlay.CreatePost -> 1004L
            is FlowOverlay.EditProfile -> 1003L
            is FlowOverlay.EditPost -> 1005L
            is FlowOverlay.ViewPost -> 1006L
        }
    }

    val isCreatePost = currentOverlay is FlowOverlay.CreatePost
    val (sheetHeightFraction, scrimMaxAlpha) = when (currentOverlay) {
        is FlowOverlay.CreatePost -> 0.86f to 0.57f
        is FlowOverlay.EditProfile -> 0.85f to 0.32f
        is FlowOverlay.EditPost -> 0.72f to 0.69f
        is FlowOverlay.ViewPost -> 0.62f to 0.57f
    }

    val canDragToDismiss = when (currentOverlay) {
        is FlowOverlay.ViewPost -> isPostScrollAtTop
        else -> true
    }

    FlowModalSheetOverlay(
        entryId = entryId,
        onClose = { overlayState.dismiss() },
        onRevealChanged = { overlayState.revealProgress = it },
        canDragToDismiss = canDragToDismiss,
        expandable = true,
        sheetHeightFraction = sheetHeightFraction,
        expandedSheetHeightFraction = 1f,
        minSheetHeight = if (isCreatePost) 555.dp else 440.dp,
        scrimMaxAlpha = scrimMaxAlpha,
        containerColor = if (isCreatePost) Color.Transparent else MaterialTheme.colorScheme.background,
        bottomPadding = if (isCreatePost) 0.dp else null,
        respectStatusBar = true,
        avoidIme = false,
        expandRequestKey = if (isCreatePost) createPostExpandRequest else null,
        sheetShape = if (isCreatePost) RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp) else RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        when (currentOverlay) {
            is FlowOverlay.CreatePost -> {
                CreatePostSheetRoute(
                    onBackClick = { overlayState.dismiss() },
                    onPublished = { createdPost ->
                        if (createdPost != null) {
                            profileStateHolder?.insertCreatedPost(createdPost)
                        } else {
                            profileStateHolder?.loadIfNeeded()
                        }
                        overlayState.dismiss()
                    },
                    onDraftChanged = {},
                    dragHandleModifier = dragHandleModifier,
                    modifier = Modifier.fillMaxSize(),
                    galleryResetKey = entryId,
                    onEmptyPreviewClick = {
                        createPostExpandRequest = (createPostExpandRequest ?: 0) + 1
                    },
                )
            }

            is FlowOverlay.EditPost -> {
                val editPostStateHolder = koinInject<EditPostStateHolder> {
                    parametersOf(currentOverlay.post, NoOpPlatformImagePicker)
                }
                EditPostSheetRoute(
                    stateHolder = editPostStateHolder,
                    onBackClick = { overlayState.dismiss() },
                    onSaved = {
                        profileStateHolder?.load(force = true)
                        overlayState.dismiss()
                    },
                    dragHandleModifier = dragHandleModifier,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is FlowOverlay.ViewPost -> {
                FlowPostRoute(
                    postId = currentOverlay.model.postId,
                    presentation = PostPresentation.ProfileModal,
                    cachedPost = currentOverlay.model.toPost(),
                    onBackClick = { overlayState.dismiss() },
                    onProfileClick = { authorId ->
                        overlayState.dismiss()
                        onNavigateToProfile(authorId)
                    },
                    onCommentsClick = { postId ->
                        overlayState.dismiss()
                        onNavigateToComments(postId)
                    },
                    onEditPost = { postItem ->
                        overlayState.show(FlowOverlay.EditPost(postItem))
                    },
                    onDeletePost = {
                        profileStateHolder?.let { holder ->
                            coroutineScope.launch {
                                holder.deletePost(currentOverlay.model.postId)
                            }
                        }
                        overlayState.dismiss()
                    },
                    onPresentFullscreenViewer = onPresentFullscreenViewer,
                    onPostCached = null,
                    onProfileModalScrollAtTopChanged = { isPostScrollAtTop = it },
                    topBarModifier = dragHandleModifier,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is FlowOverlay.EditProfile -> {
                val editProfileStateHolder = koinInject<EditProfileStateHolder> {
                    parametersOf(currentOverlay.data)
                }
                val uiState by editProfileStateHolder.state.collectAsState()
                var activeProfilePickerTarget by remember { mutableStateOf<ProfilePickerTarget?>(null) }
                LaunchedEffect(editProfileStateHolder) {
                    editProfileStateHolder.events.collect { event ->
                        when (event) {
                            is EditProfileStateHolder.Event.Saved -> {
                                profileStateHolder?.load(force = true)
                                overlayState.dismiss()
                            }
                            is EditProfileStateHolder.Event.ShowMessage -> {}
                        }
                    }
                }
                EditProfileBottomSheet(
                    state = uiState,
                    onDismissRequest = { overlayState.dismiss() },
                    onDoneClick = editProfileStateHolder::save,
                    onAvatarPickerClick = { activeProfilePickerTarget = ProfilePickerTarget.AVATAR },
                    onBackgroundPickerClick = { activeProfilePickerTarget = ProfilePickerTarget.BACKGROUND },
                    onNameChange = editProfileStateHolder::updateName,
                    onUsernameChange = editProfileStateHolder::updateUsername,
                    onBiographyChange = editProfileStateHolder::updateBio,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (activeProfilePickerTarget != null) {
                    ChatAttachmentPickerSheet(
                        onDismissRequest = { activeProfilePickerTarget = null },
                        maxSelection = 1,
                        onImagesSelected = { uris, _ ->
                            val uri = uris.firstOrNull()
                            if (uri != null) {
                                when (activeProfilePickerTarget) {
                                    ProfilePickerTarget.AVATAR -> editProfileStateHolder.setAvatar(uri)
                                    ProfilePickerTarget.BACKGROUND -> editProfileStateHolder.setBackground(uri)
                                    null -> Unit
                                }
                            }
                            activeProfilePickerTarget = null
                        },
                    )
                }
            }
        }
    }
}
