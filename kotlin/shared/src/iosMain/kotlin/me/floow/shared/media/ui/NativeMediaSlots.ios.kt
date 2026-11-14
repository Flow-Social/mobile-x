package me.floow.shared.media.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.interop.UIKitView
import coil3.compose.AsyncImage
import me.floow.uikit.chat.components.videocircle.videoCircleSeekGesture
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectZero
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.NSURL
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIView
import platform.UIKit.UIColor
import platform.darwin.NSObject

@Composable
actual fun NativeCameraRecordingPreview(
    isRecording: Boolean,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse_ios")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha_ios"
    )

    val borderColor = if (isRecording) {
        Color(0xFFFF3B30).copy(alpha = borderAlpha)
    } else {
        Color.White.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFF1C1C1E))
            .border(3.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = "Camera Preview",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp)
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosVideoCircleContainerView : UIView(frame = platform.CoreGraphics.CGRectZero.readValue()) {
    var player: AVPlayer? = null
    var playerLayer: AVPlayerLayer? = null
    var endObserver: platform.darwin.NSObjectProtocol? = null

    init {
        backgroundColor = UIColor.blackColor
        clipsToBounds = true
    }

    fun attach(player: AVPlayer, playerItem: AVPlayerItem) {
        release()
        this.player = player
        player.actionAtItemEnd = platform.AVFoundation.AVPlayerActionAtItemEndNone

        endObserver = platform.Foundation.NSNotificationCenter.defaultCenter.addObserverForName(
            name = platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification,
            `object` = playerItem,
            queue = platform.Foundation.NSOperationQueue.mainQueue,
        ) { _ ->
            player.seekToTime(platform.CoreMedia.CMTimeMake(0, 1))
            player.play()
        }

        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        layer.videoGravity = platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
        this.playerLayer = layer
        this.layer.addSublayer(layer)
        setNeedsLayout()
    }

    fun release() {
        endObserver?.let {
            platform.Foundation.NSNotificationCenter.defaultCenter.removeObserver(it)
        }
        endObserver = null
        player?.pause()
        player = null
        playerLayer?.removeFromSuperlayer()
        playerLayer = null
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val currentBounds = bounds
        platform.QuartzCore.CATransaction.begin()
        platform.QuartzCore.CATransaction.setDisableActions(true)
        playerLayer?.frame = currentBounds
        val radius = currentBounds.useContents { size.width / 2.0 }
        layer.cornerRadius = radius
        layer.masksToBounds = true
        platform.QuartzCore.CATransaction.commit()
    }
}

private val InactiveCircleSize = 228.dp
private val ActiveCircleSize = 252.dp

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun NativeVideoCirclePlayer(
    videoUrl: String,
    thumbnailUrl: String?,
    playbackUiState: me.floow.uikit.chat.model.VideoCirclePlaybackUiState,
    onClick: (() -> Unit)?,
    onSeek: ((Float) -> Unit)?,
    onPlaybackEnded: (() -> Unit)?,
    modifier: Modifier
) {
    val progress = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var activePlayer by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<AVPlayer?>(null) }

    val isPlaying = playbackUiState.isPlaying
    val isPaused = playbackUiState.isPaused
    val isProgressVisible = playbackUiState.isProgressVisible

    val pauseProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPaused) 1f else 0f,
        animationSpec = if (isPaused) {
            tween(
                durationMillis = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressInDurationMs,
                easing = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressInEasing
            )
        } else {
            tween(
                durationMillis = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressOutDurationMs,
                easing = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressOutEasing
            )
        },
        label = "pause_progress_ios",
    )
    val overshootPauseProgress = if (isPaused) {
        me.floow.uikit.chat.components.videocircle.overshootInterpolation(pauseProgress)
    } else {
        pauseProgress
    }

    androidx.compose.runtime.LaunchedEffect(activePlayer, isPlaying) {
        val player = activePlayer ?: return@LaunchedEffect
        while (isPlaying) {
            val currentItem = player.currentItem
            if (currentItem != null) {
                val duration = runCatching { platform.CoreMedia.CMTimeGetSeconds(currentItem.duration) }.getOrDefault(0.0)
                val current = runCatching { platform.CoreMedia.CMTimeGetSeconds(player.currentTime()) }.getOrDefault(0.0)
                if (!duration.isNaN() && !duration.isInfinite() && duration > 0.0 &&
                    !current.isNaN() && !current.isInfinite() && current >= 0.0) {
                    progress.floatValue = (current / duration).coerceIn(0.0, 1.0).toFloat()
                }
            }
            kotlinx.coroutines.delay(32)
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            val isMicro = remember(thumbnailUrl) { thumbnailUrl.startsWith("data:image/") }
            val thumbMod = if (isMicro) {
                Modifier.fillMaxSize().blur(16.dp)
            } else {
                Modifier.fillMaxSize()
            }
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Video Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = thumbMod
            )
        }

        if ((isPlaying || isPaused) && videoUrl.isNotBlank()) {
            UIKitView(
                factory = {
                    val container = IosVideoCircleContainerView()

                    val cleanPath = if (videoUrl.startsWith("file://")) {
                        videoUrl.removePrefix("file://")
                    } else {
                        videoUrl
                    }

                    val url = if (cleanPath.startsWith("/")) {
                        NSURL.fileURLWithPath(cleanPath)
                    } else {
                        NSURL.URLWithString(videoUrl)
                    }

                    if (url != null) {
                        try {
                            val audioSession = platform.AVFAudio.AVAudioSession.sharedInstance()
                            audioSession.setCategory(platform.AVFAudio.AVAudioSessionCategoryPlayback, error = null)
                        } catch (_: Throwable) {}

                        val playerItem = AVPlayerItem(uRL = url)
                        val player = AVPlayer(playerItem = playerItem)
                        container.attach(player, playerItem)

                        activePlayer = player
                        if (isPlaying) {
                            player.play()
                        }
                    }
                    container
                },
                update = { container ->
                    if (isPlaying) {
                        container.player?.play()
                    } else {
                        container.player?.pause()
                    }
                },
                onRelease = { container ->
                    container.release()
                    activePlayer = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Telegram-grade circular progress ring
        me.floow.uikit.chat.components.videocircle.VideoCircleProgressOverlay(
            visible = isProgressVisible,
            isPlaying = isPlaying,
            isPaused = isPaused,
            pauseProgress = pauseProgress,
            overshootPauseProgress = overshootPauseProgress,
            progress = progress.floatValue,
            progressColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )

        // Pulsing equalizer when playing
        me.floow.uikit.chat.components.videocircle.VideoCirclePlayingEqualizer(
            visible = isPlaying,
        )

        // Telegram-grade circular seek & tap gesture overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onClick != null) {
                        Modifier.videoCircleSeekGesture(
                            isProgressVisible = isProgressVisible,
                            isPaused = isPaused,
                            progress = progress.floatValue,
                            onSeekingChange = {},
                            consumeTapUp = true,
                            onTogglePlayback = onClick,
                            onLongPress = {},
                            onSeekProgress = { nextProg ->
                                activePlayer?.let { p ->
                                    val currentItem = p.currentItem ?: return@let
                                    val duration = runCatching { platform.CoreMedia.CMTimeGetSeconds(currentItem.duration) }.getOrDefault(0.0)
                                    if (duration > 0.0) {
                                        val targetSeconds = duration * nextProg.toDouble()
                                        val targetTime = platform.CoreMedia.CMTimeMakeWithSeconds(targetSeconds, 1000)
                                        p.seekToTime(targetTime)
                                    }
                                }
                                onSeek?.invoke(nextProg)
                            },
                        )
                    } else Modifier
                )
        )
    }
}
