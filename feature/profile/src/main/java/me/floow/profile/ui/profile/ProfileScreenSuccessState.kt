package me.floow.profile.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate
import me.floow.profile.R
import me.floow.profile.ui.common.SheetStatusBarStyle
import me.floow.profile.ui.common.bumpSheetStatusBarAppearance
import me.floow.profile.ui.profile.bump.BumpBleProximityEffect
import me.floow.profile.ui.profile.bump.BumpDetectorEffect
import me.floow.profile.uilogic.bump.ProfileBumpMode
import me.floow.profile.uilogic.bump.ProfileBumpUiState
import me.floow.shared.profile.uilogic.ProfileScreenState as SharedProfileScreenState
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.util.SetStatusBarStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenSuccessState(
    state: SharedProfileScreenState.Success,
    onProfileEditClick: () -> Unit,
    onAddPostButtonClick: () -> Unit,
    onMessageButtonClick: () -> Unit,
    onShareProfileClick: () -> Unit,
    onOpenBumpSheet: () -> Unit,
    onHideBumpSheet: () -> Unit,
    onStartBumpClick: () -> Unit,
    onCancelBumpClick: () -> Unit,
    onBumpImpactDetected: (Float) -> Unit,
    onBumpPeerDetected: (String, Int) -> Unit,
    bumpUiState: ProfileBumpUiState,
    bumpMatchSignal: Int,
    bumpEnabled: Boolean,
    onBackClick: () -> Unit,
    onPostClick: (Post, PostMediaSourceSnapshot?) -> Unit,
    onEditPost: (Post, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onSharePost: (Post) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    onLoadMorePosts: () -> Unit = {},
    suppressStatusBarStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    val statusBarColor = if (bumpUiState.isSheetVisible) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        Color.Transparent
    }
    val useDarkStatusIcons = if (bumpUiState.isSheetVisible) {
        false
    } else {
        statusBarColor.luminance() > 0.5f
    }
    val bumpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!suppressStatusBarStyle) {
        SetStatusBarStyle(
            color = statusBarColor,
            darkIcons = useDarkStatusIcons
        )
    }

    LaunchedEffect(bumpUiState.isSheetVisible, bumpEnabled, bumpUiState.mode) {
        if (!bumpUiState.isSheetVisible || !bumpEnabled) return@LaunchedEffect
        if (
            bumpUiState.mode == ProfileBumpMode.Idle ||
            bumpUiState.mode == ProfileBumpMode.Timeout ||
            bumpUiState.mode == ProfileBumpMode.Error
        ) {
            onStartBumpClick()
        }
    }

    LaunchedEffect(bumpMatchSignal) {
        if (bumpMatchSignal <= 0 || !bumpUiState.isSheetVisible) return@LaunchedEffect
        onHideBumpSheet()
    }

    BumpDetectorEffect(
        enabled = bumpUiState.isSheetVisible && bumpUiState.isDetectorEnabled,
        onImpactDetected = onBumpImpactDetected,
    )
    BumpBleProximityEffect(
        enabled = bumpUiState.isSheetVisible && bumpUiState.isBleProximityEnabled,
        advertiseToken = bumpUiState.bleToken,
        onPeerTokenDetected = onBumpPeerDetected,
    )

    Box(modifier = modifier.fillMaxSize()) {
        me.floow.shared.profile.ui.ProfileScreenSuccessState(
            id = state.id,
            shortUsername = state.shortUsername,
            avatarUri = state.avatarUri,
            backgroundUri = state.backgroundUri,
            displayName = state.displayName,
            description = state.description,
            totalLikesReceived = state.totalLikesReceived,
            isSelf = state.isSelf,
            posts = state.posts,
            arePostsLoading = state.arePostsLoading,
            arePostsError = state.arePostsError,
            canLoadMorePosts = state.canLoadMorePosts,
            isLoadingMorePosts = state.isLoadingMorePosts,
            isOnline = state.isOnline,
            lastSeenAtMillis = state.lastSeenAtMillis,
            onProfileEditClick = onProfileEditClick,
            onAddPostButtonClick = onAddPostButtonClick,
            onMessageButtonClick = onMessageButtonClick,
            onShareProfileClick = onShareProfileClick,
            onTopBarActionClick = if (state.isSelf) onOpenBumpSheet else onShareProfileClick,
            onBackClick = onBackClick,
            onPostClick = { postId, snapshot ->
                state.posts.firstOrNull { it.id == postId }?.let { post ->
                    onPostClick(post.toDomainPost(state), snapshot)
                }
            },
            onEditPost = { postId, snapshot ->
                state.posts.firstOrNull { it.id == postId }?.let { post ->
                    onEditPost(post.toDomainPost(state), snapshot)
                }
            },
            onSharePost = { postId ->
                state.posts.firstOrNull { it.id == postId }?.let { post ->
                    onSharePost(post.toDomainPost(state))
                }
            },
            onDeletePost = onDeletePost,
            onLoadMorePosts = onLoadMorePosts,
            modifier = Modifier.fillMaxSize()
        )

        if (bumpUiState.isSheetVisible) {
            ModalBottomSheet(
                onDismissRequest = onCancelBumpClick,
                sheetState = bumpSheetState,
            ) {
                BumpOverlaySheetContent(
                    bumpEnabled = bumpEnabled,
                    bumpUiState = bumpUiState,
                    onShowQrClick = onShareProfileClick,
                    onShareLinkClick = onShareProfileClick,
                )
            }
        }
    }
}

@OptIn(RawValueObjectCreate::class)
private fun ProfilePostItem.toDomainPost(owner: SharedProfileScreenState.Success): Post {
    return Post(
        id = id,
        author = PostAuthor(
            id = owner.id,
            name = owner.displayName?.takeIf(String::isNotBlank)?.let(ProfileName::createRaw),
            username = owner.shortUsername?.takeIf(String::isNotBlank)?.let(ProfileUsername::createRaw),
            avatarUrl = owner.avatarUri,
        ),
        content = PostContent(
            imageUrls = viewerUrls.ifEmpty { listOfNotNull(fullUrl, previewUrl, lqUrl) },
            description = description,
            imageVariants = emptyList(),
        ),
        category = category.orEmpty(),
        createdAt = createdAtMillis,
        likesCount = likesCount,
        commentsCount = commentsCount,
    )
}

@Composable
private fun BumpOverlaySheetContent(
    bumpEnabled: Boolean,
    bumpUiState: ProfileBumpUiState,
    onShowQrClick: () -> Unit,
    onShareLinkClick: () -> Unit,
) {
    SheetStatusBarStyle(
        appearance = bumpSheetStatusBarAppearance(MaterialTheme.colorScheme),
    )
    val helperText = when {
        !bumpEnabled -> stringResource(R.string.bump_feature_disabled)
        !bumpUiState.errorMessage.isNullOrBlank() -> bumpUiState.errorMessage
        bumpUiState.mode == ProfileBumpMode.Timeout -> stringResource(R.string.bump_status_timeout)
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.bump_sheet_headline_line1),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.bump_sheet_headline_line2),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )

        if (!helperText.isNullOrBlank()) {
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.bumpme),
            contentDescription = null,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxSize()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )

        OutlinedButton(
            onClick = onShowQrClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = stringResource(R.string.bump_show_qr_action),
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        Button(
            onClick = onShareLinkClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text(
                text = stringResource(R.string.bump_share_link_action),
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }
    }
}
