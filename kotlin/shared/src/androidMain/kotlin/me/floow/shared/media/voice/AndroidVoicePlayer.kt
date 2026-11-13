package me.floow.shared.media.voice

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools
import java.io.File

@OptIn(UnstableApi::class)
class AndroidVoicePlayer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : VoicePlayer {
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var currentSource: String? = null

    private val _state = MutableStateFlow(VoicePlaybackState())
    override val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.update { it.copy(isBuffering = true) }
                }
                Player.STATE_ENDED -> {
                    stopProgressPolling()
                    _state.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            currentPositionMs = 0L,
                        )
                    }
                    exoPlayer?.seekTo(0L)
                    exoPlayer?.pause()
                }
                Player.STATE_READY -> {
                    val actualDuration = exoPlayer?.duration?.takeIf { it > 0L }
                    _state.update {
                        it.copy(
                            isBuffering = false,
                            durationMs = actualDuration ?: it.durationMs,
                        )
                    }
                }
                else -> {
                    _state.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startProgressPolling()
            } else {
                stopProgressPolling()
            }
        }
    }

    private fun resolveContext(): Context {
        return runCatching {
            KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<Context>()
        }.getOrNull()
            ?: runCatching { me.floow.database.AndroidDatabaseContext.get() }.getOrNull()
            ?: error("Context is not available for AndroidVoicePlayer")
    }

    private fun ensurePlayer(): ExoPlayer {
        val existing = exoPlayer
        if (existing != null) return existing

        val context = resolveContext().applicationContext
        val cacheProvider = VoiceMediaCacheProvider(context)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()
        val mediaSourceFactory = DefaultMediaSourceFactory(cacheProvider.dataSourceFactory())

        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                addListener(playerListener)
            }
        exoPlayer = newPlayer
        return newPlayer
    }

    override fun play(
        messageId: Long?,
        urlOrPath: String,
        uiKey: String?,
        durationMs: Long,
    ) {
        val isSameSource = currentSource == urlOrPath && _state.value.activeUiKey == uiKey
        if (isSameSource) {
            if (_state.value.isPlaying) {
                pause()
            } else {
                resume()
            }
            return
        }

        val player = runCatching { ensurePlayer() }.getOrNull()
        if (player == null) {
            _state.update { it.copy(isPlaying = false, isBuffering = false) }
            return
        }

        currentSource = urlOrPath
        val uri = if (urlOrPath.startsWith("/") || urlOrPath.startsWith("file://")) {
            val cleanPath = urlOrPath.removePrefix("file://")
            Uri.fromFile(File(cleanPath))
        } else {
            Uri.parse(urlOrPath)
        }

        val mediaItem = MediaItem.fromUri(uri)

        player.stop()
        player.setMediaItem(mediaItem)
        player.playbackParameters = PlaybackParameters(_state.value.speed)
        player.prepare()
        player.playWhenReady = true

        _state.update {
            it.copy(
                activeMessageId = messageId,
                activeUiKey = uiKey,
                isPlaying = true,
                isBuffering = true,
                currentPositionMs = 0L,
                durationMs = durationMs,
            )
        }
    }

    override fun pause() {
        exoPlayer?.pause()
        _state.update { it.copy(isPlaying = false) }
        stopProgressPolling()
    }

    override fun resume() {
        exoPlayer?.let { player ->
            player.play()
            _state.update { it.copy(isPlaying = true) }
            startProgressPolling()
        }
    }

    override fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _state.value.durationMs.coerceAtLeast(0L))
        player.seekTo(clamped)
        _state.update { it.copy(currentPositionMs = clamped) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
    }

    override fun toggleSpeed() {
        val currentSpeed = _state.value.speed
        val nextSpeed = when {
            currentSpeed < 1.25f -> 1.5f
            currentSpeed < 1.75f -> 2.0f
            else -> 1.0f
        }
        setSpeed(nextSpeed)
    }

    override fun stop() {
        stopProgressPolling()
        exoPlayer?.stop()
        _state.update {
            VoicePlaybackState(speed = it.speed)
        }
        currentSource = null
    }

    override fun release() {
        stop()
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (isActive) {
                delay(40)
                val player = exoPlayer ?: break
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.takeIf { it > 0L } ?: _state.value.durationMs
                _state.update {
                    it.copy(
                        currentPositionMs = pos,
                        durationMs = dur,
                    )
                }
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }
}

actual fun createVoicePlayer(): VoicePlayer = AndroidVoicePlayer()
