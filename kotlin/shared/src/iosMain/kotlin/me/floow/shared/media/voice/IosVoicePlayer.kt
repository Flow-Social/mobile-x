package me.floow.shared.media.voice

import kotlinx.cinterop.ExperimentalForeignApi
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
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
class IosVoicePlayer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) : VoicePlayer {
    private var avPlayer: AVPlayer? = null
    private var progressJob: Job? = null
    private var currentSource: String? = null
    private var endObserver: platform.darwin.NSObjectProtocol? = null

    private val _state = MutableStateFlow(VoicePlaybackState())
    override val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

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

        stop()
        currentSource = urlOrPath

        _state.update {
            it.copy(
                activeMessageId = messageId,
                activeUiKey = uiKey,
                isBuffering = true,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = durationMs,
            )
        }

        try {
            val url = if (urlOrPath.startsWith("/") || urlOrPath.startsWith("file://")) {
                val cleanPath = urlOrPath.removePrefix("file://")
                NSURL.fileURLWithPath(cleanPath)
            } else {
                NSURL.URLWithString(urlOrPath)
            }

            if (url == null) {
                _state.update { it.copy(isBuffering = false, isPlaying = false) }
                return
            }

            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                category = AVAudioSessionCategoryPlayback,
                withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
                error = null,
            )

            val playerItem = AVPlayerItem(uRL = url)
            endObserver = platform.Foundation.NSNotificationCenter.defaultCenter.addObserverForName(
                name = platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification,
                `object` = playerItem,
                queue = platform.Foundation.NSOperationQueue.mainQueue,
            ) { _ ->
                _state.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                stop()
            }

            val player = AVPlayer(playerItem = playerItem)
            avPlayer = player

            player.play()
            if (_state.value.speed != 1.0f) {
                player.rate = _state.value.speed
            }

            _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = true,
                    durationMs = durationMs,
                )
            }
            startProgressPolling()
        } catch (_: Exception) {
            _state.update { it.copy(isBuffering = false, isPlaying = false) }
        }
    }

    override fun pause() {
        avPlayer?.pause()
        _state.update { it.copy(isPlaying = false) }
        stopProgressPolling()
    }

    override fun resume() {
        avPlayer?.let { player ->
            player.play()
            if (_state.value.speed != 1.0f) {
                player.rate = _state.value.speed
            }
            _state.update { it.copy(isPlaying = true) }
            startProgressPolling()
        }
    }

    override fun seekTo(positionMs: Long) {
        val player = avPlayer ?: return
        val maxDur = _state.value.durationMs.coerceAtLeast(0L)
        val clampedPos = positionMs.coerceIn(0L, maxDur)
        val clampedSec = clampedPos.toDouble() / 1000.0
        if (!clampedSec.isNaN() && !clampedSec.isInfinite() && clampedSec >= 0.0) {
            val targetTime = CMTimeMakeWithSeconds(clampedSec, preferredTimescale = 1000)
            player.seekToTime(targetTime)
        }
        _state.update { it.copy(currentPositionMs = clampedPos) }
    }

    override fun setSpeed(speed: Float) {
        _state.update { it.copy(speed = speed) }
        avPlayer?.let { player ->
            if (_state.value.isPlaying) {
                player.rate = speed
            }
        }
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
        endObserver?.let {
            platform.Foundation.NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        endObserver = null
        avPlayer?.pause()
        avPlayer = null
        currentSource = null
        _state.update {
            VoicePlaybackState(speed = it.speed)
        }
    }

    override fun release() {
        stop()
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = scope.launch {
            while (isActive) {
                delay(50)
                val player = avPlayer ?: break
                val currentItem = player.currentItem
                val rawCurrentSec = runCatching { CMTimeGetSeconds(player.currentTime()) }.getOrDefault(0.0)
                val currentSec = if (rawCurrentSec.isNaN() || rawCurrentSec.isInfinite() || rawCurrentSec < 0.0) 0.0 else rawCurrentSec

                val rawDurationSec = currentItem?.duration?.let { runCatching { CMTimeGetSeconds(it) }.getOrNull() } ?: 0.0
                val durationSec = if (rawDurationSec.isNaN() || rawDurationSec.isInfinite() || rawDurationSec <= 0.0) 0.0 else rawDurationSec

                val isPlaying = player.timeControlStatus == AVPlayerTimeControlStatusPlaying
                val posMs = (currentSec * 1000.0).toLong().coerceAtLeast(0L)
                val durMs = if (durationSec > 0.0) {
                    (durationSec * 1000.0).toLong()
                } else {
                    _state.value.durationMs
                }

                _state.update {
                    it.copy(
                        isPlaying = isPlaying,
                        currentPositionMs = posMs,
                        durationMs = durMs,
                        isBuffering = currentItem?.status != AVPlayerItemStatusReadyToPlay && isPlaying,
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

actual fun createVoicePlayer(): VoicePlayer = IosVoicePlayer()
