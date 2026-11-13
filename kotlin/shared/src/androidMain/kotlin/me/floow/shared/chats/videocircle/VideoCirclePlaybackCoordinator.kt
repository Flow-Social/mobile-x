package me.floow.shared.chats.videocircle

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.StateFlow
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.VideoCircleOutMessage

class VideoCirclePlaybackCoordinator internal constructor(
    private val driver: VideoCirclePlaybackDriver,
    private val audioFocus: VideoCircleAudioFocus,
) {

    val state: StateFlow<VideoCirclePlaybackState> = driver.state
    val player: ExoPlayer? get() = driver.player

    private val knownMessages = LinkedHashMap<String, VideoCircleOutMessage>()
    private var latestViewportSnapshot: ChatViewportSnapshot? = null

    fun register(message: VideoCircleOutMessage) {
        if (message.playableSource != null) {
            knownMessages[message.uiKey] = message
        }
    }

    fun unregister(uiKey: String) {
        knownMessages.remove(uiKey)
        if (driver.state.value.activeUiKey == uiKey) {
            clear()
        }
    }

    fun onViewportSnapshotChanged(snapshot: ChatViewportSnapshot) {
        latestViewportSnapshot = snapshot
        val candidates = snapshot.visibleVideoCandidates()
        val current = driver.state.value

        if (current.activeUiKey != null && candidates.none { it.uiKey == current.activeUiKey }) {
            clear()
        }

        val afterVisibilityClear = driver.state.value
        if (afterVisibilityClear.isUserActivated && afterVisibilityClear.status != VideoCirclePlaybackStatus.Idle) {
            preloadNext(candidates, exceptUiKey = afterVisibilityClear.activeUiKey)
            return
        }

        val bestCandidate = candidates
            .filter { it.visibleFraction >= AutoplayThreshold }
            .maxByOrNull(VideoCirclePlaybackCandidate::visibleFraction)

        if (bestCandidate == null) {
            if (!afterVisibilityClear.isUserActivated) {
                driver.clear()
            }
            return
        }
        driver.preload(bestCandidate.source)
        preloadNext(candidates, exceptUiKey = bestCandidate.uiKey)
    }

    fun onVideoCircleClick(message: VideoCircleOutMessage) {
        register(message)
        val source = message.playableSource ?: return
        val current = driver.state.value

        if (current.activeUiKey == message.uiKey && current.status == VideoCirclePlaybackStatus.Playing && !current.isMuted) {
            driver.pause()
            return
        }

        if (!audioFocus.request()) return

        driver.play(
            VideoCirclePlaybackRequest(
                uiKey = message.uiKey,
                messageId = message.id,
                source = source,
                muted = false,
                userActivated = true,
            ),
        )
    }

    fun seekToProgress(progress: Float) {
        if (driver.state.value.canSeek()) {
            driver.seekToProgress(progress)
        }
    }

    fun pause() {
        driver.pause()
    }

    fun clear() {
        audioFocus.abandon()
        driver.clear()
    }

    fun release() {
        audioFocus.abandon()
        driver.release()
        knownMessages.clear()
    }

    private fun ChatViewportSnapshot.visibleVideoCandidates(): List<VideoCirclePlaybackCandidate> {
        return visibleItems.mapNotNull { item ->
            val message = knownMessages.values.firstOrNull { it.id == item.messageId } ?: return@mapNotNull null
            val source = message.playableSource ?: return@mapNotNull null
            VideoCirclePlaybackCandidate(
                uiKey = message.uiKey,
                messageId = message.id,
                source = source,
                visibleFraction = item.visibleFraction,
            )
        }
    }

    private fun preloadNext(
        candidates: List<VideoCirclePlaybackCandidate>,
        exceptUiKey: String?,
    ) {
        val nextCandidate = candidates
            .asSequence()
            .filter { it.uiKey != exceptUiKey }
            .sortedByDescending(VideoCirclePlaybackCandidate::visibleFraction)
            .firstOrNull() ?: return
        driver.preload(nextCandidate.source)
    }

    companion object {
        const val AutoplayThreshold = 0.60f
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

@Composable
fun rememberVideoCirclePlaybackCoordinator(): VideoCirclePlaybackCoordinator {
    val context = LocalContext.current
    val coordinator = remember(context) {
        lateinit var created: VideoCirclePlaybackCoordinator
        val audioFocus = VideoCircleAudioFocusController(
            context = context,
            onTransientLoss = { created.pause() },
            onPermanentLoss = { created.clear() },
        )
        val cacheProvider = VideoCircleCacheProvider(context)
        val driver = VideoCirclePlayerManager(context, cacheProvider)
        created = VideoCirclePlaybackCoordinator(driver, audioFocus)
        created
    }

    DisposableEffect(coordinator) {
        onDispose {
            coordinator.release()
        }
    }

    return coordinator
}
