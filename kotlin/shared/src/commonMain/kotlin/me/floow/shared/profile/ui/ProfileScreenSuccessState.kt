package me.floow.shared.profile.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
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
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.delete
import flow.shared.generated.resources.edit
import flow.shared.generated.resources.share
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import me.floow.shared.image.PrefetchImageUrlsEffect
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.ProfilePostSyncState
import me.floow.shared.profile.ui.segments.buttons.ProfileButtonsSegment
import me.floow.shared.profile.ui.segments.content.ProfileContentSegment
import me.floow.shared.profile.ui.segments.summary.ProfileSummarySegment
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.misc.PostActionsSheetContent
import me.floow.uikit.util.formatPresenceStatusLabel
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private data class PostMenuContext(
    val post: ProfilePostItem,
    val sourceSnapshot: PostMediaSourceSnapshot?,
)

private const val PROFILE_PREFETCH_POSTS_COUNT = 4
private val PROFILE_PREFETCH_POST_IMAGE_SIZE = IntSize(420, 560)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreenSuccessState(
    id: String,
    shortUsername: String?,
    avatarUri: String?,
    avatarThumb: String? = null,
    backgroundUri: String?,
    backgroundThumb: String? = null,
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
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit,
    onPostClick: (String, PostMediaSourceSnapshot?) -> Unit,
    onEditPost: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onSharePost: (String) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    onRetryPendingPost: (String) -> Unit = {},
    onLoadMorePosts: () -> Unit = {},
    onSheetExpandedChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val heroPrefetchUrls = remember(avatarUri, backgroundUri) {
        buildList {
            addAll(addProfileHeroPrefetchUrls(avatarUri))
            addAll(addProfileHeroPrefetchUrls(backgroundUri))
        }.distinct()
    }
    val postPrefetchUrls = remember(posts) {
        posts
            .take(PROFILE_PREFETCH_POSTS_COUNT)
            .flatMap { it.prefetchUrlsForProfile() }
            .distinct()
    }
    PrefetchImageUrlsEffect(
        urls = heroPrefetchUrls,
        size = null,
    )
    PrefetchImageUrlsEffect(
        urls = postPrefetchUrls,
        size = PROFILE_PREFETCH_POST_IMAGE_SIZE,
    )

    var postToDelete by remember { mutableStateOf<ProfilePostItem?>(null) }
    var showMenuForPost by remember { mutableStateOf<PostMenuContext?>(null) }
    val postsGridState = rememberLazyGridState()
    val postMenuSheetState = rememberModalBottomSheetState()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val isSheetExpanded by remember {
        derivedStateOf {
            scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded
        }
    }
    val sheetCornerRadius by animateDpAsState(
        targetValue = if (isSheetExpanded) 0.dp else 24.dp,
        label = "profileSheetCornerRadius"
    )
    val dragHandleAlpha by animateFloatAsState(
        targetValue = if (isSheetExpanded) 0f else 1f,
        label = "profileDragHandleAlpha"
    )
    val isPostsGridAtTop by remember {
        derivedStateOf {
            postsGridState.firstVisibleItemIndex == 0 &&
            postsGridState.firstVisibleItemScrollOffset == 0
        }
    }
    LaunchedEffect(isSheetExpanded) {
        onSheetExpandedChanged(isSheetExpanded)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var heroBoundsInWindow by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    showMenuForPost?.let { menuContext ->
        val optimisticState = menuContext.post.optimisticState
        val pendingType = menuContext.post.pendingMutationType
        val isPendingCreate = pendingType == me.floow.shared.profile.uilogic.ProfilePostPendingMutationType.CREATE
        val isFailed = optimisticState == "FAILED" || menuContext.post.syncState == ProfilePostSyncState.FAILED
        val isActivePending = optimisticState == "PENDING"
        ModalBottomSheet(
            onDismissRequest = { showMenuForPost = null },
            sheetState = postMenuSheetState
        ) {
            PostActionsSheetContent(
                canEdit = isSelf && optimisticState == null && !isFailed,
                canDelete = (isSelf && optimisticState == null) || isFailed,
                canShare = optimisticState == null && !isFailed,
                retryText = if (isFailed) "Повторить загрузку" else null,
                cancelText = if (isPendingCreate && isActivePending && !isFailed) "Отменить" else null,
                queueDeleteText = null,
                editText = stringResource(Res.string.edit),
                deleteText = if (optimisticState == null && !isFailed) stringResource(Res.string.delete) else "Удалить",
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
                },
                onRetry = {
                    onRetryPendingPost(menuContext.post.id)
                    showMenuForPost = null
                },
                onCancel = {
                    postToDelete = menuContext.post
                    showMenuForPost = null
                },
                onQueueDelete = {
                    postToDelete = menuContext.post
                    showMenuForPost = null
                }
            )
        }
    }

    postToDelete?.let { deletingPost ->
        val isFailed = deletingPost.optimisticState == "FAILED" || deletingPost.syncState == ProfilePostSyncState.FAILED
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = {
                Text(
                    when {
                        isFailed -> "Удалить пост?"
                        deletingPost.pendingMutationType == me.floow.shared.profile.uilogic.ProfilePostPendingMutationType.CREATE && deletingPost.optimisticState == "PENDING" -> "Отменить публикацию?"
                        deletingPost.optimisticState == null -> "Удалить пост?"
                        else -> "Удалить из очереди?"
                    }
                )
            },
            text = {
                Text(
                    when {
                        isFailed -> "Пост не будет опубликован"
                        deletingPost.pendingMutationType == me.floow.shared.profile.uilogic.ProfilePostPendingMutationType.CREATE && deletingPost.optimisticState == "PENDING" -> "Пост будет удалён из очереди отправки"
                        deletingPost.optimisticState == null -> "Это действие нельзя отменить"
                        else -> "Пост не будет опубликован"
                    }
                )
            },
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val availableHeight = maxHeight
            val currentPeekHeight = (availableHeight * 0.25f).coerceIn(160.dp, 210.dp)
            val heroHeight = (availableHeight - currentPeekHeight).coerceAtLeast(0.dp)
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                topBar = {},
                containerColor = Color.Black,
                sheetDragHandle = {
                    BottomSheetDefaults.DragHandle(
                        modifier = Modifier.graphicsLayer { alpha = dragHandleAlpha }
                    )
                },
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
                            .background(
                                MaterialTheme.colorScheme.surface,
                                androidx.compose.foundation.shape.RoundedCornerShape(topStart = sheetCornerRadius, topEnd = sheetCornerRadius)
                            )
                    ) {
                        ProfileContentSegment(
                            posts = posts,
                            arePostsLoading = arePostsLoading,
                            arePostsError = arePostsError,
                            canLoadMorePosts = canLoadMorePosts,
                            isLoadingMorePosts = isLoadingMorePosts,
                            onLoadMorePosts = onLoadMorePosts,
                            onRetryPendingPost = onRetryPendingPost,
                            onCancelPendingPost = onDeletePost,
                            gridState = postsGridState,
                            userScrollEnabled = isSheetExpanded,
                            onPostLongClick = { postId, sourceSnapshot ->
                                posts.firstOrNull { it.id == postId }?.let { post ->
                                    val isFailed = post.syncState == ProfilePostSyncState.FAILED || post.optimisticState == "FAILED"
                                    if (!post.isOptimistic || isFailed) {
                                        showMenuForPost = PostMenuContext(post, sourceSnapshot)
                                    }
                                }
                            },
                            onPostClick = { postId, sourceSnapshot ->
                                posts.firstOrNull { it.id == postId }?.let { post ->
                                    if (!post.isOptimistic) {
                                        onPostClick(postId, sourceSnapshot)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                },
                sheetPeekHeight = currentPeekHeight,
                sheetSwipeEnabled = true,
                sheetShape = androidx.compose.foundation.shape.RoundedCornerShape(
                    topStart = sheetCornerRadius,
                    topEnd = sheetCornerRadius
                ),
                sheetContainerColor = MaterialTheme.colorScheme.surface,
            ) { _ ->
                Box(
                    modifier = Modifier.fillMaxSize()
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
                        var heroBackgroundPainter by remember(backgroundModel) {
                            mutableStateOf<Painter>(fallbackPainter)
                        }
                        if (backgroundModel == null) {
                            Image(
                                painter = fallbackPainter,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ProgressiveImage(
                                thumbUrl = backgroundThumb,
                                targetUrl = backgroundModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholderColor = Color.DarkGray,
                                onPainterChanged = { painter ->
                                    if (painter != null) {
                                        heroBackgroundPainter = painter
                                    }
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

                        val statusLabel = if (!isSelf) {
                            formatPresenceStatusLabel(
                                isOnline = isOnline,
                                lastSeenAtMillis = lastSeenAtMillis,
                            )
                        } else {
                            null
                        }

                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            ProfileScreenTopBar(
                                username = shortUsername,
                                isSelf = isSelf,
                                onShareClick = onTopBarActionClick,
                                onSettingsClick = onSettingsClick,
                                onBackClick = onBackClick,
                                heroBackgroundPainter = heroBackgroundPainter,
                                heroBoundsInWindow = heroBoundsInWindow,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                ProfileSummarySegment(
                                    profileAvatarUri = avatarUri,
                                    avatarThumb = avatarThumb,
                                    displayName = displayName,
                                    description = description,
                                    totalLikesReceived = totalLikesReceived,
                                    statusLabel = statusLabel,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            ProfileButtonsSegment(
                                isSelf = isSelf,
                                onAddPostButtonClick = onAddPostButtonClick,
                                onMessageButtonClick = {
                                    if (!isSelf) onMessageButtonClick()
                                },
                                onEditButtonClick = onProfileEditClick,
                                onShareButtonClick = onShareProfileClick,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            val bottomSpacer = if (heroHeight >= 550.dp) 25.dp else (heroHeight * 0.045f).coerceIn(14.dp, 22.dp)
                            Spacer(Modifier.height(bottomSpacer))
                        }
                    }
                }
            }
        }
    }
}

private fun addProfileHeroPrefetchUrls(rawUrl: String?): List<String> {
    val normalized = rawUrl?.trim().orEmpty()
    if (normalized.isBlank()) return emptyList()
    return listOf(normalized)
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
