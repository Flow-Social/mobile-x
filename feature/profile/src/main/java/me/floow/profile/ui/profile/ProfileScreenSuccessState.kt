package me.floow.profile.ui.profile

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import me.floow.domain.models.Post
import me.floow.profile.ui.profile.segments.buttons.ProfileButtonsSegment
import me.floow.profile.ui.profile.segments.content.ProfileContentSegment
import me.floow.profile.ui.profile.segments.summary.ProfileSummarySegment
import me.floow.profile.uilogic.profile.ProfileScreenState
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.theme.FlowTheme
import androidx.compose.material3.SheetValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import me.floow.uikit.components.misc.PostActionsSheetContent
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode

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
    onShareButtonClick: () -> Unit,
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
    val sheetState = rememberModalBottomSheetState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    
    val density = LocalDensity.current
    var contentHeight by remember { mutableStateOf(0.dp) }
    val heroHeight = 540.dp
    val isSheetExpanded by remember {
        derivedStateOf { scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded }
    }
    val isPostsGridAtTop by remember {
        derivedStateOf {
            postsGridState.firstVisibleItemIndex == 0 &&
                postsGridState.firstVisibleItemScrollOffset == 0
        }
    }
    val isLightTheme = !isSystemInDarkTheme()
    val isLightThemeState = rememberUpdatedState(isLightTheme)
    val sheetCornerRadius by animateDpAsState(
        targetValue = if (isSheetExpanded) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 220),
        label = "sheetCornerRadius"
    )
    val statusBarColor = if (isSheetExpanded) {
        MaterialTheme.colorScheme.surface
    } else {
        Color.Transparent
    }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val hostActivity = view.context as? Activity
    var heroBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    LaunchedEffect(hostActivity, isSheetExpanded, isLightTheme, statusBarColor) {
        val window = hostActivity?.window ?: return@LaunchedEffect
        val darkIcons = isLightTheme && isSheetExpanded
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
        window.statusBarColor = statusBarColor.toArgb()
    }
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
    DisposableEffect(hostActivity) {
        onDispose {
            val window = hostActivity?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightThemeState.value
        }
    }

    showMenuForPost?.let { menuContext ->
        ModalBottomSheet(
            onDismissRequest = { showMenuForPost = null },
            sheetState = sheetState
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
            val rawPeekHeight = (availableHeight - contentHeight).coerceAtLeast(80.dp)
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
	                            val fallbackPainter = painterResource(me.floow.profile.R.drawable.profile_hero_bg)
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
                                onShareClick = onShareButtonClick,
                                onBackClick = onBackClick,
                                heroBackgroundPainter = heroBackgroundPainter,
                                heroBoundsInWindow = heroBoundsInWindow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .align(Alignment.TopCenter)
                            )

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
                                    Modifier.fillMaxWidth(),
                                )

                                ProfileButtonsSegment(
                                    isSelf = state.isSelf,
                                    onAddPostButtonClick = onAddPostButtonClick,
                                    onMessageButtonClick = onMessageButtonClick,
                                    onEditButtonClick = onProfileEditClick,
                                    onShareButtonClick = onShareButtonClick,
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
				onShareButtonClick = {},
				onBackClick = {},
				onPostClick = { _, _ -> },
				modifier = Modifier.fillMaxWidth()
			)
		}
	}
}
