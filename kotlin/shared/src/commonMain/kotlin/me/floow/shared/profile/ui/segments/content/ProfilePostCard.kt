package me.floow.shared.profile.ui.segments.content

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.blur
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
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chats_icon
import me.floow.shared.profile.ui.model.ProfilePostItem
import me.floow.shared.profile.uilogic.ProfilePostPendingMutationType
import me.floow.shared.profile.uilogic.ProfilePostSyncState
import me.floow.uikit.components.media.ProgressiveImage
import me.floow.uikit.chat.components.media.MediaTransferOverlay
import me.floow.uikit.chat.model.MediaTransferActionIcon
import me.floow.uikit.chat.model.MediaTransferPhase
import me.floow.uikit.chat.model.MediaTransferUiState
import me.floow.uikit.components.buttons.BlurGlassButton
import me.floow.uikit.components.media.transfer.PainterRef
import me.floow.uikit.components.media.transfer.PostMediaSourceOwner
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import org.jetbrains.compose.resources.painterResource

@Composable
fun ProfilePostCard(
    post: ProfilePostItem,
    onClick: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onLongClick: (String, PostMediaSourceSnapshot?) -> Unit = { _, _ -> },
    onRetry: (String) -> Unit = {},
    onCancel: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var painter by remember {
        mutableStateOf<Painter>(ColorPainter(Color.Transparent))
    }
    var hasResolvedPainter by remember {
        mutableStateOf(false)
    }
    val backgroundImageBounds = remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    val isFailed = post.syncState == ProfilePostSyncState.FAILED || post.optimisticState == "FAILED"
    val isInteractiveCard = !post.isOptimistic || isFailed
    val isSendingPost = post.isSendingPost()
    Box(
        modifier = modifier
            .aspectRatio(136f / 153f)
            .clip(RoundedCornerShape(24.dp))
            .then(
                if (isInteractiveCard) {
                    Modifier.combinedClickable(
                        onClick = {
                            if (isFailed) {
                                onRetry(post.id)
                            } else {
                                onClick(
                                    post.id,
                                    buildProfilePostSnapshot(
                                        post = post,
                                        selectedPainter = painter.takeIf { hasResolvedPainter },
                                        imageBounds = backgroundImageBounds.value
                                    )
                                )
                            }
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
                } else {
                    Modifier
                }
            )
    ) {
        val thumbUrl = post.thumbUrl ?: post.lqUrl
        val targetUrl = post.targetUrl ?: post.previewUrl ?: post.fullUrl
        if (!thumbUrl.isNullOrBlank() || !targetUrl.isNullOrBlank()) {
            ProgressiveImage(
                thumbUrl = thumbUrl,
                targetUrl = targetUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (post.isOptimistic) Modifier.blur(8.dp) else Modifier)
                    .onGloballyPositioned { coordinates ->
                        backgroundImageBounds.value = coordinates.boundsInWindow()
                    },
                onPainterChanged = { p ->
                    if (p != null) {
                        painter = p
                        hasResolvedPainter = true
                    }
                }
            )
        }

        if (!post.isOptimistic) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
        }

        pendingMediaTransferState(post)?.let { transferState ->
            MediaTransferOverlay(
                state = transferState,
                shape = RoundedCornerShape(24.dp),
                controlSize = 40.dp,
                modifier = Modifier.fillMaxSize(),
                onRetry = if (transferState.canRetry) ({ onRetry(post.id) }) else null,
                onCancel = if (transferState.canCancel) ({ onCancel(post.id) }) else null,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (post.isOptimistic && !isSendingPost) {
                Text(
                    text = post.pendingBadgeText(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            if (!(post.isOptimistic && post.pendingMutationType == ProfilePostPendingMutationType.CREATE)) {
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
        }

        if (!isSendingPost) {
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
}

private fun ProfilePostItem.isSendingPost(): Boolean {
    return isOptimistic && when (syncState) {
        ProfilePostSyncState.PENDING,
        ProfilePostSyncState.UPLOADING,
        ProfilePostSyncState.SYNCING,
        ProfilePostSyncState.FAILED,
        null -> true
    }
}

private fun ProfilePostItem.pendingBadgeText(): String {
    return when (syncState) {
        ProfilePostSyncState.FAILED -> lastSyncError?.takeIf(String::isNotBlank) ?: "Не удалось отправить"
        ProfilePostSyncState.UPLOADING,
        ProfilePostSyncState.SYNCING,
        ProfilePostSyncState.PENDING,
        null -> when (pendingMutationType) {
            ProfilePostPendingMutationType.UPDATE -> "Сохранение..."
            ProfilePostPendingMutationType.CREATE,
            ProfilePostPendingMutationType.DELETE,
            null -> "Отправляется"
        }
    }
}

private fun pendingMediaTransferState(post: ProfilePostItem): MediaTransferUiState? {
    val syncState = post.syncState ?: return null
    return when (syncState) {
        ProfilePostSyncState.PENDING -> MediaTransferUiState(
            phase = MediaTransferPhase.Preparing,
            progress = null,
            actionIcon = MediaTransferActionIcon.Cancel,
            canRetry = false,
            canCancel = true,
            showDim = true,
            showBlur = true,
            visible = true,
        )
        ProfilePostSyncState.UPLOADING -> MediaTransferUiState(
            phase = MediaTransferPhase.Uploading,
            progress = null,
            actionIcon = MediaTransferActionIcon.Cancel,
            canRetry = false,
            canCancel = true,
            showDim = true,
            showBlur = true,
            visible = true,
        )
        ProfilePostSyncState.SYNCING -> null
        ProfilePostSyncState.FAILED -> MediaTransferUiState(
            phase = MediaTransferPhase.Failed,
            progress = null,
            actionIcon = MediaTransferActionIcon.Retry,
            canRetry = true,
            canCancel = false,
            showDim = true,
            showBlur = true,
            visible = true,
        )
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
