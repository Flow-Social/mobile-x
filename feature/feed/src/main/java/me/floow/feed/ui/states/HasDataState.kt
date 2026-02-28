package me.floow.feed.ui.states

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.material3.NavigationBarDefaults
import coil.Coil
import coil.request.ImageRequest
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.feed.ui.components.*
import me.floow.feed.uilogic.FeedScreenState
import me.floow.domain.models.Post
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerV2
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.misc.PostActionsSheetContent
import me.floow.uikit.R
import me.floow.uikit.util.SystemBarsScrim
import me.floow.uikit.util.SetNavigationBarColor

private const val PREFETCH_FEED_POSTS_COUNT = 3
private const val PREFETCH_IMAGE_VARIANTS_PER_POST = 3
private const val PREFETCH_CARD_WIDTH_PX = 420
private const val PREFETCH_CARD_HEIGHT_PX = 560

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HasDataState(
    state: FeedScreenState.Success,
    feedItems: List<me.floow.feed.uilogic.FeedItem>,
    undoAnimationSignal: Long,
    undoAnimationFrom: UndoAnimationFrom,
    onSkipClick: () -> Unit,
    onLikeClick: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onToggleDebug: () -> Unit,
    onClearProfile: () -> Unit,
    onResetRecommendations: () -> Unit,
    onProfileClick: (String) -> Unit = {},
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onPostOpen: (Post, Rect) -> Unit = { _, _ -> },
    onCommentsClick: (Post) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (Post) -> Unit = {},
    onDeletePost: (String) -> Unit = {},
    isMockBuild: Boolean = false,
    isDebugBuild: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenuForPost by remember { mutableStateOf<Post?>(null) }
    var postToDelete by remember { mutableStateOf<Post?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var overlayPost by remember { mutableStateOf<Post?>(null) }
    var overlayStartRect by remember { mutableStateOf<Rect?>(null) }
    var overlayLaunchData by remember { mutableStateOf<OverlayLaunchData?>(null) }
    var isOverlayVisible by remember { mutableStateOf(false) }
    var overlayDetachedCount by remember { mutableIntStateOf(0) }
    val overlayOrigins = remember { mutableStateMapOf<Int, SharedImageOrigin>() }
    val viewerState = rememberFullscreenImageViewerState()
    var viewerPost by remember { mutableStateOf<Post?>(null) }
    val prefetchUrls = remember(feedItems) {
        feedItems
            .take(PREFETCH_FEED_POSTS_COUNT)
            .flatMap { it.post.prefetchUrlsForFeed() }
            .distinct()
    }

    LaunchedEffect(prefetchUrls) {
        if (prefetchUrls.isEmpty()) return@LaunchedEffect
        val imageLoader = Coil.imageLoader(context)
        prefetchUrls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(PREFETCH_CARD_WIDTH_PX, PREFETCH_CARD_HEIGHT_PX)
                .build()
            imageLoader.enqueue(request)
        }
    }

    BackHandler(enabled = overlayPost != null && !viewerState.visible) {
        isOverlayVisible = false
    }
    SystemBarsScrim(
        visible = overlayPost != null && isOverlayVisible && !viewerState.visible,
        scrim = Color.Black.copy(alpha = 0.14f),
        navigationScrim = Color.Black.copy(alpha = 0.12f)
    )
    if (overlayPost == null) {
        SetNavigationBarColor(NavigationBarDefaults.containerColor)
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить профиль?") },
            text = { Text("Это полностью очистит все данные и потребует повторного входа.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onClearProfile()
                }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showMenuForPost != null) {
        val menuPost = showMenuForPost
        ModalBottomSheet(
            onDismissRequest = { showMenuForPost = null },
            sheetState = sheetState
        ) {
            PostActionsSheetContent(
                canEdit = menuPost?.author?.id == "me",
                editText = "Редактировать",
                deleteText = "Удалить",
                shareText = "Поделиться",
                onEdit = {
                    menuPost?.let(onEditPost)
                    showMenuForPost = null
                },
                onDelete = {
                    postToDelete = menuPost
                    showMenuForPost = null
                },
                onShare = {
                    menuPost?.let(onSharePost)
                    showMenuForPost = null
                }
            )
        }
    }

    if (postToDelete != null) {
        AlertDialog(
            onDismissRequest = { postToDelete = null },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(
                    onClick = {
                        postToDelete?.let { onDeletePost(it.id) }
                        postToDelete = null
                    }
                ) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { postToDelete = null }) { Text("Отмена") }
            }
        )
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Swipeable карточка
            SwipeablePostCard(
                items = feedItems,
                undoAnimationSignal = undoAnimationSignal,
                undoAnimationFrom = undoAnimationFrom,
                onSwipeLeft = onSkipClick,
                onSwipeRight = onLikeClick,
                onSwipeUp = onSwipeUp,
                onSwipeDown = onSwipeDown,
                onProfileClick = onProfileClick,
                onProfileTagClick = onProfileTagClick,
                onPostLinkClick = onPostLinkClick,
                onPostOpen = { post, launchData ->
                    if (overlayPost != null) return@SwipeablePostCard
                    overlayPost = post
                    overlayLaunchData = launchData
                    overlayStartRect = launchData.buttonRect
                    overlayDetachedCount = 1
                    isOverlayVisible = true
                    onPostOpen(post, launchData.buttonRect ?: Rect(0f, 0f, 0f, 0f))
                },
                onOverlaySourceSnapshot = { postId, launchData ->
                    if (overlayPost?.id == postId && overlayPost != null) {
                        overlayLaunchData = launchData
                    }
                },
                onCommentsClick = onCommentsClick,
                overlayPostId = overlayPost?.id,
                overlayVisible = overlayPost != null,
                overlayDetachedCount = overlayDetachedCount,
                onPostMenuClick = { showMenuForPost = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Debug информация
            if (state.isDebugMode) {
                state.lastAnalysisResult?.let { analysisResult ->
                    AnalysisDebugCard(
                        analysisResult = analysisResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Кнопки управления
            if (isDebugBuild) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SwipeButtons(
                            onSkipClick = onSkipClick,
                            onLikeClick = onLikeClick,
                            enabled = !state.isLoadingNext,
                            modifier = Modifier.weight(1f)
                        )

                        // Поиск (переключатель Debug)
                        IconButton(
                            onClick = onToggleDebug,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (state.isDebugMode) R.drawable.done_icon else R.drawable.search_icon
                                ),
                                contentDescription = "Debug",
                                tint = if (state.isDebugMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Тестовые кнопки (только в Mock + Debug)
                    if (state.isDebugMode && isMockBuild) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onResetRecommendations,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_refresh),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Сброс ленты", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Удалить всё", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Инфо о профиле
            if (state.isDebugMode) {
                UserProfileInfoBlock(
                    userProfile = state.userProfile,
                    lastSwipeInfo = state.lastSwipeInfo,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (overlayPost != null) {
            Dialog(
                onDismissRequest = { isOverlayVisible = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                DisposableEffect(dialogWindowProvider) {
                    dialogWindowProvider?.window?.setDimAmount(0f)
                    onDispose { }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    ImageOverlayGrid(
                        post = overlayPost!!,
                        startRect = overlayStartRect,
                        launchData = overlayLaunchData,
                        visible = isOverlayVisible,
                        onDismiss = { isOverlayVisible = false },
                        onImageClick = { index, origin ->
                            val currentPost = overlayPost ?: return@ImageOverlayGrid
                            val imageUrls = currentPost.viewerImageUrls()
                            if (imageUrls.isEmpty() || viewerState.visible) return@ImageOverlayGrid
                            val page = index.coerceIn(0, imageUrls.lastIndex)
                            viewerPost = currentPost
                            viewerState.reduce(
                                FullscreenImageViewerAction.Open(
                                    page = page,
                                    origin = origin
                                ),
                                imageUrls.size
                            )
                        },
                        onOriginChanged = { index, origin ->
                            if (origin == null) {
                                overlayOrigins.remove(index)
                            } else {
                                overlayOrigins[index] = origin
                            }
                        },
                        onDetachedCountChange = { detachedCount ->
                            overlayDetachedCount = detachedCount
                        },
                        onClosedAnimationEnd = {
                            overlayPost = null
                            overlayLaunchData = null
                            overlayDetachedCount = 0
                            overlayOrigins.clear()
                            viewerPost = null
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    val activeViewerPost = viewerPost
                    val viewerImages = remember(activeViewerPost) { activeViewerPost?.viewerImageUrls().orEmpty() }
                    FullscreenImageViewerV2(
                        model = FullscreenImageViewerModel(
                            images = viewerImages,
                            title = activeViewerPost?.author?.name?.value ?: "@${activeViewerPost?.author?.username?.value ?: "unknown"}",
                            subtitleProvider = { "Фото" },
                            originForPage = { page -> overlayOrigins[page] }
                        ),
                        state = viewerState,
                        onAction = { action ->
                            when (action) {
                                FullscreenImageViewerAction.CloseAnimationFinished -> {
                                    viewerState.reduce(action, viewerImages.size)
                                    viewerPost = null
                                }

                                else -> viewerState.reduce(action, viewerImages.size)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1000f)
                    )
                }
            }
        }

        AnalysisToast(
            analysisResult = if (!state.isDebugMode) state.lastAnalysisResult else null,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

private fun Post.viewerImageUrls(): List<String> {
    return content.viewerImageUrls()
}

private fun Post.prefetchUrlsForFeed(): List<String> {
    return content
        .resolvedImageVariants()
        .take(PREFETCH_IMAGE_VARIANTS_PER_POST)
        .flatMap { variant ->
            buildList {
                variant.lqUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
                variant.previewUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
}
