package me.floow.shared.post.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreHoriz
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.components.media.ProgressiveImage
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.current_reply_close_icon
import flow.shared.generated.resources.share_icon
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.FullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SourceImageScaleMode
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.viewerSourceRevealProgress
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PainterRef
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.misc.textwrap.findFlowMarkdownMatches
import me.floow.uikit.components.topbar.ModalSheetTopBar
import me.floow.uikit.theme.NinehedronShape
import me.floow.uikit.util.overlayHorizontalSwipeZone
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostScreen(
    model: PostScreenModel,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCommentsClick: (PostMediaSourceSnapshot?) -> Unit,
    onShareClick: () -> Unit,
    onEditPost: (PostMediaSourceSnapshot?) -> Unit,
    onDeletePost: () -> Unit,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    onProfileModalScrollAtTopChanged: (Boolean) -> Unit = {},
    presentation: PostScreenPresentation = PostScreenPresentation.FullscreenLegacy,
    topBarModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val cleanedImageUrls = remember(model.imageUrls) { model.imageUrls.map(String::trim).filter(String::isNotEmpty) }
    val handoffSnapshot = remember(model.mediaTransferSnapshot, model.postId) {
        model.mediaTransferSnapshot?.takeIf { it.postId == model.postId }
    }
    val previewUrls = remember(cleanedImageUrls, handoffSnapshot) {
        val snapshotPreviewUrls = handoffSnapshot
            ?.previewUrls
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
        if (snapshotPreviewUrls.size == cleanedImageUrls.size) snapshotPreviewUrls
        else cleanedImageUrls.map { toDetailVariantUrls(it).previewUrl }
    }
    val initialPage = remember(cleanedImageUrls, handoffSnapshot) {
        if (cleanedImageUrls.isEmpty()) 0
        else handoffSnapshot?.selectedIndex?.coerceIn(0, cleanedImageUrls.lastIndex) ?: 0
    }
    val galleryPagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { cleanedImageUrls.size }
    )
    var profileModalSelectedIndex by remember(model.postId, initialPage) {
        mutableStateOf(initialPage)
    }
    val profileModalListState = rememberLazyListState()
    val viewerState = rememberFullscreenImageViewerState()
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val pagerZoneKey = remember { "shared_post_gallery_pager_zone" }
    val liveOrigins = remember(model.postId, cleanedImageUrls) { mutableStateMapOf<Int, SharedImageOrigin>() }
    val sourcePainters = remember(model.postId, cleanedImageUrls) { mutableStateMapOf<Int, Painter>() }
    val aspectRatioForPage: (Int) -> Float? = { page ->
        model.imageVariants.getOrNull(page)?.aspectRatioOrNull()
            ?: sourcePainters[page]?.let(::resolvePainterAspectRatioOrNull)
            ?: handoffSnapshot?.paintersByIndex?.get(page)?.painter?.let(::resolvePainterAspectRatioOrNull)
            ?: liveOrigins[page]?.aspectRatio
    }
    var hiddenSourceKey by remember(model.postId) { mutableStateOf<String?>(null) }
    var pendingClosePage by remember(model.postId) { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProfileActionsMenu by remember { mutableStateOf(false) }
    var viewerOpeningPainter by remember(model.postId) { mutableStateOf<Painter?>(null) }
    val viewerModelProvider = {
        FullscreenImageViewerModel(
            images = cleanedImageUrls,
            title = model.authorName ?: "@${model.authorUsername ?: "unknown"}",
            subtitleProvider = { listOfNotNull("Фото", model.createdAtLabel).joinToString(" • ") },
            openingPainter = if (viewerState.phase == me.floow.uikit.components.media.viewer2.ViewerPhase.Opening) {
                viewerOpeningPainter
                    ?: sourcePainters[viewerState.page]
                    ?: handoffSnapshot?.paintersByIndex?.get(viewerState.page)?.painter
            } else {
                null
            },
            originForPage = { page ->
                liveOrigins[page] ?: liveOrigins.values.firstOrNull()?.let { sample ->
                    sample.rectInWindow.toPostGalleryOrigin(
                        sourceKey = viewerSourceKeyForPage(presentation, model.postId, page),
                        imageAspectRatio = aspectRatioForPage(page),
                        cornerRadiusPx = sample.cornerRadiusPx,
                        sourceScaleMode = sample.sourceScaleMode,
                    )
                }
            },
            aspectRatioForPage = aspectRatioForPage,
        )
    }
    val hostedViewer = remember(viewerState, onPresentFullscreenViewer) {
        HostedFullscreenImageViewer(
            modelProvider = viewerModelProvider,
            state = viewerState,
            onAction = { action ->
                when (action) {
                    is FullscreenImageViewerAction.RequestClose -> {
                        val viewerModel = viewerModelProvider()
                        val closePage = action.page?.coerceIn(0, (viewerModel.images.size - 1).coerceAtLeast(0))
                        pendingClosePage = closePage
                        closePage?.let { page ->
                            if (presentation == PostScreenPresentation.ProfileModal) {
                                profileModalSelectedIndex = page
                            } else if (galleryPagerState.currentPage != page) {
                                coroutineScope.launch { galleryPagerState.scrollToPage(page) }
                            }
                        }
                        val resolvedOrigin = action.origin
                            ?: closePage?.let { page ->
                                liveOrigins[page] ?: liveOrigins.values.firstOrNull()?.let { sample ->
                                    sample.rectInWindow.toPostGalleryOrigin(
                                        sourceKey = viewerSourceKeyForPage(presentation, model.postId, page),
                                        imageAspectRatio = aspectRatioForPage(page),
                                        cornerRadiusPx = sample.cornerRadiusPx,
                                        sourceScaleMode = sample.sourceScaleMode,
                                    )
                                }
                            }
                        hiddenSourceKey = resolvedOrigin?.sourceKey
                            ?: closePage?.let { viewerSourceKeyForPage(presentation, model.postId, it) }
                            ?: hiddenSourceKey
                        viewerState.reduce(
                            action.copy(page = closePage ?: action.page ?: 0, origin = resolvedOrigin),
                            viewerModel.images.size,
                        )
                    }
                    FullscreenImageViewerAction.CloseAnimationFinished -> {
                        val viewerModel = viewerModelProvider()
                        viewerState.reduce(action, viewerModel.images.size)
                        pendingClosePage = null
                        hiddenSourceKey = null
                        viewerOpeningPainter = null
                        onPresentFullscreenViewer?.invoke(null)
                    }
                    is FullscreenImageViewerAction.Open -> {
                        val viewerModel = viewerModelProvider()
                        hiddenSourceKey = action.origin?.sourceKey
                            ?: viewerSourceKeyForPage(presentation, model.postId, action.page)
                        pendingClosePage = null
                        viewerState.reduce(action, viewerModel.images.size)
                    }
                    is FullscreenImageViewerAction.PageChanged -> {
                        val page = action.page.coerceIn(0, (viewerModelProvider().images.size - 1).coerceAtLeast(0))
                        if (presentation == PostScreenPresentation.ProfileModal) {
                            profileModalSelectedIndex = page
                        } else if (galleryPagerState.currentPage != page) {
                            coroutineScope.launch { galleryPagerState.scrollToPage(page) }
                        }
                        hiddenSourceKey = liveOrigins[page]?.sourceKey
                            ?: viewerSourceKeyForPage(presentation, model.postId, page)
                        viewerState.reduce(action, viewerModelProvider().images.size)
                    }
                    else -> viewerState.reduce(action, viewerModelProvider().images.size)
                }
            }
        )
    }

    val currentMediaSnapshot by remember(
        model.postId,
        cleanedImageUrls,
        previewUrls,
        galleryPagerState,
        presentation,
        profileModalSelectedIndex
    ) {
        derivedStateOf {
            if (cleanedImageUrls.isEmpty()) return@derivedStateOf null
            val selectedIndex = when (presentation) {
                PostScreenPresentation.ProfileModal -> profileModalSelectedIndex.coerceIn(0, cleanedImageUrls.lastIndex)
                PostScreenPresentation.FullscreenLegacy -> galleryPagerState.currentPage.coerceIn(0, cleanedImageUrls.lastIndex)
            }
            PostMediaSourceSnapshot(
                postId = model.postId,
                owner = PostMediaSourceOwner.POST,
                selectedIndex = selectedIndex,
                urls = cleanedImageUrls,
                previewUrls = previewUrls,
                paintersByIndex = sourcePainters.mapValues { (_, painter) -> PainterRef(painter) },
                boundsByIndex = liveOrigins.mapValues { (_, origin) -> origin.rectInWindow },
            )
        }
    }

    DisposableEffect(onPresentFullscreenViewer) {
        onDispose { onPresentFullscreenViewer?.invoke(null) }
    }

    LaunchedEffect(presentation, profileModalListState) {
        if (presentation == PostScreenPresentation.ProfileModal) {
            snapshotFlow {
                profileModalListState.firstVisibleItemIndex == 0 && profileModalListState.firstVisibleItemScrollOffset == 0
            }.collect { isAtTop ->
                onProfileModalScrollAtTopChanged(isAtTop)
            }
        } else {
            onProfileModalScrollAtTopChanged(true)
        }
    }
    LaunchedEffect(viewerState.phase) {
        if (viewerState.phase == ViewerPhase.Closed) {
            hiddenSourceKey = null
            pendingClosePage = null
            viewerOpeningPainter = null
        }
    }
    val hiddenSourceRevealProgress = viewerSourceRevealProgress(
        phase = viewerState.phase,
        transitionProgress = viewerState.transitionProgress,
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить пост?") },
            text = { Text("Это действие нельзя отменить") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePost()
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

    Box(modifier = modifier.fillMaxSize()) {
        if (presentation == PostScreenPresentation.ProfileModal) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp)
            ) {
                ModalSheetTopBar(
                    onCloseClick = onBackClick,
                    modifier = topBarModifier,
                    titleText = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)) {
                            append("@${model.authorUsername ?: "unknown"}")
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)) {
                            append(" / ${model.topBarCreatedAtLabel ?: model.createdAtLabel ?: ""}")
                        }
                    },
                    action = {
                        Box {
                            OutlinedButton(
                                onClick = { showProfileActionsMenu = !showProfileActionsMenu },
                                modifier = Modifier.size(width = 54.dp, height = 32.dp),
                                shape = RoundedCornerShape(100.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreHoriz,
                                    contentDescription = "Действия",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showProfileActionsMenu,
                                onDismissRequest = { showProfileActionsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Поделиться") },
                                    onClick = {
                                        showProfileActionsMenu = false
                                        onShareClick()
                                    }
                                )
                                if (model.isSelf) {
                                    DropdownMenuItem(
                                        text = { Text("Редактировать") },
                                        onClick = {
                                            showProfileActionsMenu = false
                                            onEditPost(currentMediaSnapshot)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить") },
                                        onClick = {
                                            showProfileActionsMenu = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                LazyColumn(
                    state = profileModalListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(0.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    item("profile_modal_gallery") {
                        if (cleanedImageUrls.isNotEmpty()) {
                            ProfileModalHeroCarousel(
                                imageUrls = cleanedImageUrls,
                                imageVariants = model.imageVariants,
                                initialIndex = initialPage,
                                selectedIndex = profileModalSelectedIndex,
                                onSelectedIndexChange = { profileModalSelectedIndex = it },
                                postId = model.postId,
                                initialPainters = handoffSnapshot?.paintersByIndex?.mapValues { it.value.painter },
                                onOriginChanged = { index, origin ->
                                    if (origin == null) liveOrigins.remove(index) else liveOrigins[index] = origin
                                },
                                onPainterReady = { index, painter ->
                                    sourcePainters[index] = painter
                                },
                                onImageClick = { index, painter ->
                                    if (viewerState.visible) return@ProfileModalHeroCarousel
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewerOpeningPainter = painter
                                        ?: sourcePainters[index]
                                        ?: handoffSnapshot?.paintersByIndex?.get(index)?.painter
                                    hiddenSourceKey = liveOrigins[index]?.sourceKey
                                    onPresentFullscreenViewer?.invoke(hostedViewer)
                                    hostedViewer.onAction(
                                        FullscreenImageViewerAction.Open(
                                            page = index,
                                            origin = liveOrigins[index],
                                        )
                                    )
                                },
                                hiddenSourceKey = hiddenSourceKey,
                                hiddenSourceRevealProgress = hiddenSourceRevealProgress,
                            )
                        }
                    }
                    item("profile_modal_content") {
                        ProfileModalFeedTextContent(
                            description = model.description,
                            timeLabel = model.descriptionTimeLabel ?: model.createdAtLabel.orEmpty(),
                            linkColor = categoryColor(model.category.orEmpty()),
                            onProfileTagClick = onProfileTagClick,
                            onPostLinkClick = onPostLinkClick,
                            onCommentsClick = { onCommentsClick(currentMediaSnapshot) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    top = 16.dp,
                                    end = 4.dp,
                                    bottom = 6.dp,
                                )
                        )
                    }
                }
                ProfileModalFeedPostFooter(
                    commentsCount = model.commentsCount,
                    commentersPreview = model.commentersPreview,
                    onCommentsClick = { onCommentsClick(currentMediaSnapshot) }
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "@${model.authorUsername ?: "unknown"}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.clickable(enabled = model.authorId.isNotBlank()) {
                                        onProfileClick(model.authorId)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                if (!model.category.isNullOrBlank()) {
                                    Surface(
                                        color = categoryColor(model.category),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = toDisplayTag(model.category),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = onShareClick) {
                                Icon(
                                    painter = painterResource(Res.drawable.share_icon),
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
                        commentsCount = model.commentsCount,
                        commentersPreview = model.commentersPreview,
                        onCommentsClick = { onCommentsClick(currentMediaSnapshot) }
                    )
                },
                contentWindowInsets = WindowInsets(0)
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(innerPadding)
                ) {
                    item(key = "gallery") {
                        if (cleanedImageUrls.isNotEmpty()) {
                            PostImageGallery(
                                imageUrls = cleanedImageUrls,
                                imageVariants = model.imageVariants,
                                initialPainters = handoffSnapshot?.paintersByIndex?.mapValues { it.value.painter },
                                pagerState = galleryPagerState,
                                hapticFeedback = hapticFeedback,
                                pagerZoneKey = pagerZoneKey,
                                postId = model.postId,
                                onOriginChanged = { index, origin ->
                                    if (origin == null) liveOrigins.remove(index) else liveOrigins[index] = origin
                                },
                                onPainterReady = { index, painter ->
                                    sourcePainters[index] = painter
                                },
                                aspectRatioForPage = aspectRatioForPage,
                                onImageClick = { index, painter ->
                                    if (viewerState.visible) return@PostImageGallery
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewerOpeningPainter = painter
                                        ?: sourcePainters[index]
                                        ?: handoffSnapshot?.paintersByIndex?.get(index)?.painter
                                    hiddenSourceKey = liveOrigins[index]?.sourceKey
                                    onPresentFullscreenViewer?.invoke(hostedViewer)
                                    hostedViewer.onAction(
                                        FullscreenImageViewerAction.Open(
                                            page = index,
                                            origin = liveOrigins[index],
                                        )
                                    )
                                },
                                hiddenSourceKey = hiddenSourceKey,
                                hiddenSourceRevealProgress = hiddenSourceRevealProgress,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (model.isSelf) {
                        item(key = "actions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onEditPost(currentMediaSnapshot) },
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
                            if (!model.description.isNullOrBlank()) {
                                SharedLinkifiedText(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.onBackground
                                    ),
                                    linkColor = categoryColor(model.category.orEmpty()),
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
                                    text = model.likesCount.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            if (!model.createdAtLabel.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = model.createdAtLabel,
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

        if (onPresentFullscreenViewer == null) {
            FullscreenImageViewer(
                model = viewerModelProvider(),
                state = viewerState,
                onAction = hostedViewer.onAction,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

data class PostScreenModel(
    val postId: String,
    val imageUrls: List<String>,
    val imageVariants: List<PostImageVariant> = emptyList(),
    val mediaTransferSnapshot: PostMediaSourceSnapshot? = null,
    val description: String?,
    val authorId: String = "",
    val authorName: String?,
    val authorUsername: String?,
    val authorAvatarUrl: String?,
    val authorAvatarThumb: String? = null,
    val category: String? = null,
    val createdAtLabel: String? = null,
    val topBarCreatedAtLabel: String? = null,
    val descriptionTimeLabel: String? = null,
    val likesCount: Int,
    val commentsCount: Int,
    val commentersPreview: List<String>,
    val isSelf: Boolean,
)

@Composable
private fun ProfileModalFeedTextContent(
    description: String?,
    timeLabel: String,
    linkColor: Color,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!description.isNullOrBlank()) {
            val (title, body) = extractTitleAndBody(description)
            if (title.isNotBlank()) {
                SharedLinkifiedText(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    linkColor = linkColor,
                    onProfileTagClick = onProfileTagClick,
                    onPostLinkClick = onPostLinkClick,
                )
            }
            if (body.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SharedLinkifiedText(
                        text = body,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        linkColor = linkColor,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        onProfileTagClick = onProfileTagClick,
                        onPostLinkClick = onPostLinkClick,
                        onTextClick = onCommentsClick,
                    )
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            } else if (timeLabel.isNotBlank()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@Composable
private fun ProfileModalFeedPostFooter(
    commentsCount: Int,
    commentersPreview: List<String>,
    onCommentsClick: () -> Unit,
) {
    val previewAvatars = commentersPreview.toPostCommenterAvatarUiModels(limit = 3)
    val avatarSize = 28.dp
    val overlapStep = 12.dp
    val avatarsGroupWidth =
        if (previewAvatars.isEmpty()) 0.dp else avatarSize + overlapStep * (previewAvatars.size - 1)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .padding(bottom = 6.dp),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable { onCommentsClick() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (previewAvatars.isNotEmpty()) {
                    Box(Modifier.width(avatarsGroupWidth)) {
                        previewAvatars.forEachIndexed { index, avatar ->
                            val avatarModifier = Modifier
                                .offset(x = overlapStep * index)
                                .border(1.2.dp, MaterialTheme.colorScheme.surfaceContainer, NinehedronShape)
                                .size(avatarSize)
                            when (avatar) {
                                is PostCommenterAvatarUiModel.Remote -> {
                                    NetworkAvatar(
                                        name = avatar.fallbackName,
                                        avatarModel = avatar.url,
                                        modifier = avatarModifier,
                                        size = avatarSize,
                                        shape = NinehedronShape,
                                    )
                                }
                                is PostCommenterAvatarUiModel.Initial -> {
                                    InitialAvatar(
                                        name = avatar.name,
                                        modifier = avatarModifier,
                                        size = avatarSize,
                                        shape = NinehedronShape,
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = "$commentsCount комментариев",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Открыть комментарии",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .size(avatarSize)

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

@Composable
private fun PostImageGallery(
    imageUrls: List<String>,
    imageVariants: List<PostImageVariant> = emptyList(),
    initialPainters: Map<Int, Painter>? = null,
    pagerState: PagerState,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    pagerZoneKey: String,
    postId: String,
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit,
    onPainterReady: (Int, Painter) -> Unit,
    aspectRatioForPage: (Int) -> Float?,
    onImageClick: (Int, Painter?) -> Unit,
    hiddenSourceKey: String?,
    hiddenSourceRevealProgress: Float,
    modifier: Modifier = Modifier
) {
    if (imageUrls.size <= 1) {
        val sourceKey = remember(postId) { "post_gallery_${postId}_0" }
        var bounds by remember(sourceKey) { mutableStateOf<Rect?>(null) }
        val initialPainter = initialPainters?.get(0)
        var painter by remember(sourceKey, initialPainter) { mutableStateOf<Painter?>(initialPainter) }
        var aspectRatio by remember(sourceKey) { mutableStateOf<Float?>(null) }
        LaunchedEffect(bounds, aspectRatio, sourceKey) {
            val rect = bounds ?: return@LaunchedEffect
            onOriginChanged(
                0,
                rect.toPostGalleryOrigin(
                    sourceKey = sourceKey,
                    imageAspectRatio = aspectRatio ?: aspectRatioForPage(0),
                ),
            )
        }
        Box(
            modifier = modifier
                .background(Color.Black)
                .aspectRatio(3f / 4f)
                .onGloballyPositioned { coordinates ->
                    bounds = coordinates.boundsInWindow()
                }
                .clickable(enabled = imageUrls.isNotEmpty() && hiddenSourceKey != sourceKey) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onImageClick(0, painter)
                }
        ) {
            val singleUrl = imageUrls.firstOrNull()
            val singleVariant = imageVariants.firstOrNull()
            val singleThumb = singleVariant?.thumbUrl
            if (initialPainter != null && painter == null) {
                androidx.compose.foundation.Image(
                    painter = initialPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!singleUrl.isNullOrBlank()) {
                ProgressiveImage(
                    thumbUrl = singleThumb,
                    targetUrl = singleUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    placeholderColor = Color.Transparent,
                    onPainterChanged = { p ->
                        if (p != null) {
                            painter = p
                            aspectRatio = resolvePainterAspectRatioOrNull(p)
                            onPainterReady(0, p)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = sourceAlphaForTransition(sourceKey, hiddenSourceKey, hiddenSourceRevealProgress)
                        },
                )
            }
        }
        return
    }

    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color.Black)
                .overlayHorizontalSwipeZone(
                    zoneKey = pagerZoneKey,
                    atStart = pagerState.currentPage == 0,
                )
        ) { page ->
            val sourceKey = remember(postId, page) { "post_gallery_${postId}_$page" }
            var bounds by remember(sourceKey) { mutableStateOf<Rect?>(null) }
            val initialPainter = initialPainters?.get(page)
            var painter by remember(sourceKey, initialPainter) { mutableStateOf<Painter?>(initialPainter) }
            var aspectRatio by remember(sourceKey) { mutableStateOf<Float?>(null) }
            LaunchedEffect(bounds, aspectRatio, sourceKey) {
                val rect = bounds ?: return@LaunchedEffect
                onOriginChanged(
                    page,
                    rect.toPostGalleryOrigin(
                        sourceKey = sourceKey,
                        imageAspectRatio = aspectRatio ?: aspectRatioForPage(page),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onGloballyPositioned { coordinates ->
                        bounds = coordinates.boundsInWindow()
                    }
                    .clickable(enabled = hiddenSourceKey != sourceKey) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onImageClick(page, painter)
                    }
            ) {
                val pageUrl = imageUrls.getOrNull(page)
                val pageVariant = imageVariants.getOrNull(page)
                val pageThumb = pageVariant?.thumbUrl
                if (initialPainter != null && painter == null) {
                    androidx.compose.foundation.Image(
                        painter = initialPainter,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (!pageUrl.isNullOrBlank()) {
                    ProgressiveImage(
                        thumbUrl = pageThumb,
                        targetUrl = pageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        placeholderColor = Color.Transparent,
                        onPainterChanged = { p ->
                            if (p != null) {
                                painter = p
                                aspectRatio = resolvePainterAspectRatioOrNull(p)
                                onPainterReady(page, p)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = sourceAlphaForTransition(sourceKey, hiddenSourceKey, hiddenSourceRevealProgress)
                            },
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

private fun Rect.toPostGalleryOrigin(
    sourceKey: String,
    imageAspectRatio: Float? = null,
    cornerRadiusPx: Float = 0f,
    sourceScaleMode: SourceImageScaleMode = SourceImageScaleMode.Fit,
): SharedImageOrigin {
    val frameAspectRatio = width.takeIf { it > 0f }?.let { safeWidth ->
        height.takeIf { it > 0f }?.let { safeHeight -> safeWidth / safeHeight }
    } ?: (3f / 4f)
    val aspectRatio = (imageAspectRatio ?: frameAspectRatio).coerceAtLeast(0.01f)
    val contentRect = if (sourceScaleMode == SourceImageScaleMode.Fit) {
        fitRectInBounds(this, aspectRatio)
    } else {
        this
    }
    return SharedImageOrigin(
        sourceKey = sourceKey,
        rectInWindow = contentRect,
        aspectRatio = aspectRatio,
        contentRectInWindow = contentRect,
        cornerRadiusPx = cornerRadiusPx,
        sourceScaleMode = sourceScaleMode,
    )
}

private fun fitRectInBounds(bounds: Rect, aspectRatio: Float): Rect {
    val safeAspect = aspectRatio.coerceAtLeast(0.01f)
    val boundsAspect = (bounds.width / bounds.height.coerceAtLeast(1f)).coerceAtLeast(0.01f)
    return if (boundsAspect > safeAspect) {
        val height = bounds.height
        val width = height * safeAspect
        val left = bounds.left + (bounds.width - width) / 2f
        Rect(left, bounds.top, left + width, bounds.bottom)
    } else {
        val width = bounds.width
        val height = width / safeAspect
        val top = bounds.top + (bounds.height - height) / 2f
        Rect(bounds.left, top, bounds.right, top + height)
    }
}

private fun sourceAlphaForTransition(
    sourceKey: String,
    hiddenSourceKey: String?,
    revealProgress: Float,
): Float {
    return if (hiddenSourceKey == sourceKey) revealProgress.coerceIn(0f, 1f) else 1f
}

private fun viewerSourceKeyForPage(
    presentation: PostScreenPresentation,
    postId: String,
    page: Int,
): String {
    return when (presentation) {
        PostScreenPresentation.ProfileModal -> "post_modal_gallery_${postId}_$page"
        PostScreenPresentation.FullscreenLegacy -> "post_gallery_${postId}_$page"
    }
}


private fun resolvePainterAspectRatioOrNull(painter: Painter): Float? {
    val size = painter.intrinsicSize
    val width = size.width
    val height = size.height
    return if (width.isFinite() && height.isFinite() && width > 0f && height > 0f) {
        (width / height).coerceAtLeast(0.01f)
    } else {
        null
    }
}

private fun PostImageVariant.aspectRatioOrNull(): Float? {
    val safeWidth = width?.takeIf { it > 0 } ?: return null
    val safeHeight = height?.takeIf { it > 0 } ?: return null
    return (safeWidth.toFloat() / safeHeight.toFloat()).coerceAtLeast(0.01f)
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
            .height(18.dp)
            .background(color)
    )
}


internal fun extractTitleAndBody(description: String): Pair<String, String> {
    val trimmed = description.trim()
    if (trimmed.isEmpty()) return "" to ""

    val words = trimmed.split("\\s+".toRegex())
    if (words.size <= 3) {
        return trimmed to ""
    }

    var splitIndex = 0
    var wordCount = 0
    for (i in trimmed.indices) {
        if (i == 0 || (trimmed[i - 1].isWhitespace() && !trimmed[i].isWhitespace())) {
            wordCount++
            if (wordCount == 4) {
                splitIndex = i
                break
            }
        }
    }

    val matches = findFlowMarkdownMatches(trimmed)
    for (m in matches) {
        if (splitIndex in (m.start + 1) until m.end) {
            splitIndex = if (m.start == 0) m.end else m.start
            break
        }
    }

    val title = trimmed.substring(0, splitIndex).trim()
    val body = trimmed.substring(splitIndex).trim()
    return title to body
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
        .map(String::trim)
        .filter(String::isNotEmpty)
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

private data class DetailVariantUrls(
    val lqUrl: String,
    val previewUrl: String
)

private fun toDetailVariantUrls(fullUrl: String): DetailVariantUrls {
    return DetailVariantUrls(
        lqUrl = fullUrl,
        previewUrl = fullUrl
    )
}

private fun toDisplayTag(category: String): String {
    val normalized = category.trim()
    if (normalized.isBlank()) return "#unknown"
    return "#${normalized.lowercase().replace('_', ' ')}"
}

private fun categoryColor(category: String): Color {
    val normalized = category.trim().uppercase()
    if (normalized.isBlank()) return Color(0xFF546E7A)

    val known = when (normalized) {
        "NATURE" -> Color(0xFF2E7D32)
        "FOOD" -> Color(0xFFF57C00)
        "ART" -> Color(0xFFD81B60)
        "TRAVEL" -> Color(0xFF0288D1)
        "TECH" -> Color(0xFF3949AB)
        "MEMES" -> Color(0xFF8E24AA)
        "LIFESTYLE" -> Color(0xFF00897B)
        "SPACE" -> Color(0xFF5E35B1)
        "PETS" -> Color(0xFF6D4C41)
        "SPORT" -> Color(0xFFC62828)
        else -> null
    }
    if (known != null) return known

    val seed = abs(normalized.hashCode())
    val r = 60 + (seed and 0x7F)
    val g = 60 + ((seed shr 8) and 0x7F)
    val b = 60 + ((seed shr 16) and 0x7F)
    val argb = 0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
    return Color(argb)
}
