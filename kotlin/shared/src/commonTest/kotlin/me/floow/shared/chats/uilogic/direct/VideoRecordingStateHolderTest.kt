package me.floow.shared.chats.uilogic.direct

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.floow.uikit.chat.model.VideoRecordingMode

@OptIn(ExperimentalCoroutinesApi::class)
class VideoRecordingStateHolderTest {

    @Test
    fun `release before minimum duration cancels recording`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(400)

        holder.onRecordButtonRelease()
        advanceUntilIdle()

        assertEquals(1, recorder.startCalls)
        assertEquals(1, recorder.cancelCalls)
        assertEquals(0, recorder.stopCalls)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `late null completion after short cancel is ignored`() = runTest {
        val recorder = FakeVideoRecorderBridge(completeNullOnCancel = true)
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(400)
        holder.onRecordButtonRelease()
        advanceUntilIdle()

        assertEquals(1, recorder.cancelCalls)
        assertEquals(0, recorder.stopCalls)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `manual stop before minimum duration cancels instead of failing`() = runTest {
        val recorder = FakeVideoRecorderBridge(completeNullOnCancel = true)
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(400)
        holder.stopRecording()
        advanceUntilIdle()

        assertEquals(1, recorder.cancelCalls)
        assertEquals(0, recorder.stopCalls)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `release after minimum duration sends local clip immediately`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(1_000)

        holder.onRecordButtonRelease()

        assertEquals(1, recorder.startCalls)
        assertEquals(VideoRecordingMode.Sending, holder.state.value.mode)
        assertEquals(1, recorder.stopCalls)

        recorder.complete("/tmp/video_circle.mp4")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle.mp4"), sentClips.map { it.path })
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `left drag at cancel threshold cancels active recording`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(900)

        holder.onRecordDrag(
            offsetX = -holder.state.value.cancelThresholdPx,
            offsetY = 0f,
        )
        advanceUntilIdle()

        assertEquals(1, recorder.cancelCalls)
        assertEquals(0, recorder.stopCalls)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `left drag uses cancel threshold from state as single source`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        val cancelThresholdPx = holder.state.value.cancelThresholdPx

        holder.onRecordDrag(
            offsetX = -(cancelThresholdPx - 1f),
            offsetY = 0f,
        )

        assertEquals(0, recorder.cancelCalls)
        assertEquals(VideoRecordingMode.Recording, holder.state.value.mode)
        assertEquals(-(cancelThresholdPx - 1f), holder.state.value.dragOffsetX)
        assertTrue(holder.state.value.cancelProgress < 1f)

        holder.onRecordDrag(
            offsetX = -cancelThresholdPx,
            offsetY = 0f,
        )
        advanceUntilIdle()

        assertEquals(1, recorder.cancelCalls)
        assertEquals(0, recorder.stopCalls)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `vertical up drag does not cancel recording before lock threshold`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(900)

        holder.onRecordDrag(
            offsetX = 0f,
            offsetY = -(holder.state.value.lockThresholdPx - 1f),
        )
        advanceUntilIdle()

        assertEquals(0, recorder.cancelCalls)
        assertEquals(0, recorder.stopCalls)
        assertEquals(VideoRecordingMode.Recording, holder.state.value.mode)
        assertEquals(-(holder.state.value.lockThresholdPx - 1f), holder.state.value.dragOffsetY)

        holder.onRecordButtonRelease()

        assertEquals(1, recorder.stopCalls)
        assertEquals(VideoRecordingMode.Sending, holder.state.value.mode)

        recorder.complete("/tmp/video_circle_vertical_drag.mp4")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_vertical_drag.mp4"), sentClips.map { it.path })
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `swipe up locks recording and release does not send until stop`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(900)
        holder.onRecordDrag(
            offsetX = 0f,
            offsetY = -holder.state.value.lockThresholdPx,
        )
        holder.onSwipeUpLock()

        assertTrue(holder.state.value.isLocked)
        assertEquals(1f, holder.state.value.lockProgress)

        holder.onRecordButtonRelease()
        advanceUntilIdle()

        assertEquals(0, recorder.stopCalls)
        assertEquals(VideoRecordingMode.Recording, holder.state.value.mode)

        holder.stopRecording()

        assertEquals(1, recorder.stopCalls)
        assertEquals(VideoRecordingMode.Sending, holder.state.value.mode)

        recorder.complete("/tmp/video_circle_locked.mp4")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_locked.mp4"), sentClips.map { it.path })
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `manual stop button sends without waiting for release`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(1_100)
        holder.stopRecording()

        assertEquals(VideoRecordingMode.Sending, holder.state.value.mode)
        assertEquals(1, recorder.stopCalls)

        recorder.complete("/tmp/video_circle_manual_stop.mp4")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_manual_stop.mp4"), sentClips.map { it.path })
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `pause recording transitions to preview mode without sending until sendRecordedVideo`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(1_200)
        holder.pauseRecording()

        assertEquals(1, recorder.stopCalls)
        recorder.complete("/tmp/video_circle_preview.mp4", durationMs = 1_200L)
        advanceUntilIdle()

        assertEquals(VideoRecordingMode.Preview, holder.state.value.mode)
        assertEquals("/tmp/video_circle_preview.mp4", holder.previewClip?.path)
        assertTrue(sentClips.isEmpty())

        holder.sendRecordedVideo()
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_preview.mp4"), sentClips.map { it.path })
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `cancel recording from preview deletes temp file and resets to idle`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(1_200)
        holder.pauseRecording()

        recorder.complete("/tmp/video_circle_cancelled.mp4", durationMs = 1_200L)
        advanceUntilIdle()

        assertEquals(VideoRecordingMode.Preview, holder.state.value.mode)
        holder.cancelRecording()
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_cancelled.mp4"), recorder.deletedTempFiles)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `finalized clip shorter than minimum is discarded`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(900)
        holder.stopRecording()
        recorder.complete("/tmp/video_circle_short.mp4", durationMs = 500L)
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_short.mp4"), recorder.deletedTempFiles)
        assertTrue(sentClips.isEmpty())
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `max duration finalizes only once`() = runTest {
        val recorder = FakeVideoRecorderBridge()
        val sentClips = mutableListOf<RecordedClip>()
        val holder = createHolder(backgroundScope, recorder, sentClips) { testScheduler.currentTime }

        holder.onRecordButtonPress()
        advanceTimeBy(holder.state.value.maxDurationMs + 100)

        assertEquals(1, recorder.stopCalls)
        assertEquals(VideoRecordingMode.Sending, holder.state.value.mode)

        recorder.complete("/tmp/video_circle_limit.mp4")
        advanceUntilIdle()

        assertEquals(listOf("/tmp/video_circle_limit.mp4"), sentClips.map { it.path })
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
    }

    @Test
    fun `release with instant flight triggers takeoff immediately on button release`() = runTest {
        val recorder = FakeVideoRecorderBridge().apply {
            currentRecordingFilePath = "/tmp/video_circle_instant.mp4"
        }
        var instantClip: RecordedClip? = null
        var finalizedClip: RecordedClip? = null
        val holder = VideoRecordingStateHolder(scope = backgroundScope) { testScheduler.currentTime }.apply {
            this.recorder = recorder
            onInstantFlightTrigger = { clip, _ -> instantClip = clip }
            onVideoFinalized = { clip -> finalizedClip = clip }
        }

        holder.onRecordButtonPress()
        advanceTimeBy(1_000)

        // On finger release: flight triggers instantly without waiting for encoder!
        holder.onRecordButtonRelease()

        assertEquals("/tmp/video_circle_instant.mp4", instantClip?.path)
        assertEquals(VideoRecordingMode.Idle, holder.state.value.mode)
        assertTrue(finalizedClip == null)

        // Then in background encoder completes:
        recorder.complete("/tmp/video_circle_instant.mp4", durationMs = 1_000L)
        advanceUntilIdle()

        assertEquals("/tmp/video_circle_instant.mp4", finalizedClip?.path)
    }

    private fun createHolder(
        scope: kotlinx.coroutines.CoroutineScope,
        recorder: FakeVideoRecorderBridge,
        sentClips: MutableList<RecordedClip>,
        nowMs: () -> Long,
    ): VideoRecordingStateHolder {
        return VideoRecordingStateHolder(scope = scope, nowMs = nowMs).apply {
            this.recorder = recorder
            onVideoReadyForLocalInsertion = { clip, _ -> sentClips += clip }
        }
    }
}

private class FakeVideoRecorderBridge(
    private val completeNullOnCancel: Boolean = false,
) : VideoRecorderBridge {
    override var currentRecordingFilePath: String? = null
    var startCalls = 0
    var stopCalls = 0
    var cancelCalls = 0
    val deletedTempFiles = mutableListOf<String>()

    private var completion: ((RecordedClip?) -> Unit)? = null

    override fun startRecording(
        onAudioLevel: (Float) -> Unit,
        onComplete: (RecordedClip?) -> Unit,
    ) {
        startCalls++
        completion = onComplete
    }

    override fun stopRecording() {
        stopCalls++
    }

    override fun cancelRecording() {
        cancelCalls++
        val callback = completion
        completion = null
        if (completeNullOnCancel) {
            callback?.invoke(null)
        }
    }

    override fun switchCamera(): Boolean = false

    override fun zoomBy(scaleFactor: Float): Float? = null

    override fun setZoomRatio(zoomRatio: Float): Float? = null

    override fun deleteTempFile(path: String) {
        deletedTempFiles += path
    }

    fun complete(path: String?, durationMs: Long = 1_000L) {
        val callback = completion
        completion = null
        callback?.invoke(path?.let { RecordedClip(path = it, durationMs = durationMs, width = 1080, height = 1080) })
    }
}
