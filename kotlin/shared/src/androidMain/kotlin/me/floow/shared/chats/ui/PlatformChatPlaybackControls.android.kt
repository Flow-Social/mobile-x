package me.floow.shared.chats.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import flow.shared.generated.resources.Res
import flow.shared.generated.resources.chat_playback_stop
import flow.shared.generated.resources.chat_playback_video
import flow.shared.generated.resources.chat_playback_voice
import me.floow.shared.chats.playback.FlowChatPlaybackService
import me.floow.shared.media.voice.AndroidVoicePlayer
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun PlatformChatPlaybackControls(playback: ChatPlaybackSession) {
    val context = LocalContext.current.applicationContext
    val active = playback.target != null
    var service by remember { mutableStateOf<FlowChatPlaybackService?>(null) }
    DisposableEffect(context, active) {
        if (!active) return@DisposableEffect onDispose {}
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as? FlowChatPlaybackService.PlaybackBinder)?.service
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
            }
        }
        val intent = Intent(context, FlowChatPlaybackService::class.java).setAction(FlowChatPlaybackService.BindPlayback)
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            service?.detach()
            service = null
            if (bound) context.unbindService(connection)
        }
    }

    val isVideo = playback.isVideo
    val player = if (isVideo) playback.video.manager.player else (playback.voice as? AndroidVoicePlayer)?.nativePlayer
    val title = stringResource(if (isVideo) Res.string.chat_playback_video else Res.string.chat_playback_voice)
    val stopLabel = stringResource(Res.string.chat_playback_stop)
    DisposableEffect(service, player, active, title, stopLabel) {
        if (active && player != null) {
            service?.attach(player, title, stopLabel, playback::setPlaying, playback::stop)
        }
        onDispose { }
    }
}
