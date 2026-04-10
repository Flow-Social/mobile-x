package me.floow.shared.profile.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.coerceAtLeast
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.delete
import flow.feature.shared.generated.resources.edit
import flow.feature.shared.generated.resources.last_seen_at
import flow.feature.shared.generated.resources.offline
import flow.feature.shared.generated.resources.online
import flow.feature.shared.generated.resources.share
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import me.floow.shared.profile.resolveProfileListUrls
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.ui.segments.buttons.ProfileButtonsSegment
import me.floow.shared.profile.ui.segments.content.ProfileContentSegment
import me.floow.shared.profile.ui.segments.summary.ProfileSummarySegment
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.misc.PostActionsSheetContent
import me.floow.uikit.util.formatLastSeen
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private data class PostMenuContext(
    val post: ProfilePostItem,
    val sourceSnapshot: PostMediaSourceSnapshot?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenSuccessState(
    id: String,
    shortUsername: String?,
    avatarUri: String?,
    backgroundUri: String?,
    displayName: String?,
    description: String?,
    totalLikesReceived: Int,
    isSelf: Boolean,
    posts: List<ProfilePostItem>,
    arePostsLoading: Boolean,
    arePostsError: Boolean,
    canLoadMorePosts: Boolean,
    isLoadingMorePosts: Boolean,
    isOnline: Boolean = false,
    lastSeenAtMillis: Long? = null,
    onProfileEditClick: () -> Unit,
    onAddPostButtonClick: () -> Unit,
    onMessageButtonClick: () -> Unit,
    onShareProfileClick: () -> Unit,
    onTopBarActionClick: () -> Unit,
    onBackClick: () -> Unit,
    onPostClick: (String, PostMediaSourceSnapshot?) -> Unit,
    onEditPost: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onSharePost: (String) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    onLoadMorePosts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var postToDelete by remember { mutableStateOf<ProfilePostItem?>(null) }
    var showMenuForPost by remember { mutableStateOf<PostMenuContext?>(null) }
    val postsGridState = rememberLazyGridState()
    val postMenuSheetState = rememberModalBottomSheetState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    var contentHeight by remember { mutableStateOf(0.dp) }
    var availableBoundsBottomY by remember { mutableStateOf(0f) }
    var buttonsBottomY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
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
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val sheetCornerRadius by animateDpAsState(
        targetValue = if (isSheetExpanded) 0.dp else 24.dp,
        animationSpec = tween(durationMillis = 220),
        label = "sheetCornerRadius"
    )
    var heroBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    showMenuForPost?.let { menuContext ->
        ModalBottomSheet(
            onDismissRequest = { showMenuForPost = null },
            sheetState = postMenuSheetState
        ) {
            PostActionsSheetContent(
                canEdit = isSelf,
                editText = stringResource(Res.string.edit),
                deleteText = stringResource(Res.string.delete),
                shareText = stringResource(Res.string.share),
                onEdit = {
                    onEditPost(menuContext.post.id, menuContext.sourceSnapshot)
                    showMenuForPost = null
                },
                onDelete = {
                    postToDelete = menuContext.post
                    showMenuForPost = null
                },
                onShare = {
                    onSharePost(menuContext.post.id)
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
                    val bounds = coordinates.boundsInWindow()
                    availableBoundsBottomY = bounds.bottom
                    availableHeight = coordinates.size.height.dp
                }
        ) {
            val rawPeekHeight = if (buttonsBottomY == 0f || availableBoundsBottomY <= 0f) {
                (availableHeight - contentHeight).coerceIn(80.dp, 420.dp)
            } else {
                val remainingPx = (availableBoundsBottomY - buttonsBottomY).coerceAtLeast(0f)
                with(density) { remainingPx.toDp() }.coerceIn(80.dp, 420.dp)
            }
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
                topBar = {},
                containerColor = Color.Black,
                sheetContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .desktopSheetWheelInterceptor(
                                shouldConsumeScroll = {
                                    scaffoldState.bottomSheetState.currentValue != scaffoldState.bottomSheetState.targetValue
                                }
                            ) { scrollDeltaY ->
                                val bottomSheetState = scaffoldState.bottomSheetState
                                when {
                                    scrollDeltaY > 1f &&
                                        bottomSheetState.currentValue != SheetValue.Expanded &&
                                        bottomSheetState.targetValue != SheetValue.Expanded -> {
                                        scope.launch { bottomSheetState.expand() }
                                        true
                                    }

                                    scrollDeltaY < -1f &&
                                        isSheetExpanded &&
                                        isPostsGridAtTop &&
                                        bottomSheetState.currentValue == SheetValue.Expanded &&
                                        bottomSheetState.targetValue == SheetValue.Expanded -> {
                                        scope.launch { bottomSheetState.partialExpand() }
                                        true
                                    }

                                    else -> false
                                }
                            }
                            .pointerInput(isSheetExpanded, isPostsGridAtTop) {
                                val dragThresholdPx = 56.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    if (down.type != PointerType.Mouse) {
                                        return@awaitEachGesture
                                    }

                                    var totalDragY = 0f
                                    val dragChange = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                        if (abs(over.y) <= abs(over.x)) {
                                            return@awaitTouchSlopOrCancellation
                                        }
                                        change.consume()
                                        totalDragY += over.y
                                    }

                                    if (dragChange == null) {
                                        return@awaitEachGesture
                                    }

                                    val completed = drag(dragChange.id) { change ->
                                        val dragAmount = change.positionChange()
                                        change.consume()
                                        totalDragY += dragAmount.y
                                    }

                                    if (!completed) {
                                        return@awaitEachGesture
                                    }

                                    val bottomSheetState = scaffoldState.bottomSheetState
                                    when {
                                        totalDragY <= -dragThresholdPx &&
                                            bottomSheetState.currentValue != SheetValue.Expanded &&
                                            bottomSheetState.targetValue != SheetValue.Expanded -> {
                                            scope.launch { bottomSheetState.expand() }
                                        }

                                        totalDragY >= dragThresholdPx &&
                                            isSheetExpanded &&
                                            isPostsGridAtTop &&
                                            bottomSheetState.currentValue == SheetValue.Expanded &&
                                            bottomSheetState.targetValue == SheetValue.Expanded -> {
                                            scope.launch { bottomSheetState.partialExpand() }
                                        }
                                    }
                                }
                            }
                            .background(
                                MaterialTheme.colorScheme.surface,
                                androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                    ) {
                        ProfileContentSegment(
                            posts = posts,
                            arePostsLoading = arePostsLoading,
                            arePostsError = arePostsError,
                            canLoadMorePosts = canLoadMorePosts,
                            isLoadingMorePosts = isLoadingMorePosts,
                            onLoadMorePosts = onLoadMorePosts,
                            gridState = postsGridState,
                            onPostLongClick = { postId, sourceSnapshot ->
                                posts.firstOrNull { it.id == postId }?.let { post ->
                                    showMenuForPost = PostMenuContext(post, sourceSnapshot)
                                }
                            },
                            onPostClick = onPostClick,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                sheetPeekHeight = animatedPeekHeight,
                sheetSwipeEnabled = !isSheetExpanded || isPostsGridAtTop,
                sheetShape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = sheetCornerRadius,
                    topEnd = sheetCornerRadius
                ),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            contentHeight = coordinates.size.height.dp
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
                            val fallbackPainter = ColorPainter(Color.DarkGray)
                            val backgroundModel = backgroundUri?.takeIf { it.isNotBlank() }
                            val (backgroundLqUrl, backgroundPreviewUrl) = remember(backgroundModel) {
                                resolveProfileListUrls(backgroundModel)
                            }
                            var heroBackgroundPainter by remember(backgroundModel) {
                                mutableStateOf<Painter>(fallbackPainter)
                            }
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
                                    fullUrl = backgroundModel,
                                    mode = ProgressiveImageMode.LIST,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onPainterChanged = { painter ->
                                        if (painter != null) heroBackgroundPainter = painter
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.5f), Color.Black),
                                            startY = 0f
                                        )
                                    )
                            )

                            ProfileScreenTopBar(
                                username = shortUsername,
                                isSelf = isSelf,
                                onShareClick = onTopBarActionClick,
                                onBackClick = onBackClick,
                                heroBackgroundPainter = heroBackgroundPainter,
                                heroBoundsInWindow = heroBoundsInWindow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .align(Alignment.TopCenter)
                            )

                            val statusLabel = if (!isSelf) {
                                val formattedLastSeen = formatLastSeen(lastSeenAtMillis)
                                when {
                                    isOnline -> stringResource(Res.string.online)
                                    formattedLastSeen.isNotBlank() -> {
                                        stringResource(
                                            Res.string.last_seen_at,
                                            formattedLastSeen
                                        )
                                    }
                                    else -> stringResource(Res.string.offline)
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
                                    profileAvatarUri = avatarUri,
                                    displayName = displayName,
                                    description = description,
                                    totalLikesReceived = totalLikesReceived,
                                    statusLabel = statusLabel,
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                ProfileButtonsSegment(
                                    isSelf = isSelf,
                                    onAddPostButtonClick = onAddPostButtonClick,
                                    onMessageButtonClick = {
                                        if (!isSelf) onMessageButtonClick()
                                    },
                                    onEditButtonClick = onProfileEditClick,
                                    onShareButtonClick = onShareProfileClick,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coords ->
                                            buttonsBottomY = coords.boundsInWindow().bottom
                                        },
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

private fun Modifier.desktopSheetWheelInterceptor(
    shouldConsumeScroll: () -> Boolean,
    onScrollDeltaY: (Float) -> Boolean,
): Modifier = this.then(DesktopSheetWheelInterceptorElement(shouldConsumeScroll, onScrollDeltaY))

private data class DesktopSheetWheelInterceptorElement(
    val shouldConsumeScroll: () -> Boolean,
    val onScrollDeltaY: (Float) -> Boolean,
) : ModifierNodeElement<DesktopSheetWheelInterceptorNode>() {
    override fun create(): DesktopSheetWheelInterceptorNode =
        DesktopSheetWheelInterceptorNode(shouldConsumeScroll, onScrollDeltaY)

    override fun update(node: DesktopSheetWheelInterceptorNode) {
        node.shouldConsumeScroll = shouldConsumeScroll
        node.onScrollDeltaY = onScrollDeltaY
    }
}

private class DesktopSheetWheelInterceptorNode(
    var shouldConsumeScroll: () -> Boolean,
    var onScrollDeltaY: (Float) -> Boolean,
) : Modifier.Node(), PointerInputModifierNode {
    private var waitingForSheetSettle = false

    override fun onPointerEvent(pointerEvent: androidx.compose.ui.input.pointer.PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Initial || pointerEvent.type != PointerEventType.Scroll) {
            return
        }

        val scrollDeltaY = pointerEvent.changes.sumOf { it.scrollDelta.y.toDouble() }.toFloat()
        if (scrollDeltaY == 0f) return

        if (shouldConsumeScroll()) {
            waitingForSheetSettle = true
            pointerEvent.changes.forEach { it.consume() }
            return
        }

        if (waitingForSheetSettle) {
            waitingForSheetSettle = false
            pointerEvent.changes.forEach { it.consume() }
            return
        }

        if (onScrollDeltaY(scrollDeltaY)) {
            waitingForSheetSettle = true
            pointerEvent.changes.forEach { it.consume() }
        }
    }

    override fun onCancelPointerInput() {
        waitingForSheetSettle = false
    }
}
