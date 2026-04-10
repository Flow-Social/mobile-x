package me.floow.shared.profile.ui.segments.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.chats_icon
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.uikit.components.buttons.BlurGlassButton
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.components.media.ProgressiveImageMode
import me.floow.uikit.components.media.transfer.PainterRef
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import org.jetbrains.compose.resources.painterResource

@Composable
fun ProfilePostCard(
    post: ProfilePostItem,
    onClick: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onLongClick: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var painter by remember(post.id, post.fullUrl, post.previewUrl, post.lqUrl) {
        mutableStateOf<Painter>(ColorPainter(Color(0xFF1A1A1A)))
    }
    var hasResolvedPainter by remember(post.id, post.fullUrl, post.previewUrl, post.lqUrl) {
        mutableStateOf(false)
    }
    val backgroundImageBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    Box(
        modifier = modifier
            .aspectRatio(136f / 153f)
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                onClick = {
                    onClick(
                        post.id,
                        buildProfilePostSnapshot(
                            post = post,
                            selectedPainter = painter.takeIf { hasResolvedPainter },
                            imageBounds = backgroundImageBounds.value
                        )
                    )
                },
                onLongClick = {
                    onLongClick(
                        post.id,
                        buildProfilePostSnapshot(
                            post = post,
                            selectedPainter = painter.takeIf { hasResolvedPainter },
                            imageBounds = backgroundImageBounds.value
                        )
                    )
                }
            )
    ) {
        ProgressiveImage(
            lqUrl = post.lqUrl,
            previewUrl = post.previewUrl,
            fullUrl = post.fullUrl,
            mode = ProgressiveImageMode.LIST,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onPainterChanged = { current ->
                if (current != null) {
                    painter = current
                    hasResolvedPainter = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    backgroundImageBounds.value = coordinates.boundsInWindow()
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            post.description?.let { description ->
                Text(
                    text = description,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatsGlassButton(
                painter = painter,
                backgroundBoundsInWindow = backgroundImageBounds.value,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = post.likesCount.toString()
            )

            StatsGlassButton(
                painter = painter,
                backgroundBoundsInWindow = backgroundImageBounds.value,
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.chats_icon),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                },
                text = post.commentsCount.toString()
            )
        }
    }
}

private fun buildProfilePostSnapshot(
    post: ProfilePostItem,
    selectedPainter: Painter?,
    imageBounds: androidx.compose.ui.geometry.Rect?
): PostMediaSourceSnapshot {
    val paintersByIndex = selectedPainter?.let { painter ->
        mapOf(0 to PainterRef(painter))
    }.orEmpty()
    val boundsByIndex = imageBounds?.let { bounds ->
        mapOf(0 to bounds)
    }.orEmpty()
    return PostMediaSourceSnapshot(
        postId = post.id,
        owner = PostMediaSourceOwner.PROFILE,
        selectedIndex = 0,
        urls = post.viewerUrls,
        previewUrls = post.previewUrls,
        paintersByIndex = paintersByIndex,
        boundsByIndex = boundsByIndex
    )
}

@Composable
private fun StatsGlassButton(
    painter: Painter,
    backgroundBoundsInWindow: androidx.compose.ui.geometry.Rect?,
    icon: @Composable () -> Unit,
    text: String
) {
    BlurGlassButton(
        painter = painter,
        showBackgroundImage = false,
        backgroundBoundsInWindow = backgroundBoundsInWindow,
        backgroundContentScale = ContentScale.Crop,
        shape = RoundedCornerShape(127.dp),
        containerColor = Color.White.copy(alpha = 0.3f),
        blurRadius = 2.dp,
        refractionScale = 1.2f
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            icon()
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
    }
}
