package me.floow.shared.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chat_playback_close
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.media.ui.NativeVideoCirclePlayer
import me.floow.uikit.chat.model.VideoCirclePlaybackUiState
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun FloatingChatVideo(onOpenMessage: (ChatPlaybackTarget) -> Unit) {
    val playback = LocalChatPlayback.current
    val target = playback.target ?: return
    if (!playback.foreground || !playback.isVideo) return
    val circle = target.message.content as? ChatMessageContent.VideoCircle ?: return
    val source = circle.remoteUrl?.takeIf(String::isNotBlank) ?: circle.localPath ?: return

    val xFraction = remember { mutableFloatStateOf(1f) }
    val yFraction = remember { mutableFloatStateOf(0.36f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val margin = 16.dp
        val top = 62.dp
        val diameter = minOf(108.dp, maxWidth - margin * 2, maxHeight - top - margin)
        if (diameter < 48.dp) return@BoxWithConstraints
        val density = LocalDensity.current
        val minX = with(density) { margin.toPx() }
        val minY = with(density) { top.toPx() }
        val travelX = with(density) { (maxWidth - diameter - margin * 2).toPx() }.coerceAtLeast(0f)
        val travelY = with(density) { (maxHeight - diameter - top - margin).toPx() }.coerceAtLeast(0f)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (minX + travelX * xFraction.floatValue).roundToInt(),
                        (minY + travelY * yFraction.floatValue).roundToInt(),
                    )
                }
                .size(diameter)
                .pointerInput(travelX, travelY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        if (travelX > 0f) xFraction.floatValue = (xFraction.floatValue + drag.x / travelX).coerceIn(0f, 1f)
                        if (travelY > 0f) yFraction.floatValue = (yFraction.floatValue + drag.y / travelY).coerceIn(0f, 1f)
                    }
                },
        ) {
            Box(
                Modifier.fillMaxSize()
                    .shadow(8.dp, CircleShape)
                    .clickable(onClick = { onOpenMessage(target) }),
            ) {
                NativeVideoCirclePlayer(
                    playback = playback.video,
                    messageKey = target.uiKey,
                    videoUrl = source,
                    thumbnailUrl = circle.thumbnailPath ?: circle.remoteThumbnailUrl,
                    playbackUiState = if (playback.videoPaused) VideoCirclePlaybackUiState.ActivePaused
                    else VideoCirclePlaybackUiState.ActivePlaying,
                    progress = { playback.videoProgress },
                    onSeek = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = playback::stop,
                modifier = Modifier.align(Alignment.TopEnd).size(36.dp),
            ) {
                Box(
                    Modifier.size(26.dp).background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.chat_playback_close),
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
    }
}
