package me.floow.shared.chats.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chat_playback_close
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    val closeLabel = stringResource(Res.string.chat_playback_close)
    val currentTarget by rememberUpdatedState(target)
    val scope = rememberCoroutineScope()
    val xFraction = remember { mutableFloatStateOf(1f) }
    val yFraction = remember { mutableFloatStateOf(0.36f) }
    var slidingOut by remember(target.uiKey) { mutableStateOf(false) }
    val motion = remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val margin = 16.dp
        val top = 62.dp
        val diameter = minOf(108.dp, maxWidth - margin * 2, maxHeight - top - margin)
        if (diameter < 48.dp) return@BoxWithConstraints
        val density = LocalDensity.current
        val left = with(density) { margin.toPx() }
        val minY = with(density) { top.toPx() }
        val diameterPx = with(density) { diameter.toPx() }
        val rightEdge = with(density) { (maxWidth - diameter).toPx() }
        val right = rightEdge - left
        val travelX = (right - left).coerceAtLeast(1f)
        val travelY = with(density) { (maxHeight - diameter - top - margin).toPx() }.coerceAtLeast(0f)
        val snapRadius = with(density) { 20.dp.toPx() }
        val overscroll = diameterPx / 2f

        fun currentX() = left + travelX * xFraction.floatValue
        fun setX(x: Float) {
            xFraction.floatValue = (x - left) / travelX
        }
        fun settle(cancelled: Boolean = false) {
            val x = currentX()
            val closeLeft = !cancelled && x <= -diameterPx / 4f
            val closeRight = !cancelled && x >= rightEdge + diameterPx / 4f
            val closing = closeLeft || closeRight
            val destination = when {
                closeLeft -> -diameterPx
                closeRight -> rightEdge + diameterPx
                x <= left + snapRadius -> left
                x >= right - snapRadius -> right
                else -> x
            }
            if (x == destination) return
            val dismissedTarget = currentTarget
            motion.value?.cancel()
            slidingOut = closing
            motion.value = scope.launch {
                animate(x, destination, animationSpec = tween(170)) { value, _ -> setX(value) }
                if (closing) {
                    if (playback.target === dismissedTarget) playback.stop()
                    setX(if (closeLeft) left else right)
                    slidingOut = false
                }
            }
        }

        Box(
            modifier = Modifier
                .absoluteOffset {
                    IntOffset(currentX().roundToInt(), (minY + travelY * yFraction.floatValue).roundToInt())
                }
                .size(diameter)
                .graphicsLayer {
                    val x = currentX()
                    val outside = maxOf(0f, -x, x - rightEdge)
                    alpha = (1f - outside / diameterPx).coerceIn(0f, 1f)
                }
                .shadow(8.dp, CircleShape)
                .clickable(enabled = !slidingOut, onClick = { onOpenMessage(target) })
                .semantics {
                    customActions = listOf(CustomAccessibilityAction(closeLabel) {
                        playback.stop()
                        true
                    })
                }
                .pointerInput(left, right, travelY, diameterPx) {
                    detectDragGestures(
                        onDragStart = { if (!slidingOut) motion.value?.cancel() },
                        onDragEnd = { if (!slidingOut) settle() },
                        onDragCancel = { if (!slidingOut) settle(cancelled = true) },
                    ) { change, drag ->
                        if (!slidingOut) {
                            change.consume()
                            setX((currentX() + drag.x).coerceIn(-overscroll, rightEdge + overscroll))
                            if (travelY > 0f) yFraction.floatValue = (yFraction.floatValue + drag.y / travelY).coerceIn(0f, 1f)
                        }
                    }
                },
        ) {
            NativeVideoCirclePlayer(
                playback = playback.video,
                messageKey = target.uiKey,
                videoUrl = source,
                thumbnailUrl = circle.thumbnailPath ?: circle.remoteThumbnailUrl,
                playbackUiState = if (playback.videoPaused) VideoCirclePlaybackUiState.ActivePaused
                else VideoCirclePlaybackUiState.ActivePlaying,
                showPlaybackControls = false,
                progress = { 0f },
                onSeek = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
