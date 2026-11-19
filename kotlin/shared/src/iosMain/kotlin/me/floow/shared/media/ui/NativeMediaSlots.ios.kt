package me.floow.shared.media.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import coil3.compose.AsyncImage
import me.floow.uikit.components.media.FlowImageSource
import me.floow.uikit.components.media.ProgressiveImage
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

    init {
        backgroundColor = UIColor.blackColor
        clipsToBounds = true
    }

    fun attach(player: AVPlayer) {
        if (this.player === player) return
        release()
        this.player = player
        val layer = AVPlayerLayer.playerLayerWithPlayer(player)
        layer.videoGravity = platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
        this.playerLayer = layer
        this.layer.addSublayer(layer)
        setNeedsLayout()
    }

    fun release() {
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

@OptIn(ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal actual fun NativeVideoCirclePlayer(
    playback: NativeVideoCirclePlayback,
    messageKey: String,
    videoUrl: String,
    thumbnailUrl: String?,
    playbackUiState: me.floow.uikit.chat.model.VideoCirclePlaybackUiState,
    showMenuPreview: Boolean,
    onClick: (() -> Unit)?,
    progress: () -> Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier
) {
    val isPlaying = playbackUiState.isPlaying
    val isPaused = playbackUiState.isPaused
    val isProgressVisible = playbackUiState.isProgressVisible
    val playbackProgress = if (isProgressVisible) progress() else 0f
    val player = if (isProgressVisible) playback.playerFor(messageKey) else null

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

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            ProgressiveImage(
                source = FlowImageSource.of(url = null, microThumb = thumbnailUrl),
                contentDescription = "Video Thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (player != null) {
            UIKitView(
                factory = { IosVideoCircleContainerView() },
                update = { container ->
                    container.attach(player)
                    container.alpha = if (showMenuPreview) 0.0 else 1.0
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { it.release() },
                properties = UIKitInteropProperties(interactionMode = null),
            )
        }

        // Telegram-grade circular progress ring
        me.floow.uikit.chat.components.videocircle.VideoCircleProgressOverlay(
            visible = isProgressVisible,
            isPlaying = isPlaying,
            isPaused = isPaused,
            pauseProgress = pauseProgress,
            overshootPauseProgress = overshootPauseProgress,
            progress = playbackProgress,
            progressColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )

        // Pulsing equalizer when playing
        me.floow.uikit.chat.components.videocircle.VideoCirclePlayingEqualizer(
            visible = isPlaying,
        )

        // Telegram-grade circular seek & tap gesture overlay
        if (isProgressVisible && onClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoCircleSeekGesture(
                        isProgressVisible = isProgressVisible,
                        isPaused = isPaused,
                        progress = playbackProgress,
                        onSeekingChange = {},
                        onSeekProgress = onSeek,
                    )
            )
        }
    }
}
