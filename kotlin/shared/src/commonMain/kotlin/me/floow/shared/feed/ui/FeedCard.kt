package me.floow.shared.feed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.domain.models.Post
import me.floow.domain.models.PostImageVariant
import me.floow.domain.models.resolvedImageVariants
import me.floow.shared.post.ui.SharedLinkifiedText
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.misc.textwrap.TextWrap
import me.floow.uikit.components.misc.textwrap.TextWrapObstacleAlignment
import me.floow.uikit.components.misc.textwrap.buildFlowAnnotatedText
import kotlin.math.abs

internal val SharedPostCardShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
internal val SharedStackCardShape = RoundedCornerShape(14.dp)
internal const val SingleImageCardRotation = -5f
internal const val SingleImageAspectRatio = 180f / 240f
internal val LocalSharedSwipeItemIndex = compositionLocalOf { 0 }
internal val LocalSharedSwipeCardShape = compositionLocalOf<Shape> { SharedPostCardShape }

internal data class SharedFeedTopCardColors(
    val container: Color,
    val content: Color,
    val mutedContent: Color,
    val accent: Color,
    val border: Color,
)

@Composable
internal fun sharedFeedTopCardColors(): SharedFeedTopCardColors {
    val colorScheme = MaterialTheme.colorScheme
    return SharedFeedTopCardColors(
        container = colorScheme.surfaceContainer,
        content = colorScheme.onSurface,
        mutedContent = colorScheme.onSurfaceVariant,
        accent = colorScheme.primary,
        border = colorScheme.outlineVariant,
    )
}

@Composable
internal fun SharedFeedPostCard(
    post: Post,
    recommendationReason: String?,
    hapticFeedback: HapticFeedback,
    onProfileClick: (String) -> Unit,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: (Post) -> Unit,
    onMoreClick: (Post) -> Unit,
    onOpenImageOverlay: (SharedFeedOverlayLaunchData) -> Unit,
    onOverlaySourceSnapshot: (SharedFeedOverlayLaunchData) -> Unit,
    isViewerActive: Boolean,
    isOverlayActive: Boolean,
    overlayDetachedCount: Int,
    onNonSwipeBottomInsetChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val swipeItemIndex = LocalSharedSwipeItemIndex.current
    val cardShape = LocalSharedSwipeCardShape.current
    val topCardColors = sharedFeedTopCardColors()
    Card(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = topCardColors.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SharedPostHeader(post, recommendationReason, onProfileClick, onMoreClick, topCardColors)
            val imageVariants = remember(post.id) { post.content.resolvedImageVariants() }
            SharedImageGallery(
                imageVariants = imageVariants,
                hapticFeedback = hapticFeedback,
                onViewClick = onOpenImageOverlay,
                onOverlaySourceSnapshot = onOverlaySourceSnapshot,
                isViewerActive = isViewerActive,
                isOverlayActive = isOverlayActive,
                overlayDetachedCount = overlayDetachedCount,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            SharedTextContent(
                description = post.content.description,
                timestamp = post.createdAt,
                onProfileTagClick = onProfileTagClick,
                onPostLinkClick = onPostLinkClick,
                onCommentsClick = { onCommentsClick(post) },
                linkColor = categoryColor(post.category),
                colors = topCardColors,
            )
            SharedPostFooter(
                commentsCount = post.commentsCount,
                commentersPreview = post.commentersPreview,
                onCommentsClick = { onCommentsClick(post) },
                onHeightChanged = if (swipeItemIndex == 0) onNonSwipeBottomInsetChanged else null,
                colors = topCardColors,
            )
        }
    }
}

@Composable
internal fun SharedPostHeader(
    post: Post,
    recommendationReason: String?,
    onProfileClick: (String) -> Unit,
    onMoreClick: (Post) -> Unit,
    colors: SharedFeedTopCardColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = colors.mutedContent, fontWeight = FontWeight.SemiBold)) {
                    append("@")
                }
                withStyle(SpanStyle(color = colors.content, fontWeight = FontWeight.SemiBold)) {
                    append("${post.author.username?.value ?: "unknown"} / ")
                }
                withStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold)) {
                    append(toDisplayTag(post.category))
                }
            },
            modifier = Modifier
                .weight(1f)
                .clickable { onProfileClick(post.author.id) },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .height(32.dp)
                .width(56.dp)
                .border(1.dp, colors.border, RoundedCornerShape(100.dp))
                .clickable { onMoreClick(post) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Меню",
                tint = colors.content,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = 90f },
            )
        }
    }
}

@Composable
internal fun SharedImageGallery(
    imageVariants: List<PostImageVariant>,
    hapticFeedback: HapticFeedback,
    onViewClick: (SharedFeedOverlayLaunchData) -> Unit,
    onOverlaySourceSnapshot: (SharedFeedOverlayLaunchData) -> Unit,
    isViewerActive: Boolean,
    isOverlayActive: Boolean,
    overlayDetachedCount: Int,
    modifier: Modifier = Modifier,
) {
    val items = imageVariants.take(4)
    if (items.isEmpty()) return
    if (items.size == 1) {
        SharedSingleImageGallery(
            variant = items.first(),
            hapticFeedback = hapticFeedback,
            onViewClick = onViewClick,
            onOverlaySourceSnapshot = onOverlaySourceSnapshot,
            isViewerActive = isViewerActive,
            isOverlayActive = isOverlayActive,
            overlayDetachedCount = overlayDetachedCount,
            modifier = modifier,
        )
        return
    }
    val front = items[0]
    val left = items.getOrNull(1)
    val right = items.getOrNull(2)
    val back = items.getOrNull(3)
    var frontPainter by remember(front) { mutableStateOf<Painter>(ColorPainter(Color(0xFF1A1A1A))) }
    var leftPainter by remember(left) { mutableStateOf<Painter?>(null) }
    var rightPainter by remember(right) { mutableStateOf<Painter?>(null) }
    var backPainter by remember(back) { mutableStateOf<Painter?>(null) }
    val frontBounds = remember { mutableStateOf<Rect?>(null) }
    val galleryBounds = remember { mutableStateOf<Rect?>(null) }
    val buttonBounds = remember { mutableStateOf<Rect?>(null) }
    val leftBounds = remember { mutableStateOf<Rect?>(null) }
    val rightBounds = remember { mutableStateOf<Rect?>(null) }
    val backBounds = remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val isTwoImageLayout = items.size == 2
    val isFourImageLayout = items.size == 4

    val frontCardWidthDp = 172.dp
    val frontCardHeightDp = 214.dp
    val sideCardWidthDp = 172.dp
    val sideCardHeightDp = 223.dp

    val frontWidthPx = with(density) { frontCardWidthDp.toPx() }
    val frontHeightPx = with(density) { frontCardHeightDp.toPx() }
    val sideWidthPx = with(density) { sideCardWidthDp.toPx() }
    val sideHeightPx = with(density) { sideCardHeightDp.toPx() }
    val backWidthPx = sideWidthPx
    val backHeightPx = sideHeightPx

    val groupOffsetXPx = if (isTwoImageLayout) with(density) { 12.dp.toPx() } else 0f

    val backShiftXPx = with(density) { (-10).dp.toPx() }
    val backShiftYPx = with(density) { (-50).dp.toPx() }
    val leftShiftXPx = with(density) { (-61).dp.toPx() }
    val leftShiftYPx = with(density) { (if (isTwoImageLayout) (-10).dp else 6.dp).toPx() }
    val rightShiftXPx = with(density) { 60.dp.toPx() }
    val rightShiftYPx = with(density) { (-22).dp.toPx() }
    val frontShiftXPx = with(density) { 26.dp.toPx() }
    val frontShiftYPx = with(density) { 40.dp.toPx() }
    val frontContent = remember(front) {
        movableContentOf {
            ProgressiveImage(
                thumbUrl = front.thumbUrl,
                targetUrl = front.targetUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onPainterChanged = { if (it != null) frontPainter = it }
            )
        }
    }
    val leftContent = remember(left) {
        left?.let { variant ->
            movableContentOf {
                ProgressiveImage(
                    thumbUrl = variant.thumbUrl,
                    targetUrl = variant.targetUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onPainterChanged = { if (it != null) leftPainter = it }
                )
            }
        }
    }
    val rightContent = remember(right) {
        right?.let { variant ->
            movableContentOf {
                ProgressiveImage(
                    thumbUrl = variant.thumbUrl,
                    targetUrl = variant.targetUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onPainterChanged = { if (it != null) rightPainter = it }
                )
            }
        }
    }
    val backContent = remember(back) {
        back?.let { variant ->
            movableContentOf {
                ProgressiveImage(
                    thumbUrl = variant.thumbUrl,
                    targetUrl = variant.targetUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onPainterChanged = { if (it != null) backPainter = it }
                )
            }
        }
    }
    val buildLaunchData = remember(
        frontBounds.value,
        leftBounds.value,
        rightBounds.value,
        backBounds.value,
        buttonBounds.value,
        galleryBounds.value,
        frontWidthPx,
        frontHeightPx,
        sideWidthPx,
        sideHeightPx,
        backWidthPx,
        backHeightPx,
        groupOffsetXPx,
        backShiftXPx,
        backShiftYPx,
        leftShiftXPx,
        leftShiftYPx,
        rightShiftXPx,
        rightShiftYPx,
        frontShiftXPx,
        frontShiftYPx,
        frontContent,
        leftContent,
        rightContent,
        backContent,
        frontPainter,
        leftPainter,
        rightPainter,
        backPainter,
    ) {
        {
            val anchorCenter = frontBounds.value?.center ?: galleryBounds.value?.center
            fun poseOrFallback(rect: Rect?, width: Float, height: Float, rotation: Float, shiftX: Float = 0f, shiftY: Float = 0f): SharedFeedCardPose? {
                return if (rect != null) {
                    SharedFeedCardPose(
                        centerX = rect.center.x,
                        centerY = rect.center.y,
                        width = width,
                        height = height,
                        rotation = rotation,
                    )
                } else {
                    anchorCenter?.let { center ->
                        SharedFeedCardPose(
                            centerX = center.x + shiftX,
                            centerY = center.y + shiftY,
                            width = width,
                            height = height,
                            rotation = rotation,
                        )
                    }
                }
            }
            SharedFeedOverlayLaunchData(
                buttonRect = buttonBounds.value ?: galleryBounds.value,
                cardRects = listOf(frontBounds.value, leftBounds.value, rightBounds.value, backBounds.value),
                cardPoses = listOf(
                    poseOrFallback(frontBounds.value, frontWidthPx, frontHeightPx, 0f, shiftX = frontShiftXPx, shiftY = frontShiftYPx),
                    poseOrFallback(leftBounds.value, sideWidthPx, sideHeightPx, 0f, shiftX = leftShiftXPx, shiftY = leftShiftYPx),
                    poseOrFallback(rightBounds.value, sideWidthPx, sideHeightPx, 0f, shiftX = rightShiftXPx, shiftY = rightShiftYPx),
                    poseOrFallback(backBounds.value, backWidthPx, backHeightPx, 0f, shiftX = backShiftXPx, shiftY = backShiftYPx),
                ),
                cardContents = listOf(frontContent, leftContent, rightContent, backContent),
                cardPainters = listOf(frontPainter, leftPainter, rightPainter, backPainter),
            )
        }
    }
    LaunchedEffect(isOverlayActive, frontBounds.value, leftBounds.value, rightBounds.value, backBounds.value) {
        if (isOverlayActive) {
            onOverlaySourceSnapshot(buildLaunchData())
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .onGloballyPositioned { galleryBounds.value = it.boundsInWindow() },
        )
        back?.let {
            LayeredImageCard(
                it,
                Modifier
                    .size(sideCardWidthDp, sideCardHeightDp)
                    .graphicsLayer {
                        translationX = backShiftXPx
                        translationY = backShiftYPx
                        alpha = if (isOverlayActive && overlayDetachedCount >= 4) 0f else 1f
                    }
                    .clickable(enabled = !isViewerActive && !isOverlayActive) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onViewClick(buildLaunchData())
                    }
                    .onGloballyPositioned { coordinates -> backBounds.value = coordinates.boundsInWindow() },
                0.5f,
                movableContent = backContent,
            )
        }
        left?.let {
            LayeredImageCard(
                it,
                Modifier
                    .size(sideCardWidthDp, sideCardHeightDp)
                    .graphicsLayer {
                        translationX = leftShiftXPx + groupOffsetXPx
                        translationY = leftShiftYPx
                        alpha = if (isOverlayActive && overlayDetachedCount >= 2) 0f else 1f
                    }
                    .clickable(enabled = !isViewerActive && !isOverlayActive) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onViewClick(buildLaunchData())
                    }
                    .onGloballyPositioned { coordinates -> leftBounds.value = coordinates.boundsInWindow() },
                0.34f,
                movableContent = leftContent,
            )
        }
        right?.let {
            LayeredImageCard(
                it,
                Modifier
                    .size(sideCardWidthDp, sideCardHeightDp)
                    .graphicsLayer {
                        translationX = rightShiftXPx
                        translationY = rightShiftYPx
                        alpha = if (isOverlayActive && overlayDetachedCount >= 3) 0f else 1f
                    }
                    .clickable(enabled = !isViewerActive && !isOverlayActive) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onViewClick(buildLaunchData())
                    }
                    .onGloballyPositioned { coordinates -> rightBounds.value = coordinates.boundsInWindow() },
                if (isFourImageLayout) 0.5f else 0.5f,
                movableContent = rightContent,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(frontCardWidthDp, frontCardHeightDp)
                    .graphicsLayer {
                        translationX = frontShiftXPx + groupOffsetXPx
                        translationY = frontShiftYPx
                        alpha = if (isOverlayActive && overlayDetachedCount >= 1) 0f else 1f
                    }
                    .clickable(enabled = !isViewerActive && !isOverlayActive) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onViewClick(buildLaunchData())
                    }
                    .onGloballyPositioned { frontBounds.value = it.boundsInWindow() }
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                frontContent()
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }
        }
    }
}

@Composable
internal fun LayeredImageCard(
    variant: PostImageVariant,
    modifier: Modifier,
    shadeAlpha: Float,
    movableContent: SharedFeedMovableCardContent? = null,
) {
    Box(modifier) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
            if (movableContent != null) {
                movableContent()
            } else {
                ProgressiveImage(
                    thumbUrl = variant.thumbUrl,
                    targetUrl = variant.targetUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = shadeAlpha)))
        }
    }
}

@Composable
internal fun SharedSingleImageGallery(
    variant: PostImageVariant,
    hapticFeedback: HapticFeedback,
    onViewClick: (SharedFeedOverlayLaunchData) -> Unit,
    onOverlaySourceSnapshot: (SharedFeedOverlayLaunchData) -> Unit,
    isViewerActive: Boolean,
    isOverlayActive: Boolean,
    overlayDetachedCount: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val defaultCardWidthPx = with(density) { 220.dp.toPx() }
    val defaultCardHeightPx = with(density) { 280.dp.toPx() }
    val galleryBounds = remember { mutableStateOf<Rect?>(null) }
    val frontBounds = remember { mutableStateOf<Rect?>(null) }
    var frontPainter by remember(variant) { mutableStateOf<Painter?>(null) }
    val frontContent = remember(variant) {
        movableContentOf {
            ProgressiveImage(
                thumbUrl = variant.thumbUrl,
                targetUrl = variant.targetUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onPainterChanged = { if (it != null) frontPainter = it }
            )
        }
    }
    val buildLaunchData = remember(frontBounds.value, galleryBounds.value, frontContent, defaultCardWidthPx, defaultCardHeightPx, frontPainter) {
        {
            val rect = frontBounds.value
            val fallbackCenter = galleryBounds.value?.center
            SharedFeedOverlayLaunchData(
                buttonRect = rect ?: galleryBounds.value,
                cardRects = listOf(rect),
                cardPoses = listOf(
                    if (rect != null) {
                        SharedFeedCardPose(
                            centerX = rect.center.x,
                            centerY = rect.center.y,
                            width = rect.width,
                            height = rect.height,
                            rotation = SingleImageCardRotation,
                        )
                    } else {
                        fallbackCenter?.let { center ->
                            SharedFeedCardPose(
                                centerX = center.x,
                                centerY = center.y,
                                width = defaultCardWidthPx,
                                height = defaultCardHeightPx,
                                rotation = SingleImageCardRotation,
                            )
                        }
                    },
                ),
                cardContents = listOf(frontContent),
                cardPainters = listOf(frontPainter),
            )
        }
    }
    LaunchedEffect(isOverlayActive, frontBounds.value) {
        if (isOverlayActive) {
            onOverlaySourceSnapshot(buildLaunchData())
        }
    }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val widthByDesign = maxWidth * 0.6f
        val widthByHeight = maxHeight * SingleImageAspectRatio
        val cardWidth = if (widthByDesign < widthByHeight) widthByDesign else widthByHeight
        Box(
            Modifier
                .matchParentSize()
                .onGloballyPositioned { galleryBounds.value = it.boundsInWindow() },
        )
        Box(
            Modifier
                .width(cardWidth)
                .aspectRatio(SingleImageAspectRatio)
                .graphicsLayer {
                    rotationZ = SingleImageCardRotation
                    alpha = if (isOverlayActive && overlayDetachedCount >= 1) 0f else 1f
                }
                .clickable(enabled = !isOverlayActive && !isViewerActive) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onViewClick(buildLaunchData())
                }
                .onGloballyPositioned { frontBounds.value = it.boundsInWindow() }
                .clip(RoundedCornerShape(18.dp)),
        ) {
            frontContent()
        }
    }
}

@Composable
internal fun SharedTextContent(
    description: String?,
    timestamp: Long,
    onProfileTagClick: (String) -> Unit,
    onPostLinkClick: (String, String) -> Unit,
    onCommentsClick: () -> Unit,
    linkColor: Color,
    colors: SharedFeedTopCardColors,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!description.isNullOrBlank()) {
            val (title, body) = extractTitleAndBody(description)
            Box(Modifier.fillMaxWidth().height(100.dp)) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(),
                ) {
                    if (title.isNotBlank()) {
                        SharedLinkifiedText(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = colors.content,
                            ),
                            linkColor = linkColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onProfileTagClick = onProfileTagClick,
                            onPostLinkClick = onPostLinkClick,
                        )
                    }
                    if (body.isNotBlank()) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val maxLines = 3
                            val readMoreText = "читать дальше"
                            val textMeasurer = rememberTextMeasurer()
                            val density = LocalDensity.current
                            val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, color = colors.content)
                            val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(0)

                            val trimResult = remember(body, maxWidthPx, bodyStyle) {
                                if (maxWidthPx == 0) {
                                    SharedTrimResult(body = body, isTruncated = false)
                                } else {
                                    val fullLayout = textMeasurer.measure(
                                        text = body,
                                        style = bodyStyle,
                                        maxLines = maxLines,
                                        overflow = TextOverflow.Ellipsis,
                                        constraints = Constraints(maxWidth = maxWidthPx),
                                    )
                                    if (!fullLayout.hasVisualOverflow) {
                                        SharedTrimResult(body = body, isTruncated = false)
                                    } else {
                                        val suffix = "… $readMoreText"
                                        var cutIndex = fullLayout.getLineEnd(maxLines - 1, visibleEnd = true)
                                        var candidate = body.take(cutIndex).trimEnd()
                                        while (candidate.isNotEmpty()) {
                                            val layout = textMeasurer.measure(
                                                text = candidate + suffix,
                                                style = bodyStyle,
                                                maxLines = maxLines,
                                                overflow = TextOverflow.Clip,
                                                constraints = Constraints(maxWidth = maxWidthPx),
                                            )
                                            if (!layout.hasVisualOverflow) break
                                            cutIndex -= 1
                                            candidate = body.take(cutIndex.coerceAtLeast(0)).trimEnd()
                                        }
                                        SharedTrimResult(body = candidate, isTruncated = true)
                                    }
                                }
                            }

                            val displayBody = if (trimResult.isTruncated) "${trimResult.body}… " else trimResult.body
                            val rawTextToDisplay = if (trimResult.isTruncated) "$displayBody$readMoreText" else displayBody
                            var isSpoilerRevealed by remember(body) { mutableStateOf(false) }
                            val annotatedText = remember(rawTextToDisplay, linkColor, isSpoilerRevealed) {
                                buildFlowAnnotatedText(
                                    text = rawTextToDisplay,
                                    linkColor = linkColor,
                                    isSpoilerRevealed = isSpoilerRevealed,
                                )
                            }

                            TextWrap(
                                text = annotatedText,
                                modifier = Modifier.fillMaxWidth(),
                                color = colors.content,
                                style = bodyStyle,
                                forcedObstacleOffset = IntOffset(0, 4),
                                obstacleAlignment = TextWrapObstacleAlignment.BottomEnd,
                                isSpoilerRevealed = isSpoilerRevealed,
                                onRevealSpoiler = { isSpoilerRevealed = true },
                                onProfileTagClick = onProfileTagClick,
                                onPostLinkClick = onPostLinkClick,
                                onTextClick = if (trimResult.isTruncated) onCommentsClick else null,
                                obstacleContent = {
                                    Text(
                                        formatFeedPostTime(timestamp),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = colors.mutedContent,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                },
                            )
                        }
                    } else {
                        Text(
                            formatFeedPostTime(timestamp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = colors.mutedContent,
                            textAlign = TextAlign.End,
                            modifier = Modifier.align(Alignment.End),
                        )
                    }
                }
            }
        }
    }
}

internal fun extractTitleAndBody(description: String): Pair<String, String> {
    val words = description.trim().split("\\s+".toRegex())
    return words.take(3).joinToString(" ") to words.drop(3).joinToString(" ")
}

internal fun toDisplayTag(category: String): String = if (category.isBlank()) "#unknown" else "#${category.trim().lowercase().replace('_', ' ')}"

internal fun categoryColor(category: String): Color {
    return when (category.trim().uppercase()) {
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
        "" -> Color(0xFF546E7A)
        else -> {
            val normalized = category.trim().uppercase()
            val seed = abs(normalized.hashCode())
            val r = 60 + (seed and 0x7F)
            val g = 60 + ((seed shr 8) and 0x7F)
            val b = 60 + ((seed shr 16) and 0x7F)
            Color(0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
        }
    }
}

internal data class SharedTrimResult(
    val body: String,
    val isTruncated: Boolean,
)
