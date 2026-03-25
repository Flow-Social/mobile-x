package me.floow.profile.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.profile.ui.common.profileRouteStatusBarAppearance
import me.floow.profile.ui.profile.bump.ProfileBumpCoordinator
import me.floow.profile.uilogic.bump.BumpMatchResult
import me.floow.profile.uilogic.bump.ProfileBumpViewModel
import me.floow.profile.ui.edit.EditProfileBottomSheet
import me.floow.profile.ui.edit.EditProfileRouteInitialData
import me.floow.profile.uilogic.edit.EditProfileUiEvent
import me.floow.profile.uilogic.edit.EditProfileViewModel
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.profile.uilogic.profile.ProfileScreenViewModel
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SetStatusBarStyle

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
    modifier: Modifier = Modifier,
    viewModel: ProfileScreenViewModel,
    bumpViewModel: ProfileBumpViewModel,
    editProfileViewModel: EditProfileViewModel,
) {
    val state: ProfileScreenState by viewModel.state.collectAsStateWithLifecycle()
    val successState = state as? ProfileScreenState.Success
    val bumpUiState by bumpViewModel.uiState.collectAsStateWithLifecycle()
    val editProfileState by editProfileViewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val haptic = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var bumpMatchSignal by remember { mutableIntStateOf(0) }
    var isEditProfileSheetVisible by remember { mutableStateOf(false) }
    val editProfileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statusBarAppearance = profileRouteStatusBarAppearance(
        colors = MaterialTheme.colorScheme,
        isEditProfileSheetVisible = isEditProfileSheetVisible,
        isBumpSheetVisible = bumpUiState.isSheetVisible,
    )
    val profileParallaxTranslationY by animateDpAsState(
        targetValue = 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "profileParallaxTranslationY",
    )
    val profileParallaxScale by animateFloatAsState(
        targetValue = if (isEditProfileSheetVisible) 1.02f else 1f,
        animationSpec = tween(durationMillis = 280),
        label = "profileParallaxScale",
    )

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
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        editProfileViewModel.setAvatarFromPicker(uri?.toString())
    }
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        editProfileViewModel.setBackgroundFromPicker(uri?.toString())
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    LaunchedEffect(lifecycle, editProfileViewModel) {
        lifecycle.repeatOnLifecycle(state = Lifecycle.State.STARTED) {
            launch {
                editProfileViewModel.hapticFeedbackFlow.collectLatest {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            launch {
                editProfileViewModel.uiEvents.collectLatest { event ->
                    when (event) {
                        EditProfileUiEvent.Saved -> {
                            snackbarHostState.showSnackbar("Сохранено")
                        }
                        EditProfileUiEvent.RollbackApplied -> {
                            snackbarHostState.showSnackbar("Не удалось подтвердить изменения. Вернули прошлую версию")
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        viewModel.onProfileScreenVisible()
        onDispose {
            viewModel.onProfileScreenHidden()
        }
    }

    LaunchedEffect(refreshPostsSignal) {
        if (refreshPostsSignal) {
            viewModel.loadData(forceRemote = true)
            consumeRefreshPostsSignal()
        }
    }

    LaunchedEffect(bumpViewModel) {
        bumpViewModel.matchCompleted.collectLatest { result ->
            bumpViewModel.hideSheet()
            bumpMatchSignal += 1
            onBumpMatchNavigate(result)
            bumpViewModel.resetToIdle()
        }
    }

    if (isEditProfileSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isEditProfileSheetVisible = false },
            sheetState = editProfileSheetState,
            dragHandle = null,
        ) {
            EditProfileBottomSheet(
                state = editProfileState,
                onDismissRequest = { isEditProfileSheetVisible = false },
                onDoneClick = {
                    editProfileViewModel.updateProfile(
                        optimistic = true,
                        onSuccess = {
                            isEditProfileSheetVisible = false
                        },
                        onFailure = {
                            Toast.makeText(context, "Failure", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                onAvatarPickerClick = {
                    pickAvatarLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onBackgroundPickerClick = {
                    pickBackgroundLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onNameChange = editProfileViewModel::updateName,
                onUsernameChange = editProfileViewModel::updateUsername,
                onBiographyChange = editProfileViewModel::updateBiography,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        ProfileScreen(
            onProfileEditClick = {
                if (successState != null) {
                    editProfileViewModel.setInitialData(
                        EditProfileRouteInitialData(
                            name = successState.displayName ?: "",
                            username = successState.shortUsername ?: "",
                            description = successState.description ?: "",
                            avatarUrl = successState.avatarUri?.toString(),
                            backgroundUrl = successState.backgroundUri?.toString(),
                        )
                    )
                    isEditProfileSheetVisible = true
                }
            },
            onAddPostButtonClick = goToAddPostScreen,
            onMessageButtonClick = {
                if (successState != null) {
                    goToChatScreen(
                        successState.id,
                        successState.displayName ?: "",
                        successState.avatarUri?.toString(),
                    )
                }
            },
            onShareProfileClick = {
                val shareSlug = when (val current = state) {
                    is ProfileScreenState.Success -> current.shortUsername?.takeIf { it.isNotBlank() } ?: current.id
                    else -> ""
                }
                shareProfile(DeepLinkUrls.profileUrl(shareSlug))
            },
            onOpenBumpSheet = bumpViewModel::openSheet,
            onHideBumpSheet = bumpViewModel::hideSheet,
            onStartBumpClick = {
                if (!bumpEnabled) return@ProfileScreen
                if (!bumpUiState.isSheetVisible) return@ProfileScreen

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
                if (!bumpUiState.isSheetVisible) return@ProfileScreen
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                coroutineScope.launch {
                    bumpCoordinator.submitImpact(peak)
                }
            },
            onBumpPeerDetected = bumpViewModel::updateNearbyPeerToken,
            bumpUiState = bumpUiState,
            bumpMatchSignal = bumpMatchSignal,
            bumpEnabled = bumpEnabled,
            onBackClick = onBackClick,
            onPostClick = onPostClick,
            onSharePost = { post ->
                sharePost(DeepLinkUrls.postUrl(post.id, post.author.username?.value))
            },
            onEditPost = onEditPost,
            onDeletePost = viewModel::deletePost,
            onLoadMorePosts = viewModel::loadMorePosts,
            suppressStatusBarStyle = isEditProfileSheetVisible || bumpUiState.isSheetVisible,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = profileParallaxTranslationY.toPx()
                    scaleX = profileParallaxScale
                    scaleY = profileParallaxScale
                },
            state = state,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)
        )
    }

    if (successState == null || isEditProfileSheetVisible || bumpUiState.isSheetVisible) {
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
