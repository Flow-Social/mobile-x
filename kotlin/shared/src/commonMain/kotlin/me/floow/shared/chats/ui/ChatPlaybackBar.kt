package me.floow.shared.chats.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chat_playback_at
import flow.shared.generated.resources.chat_playback_close
import flow.shared.generated.resources.chat_playback_pause
import flow.shared.generated.resources.chat_playback_play
import flow.shared.generated.resources.chat_playback_speed
import flow.shared.generated.resources.chat_playback_video
import flow.shared.generated.resources.chat_playback_voice
import flow.shared.generated.resources.chat_playback_you
import me.floow.shared.chats.model.ChatMessageItemModel
import org.jetbrains.compose.resources.stringResource

internal data class ChatPlaybackBarItem(
    val messageId: Long,
    val label: String,
    val isVideo: Boolean,
    val isPlaying: Boolean,
    val speed: Float,
)

@Composable
internal fun ChatPlaybackBarOverlay(
    item: ChatPlaybackBarItem?,
    progress: (ChatPlaybackBarItem) -> Float,
    onPlayPause: (ChatPlaybackBarItem) -> Unit,
    onOpenMessage: (Long) -> Unit,
    onChangeSpeed: (ChatPlaybackBarItem) -> Unit,
    onClose: (ChatPlaybackBarItem) -> Unit,
) {
    AnimatedContent(
        targetState = item,
        contentKey = { it != null },
        contentAlignment = Alignment.TopCenter,
        transitionSpec = {
            ((fadeIn(tween(190)) + slideInVertically(tween(230)) { -it / 8 }) togetherWith
                (fadeOut(tween(150)) + slideOutVertically(tween(170)) { -it / 8 })).using(null)
        },
        label = "Chat playback bar",
    ) { current ->
        if (current != null) {
            ChatPlaybackBar(
                label = current.label,
                isPlaying = current.isPlaying,
                speed = current.speed,
                progress = { progress(current) },
                onPlayPause = { if (item != null) onPlayPause(current) },
                onOpenMessage = { if (item != null) onOpenMessage(current.messageId) },
                onChangeSpeed = { if (item != null) onChangeSpeed(current) },
                onClose = { if (item != null) onClose(current) },
            )
        }
    }
}

@Composable
internal fun chatPlaybackLabel(message: ChatMessageItemModel?, isVideo: Boolean): String {
    val author = if (message?.isOutgoing == true) {
        stringResource(Res.string.chat_playback_you)
    } else {
        message?.senderDisplayName?.takeIf(String::isNotBlank)
    }
    if (author != null && message != null) {
        return stringResource(Res.string.chat_playback_at, author, formatChatClockTime(message.createdAtMillis))
    }
    return author ?: stringResource(if (isVideo) Res.string.chat_playback_video else Res.string.chat_playback_voice)
}

@Composable
private fun ChatPlaybackBar(
    label: String,
    isPlaying: Boolean,
    speed: Float,
    progress: () -> Float,
    onPlayPause: () -> Unit,
    onOpenMessage: () -> Unit,
    onChangeSpeed: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val speedLabel = when {
        speed >= 1.75f -> "2×"
        speed >= 1.25f -> "1.5×"
        else -> "1×"
    }
    val speedDescription = stringResource(Res.string.chat_playback_speed, speedLabel)
    Box(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(CircleShape)
            .background(colors.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(
                        if (isPlaying) Res.string.chat_playback_pause else Res.string.chat_playback_play,
                    ),
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Box(
                modifier = Modifier.weight(1f).height(44.dp).clickable(onClick = onOpenMessage),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    label,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.size(48.dp, 44.dp)
                    .clickable(role = Role.Button, onClick = onChangeSpeed)
                    .semantics { contentDescription = speedDescription },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    speedLabel,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(colors.surfaceContainerHighest, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.chat_playback_close),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Canvas(
            Modifier.align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .height(2.dp),
        ) {
            val playedWidth = size.width * progress().coerceIn(0f, 1f)
            drawRect(colors.onSurfaceVariant.copy(alpha = 0.18f))
            if (playedWidth > 0f) {
                drawRect(colors.primary, size = Size(playedWidth, size.height))
            }
        }
    }
}
