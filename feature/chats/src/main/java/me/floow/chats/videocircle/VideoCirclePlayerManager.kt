package me.floow.chats.videocircle

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

internal interface VideoCirclePlaybackDriver {
    val state: StateFlow<VideoCirclePlaybackState>
    val player: ExoPlayer?

    fun play(request: VideoCirclePlaybackRequest)
    fun preload(source: String)
    fun seekToProgress(progress: Float)
    fun pause()
    fun clear()
    fun release()
}

internal class VideoCirclePlayerManager(
    context: Context,
    cacheProvider: VideoCircleCacheProvider,
) : VideoCirclePlaybackDriver {

    private val appContext = context.applicationContext
    private val mediaSourceFactory = DefaultMediaSourceFactory(cacheProvider.dataSourceFactory())
    private val _state = MutableStateFlow(VideoCirclePlaybackState())

    private var activePlayer: ExoPlayer? = null
    private var preloadPlayer: ExoPlayer? = null
    private var activePreparedSource: String? = null
    private var preloadPreparedSource: String? = null

    override val state: StateFlow<VideoCirclePlaybackState> = _state.asStateFlow()
    override val player: ExoPlayer? get() = activePlayer

    override fun play(request: VideoCirclePlaybackRequest) {
        val player = ensureActivePlayer()
        val sourceChanged = activePreparedSource != request.source
        if (sourceChanged) {
            player.setMediaItem(MediaItem.fromUri(request.source.toMediaUri()))
            player.prepare()
            activePreparedSource = request.source
        }

        player.repeatMode = if (request.userActivated) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        player.volume = if (request.muted) 0f else 1f
        setPlaybackRateIfAvailable(player, if (request.muted) MutedAutoplaySpeed else 1f)
        player.playWhenReady = true

        _state.value = VideoCirclePlaybackState(
            activeUiKey = request.uiKey,
            activeMessageId = request.messageId,
            activeSource = request.source,
            isMuted = request.muted,
            isUserActivated = request.userActivated,
            status = VideoCirclePlaybackStatus.Playing,
            positionMs = player.currentPosition.coerceAtLeast(0L),
        )
    }

    override fun preload(source: String) {
        if (source == activePreparedSource || source == preloadPreparedSource) return
        val player = ensurePreloadPlayer()
        player.volume = 0f
        player.repeatMode = Player.REPEAT_MODE_ALL
        player.setMediaItem(MediaItem.fromUri(source.toMediaUri()))
        player.prepare()
        player.playWhenReady = false
        preloadPreparedSource = source
    }

    override fun seekToProgress(progress: Float) {
        val player = activePlayer ?: return
        val duration = player.duration.takeIf { it > 0L } ?: return
        val coercedProgress = progress.coerceIn(0f, 1f)
        val positionMs = (duration * coercedProgress).toLong().coerceIn(0L, duration)
        player.seekTo(positionMs)
        _state.update { current ->
            if (!current.canSeek()) {
                current
            } else {
                current.copy(positionMs = positionMs)
            }
        }
    }

    override fun pause() {
        activePlayer?.playWhenReady = false
        _state.update {
            it.copy(
                status = if (it.activeUiKey == null) VideoCirclePlaybackStatus.Idle else VideoCirclePlaybackStatus.Paused,
                positionMs = activePlayer?.currentPosition?.coerceAtLeast(0L) ?: it.positionMs,
            )
        }
    }

    override fun clear() {
        activePlayer?.playWhenReady = false
        _state.value = VideoCirclePlaybackState()
    }

    override fun release() {
        activePlayer?.release()
        preloadPlayer?.release()
        activePlayer = null
        preloadPlayer = null
        activePreparedSource = null
        preloadPreparedSource = null
        _state.value = VideoCirclePlaybackState()
    }

    private fun ensureActivePlayer(): ExoPlayer {
        activePlayer?.let { return it }
        return createPlayer().also { activePlayer = it }
    }

    private fun ensurePreloadPlayer(): ExoPlayer {
        preloadPlayer?.let { return it }
        return createPlayer().also { preloadPlayer = it }
    }

    private fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ALL
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (this@apply !== activePlayer) return
                        _state.update { current ->
                            current.copy(
                                status = when (playbackState) {
                                    Player.STATE_BUFFERING -> {
                                        if (playWhenReady) {
                                            VideoCirclePlaybackStatus.Buffering
                                        } else if (current.activeUiKey == null) {
                                            VideoCirclePlaybackStatus.Idle
                                        } else {
                                            VideoCirclePlaybackStatus.Paused
                                        }
                                    }
                                    Player.STATE_READY -> {
                                        if (playWhenReady) VideoCirclePlaybackStatus.Playing else VideoCirclePlaybackStatus.Paused
                                    }
                                    Player.STATE_IDLE -> VideoCirclePlaybackStatus.Idle
                                    Player.STATE_ENDED -> {
                                        seekTo(0)
                                        playWhenReady = false
                                        return@update VideoCirclePlaybackState()
                                    }
                                    else -> current.status
                                },
                                positionMs = currentPosition.coerceAtLeast(0L),
                            )
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (this@apply !== activePlayer) return
                        _state.update {
                            it.copy(
                                status = VideoCirclePlaybackStatus.Error,
                                positionMs = currentPosition.coerceAtLeast(0L),
                            )
                        }
                    }
                })
            }
    }

    private fun String.toMediaUri(): Uri {
        return when {
            startsWith("http://", ignoreCase = true) ||
                startsWith("https://", ignoreCase = true) ||
                startsWith("content://", ignoreCase = true) ||
                startsWith("file://", ignoreCase = true) -> Uri.parse(this)
            else -> Uri.fromFile(File(this))
        }
    }

    private fun setPlaybackRateIfAvailable(player: ExoPlayer, rate: Float) {
        runCatching {
            player.setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF)
            player.setPlaybackSpeed(rate)
        }
    }

    private companion object {
        const val MutedAutoplaySpeed = 0.85f
    }
}

private fun VideoCirclePlaybackState.canSeek(): Boolean {
    return activeUiKey != null && when (status) {
        VideoCirclePlaybackStatus.Buffering,
        VideoCirclePlaybackStatus.Playing,
        VideoCirclePlaybackStatus.Paused -> true
        VideoCirclePlaybackStatus.Idle,
        VideoCirclePlaybackStatus.Error -> false
    }
}
