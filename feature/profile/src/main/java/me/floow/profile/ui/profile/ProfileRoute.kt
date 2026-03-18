package me.floow.profile.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.MaterialTheme
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.profile.ui.profile.bump.ProfileBumpCoordinator
import me.floow.profile.uilogic.bump.BumpMatchResult
import me.floow.profile.uilogic.bump.ProfileBumpViewModel
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.profile.uilogic.profile.ProfileScreenViewModel
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SetStatusBarStyle

@Composable
fun ProfileRoute(
    goToProfileEditScreen: (name: String, username: String, description: String, avatarUrl: String?, backgroundUrl: String?) -> Unit,
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
) {
    val state: ProfileScreenState by viewModel.state.collectAsStateWithLifecycle()
    val successState = state as? ProfileScreenState.Success
    val bumpUiState by bumpViewModel.uiState.collectAsStateWithLifecycle()
    val statusBarColor = MaterialTheme.colorScheme.background
    val useDarkStatusIcons = statusBarColor.luminance() > 0.5f

    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
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

    LaunchedEffect(Unit) {
        viewModel.loadData()
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

    if (successState == null) {
        SetStatusBarStyle(
            color = statusBarColor,
            darkIcons = useDarkStatusIcons
        )
    }

    ProfileScreen(
        onProfileEditClick = {
            if (successState != null) {
                goToProfileEditScreen(
                    successState.displayName ?: "",
                    successState.shortUsername ?: "",
                    successState.description ?: "",
                    successState.avatarUri?.toString(),
                    successState.backgroundUri?.toString(),
                )
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
        modifier = modifier,
        state = state,
    )

    SetNavigationBarColor(MainShellDefaults.appBackgroundColor)
}

private fun hasBleRuntimePermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    val hasAdvertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    return hasScan && hasAdvertise && hasConnect
}
