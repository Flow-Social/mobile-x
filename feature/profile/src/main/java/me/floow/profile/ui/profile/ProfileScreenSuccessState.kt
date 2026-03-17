package me.floow.profile.ui.profile

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.abs
import me.floow.domain.models.Post
import me.floow.profile.ui.profile.segments.buttons.ProfileButtonsSegment
import me.floow.profile.ui.profile.bump.BumpBleProximityEffect
import me.floow.profile.ui.profile.bump.BumpDetectorEffect
import me.floow.profile.uilogic.bump.ProfileBumpMode
import me.floow.profile.uilogic.bump.ProfileBumpUiState
import me.floow.profile.ui.profile.segments.content.ProfileContentSegment
import me.floow.profile.ui.profile.segments.summary.ProfileSummarySegment
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.domain.utils.toLocalDateTimeFromEpochMillis
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.theme.FlowTheme
import androidx.compose.material3.SheetValue
import androidx.compose.foundation.BorderStroke

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import me.floow.uikit.components.misc.PostActionsSheetContent
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.util.SetStatusBarStyle
import java.time.format.DateTimeFormatter

private data class PostMenuContext(
	val post: Post,
	val sourceSnapshot: PostMediaSourceSnapshot?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenSuccessState(
    state: ProfileScreenState.Success,
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
    modifier: Modifier = Modifier
) {
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var showMenuForPost by remember { mutableStateOf<PostMenuContext?>(null) }
    val postsGridState = rememberLazyGridState()
    val postMenuSheetState = rememberModalBottomSheetState()
    val bumpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scaffoldState = rememberBottomSheetScaffoldState()
    
    val density = LocalDensity.current
    var contentHeight by remember { mutableStateOf(0.dp) }
    val heroHeight = 580.dp
    val isSheetExpanded by remember {
        derivedStateOf {
            scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded ||
                scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded
        }
    }
    val isPostsGridAtTop by remember {
        derivedStateOf {
            postsGridState.firstVisibleItemIndex == 0 &&
                postsGridState.firstVisibleItemScrollOffset == 0
        }
    }
    val sheetCornerRadius by animateDpAsState(
        targetValue = if (isSheetExpanded) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 220),
        label = "sheetCornerRadius"
    )
    val statusBarColor = if (isSheetExpanded) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        Color.Transparent
    }
    val useDarkStatusIcons = if (isSheetExpanded) {
        statusBarColor.luminance() > 0.5f
    } else {
        false
    }
    val haptic = LocalHapticFeedback.current
    var heroBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    SetStatusBarStyle(
        color = statusBarColor,
        darkIcons = useDarkStatusIcons
    )
    LaunchedEffect(scaffoldState.bottomSheetState) {
        var lastCurrent = scaffoldState.bottomSheetState.currentValue
        var lastTarget = scaffoldState.bottomSheetState.targetValue
        snapshotFlow { scaffoldState.bottomSheetState.targetValue to scaffoldState.bottomSheetState.currentValue }
            .distinctUntilChanged()
            .collect { (target, current) ->
                if (lastTarget == SheetValue.PartiallyExpanded && target == SheetValue.Expanded) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                if (lastCurrent == SheetValue.Expanded && current != SheetValue.Expanded) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                lastTarget = target
                lastCurrent = current
            }
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

    showMenuForPost?.let { menuContext ->
        ModalBottomSheet(
            onDismissRequest = { showMenuForPost = null },
            sheetState = postMenuSheetState
        ) {
            PostActionsSheetContent(
                canEdit = state.isSelf,
                editText = "Редактировать",
                deleteText = "Удалить",
                shareText = "Поделиться",
                onEdit = {
                    onEditPost(menuContext.post, menuContext.sourceSnapshot)
                    showMenuForPost = null
                },
                onDelete = {
                    postToDelete = menuContext.post
                    showMenuForPost = null
                },
                onShare = {
                    onSharePost(menuContext.post)
                    showMenuForPost = null
                }
            )
        }
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

    if (bumpUiState.isSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = onCancelBumpClick,
            sheetState = bumpSheetState,
        ) {
            ShareAndBumpSheetContent(
                bumpEnabled = bumpEnabled,
                bumpUiState = bumpUiState,
                onShowQrClick = onShareProfileClick,
                onShareLinkClick = onShareProfileClick,
            )
        }
    }

    postToDelete?.let { deletingPost ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePost(deletingPost.id)
                        postToDelete = null
                    }
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { postToDelete = null }) { Text("Отмена") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        var availableHeight by remember { mutableStateOf(0.dp) }
        var stablePeekHeight by remember { mutableStateOf<Dp?>(null) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    availableHeight = with(density) { coordinates.size.height.toDp() }
                }
        ) {
            // Calculate peek height: space remaining after content
            // If content is taller than available space, peek height is minimal (80dp)
            val rawPeekHeight = (availableHeight - contentHeight).coerceIn(80.dp, 420.dp)
            LaunchedEffect(rawPeekHeight) {
                val previous = stablePeekHeight
                if (previous == null) {
                    stablePeekHeight = rawPeekHeight
                } else if (abs(rawPeekHeight.value - previous.value) >= 12f) {
                    stablePeekHeight = rawPeekHeight
                }
            }
            val animatedPeekHeight by animateDpAsState(
                targetValue = stablePeekHeight ?: rawPeekHeight,
                animationSpec = tween(durationMillis = 180),
                label = "sheetPeekHeight"
            )

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                // TopBar is moved outside to allow accurate measurement and placement
                topBar = {},
                containerColor = Color.Black,
                sheetDragHandle = {
                    HapticDragHandle()
                },
                sheetContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    ) {
                        ProfileContentSegment(
                            posts = state.posts,
                            arePostsLoading = state.arePostsLoading,
                            arePostsError = state.arePostsError,
                            canLoadMorePosts = state.canLoadMorePosts,
                            isLoadingMorePosts = state.isLoadingMorePosts,
                            onLoadMorePosts = onLoadMorePosts,
                            gridState = postsGridState,
                            onPostLongClick = { post, sourceSnapshot ->
                                showMenuForPost = PostMenuContext(
									post = post,
									sourceSnapshot = sourceSnapshot
								)
                            },
                            onPostClick = onPostClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                sheetPeekHeight = animatedPeekHeight,
                sheetSwipeEnabled = isPostsGridAtTop,
                sheetShape = RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier,
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            contentHeight = with(density) { coordinates.size.height.toDp() }
                        }
                    ) {
	                        Box(
	                            modifier = Modifier
	                                .fillMaxWidth()
	                                .height(heroHeight)
                                    .onGloballyPositioned { coordinates ->
                                        heroBoundsInWindow = coordinates.boundsInWindow()
                                    }
	                        ) {
	                            val fallbackPainter = androidx.compose.ui.graphics.painter.ColorPainter(Color.DarkGray)
	                            val backgroundModel = state.backgroundUri?.toString()?.takeIf { it.isNotBlank() }
                                val (backgroundLqUrl, backgroundPreviewUrl) = remember(backgroundModel) {
                                    resolveProfileListUrls(backgroundModel)
                                }
                                var heroBackgroundPainter by remember(backgroundModel) {
                                    mutableStateOf<androidx.compose.ui.graphics.painter.Painter>(fallbackPainter)
                                }
	                            if (LocalInspectionMode.current) {
	                                Image(
	                                    painter = androidx.compose.ui.graphics.painter.ColorPainter(Color.DarkGray),
	                                    contentDescription = null,
	                                    contentScale = ContentScale.Crop,
	                                    modifier = Modifier.fillMaxSize()
	                                )
	                            } else {
                                    if (backgroundModel == null) {
                                        Image(
                                            painter = heroBackgroundPainter,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        ProgressiveImage(
                                            lqUrl = backgroundLqUrl,
                                            previewUrl = backgroundPreviewUrl,
                                            fullUrl = null,
                                            mode = ProgressiveImageMode.LIST,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            onPainterChanged = { painter ->
                                                if (painter != null) {
                                                    heroBackgroundPainter = painter
                                                }
                                            }
                                        )
                                    }
	                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.5f),
                                                Color.Black
                                            ),
                                            startY = 0f
                                        )
                                    )
                            )

                            ProfileScreenTopBar(
                                username = state.shortUsername,
                                isSelf = state.isSelf,
                                onShareClick = onOpenBumpSheet,
                                onBackClick = onBackClick,
                                heroBackgroundPainter = heroBackgroundPainter,
                                heroBoundsInWindow = heroBoundsInWindow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .align(Alignment.TopCenter)
                            )

							val lastSeenLabel = if (!state.isSelf) {
								when {
									state.isOnline -> stringResource(me.floow.uikit.R.string.online)
									state.lastSeenAtMillis != null && state.lastSeenAtMillis > 0L -> {
										val timeLabel = state.lastSeenAtMillis
											.toLocalDateTimeFromEpochMillis()
											.format(DateTimeFormatter.ofPattern("HH:mm"))
										stringResource(me.floow.uikit.R.string.last_seen_at, timeLabel)
									}
									else -> stringResource(me.floow.uikit.R.string.offline)
								}
							} else {
								null
							}

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            ) {
                                ProfileSummarySegment(
                                    profileAvatarUri = state.avatarUri,
                                    displayName = state.displayName,
                                    description = state.description,
                                    totalLikesReceived = state.totalLikesReceived,
									statusLabel = lastSeenLabel,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                ProfileButtonsSegment(
                                    isSelf = state.isSelf,
                                    onAddPostButtonClick = onAddPostButtonClick,
                                    onMessageButtonClick = onMessageButtonClick,
                                    onEditButtonClick = onProfileEditClick,
                                    onShareButtonClick = onShareProfileClick,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                HorizontalDivider(color = Color.Transparent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HapticDragHandle() {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var triggered = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed && change.previousPressed) break
                        val deltaX = change.position.x - change.previousPosition.x
                        val deltaY = change.position.y - change.previousPosition.y
                        if (!triggered && deltaY != 0f && abs(deltaY) > abs(deltaX)) {
                            if (deltaY < 0f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            triggered = true
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.BottomSheetDefaults.DragHandle()
    }
}

@Composable
private fun ShareAndBumpSheetContent(
    bumpEnabled: Boolean,
    bumpUiState: ProfileBumpUiState,
    onShowQrClick: () -> Unit,
    onShareLinkClick: () -> Unit,
) {
    val mode = bumpUiState.mode
    val hasError = !bumpUiState.errorMessage.isNullOrBlank()
    val helperText = when {
        !bumpEnabled -> stringResource(me.floow.profile.R.string.bump_feature_disabled)
        hasError -> bumpUiState.errorMessage
        mode == ProfileBumpMode.Timeout -> stringResource(me.floow.profile.R.string.bump_status_timeout)
        else -> null
    }
    val colorScheme = MaterialTheme.colorScheme
    val helperTextColor = when {
        !bumpEnabled -> colorScheme.onSurfaceVariant
        hasError -> colorScheme.error
        mode == ProfileBumpMode.Timeout -> colorScheme.error
        else -> colorScheme.onSurfaceVariant
    }
    val headlineAccent = colorScheme.primary
    val sheetContainerColor = colorScheme.surfaceContainerLow
    val qrOutline = colorScheme.outlineVariant
    val headlineColor = colorScheme.onSurface
    val primaryActionContainer = colorScheme.onSurface
    val primaryActionContent = colorScheme.surface

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(sheetContainerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(me.floow.profile.R.string.bump_sheet_headline_line1),
            style = MaterialTheme.typography.headlineSmall,
            color = headlineAccent,
        )

        Text(
            text = stringResource(me.floow.profile.R.string.bump_sheet_headline_line2),
            style = MaterialTheme.typography.headlineMedium,
            color = headlineColor,
            fontWeight = FontWeight.Bold,
        )

        if (!helperText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = helperText,
                style = MaterialTheme.typography.bodyMedium,
                color = helperTextColor,
            )
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(color = sheetContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = me.floow.profile.R.drawable.bumpme),
                contentDescription = "Bump test hero image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = onShowQrClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, qrOutline),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = headlineAccent,
            ),
        ) {
            Text(
                text = stringResource(me.floow.profile.R.string.bump_show_qr_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onShareLinkClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryActionContainer,
                contentColor = primaryActionContent,
            ),
        ) {
            Text(
                text = stringResource(me.floow.profile.R.string.bump_share_link_action),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun bumpStatusLabel(mode: ProfileBumpMode): String {
    return when (mode) {
        ProfileBumpMode.Starting -> stringResource(me.floow.profile.R.string.bump_status_starting)
        ProfileBumpMode.Listening -> stringResource(me.floow.profile.R.string.bump_status_listening)
        ProfileBumpMode.Matching -> stringResource(me.floow.profile.R.string.bump_status_matching)
        ProfileBumpMode.Timeout -> stringResource(me.floow.profile.R.string.bump_status_timeout)
        ProfileBumpMode.Error -> stringResource(me.floow.profile.R.string.bump_status_error)
        ProfileBumpMode.Matched -> stringResource(me.floow.profile.R.string.bump_status_matched)
        ProfileBumpMode.Idle -> ""
    }
}

@Preview
@Composable
fun ProfileScreenSuccessStatePreview() {
	CompositionLocalProvider(LocalInspectionMode provides true) {
		FlowTheme {
			ProfileScreenSuccessState(
					state = ProfileScreenState.Success(
						id = "1",
						shortUsername = "demndevel",
						avatarUri = Uri.parse("https://http.cat/images/101.jpg"),
						backgroundUri = Uri.parse("https://http.cat/images/200.jpg"),
					displayName = "demndevel",
					description = "some description idk. I like eating lorem ipsum. Dolor sit amet. meow!",
						totalLikesReceived = 1567,
						isSelf = true,
						posts = emptyList(),
						arePostsLoading = false,
						arePostsError = false,
						canLoadMorePosts = false,
						isLoadingMorePosts = false,
					),
					onProfileEditClick = {},
					onAddPostButtonClick = {},
					onMessageButtonClick = {},
					onShareProfileClick = {},
	                onOpenBumpSheet = {},
	                onHideBumpSheet = {},
					onStartBumpClick = {},
					onCancelBumpClick = {},
				onBumpImpactDetected = {},
				onBumpPeerDetected = { _, _ -> },
				bumpUiState = ProfileBumpUiState(),
                bumpMatchSignal = 0,
				bumpEnabled = true,
				onBackClick = {},
				onPostClick = { _, _ -> },
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}
