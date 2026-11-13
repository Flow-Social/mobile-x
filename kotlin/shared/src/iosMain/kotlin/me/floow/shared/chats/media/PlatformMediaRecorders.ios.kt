package me.floow.shared.chats.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.floow.domain.utils.currentTimeMillis
import me.floow.shared.chats.uilogic.direct.RecordedClip
import me.floow.shared.chats.uilogic.direct.RecordedVoiceClip
import me.floow.shared.chats.uilogic.direct.VideoRecorderBridge
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceDiscoverySession.Companion.discoverySessionWithDeviceTypes
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureFileOutput
import platform.AVFoundation.AVCaptureFileOutputRecordingDelegateProtocol
import platform.AVFoundation.AVCaptureMovieFileOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPresetHigh
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIView
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
class IosVoiceRecorder(
    private val scope: CoroutineScope,
) : PlatformVoiceRecorderBridge {
    private var recorder: AVAudioRecorder? = null
    private var recordingJob: Job? = null
    private var currentFilePath: String? = null
    private var startTimeMs: Long = 0L
    private val waveformSamples = mutableListOf<Int>()
    private var _isRecording = false

    override val isRecording: Boolean get() = _isRecording

    override fun startRecording(
        onAudioLevel: ((Float) -> Unit)?,
        onMaxDuration: (() -> Unit)?,
    ): Boolean {
        stopRecording()
        cancelRecording()

        val fileName = "voice_${currentTimeMillis()}.m4a"
        val filePath = NSTemporaryDirectory() + fileName
        currentFilePath = filePath
        waveformSamples.clear()

        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(
            category = AVAudioSessionCategoryPlayAndRecord,
            withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        )

        val url = NSURL.fileURLWithPath(filePath)
        val settings: Map<Any?, Any?> = mapOf(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to 44100.0,
            AVNumberOfChannelsKey to 1,
        )

        val newRecorder = AVAudioRecorder(uRL = url, settings = settings, error = null)
        newRecorder.meteringEnabled = true
        val started = newRecorder.record()
        if (!started) {
            return false
        }

        recorder = newRecorder
        _isRecording = true
        startTimeMs = currentTimeMillis()

        recordingJob = scope.launch(Dispatchers.Main) {
            while (_isRecording && isActive) {
                newRecorder.updateMeters()
                val power = newRecorder.averagePowerForChannel(0UL)
                val normalized = ((power + 60f) / 60f).coerceIn(0f, 1f)
                onAudioLevel?.invoke(normalized)
                waveformSamples.add((normalized * 100).toInt().coerceIn(0, 100))

                val elapsed = currentTimeMillis() - startTimeMs
                if (elapsed >= 60_000L) {
                    onMaxDuration?.invoke()
                    break
                }
                delay(60)
            }
        }
        return true
    }

    override fun stopRecording(): RecordedVoiceClip? {
        if (!_isRecording) return null
        _isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        val rec = recorder ?: return null
        rec.stop()
        recorder = null

        val duration = (currentTimeMillis() - startTimeMs).coerceAtLeast(500L)
        val path = currentFilePath ?: return null

        val rawSamples = if (waveformSamples.isEmpty()) listOf(10, 20, 30, 20, 10) else waveformSamples.toList()
        val samples = normalizeWaveformTo48Bars(rawSamples)
        return RecordedVoiceClip(
            filePath = path,
            durationMs = duration,
            waveform = samples,
            mimeType = "audio/m4a",
        )
    }

    override fun cancelRecording() {
        _isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        recorder?.stop()
        recorder = null

        currentFilePath?.let { path ->
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
        currentFilePath = null
        waveformSamples.clear()
    }
}

private fun normalizeWaveformTo48Bars(raw: List<Int>): List<Int> {
    val targetCount = 48
    if (raw.isEmpty()) return List(targetCount) { 10 }
    if (raw.size == targetCount) return raw

    val result = ArrayList<Int>(targetCount)
    val step = raw.size.toFloat() / targetCount.toFloat()
    for (i in 0 until targetCount) {
        val startIdx = (i * step).toInt().coerceIn(0, raw.size - 1)
        val endIdx = ((i + 1) * step).toInt().coerceIn(startIdx + 1, raw.size)
        var maxVal = 0
        for (j in startIdx until endIdx) {
            if (raw[j] > maxVal) maxVal = raw[j]
        }
        result.add(maxVal.coerceIn(5, 100))
    }
    return result
}

@OptIn(ExperimentalForeignApi::class)
class IosVideoRecorder : VideoRecorderBridge {
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    val captureSession = AVCaptureSession().apply {
        sessionPreset = AVCaptureSessionPresetHigh
    }

    private var currentCameraPosition = AVCaptureDevicePositionFront
    private var videoInput: AVCaptureDeviceInput? = null
    private var audioInput: AVCaptureDeviceInput? = null
    private var movieOutput: AVCaptureMovieFileOutput? = null
    private var recordingDelegate: RecordingDelegate? = null

    private var activeRecordingPath: String? = null
    private var recordingStartTimeMs: Long = 0L
    private var onCompleteCallback: ((RecordedClip?) -> Unit)? = null

    override val currentRecordingFilePath: String? get() = activeRecordingPath

    private var isSessionConfigured = false

    override fun prepareCamera() {
        if (!isSessionConfigured) {
            setupSession()
        }
        scope.launch(Dispatchers.Default) {
            if (!captureSession.isRunning()) {
                captureSession.startRunning()
            }
        }
    }

    override fun stopCamera() {
        scope.launch(Dispatchers.Default) {
            if (captureSession.isRunning()) {
                captureSession.stopRunning()
            }
        }
    }

    private fun setupSession() {
        if (isSessionConfigured) return
        isSessionConfigured = true
        captureSession.beginConfiguration()

        val videoDevice = findCamera(currentCameraPosition)
        if (videoDevice != null) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(videoDevice, null) as? AVCaptureDeviceInput
            if (input != null && captureSession.canAddInput(input)) {
                captureSession.addInput(input)
                videoInput = input
            }
        }

        val audioDevice = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeAudio)
        if (audioDevice != null) {
            val input = AVCaptureDeviceInput.deviceInputWithDevice(audioDevice, null) as? AVCaptureDeviceInput
            if (input != null && captureSession.canAddInput(input)) {
                captureSession.addInput(input)
                audioInput = input
            }
        }

        val output = AVCaptureMovieFileOutput()
        if (captureSession.canAddOutput(output)) {
            captureSession.addOutput(output)
            movieOutput = output
        }

        captureSession.commitConfiguration()
    }

    private fun findCamera(position: platform.AVFoundation.AVCaptureDevicePosition): AVCaptureDevice? {
        val discovery = discoverySessionWithDeviceTypes(
            deviceTypes = listOf(AVCaptureDeviceTypeBuiltInWideAngleCamera),
            mediaType = AVMediaTypeVideo,
            position = position,
        )
        return discovery.devices.firstOrNull() as? AVCaptureDevice
    }

    override fun startRecording(
        onAudioLevel: (Float) -> Unit,
        onComplete: (RecordedClip?) -> Unit,
    ) {
        if (!isSessionConfigured) {
            setupSession()
        }
        if (!captureSession.isRunning()) {
            captureSession.startRunning()
        }
        val output = movieOutput ?: run {
            onComplete(null)
            return
        }

        val fileName = "video_circle_${currentTimeMillis()}.mp4"
        val filePath = NSTemporaryDirectory() + fileName
        activeRecordingPath = filePath
        recordingStartTimeMs = currentTimeMillis()
        onCompleteCallback = onComplete

        val delegate = RecordingDelegate { url, error ->
            val duration = (currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(500L)
            if (error != null || url == null) {
                onCompleteCallback?.invoke(null)
            } else {
                onCompleteCallback?.invoke(
                    RecordedClip(
                        path = filePath,
                        durationMs = duration,
                        width = 720,
                        height = 720,
                        thumbnailPath = null,
                    )
                )
            }
            onCompleteCallback = null
            activeRecordingPath = null
        }
        recordingDelegate = delegate
        output.startRecordingToOutputFileURL(NSURL.fileURLWithPath(filePath), delegate)
    }

    override fun stopRecording() {
        movieOutput?.stopRecording()
    }

    override fun cancelRecording() {
        movieOutput?.stopRecording()
        activeRecordingPath?.let { path ->
            NSFileManager.defaultManager.removeItemAtPath(path, null)
        }
        activeRecordingPath = null
        onCompleteCallback = null
    }

    override fun switchCamera(): Boolean {
        val newPosition = if (currentCameraPosition == AVCaptureDevicePositionFront) {
            AVCaptureDevicePositionBack
        } else {
            AVCaptureDevicePositionFront
        }

        val newDevice = findCamera(newPosition) ?: return false

        captureSession.beginConfiguration()
        videoInput?.let { captureSession.removeInput(it) }

        val newInput = AVCaptureDeviceInput.deviceInputWithDevice(newDevice, null) as? AVCaptureDeviceInput
        val added = if (newInput != null && captureSession.canAddInput(newInput)) {
            captureSession.addInput(newInput)
            videoInput = newInput
            currentCameraPosition = newPosition
            true
        } else {
            false
        }
        captureSession.commitConfiguration()
        return added
    }

    var previewView: UIView? = null

    override fun capturePreviewBitmap(): androidx.compose.ui.graphics.ImageBitmap? {
        val view = previewView ?: return null
        return runCatching {
            val (width, height) = view.bounds.useContents { size.width to size.height }
            if (width <= 0.0 || height <= 0.0) return null

            val cgSize = kotlinx.cinterop.cValue<platform.CoreGraphics.CGSize> {
                this.width = width
                this.height = height
            }
            platform.UIKit.UIGraphicsBeginImageContextWithOptions(cgSize, false, 0.0)
            view.drawViewHierarchyInRect(view.bounds, afterScreenUpdates = false)
            val uiImage = platform.UIKit.UIGraphicsGetImageFromCurrentImageContext()
            platform.UIKit.UIGraphicsEndImageContext()

            val jpegData = uiImage?.let { platform.UIKit.UIImageJPEGRepresentation(it, 0.85) } ?: return null
            val length = jpegData.length.toInt()
            if (length <= 0) return null

            val bytes = ByteArray(length)
            bytes.usePinned { pinned ->
                platform.posix.memcpy(pinned.addressOf(0), jpegData.bytes, jpegData.length)
            }
            org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }.getOrNull()
    }

    override fun zoomBy(scaleFactor: Float): Float? = null
    override fun setZoomRatio(zoomRatio: Float): Float? = null

    override fun deleteTempFile(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }

    fun release() {
        scope.launch(Dispatchers.Default) {
            if (captureSession.isRunning()) {
                captureSession.stopRunning()
            }
        }
    }

    private class RecordingDelegate(
        val onFinished: (NSURL?, NSError?) -> Unit,
    ) : NSObject(), AVCaptureFileOutputRecordingDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureFileOutput,
            didFinishRecordingToOutputFileAtURL: NSURL,
            fromConnections: List<*>,
            error: NSError?,
        ) {
            onFinished(didFinishRecordingToOutputFileAtURL, error)
        }
    }
}

@Composable
actual fun rememberPlatformVideoRecorder(): VideoRecorderBridge? {
    val recorder = remember { IosVideoRecorder() }
    DisposableEffect(recorder) {
        onDispose {
            recorder.release()
        }
    }
    return recorder
}

@Composable
actual fun rememberPlatformVoiceRecorder(): PlatformVoiceRecorderBridge? {
    val scope = rememberCoroutineScope()
    return remember(scope) { IosVoiceRecorder(scope) }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformCameraPreview(
    recorder: VideoRecorderBridge?,
    modifier: Modifier,
) {
    val iosRecorder = recorder as? IosVideoRecorder
    if (iosRecorder != null) {
        UIKitView(
            factory = {
                val container = UIView()
                iosRecorder.previewView = container
                val previewLayer = AVCaptureVideoPreviewLayer(session = iosRecorder.captureSession).apply {
                    videoGravity = AVLayerVideoGravityResizeAspectFill
                    frame = container.bounds
                }
                container.layer.addSublayer(previewLayer)
                container
            },
            modifier = modifier,
            update = { view ->
                iosRecorder.previewView = view
                view.layer.sublayers?.firstOrNull()?.let { layer ->
                    (layer as? AVCaptureVideoPreviewLayer)?.frame = view.bounds
                }
            }
        )
    } else {
        Box(modifier = modifier)
    }
}
