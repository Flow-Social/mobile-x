package me.floow.profile.ui.profile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.profile.ui.profile.bump.ProfileBumpCoordinator
import me.floow.profile.uilogic.bump.ProfileBumpViewModel
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.profile.uilogic.profile.ProfileScreenViewModel
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.util.SetNavigationBarColor

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
    onBumpMatchNavigate: (String) -> Unit = {},
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // Location permission is soft for bump: we still continue with BLE-only matching.
    }
    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        // BLE permission is also soft: no hard error if denied.
    }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    LaunchedEffect(refreshPostsSignal) {
        if (refreshPostsSignal) {
            viewModel.loadData(forceRemote = true)
            consumeRefreshPostsSignal()
        }
    }

    LaunchedEffect(bumpViewModel) {
        bumpViewModel.openMatchedProfile.collectLatest { matchedUserId ->
            bumpViewModel.hideSheet()
            bumpMatchSignal += 1
            onBumpMatchNavigate(matchedUserId)
            bumpViewModel.resetToIdle()
        }
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

            if (!bumpCoordinator.hasLocationPermission()) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
            if (!hasBleRuntimePermissions(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                    ),
                )
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

    SetNavigationBarColor(
        NavigationBarDefaults.containerColor,
    )
}

private fun hasBleRuntimePermissions(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val hasScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    val hasAdvertise = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    val hasConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    return hasScan && hasAdvertise && hasConnect
}
