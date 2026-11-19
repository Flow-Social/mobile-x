package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.media.ui.NativeVideoCirclePlayback
import me.floow.shared.media.ui.rememberNativeVideoCirclePlayback
import me.floow.shared.media.voice.VoicePlayer
import me.floow.shared.media.voice.createVoicePlayer

internal val LocalChatPlayback = staticCompositionLocalOf<ChatPlaybackSession> {
    error("Chat playback must be hosted by FlowRootNavHost")
}

internal data class ChatPlaybackTarget(
    val conversationId: Long?,
    val peerUserId: String,
    val peerName: String,
    val peerAvatarUrl: String?,
    val isSavedMessages: Boolean,
    val message: ChatMessageItemModel,
    val uiKey: String,
) {
    fun belongsTo(conversationId: Long?, peerUserId: String): Boolean =
        if (this.conversationId != null && conversationId != null) this.conversationId == conversationId
        else this.peerUserId.isNotBlank() && this.peerUserId == peerUserId
}

@Stable
internal class ChatPlaybackSession(
    val voice: VoicePlayer,
    val video: NativeVideoCirclePlayback,
) {
    var target by mutableStateOf<ChatPlaybackTarget?>(null)
        private set
    var isVideo by mutableStateOf(false)
        private set
    var videoPaused by mutableStateOf(false)
        private set
    var videoSpeed by mutableFloatStateOf(1f)
        private set
    var videoProgress by mutableFloatStateOf(0f)
        private set

    fun playVoice(next: ChatPlaybackTarget, source: String, durationMs: Long) {
        if (isVideo) stop()
        target = next
        voice.play(next.message.id, source, next.uiKey, durationMs)
    }

    fun playVideo(next: ChatPlaybackTarget, source: String) {
        if (isVideo && target?.uiKey == next.uiKey && target?.belongsTo(next.conversationId, next.peerUserId) == true) {
            toggle()
            return
        }
        stop()
        target = next
        isVideo = true
        videoSource = source
        video.play(next.uiKey, next.message.id, source, videoSpeed)
    }

    private var videoSource: String? = null

    fun toggle() {
        if (isVideo) {
            val current = target ?: return
            val source = videoSource ?: return
            videoPaused = !videoPaused
            if (videoPaused) video.pause()
            else video.play(current.uiKey, current.message.id, source, videoSpeed)
        } else {
            val current = voice.state.value
            if (current.isPlaying || current.isBuffering) voice.pause() else voice.resume()
        }
    }

    fun toggleSpeed() {
        if (!isVideo) {
            voice.toggleSpeed()
            return
        }
        videoSpeed = when {
            videoSpeed < 1.25f -> 1.5f
            videoSpeed < 1.75f -> 2f
            else -> 1f
        }
        val current = target ?: return
        val source = videoSource ?: return
        if (!videoPaused) video.play(current.uiKey, current.message.id, source, videoSpeed)
    }

    fun seekVideo(fraction: Float) {
        video.seekToProgress(fraction)
        videoProgress = fraction.coerceIn(0f, 1f)
    }

    fun pollVideo() {
        videoProgress = video.progress()
    }

    fun pauseVideo(uiKey: String) {
        if (isVideo && target?.uiKey == uiKey) videoPaused = true
    }

    fun finishVideo(uiKey: String) {
        if (isVideo && target?.uiKey == uiKey) stop()
    }

    fun stop() {
        val wasVideo = isVideo
        target = null
        isVideo = false
        videoPaused = false
        videoProgress = 0f
        videoSource = null
        if (wasVideo) video.stop() else voice.stop()
    }
}

@Composable
internal fun rememberChatPlaybackSession(): ChatPlaybackSession {
    var session by remember { mutableStateOf<ChatPlaybackSession?>(null) }
    val scope = rememberCoroutineScope()
    val voice = remember { createVoicePlayer() }
    val video = rememberNativeVideoCirclePlayback(
        onPlaybackEnded = { key ->
            val endedTarget = session?.target
            scope.launch {
                yield()
                if (session?.target === endedTarget) session?.finishVideo(key)
            }
        },
        onPlaybackPaused = { session?.pauseVideo(it) },
    )
    val playback = remember(voice, video) { ChatPlaybackSession(voice, video) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(playback, lifecycle) {
        session = playback
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playback.stop()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            session = null
        }
    }
    DisposableEffect(voice) { onDispose { voice.release() } }

    val playingVideo = playback.isVideo && !playback.videoPaused
    LaunchedEffect(playback, playingVideo) {
        if (playingVideo) {
            while (isActive) {
                playback.pollVideo()
                delay(50)
            }
        }
    }
    return playback
}

@Composable
internal fun SharedChatPlaybackBar(onOpenMessage: (ChatPlaybackTarget) -> Unit) {
    val playback = LocalChatPlayback.current
    val voice = playback.voice.state.collectAsState()
    val barVoice by remember(voice) {
        derivedStateOf(structuralEqualityPolicy()) {
            voice.value.copy(currentPositionMs = 0L, durationMs = 0L)
        }
    }
    val target = playback.target
    val isVideo = playback.isVideo
    val item = if (target != null && (isVideo || barVoice.activeUiKey == target.uiKey)) {
        ChatPlaybackBarItem(
            messageId = target.message.id,
            label = chatPlaybackLabel(target.message, isVideo),
            isVideo = isVideo,
            isPlaying = if (isVideo) !playback.videoPaused else barVoice.isPlaying || barVoice.isBuffering,
            speed = if (isVideo) playback.videoSpeed else barVoice.speed,
        )
    } else null

    ChatPlaybackBarOverlay(
        item = item,
        progress = { current ->
            if (current.isVideo) playback.videoProgress
            else voice.value.let { it.currentPositionMs.toFloat() / it.durationMs.coerceAtLeast(1L) }
        },
        onPlayPause = { playback.toggle() },
        onOpenMessage = { target?.let(onOpenMessage) },
        onChangeSpeed = { playback.toggleSpeed() },
        onClose = { playback.stop() },
    )
}
