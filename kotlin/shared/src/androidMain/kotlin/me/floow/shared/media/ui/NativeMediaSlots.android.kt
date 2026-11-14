package me.floow.shared.media.ui

import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import me.floow.uikit.components.media.FlowImageSource
import me.floow.uikit.components.media.ProgressiveImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.shared.chats.videocircle.CircleVideoSurface
import me.floow.shared.chats.videocircle.VideoCirclePlaybackRequest
import me.floow.shared.chats.videocircle.VideoCirclePlaybackStatus
import me.floow.shared.chats.videocircle.VideoCirclePlayerProvider
import me.floow.uikit.chat.components.videocircle.VideoCircleMotion
import me.floow.uikit.chat.components.videocircle.VideoCirclePlayingEqualizer
import me.floow.uikit.chat.components.videocircle.VideoCircleProgressOverlay
import me.floow.uikit.chat.components.videocircle.cachedThumbnail
import me.floow.uikit.chat.components.videocircle.overshootInterpolation
import me.floow.uikit.chat.components.videocircle.storeCachedThumbnail
import me.floow.uikit.chat.components.videocircle.videoCircleSeekGesture
import me.floow.uikit.chat.model.VideoCirclePlaybackUiState

@Composable
actual fun NativeCameraRecordingPreview(
    isRecording: Boolean,
    modifier: Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "border_alpha",
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
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = "Camera Preview",
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
actual fun NativeVideoCirclePlayer(
    videoUrl: String,
    thumbnailUrl: String?,
    playbackUiState: VideoCirclePlaybackUiState,
    onClick: (() -> Unit)?,
    onSeek: ((Float) -> Unit)?,
    onPlaybackEnded: (() -> Unit)?,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var isSourceError by remember(videoUrl) { mutableStateOf(false) }

    val manager = remember(context) { VideoCirclePlayerProvider.get(context) }
    val managerState by manager.state.collectAsState()

    DisposableEffect(manager, onPlaybackEnded, videoUrl) {
        val previousCallback = manager.onPlaybackEnded
        manager.onPlaybackEnded = { endedKey ->
            if (endedKey == videoUrl) {
                onPlaybackEnded?.invoke()
            }
            previousCallback?.invoke(endedKey)
        }
        onDispose {
            if (manager.onPlaybackEnded == previousCallback) {
                manager.onPlaybackEnded = null
            }
        }
    }

    var isFirstFrameRendered by remember(videoUrl) { mutableStateOf(false) }

    LaunchedEffect(playbackUiState, videoUrl) {
        when (playbackUiState) {
            VideoCirclePlaybackUiState.ActivePlaying -> {
                if (videoUrl.isNotBlank()) {
                    manager.play(
                        VideoCirclePlaybackRequest(
                            uiKey = videoUrl,
                            messageId = 0L,
                            source = videoUrl,
                            muted = false,
                            userActivated = true,
                        )
                    )
                }
            }
            VideoCirclePlaybackUiState.ActivePaused -> {
                if (managerState.activeSource == videoUrl && managerState.status == VideoCirclePlaybackStatus.Playing) {
                    manager.pause()
                }
            }
            VideoCirclePlaybackUiState.IdlePreview,
            VideoCirclePlaybackUiState.Uploading -> {
                isFirstFrameRendered = false
                if (managerState.activeSource == videoUrl && managerState.isUserActivated) {
                    manager.pause()
                }
            }
        }
    }

    val isCurrentActive = (playbackUiState == VideoCirclePlaybackUiState.ActivePlaying ||
        playbackUiState == VideoCirclePlaybackUiState.ActivePaused) &&
        videoUrl.isNotBlank() &&
        managerState.activeSource == videoUrl &&
        !isSourceError

    val progress = remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    val isPaused = playbackUiState.isPaused
    val isPlaying = playbackUiState.isPlaying
    val isProgressVisible = playbackUiState.isProgressVisible

    // Telegram: pause progress animates 220ms in, 150ms out
    val pauseProgress by animateFloatAsState(
        targetValue = if (isPaused) 1f else 0f,
        animationSpec = if (isPaused) {
            tween(
                durationMillis = VideoCircleMotion.PauseProgressInDurationMs,
                easing = VideoCircleMotion.PauseProgressInEasing,
            )
        } else {
            tween(
                durationMillis = VideoCircleMotion.PauseProgressOutDurationMs,
                easing = VideoCircleMotion.PauseProgressOutEasing,
            )
        },
        label = "pause_progress",
    )
    val overshootPauseProgress = if (isPaused) {
        overshootInterpolation(pauseProgress)
    } else {
        pauseProgress
    }

    LaunchedEffect(isCurrentActive, playbackUiState, isSeeking) {
        val currentPlayer = manager.player ?: return@LaunchedEffect
        if (!isCurrentActive) return@LaunchedEffect
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

    var localThumbnailBitmap by remember(videoUrl) {
        mutableStateOf(cachedThumbnail(videoUrl))
    }

    LaunchedEffect(videoUrl, thumbnailUrl) {
        if (thumbnailUrl.isNullOrBlank() && videoUrl.isNotBlank() && localThumbnailBitmap == null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val retriever = MediaMetadataRetriever()
                    try {
                        when {
                            videoUrl.startsWith("content://") -> {
                                retriever.setDataSource(context, android.net.Uri.parse(videoUrl))
                            }
                            videoUrl.startsWith("file://") -> {
                                val path = android.net.Uri.parse(videoUrl).path ?: videoUrl.removePrefix("file://")
                                retriever.setDataSource(path)
                            }
                            videoUrl.startsWith("/") -> {
                                retriever.setDataSource(videoUrl)
                            }
                            videoUrl.startsWith("http://") || videoUrl.startsWith("https://") -> {
                                retriever.setDataSource(videoUrl, HashMap<String, String>())
                            }
                            else -> {
                                retriever.setDataSource(videoUrl)
                            }
                        }
                        val bmp = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?: retriever.frameAtTime
                        if (bmp != null) {
                            val imgBmp = bmp.asImageBitmap()
                            storeCachedThumbnail(videoUrl, imgBmp)
                            withContext(Dispatchers.Main) {
                                localThumbnailBitmap = imgBmp
                            }
                        }
                    } finally {
                        runCatching { retriever.release() }
                    }
                }
            }
        }
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
        contentAlignment = Alignment.Center,
    ) {
        // Hardware-accelerated ExoPlayer video playback via CircleVideoSurface (rendered underneath)
        if (isCurrentActive && manager.player != null) {
            CircleVideoSurface(
                player = manager.player!!,
                onSourceError = { isSourceError = true },
                onRenderedFirstFrame = { isFirstFrameRendered = true },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Thumbnail placeholder (rendered on top of video surface until first frame is actually decoded and rendered)
        val showThumbnail = !isCurrentActive || !isFirstFrameRendered
        if (showThumbnail) {
            if (!thumbnailUrl.isNullOrBlank()) {
                ProgressiveImage(
                    source = FlowImageSource.of(url = null, microThumb = thumbnailUrl),
                    contentDescription = "Video Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (localThumbnailBitmap != null) {
                Image(
                    bitmap = localThumbnailBitmap!!,
                    contentDescription = "Video Thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Telegram-grade circular progress ring with pause overshoot knob
        VideoCircleProgressOverlay(
            visible = isProgressVisible,
            isPlaying = isPlaying,
            isPaused = isPaused,
            pauseProgress = pauseProgress,
            overshootPauseProgress = overshootPauseProgress,
            progress = progress.floatValue,
            progressColor = MaterialTheme.colorScheme.primary,
        )

        // Pulsing equalizer when playing
        VideoCirclePlayingEqualizer(
            visible = isPlaying && isFirstFrameRendered,
        )

        // Telegram-grade circular seek overlay (active only when progress is visible)
        if (isProgressVisible && onClick != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .videoCircleSeekGesture(
                        isProgressVisible = isProgressVisible,
                        isPaused = isPaused,
                        progress = progress.floatValue,
                        onSeekingChange = { isSeeking = it },
                        consumeTapUp = true,
                        onTogglePlayback = onClick,
                        onLongPress = {},
                        onSeekProgress = { nextProg: Float ->
                            manager.seekToProgress(nextProg)
                            onSeek?.invoke(nextProg)
                        },
                    )
            )
        }
    }
}
