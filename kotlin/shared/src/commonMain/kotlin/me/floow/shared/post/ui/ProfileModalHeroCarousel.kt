package me.floow.shared.post.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.components.media.viewer2.SharedImageOrigin

@Composable
internal expect fun ProfileModalHeroCarousel(
    imageUrls: List<String>,
    imageVariants: List<PostImageVariant>,
    initialIndex: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    postId: String,
    onOriginChanged: (Int, SharedImageOrigin?) -> Unit,
    onPainterReady: (Int, Painter) -> Unit,
    onImageClick: (Int, Painter?) -> Unit,
    initialPainters: Map<Int, Painter>? = null,
    hiddenSourceKey: String? = null,
    hiddenSourceRevealProgress: Float = 0f,
)
