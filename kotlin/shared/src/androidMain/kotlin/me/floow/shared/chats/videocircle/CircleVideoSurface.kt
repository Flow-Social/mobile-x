package me.floow.shared.chats.videocircle

import android.graphics.Matrix
import android.graphics.Outline
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer

@Composable
fun CircleVideoSurface(
    player: ExoPlayer,
    onSourceError: () -> Unit = {},
    onRenderedFirstFrame: () -> Unit = {},
    showMenuPreview: Boolean = false,
    captureMenuPreview: Boolean = false,
    onMenuPreview: (ImageBitmap) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    val currentOnSourceError by rememberUpdatedState(onSourceError)
    val currentOnFirstFrame by rememberUpdatedState(onRenderedFirstFrame)
    val currentOnMenuPreview by rememberUpdatedState(onMenuPreview)

    val listener = remember(player) {
        object : Player.Listener {
            override fun onRenderedFirstFrame() {
                currentOnFirstFrame()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val tv = textureViewRef ?: return
                if (videoSize.width > 0 && videoSize.height > 0) {
                    applyCenterCrop(tv, videoSize)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                currentOnSourceError()
            }
        }
    }

    LaunchedEffect(textureViewRef, captureMenuPreview) {
        val view = textureViewRef ?: return@LaunchedEffect
        if (captureMenuPreview && view.isAvailable && view.width > 0 && view.height > 0) {
            runCatching { view.getBitmap(view.width.coerceAtMost(256), view.height.coerceAtMost(256)) }
                .getOrNull()
                ?.let { currentOnMenuPreview(it.asImageBitmap()) }
        }
    }

    DisposableEffect(player, listener) {
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            textureViewRef?.let(player::clearVideoTextureView)
            textureViewRef = null
        }
    }

    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                textureViewRef = this
                isOpaque = false
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
                addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                    val tv = v as TextureView
                    val size = player.videoSize
                    if (size.width > 0 && size.height > 0) {
                        applyCenterCrop(tv, size)
                    }
                }
                player.setVideoTextureView(this)
            }
        },
        modifier = modifier,
        update = { tv ->
            if (textureViewRef !== tv) {
                textureViewRef = tv
                player.setVideoTextureView(tv)
            }
            tv.alpha = if (showMenuPreview) 0f else 1f
            val size = player.videoSize
            if (size.width > 0 && size.height > 0) {
                applyCenterCrop(tv, size)
            }
        },
        onReset = { tv ->
            player.clearVideoTextureView(tv)
            if (textureViewRef === tv) {
                textureViewRef = null
            }
        },
        onRelease = { tv ->
            player.clearVideoTextureView(tv)
            if (textureViewRef === tv) {
                textureViewRef = null
            }
        },
    )
}

private fun applyCenterCrop(view: TextureView, videoSize: VideoSize) {
    if (view.width == 0 || view.height == 0 || videoSize.width == 0 || videoSize.height == 0) {
        return
    }

    val matrix = Matrix()
    val viewWidth = view.width.toFloat()
    val viewHeight = view.height.toFloat()
    val videoWidth = videoSize.width.toFloat()
    val videoHeight = videoSize.height.toFloat()
    val viewRatio = viewWidth / viewHeight
    val videoRatio = videoWidth / videoHeight

    val scaleX: Float
    val scaleY: Float
    if (videoRatio > viewRatio) {
        scaleX = videoRatio / viewRatio
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewRatio / videoRatio
    }

    matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)

    view.setTransform(matrix)
}
