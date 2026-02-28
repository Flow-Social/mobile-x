package me.floow.post.ui

import android.content.Context
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerSharedTransitionSpec
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerV2
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.fitRectInBounds
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.transfer.PainterRef
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.text.LinkifiedText
import me.floow.uikit.theme.NinehedronShape
import me.floow.uikit.theme.categoryColor
import me.floow.uikit.theme.toDisplayTag
import me.floow.uikit.util.overlayHorizontalSwipeZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun PostScreen(
    postId: String,
    imageUrls: List<String>,
    mediaTransferSnapshot: PostMediaSourceSnapshot? = null,
    description: String?,
    authorId: String,
    authorName: String?,
    authorUsername: String?,
    authorAvatarUrl: String?,
    category: String,
    createdAt: Long,
    likesCount: Int,
    commentsCount: Int,
    commentersPreview: List<String>,
    isSelf: Boolean,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCommentsClick: (PostMediaSourceSnapshot?) -> Unit,
    onShareClick: () -> Unit,
    onEditPost: (PostMediaSourceSnapshot?) -> Unit,
    onDeletePost: () -> Unit,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val cleanedImageUrls = remember(imageUrls) { imageUrls.filter { it.isNotBlank() } }
    val handoffSnapshot = remember(mediaTransferSnapshot, postId) {
        mediaTransferSnapshot?.takeIf { it.postId == postId }
    }
    val handoffPaintersByIndex = remember(handoffSnapshot) {
        handoffSnapshot
            ?.paintersByIndex
            ?.mapValues { (_, ref) -> ref.painter }
            .orEmpty()
    }
    val cleanedPreviewUrls = remember(cleanedImageUrls, handoffSnapshot) {
        val snapshotPreviewUrls = handoffSnapshot
            ?.previewUrls
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (snapshotPreviewUrls.size == cleanedImageUrls.size) {
            snapshotPreviewUrls
        } else {
            cleanedImageUrls.map { toDetailVariantUrls(it).previewUrl }
        }
    }
    val initialPage = remember(cleanedImageUrls, handoffSnapshot) {
        if (cleanedImageUrls.isEmpty()) {
            0
        } else {
            handoffSnapshot
                ?.selectedIndex
                ?.coerceIn(0, cleanedImageUrls.lastIndex)
                ?: 0
        }
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val viewerState = rememberFullscreenImageViewerState()
    val galleryPagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { cleanedImageUrls.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val sourceBounds = remember { mutableStateMapOf<Int, Rect>() }
    val sourceFitRects = remember { mutableStateMapOf<Int, Rect>() }
    val sourceAspects = remember { mutableStateMapOf<Int, Float>() }
    val sourcePainters = remember { mutableStateMapOf<Int, Painter>() }
    var hiddenSourceKey by remember { mutableStateOf<String?>(null) }
    var pendingClosePage by remember { mutableStateOf<Int?>(null) }
    var galleryViewportRect by remember { mutableStateOf<Rect?>(null) }
    var openingTransitionPainter by remember { mutableStateOf<Painter?>(null) }
    val dismissThresholdPx = with(LocalDensity.current) { 120f * density }
    val dismissRevealProgress = if (viewerState.phase == ViewerPhase.Opened) {
        (abs(viewerState.dismissOffsetY) / dismissThresholdPx).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(viewerState.phase) {
        if (viewerState.phase == ViewerPhase.Closed) {
            hiddenSourceKey = null
            pendingClosePage = null
            openingTransitionPainter = null
        }
    }

    LaunchedEffect(handoffSnapshot, postId) {
        sourceBounds.clear()
        sourceFitRects.clear()
        sourceAspects.clear()
        sourcePainters.clear()
        if (handoffSnapshot == null) return@LaunchedEffect
        handoffSnapshot.boundsByIndex.forEach { (index, bounds) ->
            sourceBounds[index] = bounds
        }
        handoffPaintersByIndex.forEach { (index, painter) ->
            sourcePainters[index] = painter
            val aspect = resolvePainterAspectRatioOrNull(painter)
            if (aspect != null) {
                sourceAspects[index] = aspect
                val bounds = sourceBounds[index]
                if (bounds != null) {
                    sourceFitRects[index] = fitRectInBounds(bounds, aspect)
                }
            }
        }
    }

    val commentsMediaSnapshot by remember(
        postId,
        cleanedImageUrls,
        cleanedPreviewUrls,
        galleryPagerState,
        sourcePainters,
        sourceBounds,
        sourceFitRects
    ) {
        derivedStateOf {
            if (cleanedImageUrls.isEmpty()) return@derivedStateOf null
            val selectedIndex = galleryPagerState.currentPage.coerceIn(0, cleanedImageUrls.lastIndex)
            val painter = sourcePainters[selectedIndex]
            val bounds = sourceFitRects[selectedIndex] ?: sourceBounds[selectedIndex]
            PostMediaSourceSnapshot(
                postId = postId,
                owner = PostMediaSourceOwner.POST,
                selectedIndex = selectedIndex,
                urls = cleanedImageUrls,
                previewUrls = cleanedPreviewUrls,
                paintersByIndex = painter?.let { mapOf(selectedIndex to PainterRef(it)) }.orEmpty(),
                boundsByIndex = bounds?.let { mapOf(selectedIndex to it) }.orEmpty()
            )
        }
    }
    val editMediaSnapshot by remember(
        postId,
        cleanedImageUrls,
        cleanedPreviewUrls,
        galleryPagerState,
        sourcePainters,
        sourceBounds,
        sourceFitRects
    ) {
        derivedStateOf {
            if (cleanedImageUrls.isEmpty()) return@derivedStateOf null
            val selectedIndex = galleryPagerState.currentPage.coerceIn(0, cleanedImageUrls.lastIndex)
            PostMediaSourceSnapshot(
                postId = postId,
                owner = PostMediaSourceOwner.POST,
                selectedIndex = selectedIndex,
                urls = cleanedImageUrls,
                previewUrls = cleanedPreviewUrls,
                paintersByIndex = sourcePainters.mapValues { (_, painter) -> PainterRef(painter) },
                boundsByIndex = sourceFitRects.toMap().ifEmpty { sourceBounds.toMap() }
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePost()
                        showDeleteDialog = false
                    }
                ) {
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

    SharedTransitionLayout(modifier = modifier.fillMaxSize()) {
        val sharedTransitionScope = this
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "@${authorUsername ?: "unknown"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.clickable { onProfileClick(authorId) },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Surface(
                                color = category.categoryColor(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = category.toDisplayTag(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        IconButton(onClick = onShareClick) {
                            Icon(
                                painter = painterResource(me.floow.uikit.R.drawable.share_icon),
                                contentDescription = "Поделиться"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                PostCommentsFooter(
                    commentsCount = commentsCount,
                    commentersPreview = commentersPreview,
                    onCommentsClick = { onCommentsClick(commentsMediaSnapshot) }
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "image") {
                            if (cleanedImageUrls.isNotEmpty()) {
                                PostImageGallery(
                                    imageUrls = cleanedImageUrls,
                                    initialPaintersByIndex = handoffPaintersByIndex,
                                    pagerState = galleryPagerState,
                                    hiddenSourceKey = hiddenSourceKey,
                                    viewerPhase = viewerState.phase,
                                    dismissRevealProgress = dismissRevealProgress,
                                    sourceKeyProvider = { index -> postImageSourceKey(postId, index) },
                                    sharedTransitionScope = sharedTransitionScope,
                                    onGalleryViewportLayout = { viewport ->
                                        galleryViewportRect = viewport
                                    },
                                    onImageLayout = { index, bounds, sourceAspectRatio ->
                                        sourceBounds[index] = bounds
                                        if (sourceAspectRatio != null) {
                                            sourceAspects[index] = sourceAspectRatio
                                            sourceFitRects[index] = fitRectInBounds(bounds, sourceAspectRatio)
                                        }
                                    },
                                    onPainterReady = { index, painter, aspectOrNull ->
                                        sourcePainters[index] = painter
                                        if (aspectOrNull != null) {
                                            sourceAspects[index] = aspectOrNull
                                            val bounds = sourceBounds[index]
                                            if (bounds != null) {
                                                sourceFitRects[index] = fitRectInBounds(bounds, aspectOrNull)
                                            }
                                        }
                                    },
                                    onImageClick = { index, bounds, sourceKey, fallbackAspect, sourcePainter ->
                                        if (!viewerState.visible) {
                                            pendingClosePage = null
                                            hiddenSourceKey = sourceKey
                                            openingTransitionPainter = sourcePainter
                                            val painterAspect = resolvePainterAspectRatioOrNull(sourcePainter)
                                            val aspect = painterAspect
                                                ?: sourceAspects[index]
                                                ?: fallbackAspect
                                            if (painterAspect != null) {
                                                sourceAspects[index] = painterAspect
                                            }
                                            val rect = bounds?.let { fitRectInBounds(it, aspect) }
                                            viewerState.reduce(
                                                FullscreenImageViewerAction.Open(
                                                    page = index,
                                                    origin = rect?.let {
                                                        SharedImageOrigin(
                                                            sourceKey = sourceKey,
                                                            rectInWindow = it,
                                                            aspectRatio = aspect
                                                        )
                                                    }
                                                ),
                                                cleanedImageUrls.size
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        if (isSelf) {
                            item(key = "actions") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 0.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onEditPost(editMediaSnapshot) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Редактировать")
                                    }
                                    TextButton(
                                        onClick = { showDeleteDialog = true },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Удалить", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }

                        item(key = "description") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!description.isNullOrBlank()) {
                                    LinkifiedText(
                                        text = description,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            color = MaterialTheme.colorScheme.onBackground
                                        ),
                                        linkColor = category.categoryColor(),
                                        onProfileTagClick = onProfileTagClick,
                                        onPostLinkClick = onPostLinkClick
                                    )
                                } else {
                                    Text(
                                        text = "Без описания",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = likesCount.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                                Spacer(modifier = Modifier.size(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = formatTime(createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                    }
                }
            }
        }

        FullscreenImageViewerV2(
            model = FullscreenImageViewerModel(
                images = cleanedImageUrls,
                title = authorName ?: "@${authorUsername ?: "unknown"}",
                subtitleProvider = { "Фото • ${formatTime(createdAt)}" },
                openingPainter = if (viewerState.phase == ViewerPhase.Opening) openingTransitionPainter else null,
                originForPage = { page ->
                    val idx = page.coerceIn(0, cleanedImageUrls.lastIndex)
                    val viewportRect = galleryViewportRect
                    val aspect = (
                        sourceAspects[idx]
                            ?: sourceBounds[idx]?.let { boundsAspect(it) }
                            ?: viewportRect?.let { boundsAspect(it) }
                            ?: 1f
                        )
                        .coerceAtLeast(0.01f)
                    val rect = sourceFitRects[idx]
                        ?: sourceBounds[idx]?.let { fitRectInBounds(it, aspect) }
                        ?: viewportRect?.let { fitRectInBounds(it, aspect) }
                    if (rect == null) return@FullscreenImageViewerModel null
                    SharedImageOrigin(
                        sourceKey = postImageSourceKey(postId, idx),
                        rectInWindow = rect,
                        aspectRatio = aspect
                    )
                }
            ),
            state = viewerState,
            onAction = { action ->
                when (action) {
                    is FullscreenImageViewerAction.RequestClose -> {
                        val closingPage = if (cleanedImageUrls.isNotEmpty()) {
                            action.page?.coerceIn(0, cleanedImageUrls.lastIndex)
                        } else {
                            null
                        }
                        if (closingPage != null) {
                            pendingClosePage = closingPage
                            hiddenSourceKey = postImageSourceKey(postId, closingPage)
                            viewerState.reduce(action.copy(page = closingPage), cleanedImageUrls.size)
                        } else {
                            hiddenSourceKey = action.origin?.sourceKey ?: hiddenSourceKey
                            viewerState.reduce(action, cleanedImageUrls.size)
                        }
                    }

                    FullscreenImageViewerAction.CloseAnimationFinished -> {
                        viewerState.reduce(action, cleanedImageUrls.size)
                        val closePage = pendingClosePage
                        if (closePage != null && cleanedImageUrls.isNotEmpty()) {
                            val target = closePage.coerceIn(0, cleanedImageUrls.lastIndex)
                            if (galleryPagerState.currentPage != target) {
                                coroutineScope.launch {
                                    galleryPagerState.scrollToPage(target)
                                }
                            }
                        }
                        pendingClosePage = null
                        hiddenSourceKey = null
                        openingTransitionPainter = null
                    }

                    FullscreenImageViewerAction.MenuClick -> {
                        // TODO: wire post actions menu
                    }

                    else -> viewerState.reduce(action, cleanedImageUrls.size)
                }
            },
            sharedTransitionSpec = FullscreenImageViewerSharedTransitionSpec(
                scope = sharedTransitionScope,
                keyForPage = { page -> postImageSourceKey(postId, page) }
            ),
            modifier = Modifier.fillMaxSize()
        )
    }
    }
}

@Composable
private fun PostCommentsFooter(
    commentsCount: Int,
    commentersPreview: List<String>,
    onCommentsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewAvatars = commentersPreview.toPostCommenterAvatarUiModels(limit = 3)
    val avatarSize = 28.dp
    val overlapStep = 12.dp
    val avatarsGroupWidth =
        if (previewAvatars.isEmpty()) 0.dp else avatarSize + overlapStep * (previewAvatars.size - 1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            thickness = 1.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable { onCommentsClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (previewAvatars.isNotEmpty()) {
                    Box(modifier = Modifier.width(avatarsGroupWidth)) {
                        previewAvatars.forEachIndexed { index, avatar ->
                            val avatarModifier = Modifier
                                .offset(x = overlapStep * index)
                                .border(
                                    width = 1.2.dp,
                                    color = MaterialTheme.colorScheme.background,
                                    shape = NinehedronShape
                                )
                                .zIndex((previewAvatars.size - index).toFloat())

                            when (avatar) {
                                is PostCommenterAvatarUiModel.Remote -> {
                                    NetworkAvatar(
                                        name = avatar.fallbackName,
                                        avatarModel = avatar.url,
                                        size = avatarSize,
                                        shape = NinehedronShape,
                                        modifier = avatarModifier
                                    )
                                }

                                is PostCommenterAvatarUiModel.Initial -> {
                                    InitialAvatar(
                                        name = avatar.name,
                                        size = avatarSize,
                                        shape = NinehedronShape,
                                        modifier = avatarModifier
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "$commentsCount комментариев",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Открыть комментарии",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private sealed interface PostCommenterAvatarUiModel {
    data class Remote(
        val url: String,
        val fallbackName: String = "?"
    ) : PostCommenterAvatarUiModel

    data class Initial(
        val name: String
    ) : PostCommenterAvatarUiModel
}

private fun List<String>.toPostCommenterAvatarUiModels(limit: Int = 3): List<PostCommenterAvatarUiModel> {
    return asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .take(limit)
        .map { raw ->
            if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
                PostCommenterAvatarUiModel.Remote(url = raw)
            } else {
                PostCommenterAvatarUiModel.Initial(name = raw)
            }
        }
        .toList()
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PostImageGallery(
    imageUrls: List<String>,
    initialPaintersByIndex: Map<Int, Painter>,
    pagerState: PagerState,
    hiddenSourceKey: String?,
    viewerPhase: ViewerPhase,
    dismissRevealProgress: Float,
    sourceKeyProvider: (Int) -> String,
    sharedTransitionScope: SharedTransitionScope?,
    onGalleryViewportLayout: (Rect) -> Unit,
    onImageLayout: (Int, Rect, Float?) -> Unit,
    onPainterReady: (Int, Painter, Float?) -> Unit,
    onImageClick: (Int, Rect?, String, Float, Painter) -> Unit,
    modifier: Modifier = Modifier
) {
    var singleBounds by remember { mutableStateOf<Rect?>(null) }
    val initialSinglePainter = remember(initialPaintersByIndex) { initialPaintersByIndex[0] }
    var singleAspectRatio by remember(imageUrls, initialSinglePainter) {
        mutableStateOf(resolvePainterAspectRatioOrNull(initialSinglePainter ?: ColorPainter(Color.Transparent)) ?: 1f)
    }
    var singleReady by remember(imageUrls, initialSinglePainter) { mutableStateOf(initialSinglePainter != null) }

    if (imageUrls.size <= 1) {
        val context = LocalContext.current
        val sourceKey = sourceKeyProvider(0)
        var singlePainter by remember(imageUrls, initialSinglePainter) { mutableStateOf(initialSinglePainter) }
        val hiddenAlpha = sourceAlphaForTransition(
            sourceKey = sourceKey,
            hiddenSourceKey = hiddenSourceKey,
            viewerPhase = viewerPhase,
            dismissRevealProgress = dismissRevealProgress
        )
        val sharedModifier = rememberPostSourceSharedModifier(
            sourceKey = sourceKey,
            hiddenSourceKey = hiddenSourceKey,
            viewerPhase = viewerPhase,
            sharedTransitionScope = sharedTransitionScope
        )

        val detailUrls = remember(imageUrls) { toDetailVariantUrls(imageUrls.first()) }
        var singleRequestSize by remember { mutableStateOf(IntSize.Zero) }
        val lqRequest = remember(detailUrls.lqUrl, singleRequestSize) {
            buildSizedImageRequest(context, detailUrls.lqUrl, singleRequestSize)
        }
        val previewRequest = remember(detailUrls.previewUrl, singleRequestSize) {
            buildSizedImageRequest(context, detailUrls.previewUrl, singleRequestSize)
        }

        LaunchedEffect(initialSinglePainter) {
            val painter = initialSinglePainter ?: return@LaunchedEffect
            val aspect = resolvePainterAspectRatioOrNull(painter)
            singleReady = true
            onPainterReady(0, painter, aspect)
            if (aspect != null) {
                singleAspectRatio = aspect
                val bounds = singleBounds
                if (bounds != null) {
                    onImageLayout(0, bounds, aspect)
                }
            }
        }

        Box(
            modifier = modifier
                .background(Color.Black)
                .aspectRatio(3f / 4f)
                .then(sharedModifier)
                .onGloballyPositioned { coords ->
                    val bounds = coords.boundsInWindow()
                    onGalleryViewportLayout(bounds)
                    singleBounds = bounds
                    singleRequestSize = coords.size
                    if (singleReady) {
                        onImageLayout(0, bounds, singleAspectRatio)
                    }
                }
                .clickable(enabled = singleReady && hiddenSourceKey != sourceKey) {
                    if (singlePainter == null) return@clickable
                    onImageClick(
                        0,
                        singleBounds,
                        sourceKey,
                        singleAspectRatio.takeIf { it > 0f }
                            ?: (singleBounds?.let { boundsAspect(it) } ?: 1f),
                        singlePainter!!
                    )
                }
        ) {
            // Container always has black background - no alpha applied here
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                if (singlePainter != null) {
                    Image(
                        painter = singlePainter!!,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = hiddenAlpha }
                    )
                }
                // Hidden AsyncImages for progressive loading: lq -> preview
                AsyncImage(
                    model = lqRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = 0f,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state ->
                        if (singlePainter == null) {
                            singlePainter = state.painter
                            singleReady = true
                            val aspectOrNull = resolvePainterAspectRatioOrNull(state.painter)
                            onPainterReady(0, state.painter, aspectOrNull)
                            if (aspectOrNull != null) {
                                singleAspectRatio = aspectOrNull
                                val bounds = singleBounds
                                if (bounds != null) {
                                    onImageLayout(0, bounds, aspectOrNull)
                                }
                            }
                        }
                    }
                )
                AsyncImage(
                    model = previewRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = 0f,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state ->
                        singleReady = true
                        singlePainter = state.painter
                        val aspectOrNull = resolvePainterAspectRatioOrNull(state.painter)
                        onPainterReady(0, state.painter, aspectOrNull)
                        if (aspectOrNull != null) {
                            singleAspectRatio = aspectOrNull
                            val bounds = singleBounds
                            if (bounds != null) {
                                onImageLayout(0, bounds, aspectOrNull)
                            }
                        }
                    }
                )
            }
        }
        return
    }

    val rects = remember { mutableStateMapOf<Int, Rect>() }
    val aspectRatios = remember(imageUrls, initialPaintersByIndex) {
        mutableStateMapOf<Int, Float>().apply {
            initialPaintersByIndex.forEach { (index, painter) ->
                resolvePainterAspectRatioOrNull(painter)?.let { aspect ->
                    this[index] = aspect
                }
            }
        }
    }
    val readyStates = remember(imageUrls, initialPaintersByIndex) {
        mutableStateMapOf<Int, Boolean>().apply {
            initialPaintersByIndex.keys.forEach { this[it] = true }
        }
    }
    val pagePainters = remember(imageUrls, initialPaintersByIndex) {
        mutableStateMapOf<Int, Painter>().apply {
            putAll(initialPaintersByIndex)
        }
    }
    val pageSizes = remember { mutableStateMapOf<Int, IntSize>() }
    val pagerZoneKey = remember { "post_gallery_pager_zone" }

    LaunchedEffect(initialPaintersByIndex) {
        initialPaintersByIndex.forEach { (index, painter) ->
            onPainterReady(index, painter, aspectRatios[index])
        }
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .testTag("post_gallery_pager")
                .onGloballyPositioned { coords ->
                    onGalleryViewportLayout(coords.boundsInWindow())
                }
                .overlayHorizontalSwipeZone(
                    zoneKey = pagerZoneKey,
                    atStart = pagerState.currentPage == 0
                )
        ) { page ->
            val context = LocalContext.current
            val sourceKey = sourceKeyProvider(page)
            val pagePainter = pagePainters[page]
            val isReady = readyStates[page] == true && pagePainter != null
            val hiddenAlpha = sourceAlphaForTransition(
                sourceKey = sourceKey,
                hiddenSourceKey = hiddenSourceKey,
                viewerPhase = viewerPhase,
                dismissRevealProgress = dismissRevealProgress
            )
            val sharedModifier = rememberPostSourceSharedModifier(
                sourceKey = sourceKey,
                hiddenSourceKey = hiddenSourceKey,
                viewerPhase = viewerPhase,
                sharedTransitionScope = sharedTransitionScope
            )

            val detailUrls = remember(imageUrls[page]) { toDetailVariantUrls(imageUrls[page]) }
            val pageSize = pageSizes[page]
            val lqRequest = remember(detailUrls.lqUrl, pageSize) {
                buildSizedImageRequest(context, detailUrls.lqUrl, pageSize)
            }
            val previewRequest = remember(detailUrls.previewUrl, pageSize) {
                buildSizedImageRequest(context, detailUrls.previewUrl, pageSize)
            }

            Box(
                modifier = Modifier
                    .background(Color.Black)
                    .fillMaxSize()
                    .then(sharedModifier)
                    .onGloballyPositioned { coords ->
                        val bounds = coords.boundsInWindow()
                        rects[page] = bounds
                        pageSizes[page] = coords.size
                        if (isReady) {
                            onImageLayout(page, bounds, aspectRatios[page])
                        }
                    }
                    .clickable(enabled = isReady && hiddenSourceKey != sourceKey) {
                        if (pagePainter == null) return@clickable
                        onImageClick(
                            page,
                            rects[page],
                            sourceKey,
                            aspectRatios[page] ?: (rects[page]?.let { boundsAspect(it) } ?: 1f),
                            pagePainter
                        )
                    }
            ) {
                // Container always has black background - no alpha applied here
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (pagePainter != null) {
                        Image(
                            painter = pagePainter,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = hiddenAlpha }
                        )
                    }
                    // Hidden AsyncImages for progressive loading: lq -> preview
                    AsyncImage(
                        model = lqRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alpha = 0f,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            if (pagePainters[page] == null) {
                                readyStates[page] = true
                                pagePainters[page] = state.painter
                                val aspectOrNull = resolvePainterAspectRatioOrNull(state.painter)
                                onPainterReady(page, state.painter, aspectOrNull)
                                if (aspectOrNull != null) {
                                    aspectRatios[page] = aspectOrNull
                                    val bounds = rects[page]
                                    if (bounds != null) {
                                        onImageLayout(page, bounds, aspectOrNull)
                                    }
                                }
                            }
                        }
                    )
                    AsyncImage(
                        model = previewRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        alpha = 0f,
                        modifier = Modifier.fillMaxSize(),
                        onSuccess = { state ->
                            readyStates[page] = true
                            pagePainters[page] = state.painter
                            val aspectOrNull = resolvePainterAspectRatioOrNull(state.painter)
                            onPainterReady(page, state.painter, aspectOrNull)
                            if (aspectOrNull != null) {
                                aspectRatios[page] = aspectOrNull
                                val bounds = rects[page]
                                if (bounds != null) {
                                    onImageLayout(page, bounds, aspectOrNull)
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        PagerDots(
            totalDots = imageUrls.size,
            selectedIndex = pagerState.currentPage,
            dotSize = 6.dp,
            selectedDotSize = 8.dp
        )
    }
}

private fun sourceAlphaForTransition(
    sourceKey: String,
    hiddenSourceKey: String?,
    viewerPhase: ViewerPhase,
    dismissRevealProgress: Float
): Float {
    if (hiddenSourceKey != sourceKey) return 1f
    val reveal = sqrt(dismissRevealProgress.coerceIn(0f, 1f))
    return when (viewerPhase) {
        ViewerPhase.Opening -> 0f
        ViewerPhase.Opened -> reveal
        ViewerPhase.Closing -> 0f
        ViewerPhase.Closed -> 1f
    }
}

private data class DetailVariantUrls(
	val lqUrl: String,
	val previewUrl: String
)

private fun buildSizedImageRequest(
	context: Context,
	url: String,
	size: IntSize?
): ImageRequest? {
	if (url.isBlank()) return null
	val width = size?.width ?: 0
	val height = size?.height ?: 0
	if (width <= 0 || height <= 0) return null
	return ImageRequest.Builder(context)
		.data(url)
		.size(width, height)
		.build()
}

private fun toDetailVariantUrls(fullUrl: String): DetailVariantUrls {
	if (fullUrl.endsWith("_full.jpg")) {
		val stem = fullUrl.removeSuffix("_full.jpg")
		return DetailVariantUrls(
			lqUrl = "${stem}_lq.jpg",
			previewUrl = "${stem}_preview.jpg"
		)
	}
	return DetailVariantUrls(
		lqUrl = fullUrl,
		previewUrl = fullUrl
	)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun rememberPostSourceSharedModifier(
    sourceKey: String,
    hiddenSourceKey: String?,
    viewerPhase: ViewerPhase,
    sharedTransitionScope: SharedTransitionScope?
): Modifier {
    if (sharedTransitionScope == null) return Modifier
    if (viewerPhase == ViewerPhase.Opened) return Modifier
    val isSelectedSource = hiddenSourceKey == sourceKey
    val sourceVisible = !isSelectedSource ||
        viewerPhase == ViewerPhase.Opening ||
        viewerPhase == ViewerPhase.Closed
    val boundsTransform = remember {
        BoundsTransform { _: Rect, _: Rect ->
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        }
    }
    return with(sharedTransitionScope) {
        Modifier.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = sourceKey),
            visible = sourceVisible,
            boundsTransform = boundsTransform,
            renderInOverlayDuringTransition = true
        )
    }
}

@Composable
private fun PagerDots(
    totalDots: Int,
    selectedIndex: Int,
    dotSize: Dp,
    selectedDotSize: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        repeat(totalDots) { index ->
            val size = if (index == selectedIndex) selectedDotSize else dotSize
            val color = if (index == selectedIndex) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            Box(
                modifier = Modifier
                    .size(size)
                    .background(color, RoundedCornerShape(50))
            )
            if (index < totalDots - 1) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun VerticalDivider(
    color: Color,
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier
            .width(1.dp)
            .size(height = 18.dp, width = 1.dp)
            .background(color)
    )
}

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun postImageSourceKey(postId: String, index: Int): String {
    return "post:$postId:image:$index"
}

private fun resolvePainterAspectRatioOrNull(painter: Painter): Float? {
    val size: Size = painter.intrinsicSize
    val width = size.width
    val height = size.height
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        (width / height).coerceAtLeast(0.01f)
    } else null
}

private fun boundsAspect(bounds: Rect): Float {
    val w = bounds.width
    val h = bounds.height
    return if (w > 0f && h > 0f) w / h else 1f
}
