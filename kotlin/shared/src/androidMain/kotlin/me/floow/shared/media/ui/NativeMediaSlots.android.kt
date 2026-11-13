package me.floow.shared.media.ui

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil3.compose.AsyncImage
import me.floow.shared.chats.videocircle.CircleVideoSurface
import me.floow.shared.chats.videocircle.VideoCircleAudioFocusController
import me.floow.shared.chats.videocircle.VideoCircleCacheProvider
import me.floow.uikit.chat.components.videocircle.VideoCircleMotion
import me.floow.uikit.chat.components.videocircle.VideoCirclePlayingEqualizer
import me.floow.uikit.chat.components.videocircle.VideoCircleProgressOverlay
import me.floow.uikit.chat.components.videocircle.overshootInterpolation
import me.floow.uikit.chat.components.videocircle.videoCircleSeekGesture
import java.io.File

private val InactiveCircleSize = 228.dp
private val ActiveCircleSize = 252.dp

@Composable
actual fun NativeCameraRecordingPreview(
    isRecording: Boolean,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
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

@OptIn(UnstableApi::class)
@Composable
actual fun NativeVideoCirclePlayer(
    videoUrl: String,
    thumbnailUrl: String?,
    isPlaying: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier
) {
    val context = LocalContext.current
    var isSourceError by remember(videoUrl) { mutableStateOf(false) }

    val circleSize by animateDpAsState(
        targetValue = if (isPlaying) ActiveCircleSize else InactiveCircleSize,
        animationSpec = tween(180),
        label = "video_circle_size",
    )

    val player = remember(videoUrl, context, isPlaying) {
        if (!isPlaying || videoUrl.isBlank()) null
        else {
            val cacheProvider = VideoCircleCacheProvider(context)
            val mediaSourceFactory = DefaultMediaSourceFactory(cacheProvider.dataSourceFactory())
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_ALL
                    val uri = when {
                        videoUrl.startsWith("http://", ignoreCase = true) ||
                            videoUrl.startsWith("https://", ignoreCase = true) ||
                            videoUrl.startsWith("content://", ignoreCase = true) ||
                            videoUrl.startsWith("file://", ignoreCase = true) -> Uri.parse(videoUrl)
                        else -> Uri.fromFile(File(videoUrl))
                    }
                    setMediaItem(MediaItem.fromUri(uri))
                    prepare()
                }
        }
    }

    val audioFocus = remember(context) {
        VideoCircleAudioFocusController(
            context = context,
            onTransientLoss = { player?.pause() },
            onPermanentLoss = { player?.pause() },
        )
    }

    DisposableEffect(player, isPlaying) {
        if (player != null && isPlaying) {
            audioFocus.request()
            player.playWhenReady = true
        } else {
            player?.playWhenReady = false
            audioFocus.abandon()
        }
        onDispose {
            player?.playWhenReady = false
            audioFocus.abandon()
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.stop()
            player?.clearMediaItems()
            player?.release()
        }
    }

    val progress = remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    // Telegram: pause progress animates 220ms in, 150ms out
    val pauseProgress by animateFloatAsState(
        targetValue = if (!isPlaying) 1f else 0f,
        animationSpec = if (!isPlaying) {
            tween(durationMillis = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressInDurationMs, easing = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressInEasing)
        } else {
            tween(durationMillis = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressOutDurationMs, easing = me.floow.uikit.chat.components.videocircle.VideoCircleMotion.PauseProgressOutEasing)
        },
        label = "pause_progress",
    )
    val overshootPauseProgress = if (!isPlaying) {
        me.floow.uikit.chat.components.videocircle.overshootInterpolation(pauseProgress)
    } else {
        pauseProgress
    }

    LaunchedEffect(player, isPlaying, isSeeking) {
        val currentPlayer = player ?: return@LaunchedEffect
        while (true) {
            if (!isSeeking) {
                val duration = currentPlayer.duration.takeIf { it > 0L } ?: 0L
                val position = currentPlayer.currentPosition.coerceAtLeast(0L)
                progress.floatValue = if (duration > 0L) {
                    (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            }
            kotlinx.coroutines.delay(32)
        }
    }

    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Thumbnail placeholder
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

        // Hardware-accelerated ExoPlayer video playback via CircleVideoSurface
        if (player != null && !isSourceError) {
            CircleVideoSurface(
                player = player,
                onSourceError = { isSourceError = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Telegram-grade circular progress ring with pause overshoot knob
        VideoCircleProgressOverlay(
            visible = true,
            isPlaying = isPlaying,
            isPaused = !isPlaying,
            pauseProgress = pauseProgress,
            overshootPauseProgress = overshootPauseProgress,
            progress = progress.floatValue,
            progressColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
        )

        // Pulsing equalizer when playing
        VideoCirclePlayingEqualizer(
            visible = isPlaying,
        )

        // Telegram-grade circular seek & tap gesture overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onClick != null) {
                        Modifier.videoCircleSeekGesture(
                            isPaused = !isPlaying,
                            progress = progress.floatValue,
                            onSeekingChange = { isSeeking = it },
                            consumeTapUp = true,
                            onTogglePlayback = onClick,
                            onLongPress = {},
                            onSeekProgress = { nextProg: Float ->
                                player?.let { p ->
                                    val dur = p.duration.takeIf { it > 0L } ?: 0L
                                    if (dur > 0L) {
                                        p.seekTo((nextProg * dur).toLong())
                                    }
                                }
                            },
                        )
                    } else Modifier
                )
        )
    }
}
