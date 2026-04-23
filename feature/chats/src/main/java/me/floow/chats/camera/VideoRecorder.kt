package me.floow.chats.camera

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.video.ExperimentalAudioApi
import androidx.camera.video.FallbackStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.LifecycleOwner
import me.floow.shared.chats.uilogic.direct.RecordedClip
import java.io.File
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Android CameraX-backed video circle recorder.
 *
 * Session-based: every recording run is encapsulated in a [RecorderSession]
 * so that concurrent commands (switch camera during finalize, fast cancel
 * churn, etc.) cannot corrupt shared mutable state. All public commands
 * serialize on [sessionLock]; long-running IO (MediaMetadataRetriever,
 * file delete) happens on [recorderExecutor] off the main thread.
 */
class VideoRecorder(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val previewView = PreviewView(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recorder: Recorder? = null
    private var isFrontCamera = true
    private var currentZoomRatio = 1f
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recorderExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var outputDirectory: File? = null

    /** Guards the active recording session + camera-rebind operations. */
    private val sessionLock = Any()
    private var activeSession: RecorderSession? = null
    private var isCameraReady = false
    private var isStartingCamera = false
    private val pendingCameraReadyCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var lastCameraSwitchAtMs = 0L

    private companion object {
        private const val TAG = "VideoRecorder"
        private const val SWITCH_CAMERA_COOLDOWN_MS = 700L
    }

    val previewViewChild: PreviewView get() = previewView
    val isReady: Boolean get() = isCameraReady && videoCapture != null

    fun init(outputDir: File) {
        outputDirectory = outputDir
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    fun startCamera(onReady: (Boolean) -> Unit = {}) {
        synchronized(sessionLock) {
            if (isCameraReady && videoCapture != null) {
                mainHandler.post { onReady(true) }
                return
            }
            pendingCameraReadyCallbacks += onReady
            if (isStartingCamera) return
            isStartingCamera = true
        }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            var callbacks: List<(Boolean) -> Unit>
            var success: Boolean
            try {
                val provider = future.get()
                synchronized(sessionLock) {
                    cameraProvider = provider
                    success = bindUseCases(provider, isFrontCamera)
                    isCameraReady = success
                    isStartingCamera = false
                    callbacks = pendingCameraReadyCallbacks.toList()
                    pendingCameraReadyCallbacks.clear()
                }
                Log.d(TAG, "Camera ready: $success")
            } catch (e: Exception) {
                Log.e(TAG, "startCamera failed", e)
                synchronized(sessionLock) {
                    isCameraReady = false
                    isStartingCamera = false
                    callbacks = pendingCameraReadyCallbacks.toList()
                    pendingCameraReadyCallbacks.clear()
                }
                success = false
            }
            callbacks.forEach { callback -> callback(success) }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Toggles between front and back camera. Safe to call while a recording
     * session is active unless that session is already finalizing.
     */
    fun switchCamera(): Boolean {
        val provider = cameraProvider ?: return false
        val now = SystemClock.elapsedRealtime()

        synchronized(sessionLock) {
            if (now - lastCameraSwitchAtMs < SWITCH_CAMERA_COOLDOWN_MS) {
                Log.d(TAG, "switchCamera ignored: cooldown active")
                return false
            }
            val session = activeSession
            if (session != null && session.state == RecorderSession.State.Finalizing) {
                Log.d(TAG, "switchCamera blocked: session is finalizing")
                return false
            }
            val recording = session?.recording
            val wasRecording = recording != null
            if (wasRecording) {
                val pauseResult = runCatching { recording.pause() }
                if (pauseResult.isFailure) {
                    Log.e(TAG, "switchCamera pause failed", pauseResult.exceptionOrNull())
                    return false
                }
            }

            val nextCamera = !isFrontCamera
            val switched = bindUseCases(provider, nextCamera)
            if (switched) {
                isFrontCamera = nextCamera
                lastCameraSwitchAtMs = now
                if (wasRecording) {
                    runCatching { recording.resume() }.onFailure {
                        Log.e(TAG, "switchCamera resume failed", it)
                    }
                }
            } else if (wasRecording) {
                runCatching { recording.resume() }.onFailure {
                    Log.e(TAG, "switchCamera rollback resume failed", it)
                }
            }
            return switched
        }
    }

    fun zoomBy(scaleFactor: Float): Float? {
        if (scaleFactor <= 0f) return null
        return setZoomRatio(currentZoomRatio * scaleFactor)
    }

    fun setZoomRatio(zoomRatio: Float): Float? {
        val camera = camera ?: return null
        val zoomState = camera.cameraInfo.zoomState.value ?: return null
        val applied = zoomRatio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        camera.cameraControl.setZoomRatio(applied)
        currentZoomRatio = applied
        return applied
    }

    /**
     * Starts a new recording session. [onComplete] is dispatched on the main
     * thread once the clip is finalized (or `null` on error / cancel).
     */
    fun startRecording(
        onAudioLevel: (Float) -> Unit = {},
        onComplete: (RecordedClip?) -> Unit,
    ) {
        synchronized(sessionLock) {
            if (!isCameraReady) {
                Log.e(TAG, "startRecording: camera not ready")
                mainHandler.post { onComplete(null) }
                return
            }
            if (activeSession != null) {
                Log.e(TAG, "startRecording: another session is active")
                mainHandler.post { onComplete(null) }
                return
            }
            val dir = outputDirectory ?: run {
                Log.e(TAG, "startRecording: output directory is null")
                mainHandler.post { onComplete(null) }
                return
            }
            val capture = videoCapture ?: run {
                Log.e(TAG, "startRecording: videoCapture is null")
                mainHandler.post { onComplete(null) }
                return
            }

            val file = File(dir, "video_circle_${System.currentTimeMillis()}.mp4")
            val session = RecorderSession(
                id = UUID.randomUUID().toString(),
                filePath = file.absolutePath,
                onComplete = onComplete,
            )
            activeSession = session
            currentZoomRatio = 1f
            lastCameraSwitchAtMs = 0L
            setZoomRatio(1f)

            val outputOptions = FileOutputOptions.Builder(file).build()
            val pendingRecording = capture.output
                .prepareRecording(context, outputOptions)
                .apply {
                    if (PermissionChecker.checkSelfPermission(
                            context,
                            android.Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        withAudioEnabled()
                    }
                }
                .asPersistentRecording()

            session.recording = pendingRecording.start(recorderExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> onSessionActive(session.id)
                    is VideoRecordEvent.Status -> dispatchAudioLevel(event, onAudioLevel)
                    is VideoRecordEvent.Pause,
                    is VideoRecordEvent.Resume -> Unit
                    is VideoRecordEvent.Finalize -> onSessionFinalize(session.id, event)
                }
            }
        }
    }

    @OptIn(ExperimentalAudioApi::class)
    private fun dispatchAudioLevel(
        event: VideoRecordEvent,
        onAudioLevel: (Float) -> Unit,
    ) {
        val amplitude = event.recordingStats.audioStats.audioAmplitude
            .toFloat()
            .coerceIn(0f, 1f)
        mainHandler.post { onAudioLevel(amplitude) }
    }

    private fun onSessionActive(sessionId: String) {
        synchronized(sessionLock) {
            val s = activeSession ?: return
            if (s.id == sessionId && s.state == RecorderSession.State.Starting) {
                s.state = RecorderSession.State.Active
            }
        }
    }

    private fun onSessionFinalize(sessionId: String, event: VideoRecordEvent.Finalize) {
        val sessionSnapshot: RecorderSession
        synchronized(sessionLock) {
            val s = activeSession ?: return
            if (s.id != sessionId) return
            sessionSnapshot = s
            s.state = RecorderSession.State.Closed
            activeSession = null
        }

        val hasError = event.hasError()
        if (hasError) {
            Log.e(TAG, "recording error: ${event.cause}")
            File(sessionSnapshot.filePath).delete()
            mainHandler.post { sessionSnapshot.onComplete(null) }
            return
        }

        // Metadata extraction is relatively heavy; keep it off the main thread.
        recorderExecutor.execute {
            val clip = buildClip(sessionSnapshot.filePath)
            mainHandler.post { sessionSnapshot.onComplete(clip) }
        }
    }

    private fun buildClip(path: String): RecordedClip? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "buildClip: file missing or empty: $path")
            file.delete()
            return null
        }
        var durationMs = 0L
        var width = 0
        var height = 0
        try {
            MediaMetadataRetriever().use { mmr ->
                mmr.setDataSource(path)
                durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull() ?: 0
                height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull() ?: 0
                val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                    ?.toIntOrNull() ?: 0
                if (rotation % 180 != 0) {
                    val tmp = width; width = height; height = tmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "buildClip: metadata read failed", e)
        }
        return RecordedClip(path = path, durationMs = durationMs, width = width, height = height)
    }

    /**
     * Transitions the active session to finalizing and requests `stop`.
     * Safe to call multiple times; second call is a no-op.
     */
    fun stopRecording(): String? {
        synchronized(sessionLock) {
            val session = activeSession ?: return null
            if (session.state == RecorderSession.State.Finalizing ||
                session.state == RecorderSession.State.Closed
            ) {
                return session.filePath
            }
            session.state = RecorderSession.State.Finalizing
            runCatching { session.recording?.stop() }.onFailure {
                Log.e(TAG, "stopRecording failed", it)
            }
            return session.filePath
        }
    }

    fun cancelRecording() {
        val sessionSnapshot: RecorderSession?
        synchronized(sessionLock) {
            sessionSnapshot = activeSession
            activeSession = null
            sessionSnapshot?.state = RecorderSession.State.Closed
        }
        sessionSnapshot ?: return
        runCatching { sessionSnapshot.recording?.close() }
        File(sessionSnapshot.filePath).delete()
        // Surface completion as null so any pending consumer doesn't hang.
        mainHandler.post { sessionSnapshot.onComplete(null) }
    }

    fun cleanupTempFiles() {
        outputDirectory?.listFiles()?.forEach { it.delete() }
    }

    fun deleteTempFile(path: String) {
        File(path).delete()
    }

    fun release() {
        stopCamera()
        recorderExecutor.shutdown()
    }

    fun stopCamera() {
        synchronized(sessionLock) {
            if (activeSession != null) return
            cameraProvider?.unbindAll()
            cameraProvider = null
            camera = null
            previewUseCase = null
            videoCapture = null
            recorder = null
            isCameraReady = false
            isStartingCamera = false
            pendingCameraReadyCallbacks.clear()
        }
    }

    private fun bindUseCases(provider: ProcessCameraProvider, useFront: Boolean): Boolean {
        val previousPreview = previewUseCase
        val previousCapture = videoCapture
        val previousCamera = camera
        val previousIsFront = isFrontCamera
        val previousZoomRatio = currentZoomRatio
        val recorderInstance = recorder ?: Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.SD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                )
            )
            .build()
            .also { recorder = it }
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val capture = VideoCapture.withOutput(recorderInstance)
        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
            previewUseCase = preview
            videoCapture = capture
            setZoomRatio(previousZoomRatio)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "bindUseCases failed", e)
            camera = previousCamera
            previewUseCase = previousPreview
            videoCapture = previousCapture
            isFrontCamera = previousIsFront
            currentZoomRatio = previousZoomRatio
            if (previousPreview != null && previousCapture != null) {
                try {
                    provider.unbindAll()
                    val restoreSelector = if (previousIsFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    camera = provider.bindToLifecycle(lifecycleOwner, restoreSelector, previousPreview, previousCapture)
                    previewUseCase = previousPreview
                    videoCapture = previousCapture
                    setZoomRatio(previousZoomRatio)
                    return true
                } catch (restoreException: Exception) {
                    Log.e(TAG, "restore after failed bindUseCases also failed", restoreException)
                }
            }
            camera = null
            videoCapture = null
            return false
        }
    }
}

@Composable
fun VideoRecorderPreview(
    recorder: VideoRecorder,
    modifier: Modifier = Modifier,
) {
    val previewView = remember { recorder.previewViewChild }
    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}
