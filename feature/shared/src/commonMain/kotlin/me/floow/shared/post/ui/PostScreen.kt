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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import coil3.compose.AsyncImage
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.current_reply_close_icon
import flow.feature.shared.generated.resources.share_icon
import me.floow.uikit.components.avatar.InitialAvatar
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.theme.NinehedronShape
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.text.ClickableText
import kotlin.math.abs
import kotlin.math.max

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
    val viewerState = rememberFullscreenImageViewerState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val viewerModel = remember(model.authorName, model.authorUsername, model.createdAtLabel, cleanedImageUrls) {
        FullscreenImageViewerModel(
            images = cleanedImageUrls,
            title = model.authorName ?: "@${model.authorUsername ?: "unknown"}",
            subtitleProvider = { listOfNotNull("Фото", model.createdAtLabel).joinToString(" • ") },
        )
    }
    val hostedViewer = remember(viewerModel, viewerState, onPresentFullscreenViewer) {
        HostedFullscreenImageViewer(
            modelProvider = { viewerModel },
            state = viewerState,
            onAction = { action ->
                viewerState.reduce(action, viewerModel.images.size)
                if (action == FullscreenImageViewerAction.CloseAnimationFinished) {
                    onPresentFullscreenViewer?.invoke(null)
                }
            }
        )
    }

    val currentMediaSnapshot by remember(model.postId, cleanedImageUrls, previewUrls, galleryPagerState) {
        derivedStateOf {
            if (cleanedImageUrls.isEmpty()) return@derivedStateOf null
            val selectedIndex = galleryPagerState.currentPage.coerceIn(0, cleanedImageUrls.lastIndex)
            PostMediaSourceSnapshot(
                postId = model.postId,
                owner = PostMediaSourceOwner.POST,
                selectedIndex = selectedIndex,
                urls = cleanedImageUrls,
                previewUrls = previewUrls,
            )
        }
    }

    DisposableEffect(onPresentFullscreenViewer) {
        onDispose { onPresentFullscreenViewer?.invoke(null) }
    }

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
                            pagerState = galleryPagerState,
                            onImageClick = { index ->
                                onPresentFullscreenViewer?.invoke(hostedViewer)
                                hostedViewer.onAction(
                                    FullscreenImageViewerAction.Open(
                                        page = index,
                                        origin = null,
                                    )
                                )
                            },
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

        if (onPresentFullscreenViewer == null) {
            SharedFullscreenImageViewer(
                model = viewerModel,
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
    val mediaTransferSnapshot: PostMediaSourceSnapshot? = null,
    val description: String?,
    val authorId: String = "",
    val authorName: String?,
    val authorUsername: String?,
    val authorAvatarUrl: String?,
    val category: String? = null,
    val createdAtLabel: String? = null,
    val likesCount: Int,
    val commentsCount: Int,
    val commentersPreview: List<String>,
    val isSelf: Boolean,
)

@Composable
private fun ZoomableViewerImage(
    imageUrl: String,
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(imageUrl) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val nextZoom = (zoom * zoomChange).coerceIn(1f, 4f)
                        val nextPan = if (nextZoom <= 1.01f) {
                            Offset.Zero
                        } else {
                            clampOffset(
                                rawOffset = pan + panChange,
                                containerWidthPx = containerWidthPx,
                                containerHeightPx = containerHeightPx,
                                scale = nextZoom
                            )
                        }
                        onTransform(nextZoom, nextPan)
                    }
                }
                .pointerInput(imageUrl) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            val targetZoom = if (zoom > 1.01f) 1f else 2.5f
                            onTransform(targetZoom, Offset.Zero)
                        }
                    )
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
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
    pagerState: PagerState,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageUrls.size <= 1) {
        AsyncImage(
            model = imageUrls.firstOrNull(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .background(Color.Black)
                .aspectRatio(3f / 4f)
                .clickable(enabled = imageUrls.isNotEmpty()) { onImageClick(0) }
        )
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
        ) { page ->
            AsyncImage(
                model = imageUrls[page],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { onImageClick(page) }
            )
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

@Composable
private fun SharedLinkifiedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
) {
    val annotated = remember(text, linkColor) {
        buildAnnotatedPostText(text, linkColor)
    }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    ClickableText(
        text = annotated,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations(TAG_POST, offset, offset).firstOrNull()?.let { ann ->
                val parts = ann.item.split("/", limit = 2)
                if (parts.size == 2) onPostLinkClick(parts[0], parts[1])
                return@ClickableText
            }
            annotated.getStringAnnotations(TAG_PROFILE, offset, offset).firstOrNull()?.let { ann ->
                onProfileTagClick(ann.item)
            }
        },
        onTextLayout = { textLayout = it }
    )
}

private fun buildAnnotatedPostText(
    text: String,
    linkColor: Color,
): AnnotatedString {
    val links = findPostLinks(text)
    if (links.isEmpty()) return AnnotatedString(text)

    return buildAnnotatedString {
        var cursor = 0
        links.forEach { link ->
            if (cursor < link.start) append(text.substring(cursor, link.start))
            val segment = text.substring(link.start, link.end)
            withStyle(SpanStyle(color = linkColor)) { append(segment) }
            addStringAnnotation(
                tag = link.tag,
                annotation = link.value,
                start = length - segment.length,
                end = length
            )
            cursor = link.end
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

private data class LinkMatch(
    val start: Int,
    val end: Int,
    val tag: String,
    val value: String
)

private fun findPostLinks(text: String): List<LinkMatch> {
    val matches = mutableListOf<LinkMatch>()
    val postPattern = Regex("https?://[^\\s]+/([a-z0-9_]+)/([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE)
    val mentionPattern = Regex("@([a-z0-9_]+)", RegexOption.IGNORE_CASE)

    postPattern.findAll(text).forEach { match ->
        matches += LinkMatch(
            start = match.range.first,
            end = match.range.last + 1,
            tag = TAG_POST,
            value = "${match.groupValues[1].lowercase()}/${match.groupValues[2]}"
        )
    }

    mentionPattern.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        if (matches.any { overlaps(it.start, it.end, start, end) }) return@forEach
        matches += LinkMatch(
            start = start,
            end = end,
            tag = TAG_PROFILE,
            value = match.groupValues[1].lowercase()
        )
    }

    return matches.sortedBy { it.start }
}

private fun overlaps(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Boolean {
    return aStart < bEnd && bStart < aEnd
}

private fun clampOffset(
    rawOffset: Offset,
    containerWidthPx: Float,
    containerHeightPx: Float,
    scale: Float
): Offset {
    val scaledWidth = containerWidthPx * scale
    val scaledHeight = containerHeightPx * scale
    val maxX = max(0f, (scaledWidth - containerWidthPx) / 2f)
    val maxY = max(0f, (scaledHeight - containerHeightPx) / 2f)
    return Offset(
        x = rawOffset.x.coerceIn(-maxX, maxX),
        y = rawOffset.y.coerceIn(-maxY, maxY)
    )
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

private const val TAG_PROFILE = "profile"
private const val TAG_POST = "post"
