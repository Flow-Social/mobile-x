package me.floow.shared.media.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.darwin.NSObjectProtocol

@OptIn(ExperimentalForeignApi::class)
internal actual class NativeVideoCirclePlayback(private val onPlaybackEnded: (String) -> Unit) {
    private var activeKey by mutableStateOf<String?>(null)
    private var player by mutableStateOf<AVPlayer?>(null)
    private var activeSource: String? = null
    private var observers: List<NSObjectProtocol> = emptyList()

    fun playerFor(uiKey: String): AVPlayer? = if (activeKey == uiKey) player else null

    actual fun play(uiKey: String, messageId: Long, source: String, speed: Float) {
        val preparedPlayer = player?.takeIf { activeKey == uiKey && activeSource == source }
        if (preparedPlayer != null) {
            preparedPlayer.rate = speed
            return
        }
        stop()
        val url = if (source.startsWith("/") || source.startsWith("file://")) {
            NSURL.fileURLWithPath(source.removePrefix("file://"))
        } else NSURL.URLWithString(source)
        if (url == null) {
            onPlaybackEnded(uiKey)
            return
        }
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        val item = AVPlayerItem(uRL = url)
        val newPlayer = AVPlayer(playerItem = item)
        player = newPlayer
        activeKey = uiKey
        activeSource = source
        observers = listOf(
            AVPlayerItemDidPlayToEndTimeNotification,
            AVPlayerItemFailedToPlayToEndTimeNotification,
        ).map { name ->
            NSNotificationCenter.defaultCenter.addObserverForName(name, item, NSOperationQueue.mainQueue) {
                if (player === newPlayer) onPlaybackEnded(uiKey)
            }
        }
        newPlayer.rate = speed
    }

    actual fun pause() {
        player?.pause()
    }

    actual fun stop() {
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observers = emptyList()
        player?.pause()
        player = null
        activeKey = null
        activeSource = null
    }

    actual fun seekToProgress(progress: Float) {
        val player = player ?: return
        val duration = duration(player) ?: return
        player.seekToTime(CMTimeMakeWithSeconds(duration * progress.coerceIn(0f, 1f), 1000))
    }

    actual fun progress(): Float {
        val player = player ?: return 0f
        val duration = duration(player) ?: return 0f
        val position = CMTimeGetSeconds(player.currentTime())
        return if (position.isFinite()) (position / duration).toFloat().coerceIn(0f, 1f) else 0f
    }

    private fun duration(player: AVPlayer): Double? =
        player.currentItem?.let { CMTimeGetSeconds(it.duration) }?.takeIf { it.isFinite() && it > 0.0 }
}

@Composable
internal actual fun rememberNativeVideoCirclePlayback(
    onPlaybackEnded: (String) -> Unit,
    onPlaybackPaused: (String) -> Unit,
): NativeVideoCirclePlayback {
    val onEnded = rememberUpdatedState(onPlaybackEnded)
    val playback = remember { NativeVideoCirclePlayback { onEnded.value(it) } }
    DisposableEffect(playback) {
        onDispose { playback.stop() }
    }
    return playback
}
