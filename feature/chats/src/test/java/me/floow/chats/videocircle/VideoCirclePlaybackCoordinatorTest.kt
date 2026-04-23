package me.floow.chats.videocircle

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.uikit.chat.model.ChatVisibleItemSnapshot
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.VideoCircleOutMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCirclePlaybackCoordinatorTest {

    @Test
    fun `visible item below threshold does not preload or start playback`() {
        val fixture = fixture()
        val message = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.register(message)

        fixture.coordinator.onViewportSnapshotChanged(viewport(visible(message, 0.59f)))

        assertTrue(fixture.driver.playRequests.isEmpty())
        assertTrue(fixture.driver.preloadedSources.isEmpty())
        assertNull(fixture.driver.state.value.activeUiKey)
    }

    @Test
    fun `visible item at threshold preloads without becoming active or requesting audio focus`() {
        val fixture = fixture()
        val message = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.register(message)

        fixture.coordinator.onViewportSnapshotChanged(viewport(visible(message, 0.60f)))

        assertTrue(fixture.driver.playRequests.isEmpty())
        assertEquals(listOf(message.playableSource), fixture.driver.preloadedSources)
        assertNull(fixture.driver.state.value.activeUiKey)
        assertEquals(0, fixture.audio.requestCount)
    }

    @Test
    fun `best visible candidate wins preload`() {
        val fixture = fixture()
        val first = message(id = 1L, uiKey = "msg_1")
        val second = message(id = 2L, uiKey = "msg_2")
        fixture.coordinator.register(first)
        fixture.coordinator.register(second)

        fixture.coordinator.onViewportSnapshotChanged(
            viewport(
                visible(first, 0.70f),
                visible(second, 0.95f),
            )
        )

        assertTrue(fixture.driver.playRequests.isEmpty())
        assertEquals(second.playableSource, fixture.driver.preloadedSources.first())
    }

    @Test
    fun `tap converts muted autoplay into unmuted user playback`() {
        val fixture = fixture()
        val message = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.register(message)
        fixture.coordinator.onViewportSnapshotChanged(viewport(visible(message, 0.80f)))

        fixture.coordinator.onVideoCircleClick(message)

        val request = fixture.driver.playRequests.single()
        assertEquals("msg_1", request.uiKey)
        assertFalse(request.muted)
        assertTrue(request.userActivated)
        assertEquals(1, fixture.audio.requestCount)
    }

    @Test
    fun `active unmuted playback is not stolen by autoplay`() {
        val fixture = fixture()
        val active = message(id = 1L, uiKey = "msg_1")
        val next = message(id = 2L, uiKey = "msg_2")
        fixture.coordinator.register(active)
        fixture.coordinator.register(next)
        fixture.coordinator.onVideoCircleClick(active)

        fixture.coordinator.onViewportSnapshotChanged(
            viewport(
                visible(active, 0.70f),
                visible(next, 0.95f),
            )
        )

        assertEquals("msg_1", fixture.driver.state.value.activeUiKey)
        assertEquals(listOf("msg_1"), fixture.driver.playRequests.map(VideoCirclePlaybackRequest::uiKey))
    }

    @Test
    fun `invisible active item clears playback`() {
        val fixture = fixture()
        val active = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.register(active)
        fixture.coordinator.onVideoCircleClick(active)

        fixture.coordinator.onViewportSnapshotChanged(viewport())

        assertNull(fixture.driver.state.value.activeUiKey)
        assertEquals(1, fixture.audio.abandonCount)
    }

    @Test
    fun `onStop pauses and abandons audio focus`() {
        val fixture = fixture()
        val active = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.onVideoCircleClick(active)

        fixture.coordinator.onStop()

        assertEquals(VideoCirclePlaybackStatus.Paused, fixture.driver.state.value.status)
        assertEquals(1, fixture.audio.abandonCount)
    }

    @Test
    fun `same visible source does not start playback`() {
        val fixture = fixture()
        val message = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.register(message)
        val snapshot = viewport(visible(message, 0.80f))

        fixture.coordinator.onViewportSnapshotChanged(snapshot)
        fixture.coordinator.onViewportSnapshotChanged(snapshot)

        assertTrue(fixture.driver.playRequests.isEmpty())
    }

    @Test
    fun `release abandons audio focus and releases driver`() {
        val fixture = fixture()

        fixture.coordinator.release()

        assertEquals(1, fixture.audio.abandonCount)
        assertTrue(fixture.driver.released)
    }

    @Test
    fun `seek progress is forwarded only while playback is active`() {
        val fixture = fixture()

        fixture.coordinator.seekToProgress(0.25f)
        assertTrue(fixture.driver.seekProgressRequests.isEmpty())

        val active = message(id = 1L, uiKey = "msg_1")
        fixture.coordinator.onVideoCircleClick(active)
        fixture.coordinator.seekToProgress(0.75f)

        assertEquals(listOf(0.75f), fixture.driver.seekProgressRequests)
        assertEquals(750L, fixture.driver.state.value.positionMs)

        fixture.coordinator.clear()
        fixture.coordinator.seekToProgress(0.5f)

        assertEquals(listOf(0.75f), fixture.driver.seekProgressRequests)
    }

    private fun fixture(): Fixture {
        val driver = FakePlaybackDriver()
        val audio = FakeAudioFocus()
        return Fixture(
            coordinator = VideoCirclePlaybackCoordinator(driver, audio),
            driver = driver,
            audio = audio,
        )
    }

    private fun message(
        id: Long,
        uiKey: String,
        source: String = "/tmp/video_$id.mp4",
    ): VideoCircleOutMessage {
        return VideoCircleOutMessage(
            id = id,
            uiKey = uiKey,
            createdAtMillis = 1L,
            localVideoPath = source,
        )
    }

    private fun visible(
        message: VideoCircleOutMessage,
        fraction: Float,
    ): ChatVisibleItemSnapshot {
        return ChatVisibleItemSnapshot(
            itemKey = message.uiKey,
            messageId = message.id,
            visibleFraction = fraction,
        )
    }

    private fun viewport(
        vararg items: ChatVisibleItemSnapshot,
    ): ChatViewportSnapshot {
        return ChatViewportSnapshot(
            visibleMessageIds = items.map(ChatVisibleItemSnapshot::messageId).toSet(),
            firstVisibleMessageId = items.firstOrNull()?.messageId,
            firstVisibleOffsetPx = 0,
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffsetPx = 0,
            visibleReadCandidateId = null,
            isAtBottom = false,
            visibleItems = items.toList(),
        )
    }

    private data class Fixture(
        val coordinator: VideoCirclePlaybackCoordinator,
        val driver: FakePlaybackDriver,
        val audio: FakeAudioFocus,
    )

    private class FakePlaybackDriver : VideoCirclePlaybackDriver {
        private val mutableState = MutableStateFlow(VideoCirclePlaybackState())
        val playRequests = mutableListOf<VideoCirclePlaybackRequest>()
        val preloadedSources = mutableListOf<String>()
        val seekProgressRequests = mutableListOf<Float>()
        var released = false

        override val state: StateFlow<VideoCirclePlaybackState> = mutableState
        override val player: ExoPlayer? = null

        override fun play(request: VideoCirclePlaybackRequest) {
            playRequests += request
            mutableState.value = VideoCirclePlaybackState(
                activeUiKey = request.uiKey,
                activeMessageId = request.messageId,
                activeSource = request.source,
                isMuted = request.muted,
                isUserActivated = request.userActivated,
                status = VideoCirclePlaybackStatus.Playing,
            )
        }

        override fun preload(source: String) {
            preloadedSources += source
        }

        override fun seekToProgress(progress: Float) {
            val current = mutableState.value
            if (!current.canSeek()) return
            val coercedProgress = progress.coerceIn(0f, 1f)
            seekProgressRequests += coercedProgress
            mutableState.value = current.copy(positionMs = (1_000L * coercedProgress).toLong())
        }

        override fun pause() {
            mutableState.value = mutableState.value.copy(status = VideoCirclePlaybackStatus.Paused)
        }

        override fun clear() {
            mutableState.value = VideoCirclePlaybackState()
        }

        override fun release() {
            released = true
            clear()
        }
    }

    private class FakeAudioFocus : VideoCircleAudioFocus {
        var requestCount = 0
        var abandonCount = 0

        override fun request(): Boolean {
            requestCount += 1
            return true
        }

        override fun abandon() {
            abandonCount += 1
        }
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
