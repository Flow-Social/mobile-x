package me.floow.shared.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import me.floow.shared.chats.videocircle.VideoCircleAudioFocusController
import me.floow.shared.chats.videocircle.VideoCircleCacheProvider
import me.floow.shared.chats.videocircle.VideoCirclePlaybackRequest
import me.floow.shared.chats.videocircle.VideoCirclePlayerManager

internal actual class NativeVideoCirclePlayback(internal val manager: VideoCirclePlayerManager) {
    actual fun play(uiKey: String, messageId: Long, source: String, speed: Float) {
        manager.play(VideoCirclePlaybackRequest(uiKey, messageId, source, muted = false, userActivated = true, speed = speed))
    }

    actual fun pause() = manager.pause()
    actual fun stop() = manager.clear()
    actual fun seekToProgress(progress: Float) = manager.seekToProgress(progress)

    actual fun progress(): Float {
        val player = manager.player ?: return 0f
        val duration = player.duration.takeIf { it > 0L } ?: return 0f
        return (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }
}

@Composable
internal actual fun rememberNativeVideoCirclePlayback(
    onPlaybackEnded: (String) -> Unit,
    onPlaybackPaused: (String) -> Unit,
): NativeVideoCirclePlayback {
    val context = LocalContext.current.applicationContext
    val onEnded = rememberUpdatedState(onPlaybackEnded)
    val onPaused = rememberUpdatedState(onPlaybackPaused)
    val playback = remember(context) {
        lateinit var manager: VideoCirclePlayerManager
        val pauseForAudioFocus: () -> Unit = {
            val key = manager.state.value.activeUiKey
            manager.pause()
            key?.let { onPaused.value(it) }
        }
        val audioFocus = VideoCircleAudioFocusController(context, pauseForAudioFocus, pauseForAudioFocus)
        manager = VideoCirclePlayerManager(context, VideoCircleCacheProvider(context), audioFocus)
        NativeVideoCirclePlayback(manager)
    }
    DisposableEffect(playback) {
        playback.manager.onPlaybackEnded = { onEnded.value(it) }
        onDispose {
            playback.manager.onPlaybackEnded = null
            playback.manager.release()
        }
    }
    return playback
}
