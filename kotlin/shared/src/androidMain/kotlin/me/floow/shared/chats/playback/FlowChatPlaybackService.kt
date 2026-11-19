package me.floow.shared.chats.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

@OptIn(UnstableApi::class)
class FlowChatPlaybackService : MediaSessionService() {
    internal inner class PlaybackBinder : Binder() {
        val service: FlowChatPlaybackService get() = this@FlowChatPlaybackService
    }

    private val binder = PlaybackBinder()
    private var session: MediaSession? = null
    private var onStopPlayback: (() -> Unit)? = null

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == BindPlayback) binder else super.onBind(intent)

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    internal fun attach(player: Player, title: String, stopLabel: String, onPlaying: (Boolean) -> Unit, onStop: () -> Unit) {
        onStopPlayback = onStop
        val controls = object : ForwardingPlayer(player) {
            override fun getMediaMetadata() = MediaMetadata.Builder().setTitle(title).build()
            override fun play() = onPlaying(true)
            override fun pause() = onPlaying(false)
            override fun setPlayWhenReady(playWhenReady: Boolean) = onPlaying(playWhenReady)
            override fun stop() = onStop()
        }
        val current = session
        if (current != null) {
            current.setPlayer(controls)
        } else {
            val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
                .setPlayerCommand(Player.COMMAND_STOP)
                .setDisplayName(stopLabel)
                .build()
            val builder = MediaSession.Builder(this, controls)
                .setMediaButtonPreferences(listOf(stopButton))
            packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                builder.setSessionActivity(
                    PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
            }
            session = builder.build().also(::addSession)
        }
        session?.let { onUpdateNotification(it, player.playWhenReady) }
    }

    internal fun detach() {
        onStopPlayback = null
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        onStopPlayback?.invoke()
        detach()
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    companion object {
        internal const val BindPlayback = "me.floow.app.BIND_CHAT_PLAYBACK"
    }
}
