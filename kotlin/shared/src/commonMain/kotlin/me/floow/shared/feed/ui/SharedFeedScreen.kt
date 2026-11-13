package me.floow.shared.feed.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.floow.domain.models.Post
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.shared.feed.uilogic.SharedFeedScreenState
import me.floow.shared.image.PrefetchImageUrlsEffect
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.FullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.calculateViewerBackgroundScale
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.viewerSourceRevealProgress
import me.floow.uikit.components.misc.PostActionsSheetContent

@Composable
internal expect fun rememberNoMorePostsPainter(): androidx.compose.ui.graphics.painter.Painter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedFeedScreen(
    state: SharedFeedScreenState,
    undoAnimationSignal: Long,
    undoAnimationFrom: SharedUndoAnimationFrom,
    onSkipClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDeletePost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onSharePost: (Post) -> Unit,
    onEditPost: (ProfilePostItem) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onRefreshClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (state) {
        SharedFeedScreenState.Loading -> SharedFeedLoadingState(contentPadding = contentPadding, modifier = modifier)
        SharedFeedScreenState.Error -> Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) { Text("ошибка загрузки ленты") }
        SharedFeedScreenState.NoMorePosts -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = rememberNoMorePostsPainter(),
                    contentDescription = null,
                    modifier = Modifier.size(160.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("нет новых постов")
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onRefreshClick) {
                    Text("Обновить ленту")
                }
            }
        }
        is SharedFeedScreenState.Success -> SharedFeedSuccessState(
            state = state,
            undoAnimationSignal = undoAnimationSignal,
            undoAnimationFrom = undoAnimationFrom,
            onSkipClick = onSkipClick,
            onLikeClick = onLikeClick,
            onDeletePost = onDeletePost,
            onProfileClick = onProfileClick,
            onProfileTagClick = onProfileTagClick,
            onPostLinkClick = onPostLinkClick,
            onCommentsClick = onCommentsClick,
            onSharePost = onSharePost,
            onEditPost = onEditPost,
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun SharedFeedLoadingState(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.TopCenter,
        ) {
            SharedFeedPostCardSkeletonStack(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = SharedFeedCardMaxWidth),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedFeedSuccessState(
    state: SharedFeedScreenState.Success,
    undoAnimationSignal: Long,
    undoAnimationFrom: SharedUndoAnimationFrom,
    onSkipClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDeletePost: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onSharePost: (Post) -> Unit,
    onEditPost: (ProfilePostItem) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val feedPrefetchUrls = remember(state.feedItems) {
        state.feedItems
            .take(4)
            .flatMap { it.post.prefetchUrlsForFeed() }
            .distinct()
    }
    PrefetchImageUrlsEffect(
        urls = feedPrefetchUrls,
        size = FeedPrefetchImageSize,
    )

    var showPostMenuFor by remember { mutableStateOf<Post?>(null) }
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    var viewerPost by remember { mutableStateOf<Post?>(null) }
    var viewerOpeningPainter by remember { mutableStateOf<Painter?>(null) }
    var overlayState by remember { mutableStateOf<SharedFeedOverlayState?>(null) }
    val overlayOrigins = remember { mutableStateListOf<SharedImageOrigin?>() }
    val viewerState = rememberFullscreenImageViewerState()
    val viewerModelProvider = {
        viewerPost?.let { post ->
            FullscreenImageViewerModel(
                images = post.content.viewerImageUrls(),
                title = post.author.name?.value ?: "@${post.author.username?.value ?: "unknown"}",
                subtitleProvider = { "Фото" },
                openingPainter = if (viewerState.phase == me.floow.uikit.components.media.viewer2.ViewerPhase.Opening) {
                    viewerOpeningPainter
                } else {
                    null
                },
                originForPage = { page -> overlayOrigins.getOrNull(page) },
                aspectRatioForPage = { page ->
                    post.content.resolvedImageVariants().getOrNull(page)?.let { variant ->
                        val width = variant.width?.takeIf { it > 0 }
                        val height = variant.height?.takeIf { it > 0 }
                        if (width != null && height != null) width.toFloat() / height.toFloat() else null
                    }
                },
            )
        }
    }
    val viewerModel = viewerModelProvider()
    val hostedViewer = remember(viewerState) {
        HostedFullscreenImageViewer(
            modelProvider = viewerModelProvider,
            state = viewerState,
            onAction = { action ->
                val imageCount = viewerModelProvider()?.images?.size ?: 0
                when (action) {
                    is FullscreenImageViewerAction.RequestClose -> {
                        val safePage = action.page?.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
                        val origin = action.origin ?: safePage?.let { overlayOrigins.getOrNull(it) }
                        overlayState = overlayState?.copy(
                            phase = SharedFeedOverlayPhase.Transfer,
                            hiddenSourceKey = origin?.sourceKey ?: overlayState?.hiddenSourceKey,
                        )
                        viewerState.reduce(
                            if (safePage != null) action.copy(page = safePage, origin = origin) else action,
                            imageCount,
                        )
                    }

                    FullscreenImageViewerAction.CloseAnimationFinished -> {
                        viewerState.reduce(action, imageCount)
                        viewerPost = null
                        viewerOpeningPainter = null
                        overlayState = overlayState?.copy(
                            phase = SharedFeedOverlayPhase.Opened,
                            hiddenSourceKey = null,
                        )
                    }

                    is FullscreenImageViewerAction.PageChanged -> {
                        val safePage = action.page.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
                        overlayState = overlayState?.copy(
                            hiddenSourceKey = overlayOrigins.getOrNull(safePage)?.sourceKey,
                        )
                        viewerState.reduce(action.copy(page = safePage), imageCount)
                    }

                    else -> viewerState.reduce(action, imageCount)
                }
            },
        )
    }
    val sheetState = rememberModalBottomSheetState()
    val isViewerActive = viewerState.visible
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    showPostMenuFor?.let { post ->
        ModalBottomSheet(
            onDismissRequest = { showPostMenuFor = null },
            sheetState = sheetState,
        ) {
            PostActionsSheetContent(
                canEdit = post.author.id == "me",
                editText = "Редактировать",
                deleteText = "Удалить",
                shareText = "Поделиться",
                onEdit = {
                    onEditPost(post.toProfilePostItem())
                    showPostMenuFor = null
                },
                onDelete = {
                    postToDelete = post
                    showPostMenuFor = null
                },
                onShare = {
                    onSharePost(post)
                    showPostMenuFor = null
                },
            )
        }
    }

    postToDelete?.let { post ->
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePost(post.id)
                    postToDelete = null
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { postToDelete = null }) { Text("Отмена") } },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                FeedSwipeDeck(
                    items = state.feedItems,
                    undoAnimationSignal = undoAnimationSignal,
                    undoAnimationFrom = undoAnimationFrom,
                    hapticFeedback = hapticFeedback,
                    onSwipeLeft = onSkipClick,
                    onSwipeRight = onLikeClick,
                    onProfileClick = onProfileClick,
                    onProfileTagClick = onProfileTagClick,
                    onPostLinkClick = onPostLinkClick,
                    onCommentsClick = onCommentsClick,
                    onPostMenuClick = { showPostMenuFor = it },
                    onOpenImageOverlay = { post, launchData ->
                        if (isViewerActive) return@FeedSwipeDeck
                        overlayState = SharedFeedOverlayState(
                            post = post,
                            launchData = launchData,
                            visible = true,
                            phase = SharedFeedOverlayPhase.Opening,
                        )
                        overlayOrigins.clear()
                        repeat(post.content.viewerImageUrls().size) { overlayOrigins += null }
                    },
                    onOverlaySourceSnapshot = { post, launchData ->
                        if (overlayState?.post?.id == post.id) {
                            overlayState = overlayState?.copy(launchData = launchData)
                        }
                    },
                    isViewerActive = isViewerActive,
                    activeOverlayPostId = overlayState?.post?.id,
                    overlayDetachedCount = overlayState?.detachedCount ?: 0,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = SharedFeedCardMaxWidth)
                        .graphicsLayer {
                            if (viewerState.visible) {
                                val dismissProgress = (kotlin.math.abs(viewerState.dismissOffsetY) / (140f * density.density * 1.5f)).coerceIn(0f, 1f)
                                val bgScale = calculateViewerBackgroundScale(
                                    phase = viewerState.phase,
                                    transitionProgress = viewerState.transitionProgress,
                                    dismissProgress = dismissProgress,
                                    closeSceneStartProgress = viewerState.closeSceneStartProgress,
                                    zoomScale = -0.05f,
                                )
                                scaleX = bgScale
                                scaleY = bgScale
                            }
                        },
                )
            }
        }

        SharedFeedAnalysisToast(
            analysisResult = state.lastAnalysisResult,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        overlayState?.let { currentOverlay ->
            FeedOverlayDialogHost(
                onDismissRequest = {
                    if (viewerState.visible) {
                        val activePage = viewerState.page
                        val origin = overlayOrigins.getOrNull(activePage)
                        hostedViewer.onAction(
                            FullscreenImageViewerAction.RequestClose(
                                page = activePage,
                                origin = origin,
                            ),
                        )
                    } else if (currentOverlay.visible) {
                        overlayState = currentOverlay.copy(
                            visible = false,
                            phase = SharedFeedOverlayPhase.Closing,
                            hiddenSourceKey = null,
                        )
                    }
                },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val post = currentOverlay.post
                    SharedFeedImageOverlayGrid(
                        post = post,
                        startRect = currentOverlay.launchData.buttonRect,
                        launchData = currentOverlay.launchData,
                        visible = currentOverlay.visible,
                        isViewerActive = viewerState.visible,
                        onDismiss = {
                            if (!viewerState.visible && currentOverlay.visible) {
                                overlayState = currentOverlay.copy(
                                    visible = false,
                                    phase = SharedFeedOverlayPhase.Closing,
                                    hiddenSourceKey = null,
                                )
                            }
                        },
                        onImageClick = { index, origin, painter ->
                            val imageCount = post.content.viewerImageUrls().size
                            if (imageCount == 0) return@SharedFeedImageOverlayGrid
                            if (overlayOrigins.size < imageCount) {
                                repeat(imageCount - overlayOrigins.size) { overlayOrigins += null }
                            }
                            if (index in 0 until overlayOrigins.size) {
                                overlayOrigins[index] = origin
                            }
                            overlayState = overlayState?.copy(
                                phase = SharedFeedOverlayPhase.Transfer,
                                hiddenSourceKey = origin?.sourceKey,
                            )
                            viewerOpeningPainter = painter
                            viewerPost = post
                            hostedViewer.onAction(
                                FullscreenImageViewerAction.Open(
                                    page = index.coerceIn(0, imageCount - 1),
                                    origin = origin,
                                ),
                            )
                        },
                        onOriginChanged = { index, origin ->
                            if (index in 0 until overlayOrigins.size) {
                                overlayOrigins[index] = origin
                            }
                        },
                        onPhaseChanged = { phase ->
                            if (phase == SharedFeedOverlayPhase.Closing) {
                                overlayState = null
                                overlayOrigins.clear()
                            } else {
                                overlayState = overlayState?.copy(phase = phase)
                            }
                        },
                        onDetachedCountChange = { count ->
                            overlayState = overlayState?.copy(detachedCount = count)
                        },
                        hiddenSourceKey = if (viewerState.visible) {
                            overlayOrigins.getOrNull(viewerState.page)?.sourceKey ?: overlayState?.hiddenSourceKey
                        } else {
                            null
                        },
                        hiddenSourceRevealProgress = viewerSourceRevealProgress(
                            phase = viewerState.phase,
                            transitionProgress = viewerState.transitionProgress,
                        ),
                        modifier = Modifier.fillMaxSize().zIndex(1f),
                    )

                    if (viewerState.visible && viewerModel != null) {
                        FullscreenImageViewer(
                            model = viewerModel,
                            state = viewerState,
                            onAction = hostedViewer.onAction,
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(100f),
                        )
                    }
                }
            }
        }

        if (overlayState == null && viewerState.visible && viewerModel != null) {
            FullscreenImageViewer(
                model = viewerModel,
                state = viewerState,
                onAction = hostedViewer.onAction,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f),
            )
        }
    }
}
