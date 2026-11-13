package me.floow.shared.post.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.SourceImageScaleMode
import kotlin.math.absoluteValue

@Composable
internal actual fun ProfileModalHeroCarousel(
    imageUrls: List<String>,
    imageVariants: List<PostImageVariant>,
    initialIndex: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    postId: String,
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit,
    onPainterReady: (Int, Painter) -> Unit,
    onImageClick: (Int, Painter?) -> Unit,
    initialPainters: Map<Int, Painter>?,
    hiddenSourceKey: String?,
    hiddenSourceRevealProgress: Float,
) {
    val items = remember(imageUrls, imageVariants) {
        buildProfileModalCarouselItems(imageUrls = imageUrls, imageVariants = imageVariants)
    }
    if (items.isEmpty()) return

    val scope = rememberCoroutineScope()
    val boundedInitial = initialIndex.coerceIn(0, items.lastIndex)
    val boundedSelected = selectedIndex.coerceIn(0, items.lastIndex)
    val pagerState = rememberPagerState(initialPage = boundedInitial, pageCount = { items.size })

    LaunchedEffect(pagerState.settledPage, items.size) {
        onSelectedIndexChange(pagerState.settledPage.coerceIn(0, items.lastIndex))
    }

    LaunchedEffect(boundedSelected, items.size) {
        if (!pagerState.isScrollInProgress && pagerState.settledPage != boundedSelected) {
            pagerState.scrollToPage(boundedSelected)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val carouselPageSize = remember(items.size) {
            object : PageSize {
                override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int {
                    val peekWidthPx = 28.dp.roundToPx()
                    val minWidthPx = 240.dp.roundToPx()
                    return if (items.size > 1) {
                        (availableSpace - (peekWidthPx + pageSpacing) * 2).coerceAtLeast(minWidthPx)
                    } else {
                        availableSpace.coerceAtLeast(minWidthPx)
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = items.size > 1,
            pageSize = carouselPageSize,
            pageSpacing = 8.dp,
            snapPosition = SnapPosition.Center,
            beyondViewportPageCount = 0,
            modifier = Modifier
                .fillMaxWidth()
                .height(376.dp)
        ) { page ->
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                .absoluteValue
                .coerceIn(0f, 1f)

            CarouselCard(
                item = items[page],
                index = page,
                postId = postId,
                scale = lerpFloat(1f, 0.96f, pageOffset),
                alpha = lerpFloat(1f, 0.82f, pageOffset),
                initialPainter = initialPainters?.get(page),
                onOriginChanged = onOriginChanged,
                onPainterReady = onPainterReady,
                onClick = { painter -> onImageClick(page, painter) },
                hiddenSourceKey = hiddenSourceKey,
                hiddenSourceRevealProgress = hiddenSourceRevealProgress,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (items.size > 1) {
            val activeDotColor = MaterialTheme.colorScheme.onSurface
            val inactiveDotColor = activeDotColor.copy(alpha = 0.32f)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(items.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (index == boundedSelected) activeDotColor else inactiveDotColor,
                                shape = RoundedCornerShape(50.dp)
                            )
                            .clickable {
                                if (index != boundedSelected) {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun CarouselCard(
    item: ProfileModalCarouselItem,
    index: Int,
    postId: String,
    scale: Float,
    alpha: Float,
    initialPainter: Painter?,
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit,
    onPainterReady: (Int, Painter) -> Unit,
    onClick: (Painter?) -> Unit,
    hiddenSourceKey: String?,
    hiddenSourceRevealProgress: Float,
    modifier: Modifier = Modifier,
) {
    val sourceKey = remember(postId, index) { "post_modal_gallery_${postId}_$index" }
    val latestPainter = remember(sourceKey, initialPainter) { androidx.compose.runtime.mutableStateOf(initialPainter) }
    val isHidden = hiddenSourceKey == sourceKey
    val contentAlpha = if (isHidden) hiddenSourceRevealProgress.coerceIn(0f, 1f) else 1f
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { 20.dp.toPx() }
    var currentCoordinates by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }

    fun sendOrigin() {
        val coords = currentCoordinates?.takeIf { it.isAttached } ?: return
        val origin = coords.boundsInWindow().toPostGalleryOrigin(
            sourceKey = sourceKey,
            imageAspectRatio = item.aspectRatio,
            cornerRadiusPx = cornerRadiusPx,
        )
        onOriginChanged(index, origin)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF111111))
            .onGloballyPositioned { coordinates ->
                currentCoordinates = coordinates
                if (coordinates.isAttached) {
                    val origin = coordinates.boundsInWindow().toPostGalleryOrigin(
                        sourceKey = sourceKey,
                        imageAspectRatio = item.aspectRatio,
                        cornerRadiusPx = cornerRadiusPx,
                    )
                    onOriginChanged(index, origin)
                }
            }
            .clickable(enabled = !isHidden) {
                sendOrigin()
                onClick(latestPainter.value ?: initialPainter)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.alpha = contentAlpha
                }
        ) {
            val currentInitialPainter = initialPainter
            if (currentInitialPainter != null && latestPainter.value == null) {
                androidx.compose.foundation.Image(
                    painter = currentInitialPainter,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ProgressiveImage(
                thumbUrl = item.thumbUrl,
                targetUrl = item.targetUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onPainterChanged = { painter ->
                    if (painter != null) {
                        latestPainter.value = painter
                        onPainterReady(index, painter)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction.coerceIn(0f, 1f)
}

private fun Rect.toPostGalleryOrigin(
    sourceKey: String,
    imageAspectRatio: Float,
    cornerRadiusPx: Float,
): SharedImageOrigin {
    return SharedImageOrigin(
        sourceKey = sourceKey,
        rectInWindow = this,
        aspectRatio = imageAspectRatio.coerceAtLeast(0.01f),
        contentRectInWindow = this,
        cornerRadiusPx = cornerRadiusPx,
        sourceScaleMode = SourceImageScaleMode.Crop,
    )
}
