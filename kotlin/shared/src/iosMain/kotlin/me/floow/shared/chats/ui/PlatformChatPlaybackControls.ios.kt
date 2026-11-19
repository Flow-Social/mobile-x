package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.structuralEqualityPolicy
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chat_playback_video
import flow.shared.generated.resources.chat_playback_voice
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import me.floow.shared.chats.model.ChatMessageContent
import org.jetbrains.compose.resources.stringResource
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.MediaPlayer.*

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformChatPlaybackControls(playback: ChatPlaybackSession) {
    val target = playback.target
    val isVideo = playback.isVideo
    val voice = playback.voice.state.collectAsState()
    val voiceStatus by remember(voice) {
        derivedStateOf(structuralEqualityPolicy()) {
            voice.value.copy(currentPositionMs = 0L)
        }
    }
    val active = target != null && (isVideo || voiceStatus.activeUiKey == target.uiKey)
    if (!active) return

    val scope = rememberCoroutineScope()
    val commands = MPRemoteCommandCenter.sharedCommandCenter()
    val nowPlaying = MPNowPlayingInfoCenter.defaultCenter()
    DisposableEffect(playback, commands) {
        val handlers = listOf(
            commands.playCommand to { playback.setPlaying(true) },
            commands.pauseCommand to { playback.setPlaying(false) },
            commands.togglePlayPauseCommand to playback::toggle,
            commands.stopCommand to playback::stop,
        ).map { (command, action) ->
            command.enabled = true
            command to command.addTargetWithHandler {
                scope.launch { action() }
                MPRemoteCommandHandlerStatusSuccess
            }
        }
        val notifications = NSNotificationCenter.defaultCenter
        val interruption = notifications.addObserverForName(
            AVAudioSessionInterruptionNotification, AVAudioSession.sharedInstance(), NSOperationQueue.mainQueue,
        ) { notification ->
            val type = notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber
            if (type?.unsignedIntegerValue == AVAudioSessionInterruptionTypeBegan) playback.setPlaying(false)
        }
        onDispose {
            handlers.forEach { (command, token) ->
                command.removeTarget(token)
                command.enabled = false
            }
            notifications.removeObserver(interruption)
            nowPlaying.nowPlayingInfo = null
        }
    }

    val title = stringResource(if (isVideo) Res.string.chat_playback_video else Res.string.chat_playback_voice)
    val durationMs = if (isVideo) {
        playback.video.durationMillis().takeIf { it > 0 }
            ?: (target?.message?.content as? ChatMessageContent.VideoCircle)?.durationMs ?: 0L
    } else voiceStatus.durationMs
    val rate = if (isVideo) {
        if (playback.videoPaused) 0f else playback.videoSpeed
    } else if (voiceStatus.isPlaying) voiceStatus.speed else 0f
    SideEffect {
        val positionMs = if (isVideo) (playback.video.progress() * durationMs).toLong()
        else voice.value.currentPositionMs
        nowPlaying.nowPlayingInfo = mapOf(
            MPMediaItemPropertyTitle to title,
            MPMediaItemPropertyPlaybackDuration to (durationMs / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to (positionMs / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to rate.toDouble(),
        )
    }
}
