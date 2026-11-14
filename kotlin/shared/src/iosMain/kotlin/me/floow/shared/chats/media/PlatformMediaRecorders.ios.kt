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
import me.floow.shared.chats.camera.VideoCirclePreset
import me.floow.shared.chats.camera.VideoCircleSpec
import me.floow.shared.chats.uilogic.direct.RecordedClip
import me.floow.shared.chats.uilogic.direct.RecordedVoiceClip
import me.floow.shared.chats.uilogic.direct.VideoRecorderBridge
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVAssetWriter
import platform.AVFoundation.AVAssetWriterInput
import platform.AVFoundation.AVAssetWriterStatusCompleted
import platform.AVFoundation.AVAssetWriterStatusFailed
import platform.AVFoundation.AVAssetWriterStatusWriting
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVCaptureAudioDataOutput
import platform.AVFoundation.AVCaptureAudioDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceDiscoverySession
import platform.AVFoundation.AVCaptureDeviceDiscoverySession.Companion.discoverySessionWithDeviceTypes
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureDevicePositionBack
import platform.AVFoundation.AVCaptureDevicePositionFront
import platform.AVFoundation.AVCaptureDeviceTypeBuiltInWideAngleCamera
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureSessionPreset640x480
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureVideoDataOutputSampleBufferDelegateProtocol
import platform.AVFoundation.AVCaptureVideoOrientationPortrait
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVFileTypeMPEG4
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVVideoAverageBitRateKey
import platform.AVFoundation.AVVideoCodecKey
import platform.AVFoundation.AVVideoCodecTypeH264
import platform.AVFoundation.AVVideoCompressionPropertiesKey
import platform.AVFoundation.AVVideoExpectedSourceFrameRateKey
import platform.AVFoundation.AVVideoHeightKey
import platform.AVFoundation.AVVideoMaxKeyFrameIntervalKey
import platform.AVFoundation.AVVideoProfileLevelH264MainAutoLevel
import platform.AVFoundation.AVVideoProfileLevelKey
import platform.AVFoundation.AVVideoScalingModeKey
import platform.AVFoundation.AVVideoScalingModeResizeAspectFill
import platform.AVFoundation.AVVideoWidthKey
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMSampleBufferRef
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_queue_create

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
class IosVideoRecorder(
    override val spec: VideoCircleSpec = VideoCirclePreset.Standard,
) : VideoRecorderBridge {
    private val scope = CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    val captureSession = AVCaptureSession().apply {
        sessionPreset = AVCaptureSessionPreset640x480
    }

    private var currentCameraPosition = AVCaptureDevicePositionFront
    private var videoInput: AVCaptureDeviceInput? = null
    private var audioInput: AVCaptureDeviceInput? = null
    private var videoOutput: AVCaptureVideoDataOutput? = null
    private var audioOutput: AVCaptureAudioDataOutput? = null
    private val recordingQueue = dispatch_queue_create("me.floow.videocircle.recording", null)
    private var sampleBufferDelegate: VideoCircleSampleDelegate? = null

    private var activeRecordingPath: String? = null
    private var recordingStartTimeMs: Long = 0L
    private var onCompleteCallback: ((RecordedClip?) -> Unit)? = null

    private var isRecording = false
    private var hasStartedSession = false
    private var firstFrameTimeSeconds: Double? = null
    private var assetWriter: AVAssetWriter? = null
    private var assetWriterVideoInput: AVAssetWriterInput? = null
    private var assetWriterAudioInput: AVAssetWriterInput? = null

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

        val delegate = VideoCircleSampleDelegate { output, buffer ->
            handleSampleBuffer(output, buffer)
        }
        sampleBufferDelegate = delegate

        val vOutput = AVCaptureVideoDataOutput().apply {
            alwaysDiscardsLateVideoFrames = true
            setSampleBufferDelegate(delegate, recordingQueue)
        }
        if (captureSession.canAddOutput(vOutput)) {
            captureSession.addOutput(vOutput)
            videoOutput = vOutput
        }

        val aOutput = AVCaptureAudioDataOutput().apply {
            setSampleBufferDelegate(delegate, recordingQueue)
        }
        if (captureSession.canAddOutput(aOutput)) {
            captureSession.addOutput(aOutput)
            audioOutput = aOutput
        }

        configureVideoConnection()

        captureSession.commitConfiguration()
    }

    private fun configureVideoConnection() {
        val output = videoOutput ?: return
        val connection = output.connectionWithMediaType(AVMediaTypeVideo) ?: return
        if (connection.isVideoOrientationSupported()) {
            connection.videoOrientation = AVCaptureVideoOrientationPortrait
        }
        if (connection.isVideoMirroringSupported()) {
            connection.videoMirrored = (currentCameraPosition == AVCaptureDevicePositionFront)
        }
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
        scope.launch(Dispatchers.Default) {
            if (!captureSession.isRunning()) {
                captureSession.startRunning()
            }
        }

        val fileName = "video_circle_${currentTimeMillis()}.mp4"
        val filePath = NSTemporaryDirectory() + fileName
        val fileUrl = NSURL.fileURLWithPath(filePath)

        NSFileManager.defaultManager.removeItemAtPath(filePath, null)

        val writer = AVAssetWriter.assetWriterWithURL(fileUrl, AVFileTypeMPEG4, null) ?: run {
            onComplete(null)
            return
        }
        writer.shouldOptimizeForNetworkUse = true

        val compressionProperties: Map<Any?, Any?> = mapOf(
            AVVideoAverageBitRateKey to spec.videoBitrateBps,
            AVVideoExpectedSourceFrameRateKey to spec.frameRate,
            AVVideoMaxKeyFrameIntervalKey to (spec.frameRate * spec.iFrameIntervalSeconds),
            AVVideoProfileLevelKey to AVVideoProfileLevelH264MainAutoLevel,
        )

        val videoSettings: Map<Any?, Any?> = mapOf(
            AVVideoCodecKey to AVVideoCodecTypeH264,
            AVVideoWidthKey to spec.targetSidePx,
            AVVideoHeightKey to spec.targetSidePx,
            AVVideoScalingModeKey to AVVideoScalingModeResizeAspectFill,
            AVVideoCompressionPropertiesKey to compressionProperties,
        )

        val vInput = AVAssetWriterInput.assetWriterInputWithMediaType(AVMediaTypeVideo, videoSettings).apply {
            expectsMediaDataInRealTime = true
        }

        val audioSettings: Map<Any?, Any?> = mapOf(
            AVFormatIDKey to kAudioFormatMPEG4AAC,
            AVSampleRateKey to spec.audioSampleRateHz.toDouble(),
            AVNumberOfChannelsKey to spec.audioChannels,
            AVEncoderBitRateKey to spec.audioBitrateBps,
        )

        val aInput = AVAssetWriterInput.assetWriterInputWithMediaType(AVMediaTypeAudio, audioSettings).apply {
            expectsMediaDataInRealTime = true
        }

        if (!writer.canAddInput(vInput) || !writer.canAddInput(aInput)) {
            onComplete(null)
            return
        }

        writer.addInput(vInput)
        writer.addInput(aInput)

        activeRecordingPath = filePath
        onCompleteCallback = onComplete
        recordingStartTimeMs = currentTimeMillis()

        dispatch_async(recordingQueue) {
            assetWriter = writer
            assetWriterVideoInput = vInput
            assetWriterAudioInput = aInput
            hasStartedSession = false
            firstFrameTimeSeconds = null
            isRecording = true
        }
    }

    private fun handleSampleBuffer(output: AVCaptureOutput, sampleBuffer: CMSampleBufferRef) {
        if (!isRecording) return
        val writer = assetWriter ?: return
        val vInput = assetWriterVideoInput ?: return
        val aInput = assetWriterAudioInput ?: return

        val isVideo = (output == videoOutput)

        if (!hasStartedSession) {
            if (!isVideo) {
                // Аудиосэмпл до первого видеосэмпла — отбрасываем для защиты от черного экрана
                return
            }
            val timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            if (!writer.startWriting()) {
                return
            }
            writer.startSessionAtSourceTime(timestamp)
            hasStartedSession = true
            firstFrameTimeSeconds = CMTimeGetSeconds(timestamp)
        }

        if (writer.status != AVAssetWriterStatusWriting) {
            return
        }

        // Аппаратный автостоп по таймстемпу кадров (60 секунд)
        val timestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
        val firstTime = firstFrameTimeSeconds
        if (firstTime != null && (CMTimeGetSeconds(timestamp) - firstTime) >= spec.maxDurationSeconds) {
            internalStopRecording()
            return
        }

        if (isVideo) {
            if (vInput.isReadyForMoreMediaData()) {
                vInput.appendSampleBuffer(sampleBuffer)
            }
        } else {
            if (aInput.isReadyForMoreMediaData()) {
                aInput.appendSampleBuffer(sampleBuffer)
            }
        }
    }

    override fun stopRecording() {
        dispatch_async(recordingQueue) {
            internalStopRecording()
        }
    }

    private fun internalStopRecording() {
        if (!isRecording) return
        isRecording = false

        val writer = assetWriter
        val vInput = assetWriterVideoInput
        val aInput = assetWriterAudioInput
        val path = activeRecordingPath
        val callback = onCompleteCallback
        val startTime = recordingStartTimeMs

        assetWriter = null
        assetWriterVideoInput = null
        assetWriterAudioInput = null
        activeRecordingPath = null
        onCompleteCallback = null

        if (writer == null || vInput == null || aInput == null || path == null) {
            callback?.invoke(null)
            return
        }

        if (!hasStartedSession || writer.status != AVAssetWriterStatusWriting) {
            vInput.markAsFinished()
            aInput.markAsFinished()
            writer.cancelWriting()
            NSFileManager.defaultManager.removeItemAtPath(path, null)
            callback?.invoke(null)
            return
        }

        vInput.markAsFinished()
        aInput.markAsFinished()

        writer.finishWritingWithCompletionHandler {
            val duration = (currentTimeMillis() - startTime).coerceAtLeast(500L)
            if (writer.status == AVAssetWriterStatusCompleted) {
                val thumbPath = generateIosVideoThumbnail(path)
                val clip = RecordedClip(
                    path = path,
                    durationMs = duration,
                    width = spec.targetSidePx,
                    height = spec.targetSidePx,
                    thumbnailPath = thumbPath,
                )
                callback?.invoke(clip)
            } else {
                callback?.invoke(null)
            }
        }
    }

    private fun generateIosVideoThumbnail(videoPath: String): String? {
        return runCatching {
            val url = NSURL.fileURLWithPath(videoPath)
            val asset = AVURLAsset(uRL = url, options = null)
            val generator = AVAssetImageGenerator(asset = asset).apply {
                appliesPreferredTrackTransform = true
            }
            val time = CMTimeMake(value = 0, timescale = 1)
            val cgImage = generator.copyCGImageAtTime(time, actualTime = null, error = null) ?: return null
            val uiImage = UIImage.imageWithCGImage(cgImage)
            val data = UIImageJPEGRepresentation(uiImage, 0.8) ?: return null
            val thumbPath = videoPath.removeSuffix(".mp4") + "_thumb.jpg"
            data.writeToFile(thumbPath, atomically = true)
            thumbPath
        }.getOrNull()
    }

    override fun cancelRecording() {
        dispatch_async(recordingQueue) {
            internalCancelRecording()
        }
    }

    private fun internalCancelRecording() {
        if (!isRecording && assetWriter == null) return
        isRecording = false

        val writer = assetWriter
        val vInput = assetWriterVideoInput
        val aInput = assetWriterAudioInput
        val path = activeRecordingPath
        val callback = onCompleteCallback

        assetWriter = null
        assetWriterVideoInput = null
        assetWriterAudioInput = null
        activeRecordingPath = null
        onCompleteCallback = null

        vInput?.markAsFinished()
        aInput?.markAsFinished()
        writer?.cancelWriting()

        path?.let {
            NSFileManager.defaultManager.removeItemAtPath(it, null)
        }
        callback?.invoke(null)
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
            configureVideoConnection()
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

    private class VideoCircleSampleDelegate(
        private val onSample: (AVCaptureOutput, CMSampleBufferRef) -> Unit,
    ) : NSObject(),
        AVCaptureVideoDataOutputSampleBufferDelegateProtocol,
        AVCaptureAudioDataOutputSampleBufferDelegateProtocol {
        override fun captureOutput(
            output: AVCaptureOutput,
            didOutputSampleBuffer: CMSampleBufferRef?,
            fromConnection: AVCaptureConnection,
        ) {
            if (didOutputSampleBuffer != null) {
                onSample(output, didOutputSampleBuffer)
            }
        }
    }
}

@Composable
actual fun rememberPlatformVideoRecorder(
    spec: VideoCircleSpec,
): VideoRecorderBridge? {
    val recorder = remember(spec) { IosVideoRecorder(spec) }
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
