package me.floow.data.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.floow.domain.settings.AutoDownloadConfig
import me.floow.domain.settings.AutoDownloadPreset
import me.floow.domain.media.DownloadableMediaType
import me.floow.domain.media.MediaDownloadController
import me.floow.domain.media.MediaDownloadPriority
import me.floow.domain.media.MediaDownloadState
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class MediaDownloadManager(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MediaDownloadController {
    private val appContext = context.applicationContext
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activePriorities = ConcurrentHashMap<String, MediaDownloadPriority>()
    private val activeConnections = ConcurrentHashMap<String, HttpURLConnection>()
    private val backgroundConcurrencyLimit = Semaphore(3)
    private val activeForegroundDownloads = MutableStateFlow(0)

    private val _states = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
    override val states: StateFlow<Map<String, MediaDownloadState>> = _states.asStateFlow()

    override fun getDownloadState(url: String): MediaDownloadState {
        val state = _states.value[url]
        if (state != null) {
            if (state is MediaDownloadState.Completed) {
                if (!File(state.localPath).exists()) {
                    _states.update { it - url }
                    return MediaDownloadState.Idle
                }
            }
            return state
        }
        for (type in DownloadableMediaType.values()) {
            val file = getTargetFile(url, type)
            if (file.exists() && file.length() > 0L) {
                val completed = MediaDownloadState.Completed(file.absolutePath)
                _states.update { it + (url to completed) }
                return completed
            }
        }
        return MediaDownloadState.Idle
    }

    override fun isDownloaded(url: String, mediaType: DownloadableMediaType): Boolean {
        val target = getTargetFile(url, mediaType)
        return target.exists() && target.length() > 0L
    }

    fun getLocalFileIfDownloaded(url: String, mediaType: DownloadableMediaType): File? {
        val target = getTargetFile(url, mediaType)
        return if (target.exists() && target.length() > 0L) target else null
    }

    override fun getLocalPathIfDownloaded(url: String, mediaType: DownloadableMediaType): String? {
        val file = getLocalFileIfDownloaded(url, mediaType) ?: return null
        val path = file.absolutePath
        if (_states.value[url] !is MediaDownloadState.Completed) {
            _states.update { it + (url to MediaDownloadState.Completed(path)) }
        }
        return path
    }

    override fun registerLocalFile(url: String, localPath: String, mediaType: DownloadableMediaType) {
        if (url.isBlank() || localPath.isBlank()) return
        val srcFile = File(localPath)
        if (!srcFile.exists() || srcFile.length() == 0L) return

        activePriorities.remove(url)
        activeJobs.remove(url)?.cancel()

        val targetFile = getTargetFile(url, mediaType)
        if (srcFile.canonicalPath != targetFile.canonicalPath) {
            runCatching {
                srcFile.copyTo(targetFile, overwrite = true)
            }
        }
        val finalPath = if (targetFile.exists() && targetFile.length() > 0L) {
            targetFile.absolutePath
        } else {
            srcFile.absolutePath
        }
        _states.update { it + (url to MediaDownloadState.Completed(finalPath)) }
    }

    override fun enqueueAutoDownload(
        url: String,
        mediaType: DownloadableMediaType,
        sizeBytes: Long,
    ) {
        val targetFile = getTargetFile(url, mediaType)
        if (targetFile.exists() && targetFile.length() > 0L) {
            _states.update { it + (url to MediaDownloadState.Completed(targetFile.absolutePath)) }
            return
        }
        val should = shouldAutoDownload(mediaType, sizeBytes)
        android.util.Log.d("MediaDownloadManager", "enqueueAutoDownload url=$url, type=$mediaType, size=$sizeBytes, should=$should")
        if (!should) {
            return
        }
        enqueue(
            url = url,
            mediaType = mediaType,
            priority = MediaDownloadPriority.BACKGROUND,
        )
    }

    fun enqueue(
        url: String,
        mediaType: DownloadableMediaType,
        priority: MediaDownloadPriority = MediaDownloadPriority.VISIBLE,
        onComplete: ((File) -> Unit)? = null,
    ) {
        if (url.isBlank() || url.startsWith("file://")) {
            return
        }

        if (priority == MediaDownloadPriority.BACKGROUND && !shouldAutoDownload(mediaType, 0L)) {
            return
        }

        val targetFile = getTargetFile(url, mediaType)
        if (targetFile.exists() && targetFile.length() > 0L) {
            _states.update { it + (url to MediaDownloadState.Completed(targetFile.absolutePath)) }
            onComplete?.invoke(targetFile)
            return
        }

        val currentJob = activeJobs[url]
        val currentPriority = activePriorities[url]
        if (currentJob != null) {
            if (priority == MediaDownloadPriority.VISIBLE && currentPriority == MediaDownloadPriority.BACKGROUND) {
                currentJob.cancel()
                activeJobs.remove(url)
                activePriorities.remove(url)
            } else {
                return
            }
        }

        activePriorities[url] = priority
        _states.update {
            it + (url to MediaDownloadState.Downloading(0f, 0L, 0L))
        }

        val job = scope.launch {
            if (priority == MediaDownloadPriority.VISIBLE) {
                activeForegroundDownloads.update { it + 1 }
            }
            try {
                if (priority == MediaDownloadPriority.VISIBLE) {
                    downloadInternal(url, targetFile, priority, onComplete)
                } else {
                    if (activeForegroundDownloads.value > 0) {
                        activeForegroundDownloads.first { it == 0 }
                    }
                    backgroundConcurrencyLimit.withPermit {
                        if (activeForegroundDownloads.value > 0) {
                            activeForegroundDownloads.first { it == 0 }
                        }
                        downloadInternal(url, targetFile, priority, onComplete)
                    }
                }
            } finally {
                if (priority == MediaDownloadPriority.VISIBLE) {
                    activeForegroundDownloads.update { (it - 1).coerceAtLeast(0) }
                }
                activeJobs.remove(url)
                activePriorities.remove(url)
            }
        }
        activeJobs[url] = job
    }

    override fun enqueue(url: String, mediaType: DownloadableMediaType, priority: MediaDownloadPriority) {
        enqueue(url = url, mediaType = mediaType, priority = priority, onComplete = null)
    }

    override fun cancel(url: String) {
        activePriorities.remove(url)
        activeConnections.remove(url)?.disconnect()
        activeJobs.remove(url)?.cancel()
        _states.update { it - url }
    }

    override fun resetAll() {
        activeForegroundDownloads.value = 0
        activeConnections.values.forEach { runCatching { it.disconnect() } }
        activeConnections.clear()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        activePriorities.clear()
        _states.value = emptyMap()
    }

    private suspend fun downloadInternal(
        sourceUrl: String,
        targetFile: File,
        priority: MediaDownloadPriority,
        onComplete: ((File) -> Unit)?,
    ) {
        _states.update {
            it + (sourceUrl to MediaDownloadState.Downloading(0f, 0L, 0L))
        }

        val tempFile = File(targetFile.parentFile, targetFile.name + ".tmp")
        var connection: HttpURLConnection? = null
        try {
            val effectiveUrl = when {
                sourceUrl.startsWith("//") -> "https:$sourceUrl"
                sourceUrl.startsWith("/") -> "https://floow.me$sourceUrl"
                else -> sourceUrl
            }
            android.util.Log.d("MediaDownloadManager", "Starting download: $effectiveUrl (priority=$priority)")
            val url = URL(effectiveUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            activeConnections[sourceUrl] = connection
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("HTTP $responseCode from $effectiveUrl")
            }

            val contentLength = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            tempFile.parentFile?.mkdirs()

            connection.inputStream.buffered(64 * 1024).use { input ->
                FileOutputStream(tempFile).buffered(64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastProgressTime = 0L
                    var lastEmittedProgress = 0f

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        currentCoroutineContext().ensureActive()
                        if (priority == MediaDownloadPriority.BACKGROUND && activeForegroundDownloads.value > 0) {
                            activeForegroundDownloads.first { it == 0 }
                        }
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (contentLength > 0L) {
                            (totalRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime >= 80L || progress - lastEmittedProgress >= 0.05f) {
                            lastProgressTime = now
                            lastEmittedProgress = progress
                            _states.update {
                                it + (sourceUrl to MediaDownloadState.Downloading(progress, totalRead, contentLength))
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                val moved = tempFile.renameTo(targetFile)
                if (!moved) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                android.util.Log.d("MediaDownloadManager", "Download complete: $sourceUrl -> ${targetFile.absolutePath}")
                _states.update {
                    it + (sourceUrl to MediaDownloadState.Completed(targetFile.absolutePath))
                }
                onComplete?.invoke(targetFile)
            } else {
                error("Downloaded file is empty")
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            tempFile.delete()
            android.util.Log.e("MediaDownloadManager", "Download failed for $sourceUrl: ${e.message}", e)
            if (priority == MediaDownloadPriority.BACKGROUND) {
                _states.update { it - sourceUrl }
            } else {
                _states.update {
                    it + (sourceUrl to MediaDownloadState.Failed(e.message ?: "Download failed"))
                }
            }
        } finally {
            activeConnections.remove(sourceUrl)
            connection?.disconnect()
        }
    }

    private fun getAutoDownloadConfig(): AutoDownloadConfig {
        val prefs = appContext.getSharedPreferences("flow_media_auto_download_prefs", Context.MODE_PRIVATE)
        val presetName = prefs.getString("preset", AutoDownloadPreset.MEDIUM.name)
        val preset = runCatching { AutoDownloadPreset.valueOf(presetName!!) }.getOrDefault(AutoDownloadPreset.MEDIUM)
        return when (preset) {
            AutoDownloadPreset.LOW -> AutoDownloadConfig(
                cellularEnabled = prefs.getBoolean("cellular", false),
                wifiEnabled = prefs.getBoolean("wifi", true),
                roamingEnabled = prefs.getBoolean("roaming", false),
                preset = preset,
                maxPhotoBytes = 2L * 1024L * 1024L,
                maxVideoBytes = 5L * 1024L * 1024L,
                maxVoiceBytes = 2L * 1024L * 1024L,
            )
            AutoDownloadPreset.MEDIUM -> AutoDownloadConfig(
                cellularEnabled = prefs.getBoolean("cellular", true),
                wifiEnabled = prefs.getBoolean("wifi", true),
                roamingEnabled = prefs.getBoolean("roaming", false),
                preset = preset,
                maxPhotoBytes = 10L * 1024L * 1024L,
                maxVideoBytes = 15L * 1024L * 1024L,
                maxVoiceBytes = 5L * 1024L * 1024L,
            )
            AutoDownloadPreset.HIGH -> AutoDownloadConfig(
                cellularEnabled = prefs.getBoolean("cellular", true),
                wifiEnabled = prefs.getBoolean("wifi", true),
                roamingEnabled = prefs.getBoolean("roaming", true),
                preset = preset,
                maxPhotoBytes = 50L * 1024L * 1024L,
                maxVideoBytes = 100L * 1024L * 1024L,
                maxVoiceBytes = 20L * 1024L * 1024L,
            )
            AutoDownloadPreset.DISABLED -> AutoDownloadConfig(
                cellularEnabled = false,
                wifiEnabled = false,
                roamingEnabled = false,
                preset = preset,
                maxPhotoBytes = 0L,
                maxVideoBytes = 0L,
                maxVoiceBytes = 0L,
            )
        }
    }

    private fun shouldAutoDownload(mediaType: DownloadableMediaType, sizeBytes: Long): Boolean {
        val config = getAutoDownloadConfig()
        if (config.preset == AutoDownloadPreset.DISABLED) {
            android.util.Log.d("MediaDownloadManager", "shouldAutoDownload: preset is DISABLED")
            return false
        }

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true

        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isRoaming = isCellular && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)

        if (isRoaming && !config.roamingEnabled) {
            android.util.Log.d("MediaDownloadManager", "shouldAutoDownload rejected: isRoaming=$isRoaming, roamingEnabled=${config.roamingEnabled}")
            return false
        }
        if (isCellular && !isWifi && !config.cellularEnabled) {
            android.util.Log.d("MediaDownloadManager", "shouldAutoDownload rejected: isCellular=$isCellular, isWifi=$isWifi, cellularEnabled=${config.cellularEnabled}")
            return false
        }
        if (isWifi && !config.wifiEnabled) {
            android.util.Log.d("MediaDownloadManager", "shouldAutoDownload rejected: isWifi=$isWifi, wifiEnabled=${config.wifiEnabled}")
            return false
        }

        val limit = when (mediaType) {
            DownloadableMediaType.PHOTO -> config.maxPhotoBytes
            DownloadableMediaType.VOICE -> config.maxVoiceBytes
            DownloadableMediaType.VIDEO_CIRCLE -> config.maxVideoBytes
        }
        val allowed = sizeBytes <= 0L || sizeBytes <= limit
        if (!allowed) {
            android.util.Log.d("MediaDownloadManager", "shouldAutoDownload rejected: sizeBytes=$sizeBytes exceeds limit=$limit")
        }
        return allowed
    }

    private fun getTargetFile(url: String, mediaType: DownloadableMediaType): File {
        val folderName = when (mediaType) {
            DownloadableMediaType.PHOTO -> "chat_images"
            DownloadableMediaType.VOICE -> "voice_media_downloads"
            DownloadableMediaType.VIDEO_CIRCLE -> "video_circle_media_cache"
        }
        val dir = File(appContext.cacheDir, folderName).apply { mkdirs() }
        val ext = when (mediaType) {
            DownloadableMediaType.PHOTO -> ".jpg"
            DownloadableMediaType.VOICE -> ".ogg"
            DownloadableMediaType.VIDEO_CIRCLE -> ".mp4"
        }
        val safeHash = Math.abs(url.hashCode()).toString(16)
        return File(dir, "media_${safeHash}$ext")
    }
}
