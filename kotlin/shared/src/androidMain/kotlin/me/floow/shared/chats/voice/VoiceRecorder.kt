package me.floow.shared.chats.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.floow.shared.chats.uilogic.direct.RecordedVoiceClip
import java.io.File
import java.io.IOException
import java.util.UUID

class VoiceRecorder(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var currentMimeType: String = "audio/mp4"
    private var recordingStartTimeMs: Long = 0L
    private var isRecording = false

    private val rawAmplitudes = mutableListOf<Float>()
    private var amplitudeJob: Job? = null

    private var onAudioLevelCallback: ((Float) -> Unit)? = null
    private var onMaxDurationReached: (() -> Unit)? = null

    val isCurrentlyRecording: Boolean get() = isRecording

    fun startRecording(
        outputDirectory: File? = null,
        onAudioLevel: ((Float) -> Unit)? = null,
        onMaxDuration: (() -> Unit)? = null,
    ): Boolean {
        if (isRecording) return false
        onAudioLevelCallback = onAudioLevel
        onMaxDurationReached = onMaxDuration
        rawAmplitudes.clear()

        val dir = outputDirectory ?: File(context.cacheDir, "voice_messages").apply { mkdirs() }
        if (!dir.exists()) dir.mkdirs()

        // AAC/M4A is supported consistently by Android MediaRecorder and by the
        // backend chat_voice allowlist.
        val extension = "m4a"
        currentMimeType = "audio/mp4"

        val outputFile = File(dir, "voice_${UUID.randomUUID()}.$extension")
        currentOutputFile = outputFile

        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(64000)
            recorder.setAudioSamplingRate(44100)

            recorder.setOutputFile(outputFile.absolutePath)
            recorder.setMaxDuration(MAX_VOICE_DURATION_MS.toInt())
            recorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    onMaxDurationReached?.invoke()
                }
            }

            recorder.prepare()
            recorder.start()

            mediaRecorder = recorder
            recordingStartTimeMs = SystemClock.elapsedRealtime()
            isRecording = true

            startAmplitudePolling()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
            false
        }
    }

    private fun startAmplitudePolling() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            while (isActive && isRecording) {
                delay(80)
                val recorder = mediaRecorder ?: break
                val maxAmp = try {
                    recorder.maxAmplitude
                } catch (e: Exception) {
                    0
                }
                val normalized = (maxAmp / 32767f).coerceIn(0f, 1f)
                rawAmplitudes.add(normalized)
                onAudioLevelCallback?.invoke(normalized)

                val elapsed = SystemClock.elapsedRealtime() - recordingStartTimeMs
                if (elapsed >= MAX_VOICE_DURATION_MS) {
                    onMaxDurationReached?.invoke()
                    break
                }
            }
        }
    }

    fun stopRecording(): RecordedVoiceClip? {
        if (!isRecording) return null
        val durationMs = (SystemClock.elapsedRealtime() - recordingStartTimeMs).coerceAtLeast(0L)
        val file = currentOutputFile
        val mimeType = currentMimeType
        val amplitudesSnapshot = rawAmplitudes.toList()

        stopInternal()

        if (file == null || !file.exists() || file.length() <= 0L || durationMs < MIN_VOICE_DURATION_MS) {
            file?.delete()
            return null
        }

        val waveform = generateWaveform(amplitudesSnapshot, targetPoints = TARGET_WAVEFORM_POINTS)
        return RecordedVoiceClip(
            filePath = file.absolutePath,
            durationMs = durationMs,
            waveform = waveform,
            mimeType = mimeType,
        )
    }

    fun cancelRecording() {
        val file = currentOutputFile
        stopInternal()
        file?.delete()
    }

    private fun stopInternal() {
        isRecording = false
        amplitudeJob?.cancel()
        amplitudeJob = null
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // ignore
        }
        cleanup()
    }

    private fun cleanup() {
        isRecording = false
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            // ignore
        }
        mediaRecorder = null
    }

    companion object {
        const val MAX_VOICE_DURATION_MS = 600_000L // 10 minutes
        const val MIN_VOICE_DURATION_MS = 300L
        const val TARGET_WAVEFORM_POINTS = 48

        fun generateWaveform(raw: List<Float>, targetPoints: Int = TARGET_WAVEFORM_POINTS): List<Int> {
            if (raw.isEmpty()) {
                return List(targetPoints) { 15 }
            }

            val result = ArrayList<Int>(targetPoints)
            val chunkSize = raw.size.toFloat() / targetPoints

            var maxSample = 0.05f
            val intermediate = FloatArray(targetPoints)

            for (i in 0 until targetPoints) {
                val startIdx = (i * chunkSize).toInt().coerceIn(0, raw.lastIndex)
                val endIdx = ((i + 1) * chunkSize).toInt().coerceIn(startIdx, raw.size)
                var sum = 0f
                var count = 0
                for (j in startIdx until endIdx) {
                    sum += raw[j]
                    count++
                }
                val avg = if (count > 0) sum / count else raw[startIdx]
                intermediate[i] = avg
                if (avg > maxSample) {
                    maxSample = avg
                }
            }

            for (i in 0 until targetPoints) {
                val normalized = (intermediate[i] / maxSample).coerceIn(0f, 1f)
                val scaled = (8 + normalized * 92).toInt().coerceIn(0, 100)
                result.add(scaled)
            }

            return result
        }
    }
}
