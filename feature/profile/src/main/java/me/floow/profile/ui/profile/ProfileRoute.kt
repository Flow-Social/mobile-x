package me.floow.profile.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.domain.data.repos.PresenceRepository
import me.floow.profile.ui.edit.AndroidSingleImagePicker
import me.floow.profile.ui.common.profileRouteStatusBarAppearance
import me.floow.profile.ui.edit.EditProfileBottomSheet
import me.floow.profile.ui.profile.bump.ProfileBumpCoordinator
import me.floow.profile.uilogic.bump.ProfileBumpUiState
import me.floow.profile.uilogic.bump.BumpMatchResult
import me.floow.profile.uilogic.bump.ProfileBumpViewModel
import me.floow.shared.profile.uilogic.toActionContext
import me.floow.shared.profile.uilogic.edit.EditProfileOverlayData
import me.floow.shared.profile.uilogic.edit.EditProfileStateHolder
import me.floow.shared.profile.uilogic.ProfileStateHolder
import me.floow.shared.profile.uilogic.toEditProfileOverlayData
import me.floow.shared.profile.uilogic.toMessageTarget
import me.floow.shared.profile.uilogic.shareProfileSlug
import me.floow.shared.profile.uilogic.ProfileScreenState as SharedProfileScreenState
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(
    goToAddPostScreen: () -> Unit,
    onPostClick: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit,
    goToChatScreen: (userId: String, name: String, avatarUrl: String?) -> Unit = { _, _, _ -> },
    shareProfile: (url: String) -> Unit,
    sharePost: (url: String) -> Unit,
    onEditPost: (me.floow.domain.models.Post, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {},
    onBumpMatchNavigate: (BumpMatchResult) -> Unit = {},
    refreshPostsSignal: Boolean = false,
    consumeRefreshPostsSignal: () -> Unit = {},
    bumpEnabled: Boolean = false,
    presenceRepository: PresenceRepository,
    modifier: Modifier = Modifier,
    stateHolder: ProfileStateHolder,
    bumpViewModel: ProfileBumpViewModel,
) {
    val sharedState by stateHolder.state.collectAsState()
    val successState = sharedState as? SharedProfileScreenState.Success
    val actionContext = successState?.toActionContext()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val editProfileSheetController = rememberEditProfileSheetController()
    val editProfileStateHolder = rememberEditProfileStateHolder(
        initialData = editProfileSheetController.initialData,
        imagePicker = editProfileSheetController.imagePicker,
    )
    val bumpHost = rememberProfileBumpHost(
        context = context,
        bumpEnabled = bumpEnabled,
        bumpViewModel = bumpViewModel,
        onBumpMatchNavigate = onBumpMatchNavigate,
    )
    val statusBarAppearance = profileRouteStatusBarAppearance(
        colors = MaterialTheme.colorScheme,
        isEditProfileSheetVisible = editProfileSheetController.isVisible,
        isBumpSheetVisible = bumpHost.uiState.isSheetVisible,
    )
    val profileParallaxTranslationY by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "profileParallaxTranslationY",
    )
    val profileParallaxScale by animateFloatAsState(
        targetValue = if (editProfileSheetController.isVisible) 1.02f else 1f,
        animationSpec = tween(durationMillis = 280),
        label = "profileParallaxScale",
    )

    BindProfilePresence(
        successState = successState,
        presenceRepository = presenceRepository,
    )

    BindProfileRouteSideEffects(
        stateHolder = stateHolder,
        editProfileSheetController = editProfileSheetController,
        editProfileStateHolder = editProfileStateHolder,
        snackbarHostState = snackbarHostState,
        refreshPostsSignal = refreshPostsSignal,
        consumeRefreshPostsSignal = consumeRefreshPostsSignal,
    )

    EditProfileSheetHost(
        controller = editProfileSheetController,
        stateHolder = editProfileStateHolder,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        ProfileScreen(
            onProfileEditClick = {
                actionContext?.let {
                    editProfileSheetController.open(it.toEditProfileOverlayData())
                }
            },
            onAddPostButtonClick = goToAddPostScreen,
            onMessageButtonClick = {
                actionContext?.toMessageTarget()?.let { target ->
                    goToChatScreen(
                        target.userId,
                        target.displayName,
                        target.avatarUrl,
                    )
                }
            },
            onShareProfileClick = {
                actionContext?.let { shareProfile(DeepLinkUrls.profileUrl(it.shareProfileSlug())) }
            },
            onOpenBumpSheet = bumpHost.onOpenSheet,
            onHideBumpSheet = bumpHost.onHideSheet,
            onStartBumpClick = bumpHost.onStartBumpClick,
            onCancelBumpClick = bumpHost.onCancelBumpClick,
            onBumpImpactDetected = bumpHost.onBumpImpactDetected,
            onBumpPeerDetected = bumpHost.onBumpPeerDetected,
            bumpUiState = bumpHost.uiState,
            bumpMatchSignal = bumpHost.matchSignal,
            bumpEnabled = bumpEnabled,
            onBackClick = onBackClick,
            onPostClick = onPostClick,
            onSharePost = { post ->
                sharePost(DeepLinkUrls.postUrl(post.id, post.author.username?.value))
            },
            onEditPost = onEditPost,
            onDeletePost = { postId ->
                coroutineScope.launch {
                    stateHolder.deletePost(postId)
                }
            },
            onLoadMorePosts = stateHolder::loadMorePosts,
            suppressStatusBarStyle = editProfileSheetController.isVisible,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = profileParallaxTranslationY.toPx()
                    scaleX = profileParallaxScale
                    scaleY = profileParallaxScale
                },
            state = sharedState,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (successState == null || editProfileSheetController.isVisible) {
        SetStatusBarStyle(
            color = statusBarAppearance.color,
            darkIcons = statusBarAppearance.darkIcons,
        )
    }

    SetNavigationBarColor(MainShellDefaults.appBackgroundColor)
}

private fun hasBleRuntimePermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    val hasAdvertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    return hasScan && hasAdvertise && hasConnect
}

private class ProfileBumpHost(
    val uiState: ProfileBumpUiState,
    val matchSignal: Int,
    val onOpenSheet: () -> Unit,
    val onHideSheet: () -> Unit,
    val onStartBumpClick: () -> Unit,
    val onCancelBumpClick: () -> Unit,
    val onBumpImpactDetected: (Float) -> Unit,
    val onBumpPeerDetected: (String, Int) -> Unit,
)

@Composable
private fun rememberProfileBumpHost(
    context: Context,
    bumpEnabled: Boolean,
    bumpViewModel: ProfileBumpViewModel,
    onBumpMatchNavigate: (BumpMatchResult) -> Unit,
): ProfileBumpHost {
    val appContext = remember(context) { context.applicationContext }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val bumpUiState by bumpViewModel.uiState.collectAsStateWithLifecycle()
    var bumpMatchSignal by remember { mutableIntStateOf(0) }
    val bumpCoordinator = remember(appContext, bumpViewModel) {
        ProfileBumpCoordinator(
            context = appContext,
            bumpViewModel = bumpViewModel,
        )
    }
    val bumpPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Bump permissions are soft: we still continue without hard blocking.
    }

    LaunchedEffect(bumpViewModel) {
        bumpViewModel.matchCompleted.collectLatest { result ->
            bumpViewModel.hideSheet()
            bumpMatchSignal += 1
            onBumpMatchNavigate(result)
            bumpViewModel.resetToIdle()
        }
    }

    return remember(
        context,
        bumpEnabled,
        bumpUiState,
        bumpViewModel,
        bumpMatchSignal,
        onBumpMatchNavigate,
        bumpCoordinator,
        bumpPermissionsLauncher,
        haptic,
        coroutineScope,
    ) {
        ProfileBumpHost(
            uiState = bumpUiState,
            matchSignal = bumpMatchSignal,
            onOpenSheet = bumpViewModel::openSheet,
            onHideSheet = bumpViewModel::hideSheet,
            onStartBumpClick = {
                if (!bumpEnabled) return@ProfileBumpHost
                if (!bumpUiState.isSheetVisible) return@ProfileBumpHost

                val missingPermissions = mutableListOf<String>()
                if (!bumpCoordinator.hasLocationPermission()) {
                    missingPermissions += Manifest.permission.ACCESS_FINE_LOCATION
                    missingPermissions += Manifest.permission.ACCESS_COARSE_LOCATION
                }
                if (!hasBleRuntimePermissions(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    missingPermissions += Manifest.permission.BLUETOOTH_SCAN
                    missingPermissions += Manifest.permission.BLUETOOTH_ADVERTISE
                    missingPermissions += Manifest.permission.BLUETOOTH_CONNECT
                }
                if (missingPermissions.isNotEmpty()) {
                    bumpPermissionsLauncher.launch(missingPermissions.distinct().toTypedArray())
                }
                coroutineScope.launch {
                    bumpCoordinator.startSession(ttlSeconds = 10)
                }
            },
            onCancelBumpClick = {
                bumpViewModel.hideSheet()
                bumpViewModel.cancelSession()
            },
            onBumpImpactDetected = { peak ->
                if (!bumpUiState.isSheetVisible) return@ProfileBumpHost
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    bumpCoordinator.submitImpact(peak)
                }
            },
            onBumpPeerDetected = bumpViewModel::updateNearbyPeerToken,
        )
    }
}

private class EditProfileSheetController(
    val imagePicker: AndroidSingleImagePicker,
) {
    var initialData by mutableStateOf<EditProfileOverlayData?>(null)
        private set

    var isVisible by mutableStateOf(false)
        private set

    fun open(data: EditProfileOverlayData) {
        initialData = data
        isVisible = true
    }

    fun dismiss() {
        isVisible = false
        initialData = null
    }
}

@Composable
private fun rememberEditProfileSheetController(): EditProfileSheetController {
    val imagePicker = remember { AndroidSingleImagePicker() }
    return remember(imagePicker) {
        EditProfileSheetController(imagePicker = imagePicker)
    }
}

@Composable
private fun rememberEditProfileStateHolder(
    initialData: EditProfileOverlayData?,
    imagePicker: AndroidSingleImagePicker,
): EditProfileStateHolder? {
    return initialData?.let { data ->
        koinInject<EditProfileStateHolder>(
            parameters = { parametersOf(data, imagePicker) }
        )
    }
}

@Composable
private fun BindProfileRouteSideEffects(
    stateHolder: ProfileStateHolder,
    editProfileSheetController: EditProfileSheetController,
    editProfileStateHolder: EditProfileStateHolder?,
    snackbarHostState: SnackbarHostState,
    refreshPostsSignal: Boolean,
    consumeRefreshPostsSignal: () -> Unit,
) {
    LaunchedEffect(stateHolder) {
        stateHolder.loadIfNeeded()
    }

    LaunchedEffect(editProfileStateHolder) {
        val holder = editProfileStateHolder ?: return@LaunchedEffect
        holder.events.collectLatest { event ->
            when (event) {
                is EditProfileStateHolder.Event.Saved -> {
                    stateHolder.updateProfileHeader(event.profile)
                    editProfileSheetController.dismiss()
                    snackbarHostState.showSnackbar("Сохранено")
                }
                is EditProfileStateHolder.Event.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    LaunchedEffect(refreshPostsSignal) {
        if (refreshPostsSignal) {
            stateHolder.load(force = true)
            consumeRefreshPostsSignal()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditProfileSheetHost(
    controller: EditProfileSheetController,
    stateHolder: EditProfileStateHolder?,
) {
    val holder = stateHolder ?: return
    if (!controller.isVisible) return

    val editProfileState by holder.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        controller.imagePicker.onImagePicked(uri?.toString())
    }
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        controller.imagePicker.onImagePicked(uri?.toString())
    }

    ModalBottomSheet(
        onDismissRequest = controller::dismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        EditProfileBottomSheet(
            state = editProfileState,
            onDismissRequest = controller::dismiss,
            onDoneClick = holder::save,
            onAvatarPickerClick = {
                controller.imagePicker.launchPicker = {
                    pickAvatarLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                holder.pickAvatar()
            },
            onBackgroundPickerClick = {
                controller.imagePicker.launchPicker = {
                    pickBackgroundLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                holder.pickBackground()
            },
            onNameChange = holder::updateName,
            onUsernameChange = holder::updateUsername,
            onBiographyChange = holder::updateBio,
        )
    }
}

@Composable
private fun BindProfilePresence(
    successState: SharedProfileScreenState.Success?,
    presenceRepository: PresenceRepository,
) {
    DisposableEffect(successState?.id, successState?.isSelf, presenceRepository) {
        val boundProfileId = successState
            ?.takeIf { !it.isSelf && it.id.isNotBlank() }
            ?.id
        if (boundProfileId != null) {
            presenceRepository.setTargets(
                owner = "profile:$boundProfileId",
                userIds = listOf(boundProfileId),
            )
        }
        onDispose {
            if (boundProfileId != null) {
                presenceRepository.clearTargets("profile:$boundProfileId")
            }
        }
    }
}
